package com.opsconsole.health.service;

import com.opsconsole.health.domain.HealthSnapshotEntity;
import com.opsconsole.health.repository.HealthSnapshotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class HealthHistoryService {

    private static final Duration WINDOW = Duration.ofHours(24);
    private static final int MAX_SNAPSHOTS = 500;
    private static final int MAX_CHART_POINTS = 48;

    private final HealthSnapshotRepository repository;

    public HealthHistoryService(HealthSnapshotRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void record(int total, int up, Instant at) {
        record(total, up, at, 0);
    }

    @Transactional
    public void record(int total, int up, Instant at, int avgResponseTimeMs) {
        Instant recordedAt = at != null ? at : Instant.now();
        repository.save(new HealthSnapshotEntity(total, up, recordedAt, avgResponseTimeMs));
        trim(recordedAt);
    }

    @Transactional(readOnly = true)
    public ServicesUpChart chartLast24Hours(int currentTotal, int currentUp) {
        Instant now = Instant.now();
        Instant windowStart = now.minus(WINDOW);
        List<HealthSnapshot> inWindow = repository.findByRecordedAtAfterOrderByRecordedAtAsc(windowStart).stream()
                .map(snapshot -> new HealthSnapshot(snapshot.getRecordedAt(), snapshot.getTotal(), snapshot.getUp()))
                .toList();

        List<HealthSnapshot> series = inWindow.isEmpty()
                ? List.of(new HealthSnapshot(now, currentTotal, currentUp))
                : downsample(inWindow, MAX_CHART_POINTS);

        int maxY = Math.max(1, series.stream().mapToInt(HealthSnapshot::total).max().orElse(currentTotal));
        if (currentTotal > maxY) {
            maxY = currentTotal;
        }

        List<ChartPoint> points = buildNormalizedPoints(series, windowStart, now, maxY);
        String linePath = buildLinePath(points);
        String areaPath = buildAreaPath(points);
        List<String> xLabels = xAxisLabels(windowStart, now);

        return new ServicesUpChart(
                points,
                maxY,
                currentUp,
                currentTotal,
                linePath,
                areaPath,
                xLabels
        );
    }

    private void trim(Instant now) {
        repository.deleteByRecordedAtBefore(now.minus(WINDOW));
        long count = repository.count();
        if (count <= MAX_SNAPSHOTS) {
            return;
        }
        List<HealthSnapshotEntity> oldest = repository.findAll().stream()
                .sorted((a, b) -> a.getRecordedAt().compareTo(b.getRecordedAt()))
                .limit(count - MAX_SNAPSHOTS)
                .toList();
        repository.deleteAll(oldest);
    }

    private static List<HealthSnapshot> downsample(List<HealthSnapshot> source, int maxPoints) {
        if (source.size() <= maxPoints) {
            return source;
        }
        List<HealthSnapshot> result = new ArrayList<>(maxPoints);
        double step = (double) (source.size() - 1) / (maxPoints - 1);
        for (int i = 0; i < maxPoints; i++) {
            int index = (int) Math.round(i * step);
            result.add(source.get(Math.min(index, source.size() - 1)));
        }
        return result;
    }

    private static List<ChartPoint> buildNormalizedPoints(
            List<HealthSnapshot> series,
            Instant windowStart,
            Instant windowEnd,
            int maxY
    ) {
        long spanMs = Math.max(1, Duration.between(windowStart, windowEnd).toMillis());
        List<ChartPoint> points = new ArrayList<>(series.size());
        DateTimeFormatter labelFmt = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());

        for (HealthSnapshot snapshot : series) {
            long offsetMs = Duration.between(windowStart, snapshot.at()).toMillis();
            double x = (offsetMs * 100.0) / spanMs;
            double y = 100.0 - ((snapshot.up() * 100.0) / maxY);
            points.add(new ChartPoint(
                    clamp(x, 0, 100),
                    clamp(y, 0, 100),
                    snapshot.up(),
                    labelFmt.format(snapshot.at())
            ));
        }
        return points;
    }

    private static String buildLinePath(List<ChartPoint> points) {
        if (points.isEmpty()) {
            return "M0 100 L100 100";
        }
        StringBuilder sb = new StringBuilder();
        ChartPoint first = points.get(0);
        sb.append(String.format("M %.2f %.2f", first.x(), first.y()));
        for (int i = 1; i < points.size(); i++) {
            ChartPoint p = points.get(i);
            sb.append(String.format(" L %.2f %.2f", p.x(), p.y()));
        }
        return sb.toString();
    }

    private static String buildAreaPath(List<ChartPoint> points) {
        if (points.isEmpty()) {
            return "M0 100 L100 100 Z";
        }
        StringBuilder sb = new StringBuilder();
        ChartPoint first = points.get(0);
        sb.append(String.format("M %.2f 100 L %.2f %.2f", first.x(), first.x(), first.y()));
        for (int i = 1; i < points.size(); i++) {
            ChartPoint p = points.get(i);
            sb.append(String.format(" L %.2f %.2f", p.x(), p.y()));
        }
        ChartPoint last = points.get(points.size() - 1);
        sb.append(String.format(" L %.2f 100 Z", last.x()));
        return sb.toString();
    }

    private static List<String> xAxisLabels(Instant start, Instant end) {
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneId.systemDefault());
        long spanMs = Duration.between(start, end).toMillis();
        List<String> labels = new ArrayList<>(5);
        for (int i = 0; i < 5; i++) {
            Instant t = start.plusMillis((spanMs * i) / 4);
            labels.add(fmt.format(t));
        }
        return labels;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public record HealthSnapshot(Instant at, int total, int up) {
    }

    public record ChartPoint(double x, double y, int upCount, String timeLabel) {
    }

    public record ServicesUpChart(
            List<ChartPoint> points,
            int maxY,
            int currentUp,
            int totalServices,
            String linePath,
            String areaPath,
            List<String> xAxisLabels
    ) {
    }

    @Transactional(readOnly = true)
    public ResponseTimeChart responseTimeChartLast24Hours(int currentAvgMs) {
        Instant now = Instant.now();
        Instant windowStart = now.minus(WINDOW);
        List<ResponseTimeBar> bars = repository.findByRecordedAtAfterOrderByRecordedAtAsc(windowStart).stream()
                .filter(snapshot -> snapshot.getAvgResponseTimeMs() > 0)
                .map(snapshot -> new ResponseTimeBar(snapshot.getAvgResponseTimeMs()))
                .toList();

        if (bars.isEmpty() && currentAvgMs > 0) {
            bars = List.of(new ResponseTimeBar(currentAvgMs));
        }

        List<ResponseTimeBar> display = bars.size() > 12
                ? downsampleResponseTime(bars, 12)
                : bars;

        int maxMs = Math.max(1, display.stream().mapToInt(ResponseTimeBar::avgMs).max().orElse(currentAvgMs));
        if (currentAvgMs > maxMs) {
            maxMs = currentAvgMs;
        }
        final int chartMaxMs = maxMs;

        List<ResponseTimeBar> normalized = display.stream()
                .map(bar -> new ResponseTimeBar(bar.avgMs(), barHeightPercent(bar.avgMs(), chartMaxMs)))
                .toList();

        return new ResponseTimeChart(normalized, currentAvgMs, chartMaxMs);
    }

    private static List<ResponseTimeBar> downsampleResponseTime(List<ResponseTimeBar> source, int maxBars) {
        if (source.size() <= maxBars) {
            return source;
        }
        List<ResponseTimeBar> result = new ArrayList<>(maxBars);
        double step = (double) (source.size() - 1) / (maxBars - 1);
        for (int i = 0; i < maxBars; i++) {
            int index = (int) Math.round(i * step);
            result.add(source.get(Math.min(index, source.size() - 1)));
        }
        return result;
    }

    private static int barHeightPercent(int avgMs, int maxMs) {
        return (int) Math.round((avgMs * 100.0) / Math.max(1, maxMs));
    }

    public record ResponseTimeBar(int avgMs, int heightPercent) {
        ResponseTimeBar(int avgMs) {
            this(avgMs, 0);
        }
    }

    public record ResponseTimeChart(
            List<ResponseTimeBar> bars,
            int currentAvgMs,
            int maxMs
    ) {
    }
}
