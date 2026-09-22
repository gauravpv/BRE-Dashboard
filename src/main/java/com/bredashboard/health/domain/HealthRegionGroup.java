package com.bredashboard.health.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record HealthRegionGroup(String region, List<SystemHealthView> systems) {

    public static List<HealthRegionGroup> fromSystems(List<SystemHealthView> systems) {
        Map<String, List<SystemHealthView>> grouped = new LinkedHashMap<>();
        for (SystemHealthView system : systems) {
            String region = system.region();
            if (region == null || region.isBlank()) {
                region = system.environment() != null && !system.environment().isBlank()
                        ? system.environment()
                        : "Other";
            }
            grouped.computeIfAbsent(region, key -> new ArrayList<>()).add(system);
        }

        return grouped.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER))
                .map(entry -> new HealthRegionGroup(
                        entry.getKey(),
                        entry.getValue().stream()
                                .sorted(Comparator
                                        .comparing(SystemHealthView::environmentId, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                                        .thenComparing(SystemHealthView::name, String.CASE_INSENSITIVE_ORDER))
                                .toList()))
                .toList();
    }
}
