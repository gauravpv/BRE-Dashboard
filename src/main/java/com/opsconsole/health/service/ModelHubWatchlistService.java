package com.opsconsole.health.service;



import com.opsconsole.health.domain.HealthStatus;

import com.opsconsole.health.domain.MonitoredHost;

import com.opsconsole.health.domain.MonitoredHostProd;

import com.opsconsole.health.domain.ServiceEndpointMatcher;

import com.opsconsole.health.domain.SystemHealthView;

import org.springframework.stereotype.Service;



import java.time.Instant;

import java.util.ArrayList;

import java.util.List;

import java.util.Locale;

import java.util.Map;

import java.util.Optional;



@Service

public class ModelHubWatchlistService {



    public List<SystemHealthView> evaluateUatWatchlist(

            List<MonitoredHost> watchlist,

            List<SystemHealthView> modelHubInstances,

            Map<String, String> environmentTagsById,

            Instant fetchedAt,

            String deploymentTier

    ) {

        return evaluateWatchlist(

                watchlist.stream().map(WatchlistEntry::fromUat).toList(),

                modelHubInstances,

                environmentTagsById,

                fetchedAt,

                deploymentTier

        );

    }



    public List<SystemHealthView> evaluateProdWatchlist(

            List<MonitoredHostProd> watchlist,

            List<SystemHealthView> modelHubInstances,

            Map<String, String> environmentTagsById,

            Instant fetchedAt,

            String deploymentTier

    ) {

        return evaluateWatchlist(

                watchlist.stream().map(WatchlistEntry::fromProd).toList(),

                modelHubInstances,

                environmentTagsById,

                fetchedAt,

                deploymentTier

        );

    }



    private List<SystemHealthView> evaluateWatchlist(

            List<WatchlistEntry> watchlist,

            List<SystemHealthView> modelHubInstances,

            Map<String, String> environmentTagsById,

            Instant fetchedAt,

            String deploymentTier

    ) {

        List<SystemHealthView> results = new ArrayList<>(watchlist.size());



        for (WatchlistEntry host : watchlist) {

            results.add(matchHost(host, modelHubInstances, environmentTagsById, fetchedAt, deploymentTier));

        }



        return results;

    }



    private static SystemHealthView matchHost(

            WatchlistEntry host,

            List<SystemHealthView> modelHubInstances,

            Map<String, String> environmentTagsById,

            Instant fetchedAt,

            String deploymentTier

    ) {

        Optional<SystemHealthView> match = modelHubInstances.stream()

                .filter(instance -> ServiceEndpointMatcher.matches(

                        host.host(),

                        host.port(),

                        instance.host(),

                        instance.port()

                ))

                .findFirst();



        String region = resolveRegion(host, match.map(SystemHealthView::environmentId).orElse(null), environmentTagsById);



        if (match.isEmpty()) {

            return downView(host, fetchedAt, "Not found in Model Hub", region, deploymentTier);

        }



        SystemHealthView instance = match.get();

        HealthStatus status = instance.status();

        String environmentId = instance.environmentId();

        return SystemHealthView.fromModelHub(

                host.id(),

                host.name(),

                host.host(),

                host.port(),

                host.environment(),

                region,

                status,

                status.displayLabel(),

                fetchedAt,

                instance.serviceUrl(),

                environmentId,

                instance.requests200(),

                instance.requests500(),

                instance.appVersion(),

                instance.appName(),

                instance.heartbeatUntil(),

                deploymentTier

        );

    }



    static String resolveRegion(WatchlistEntry host, String environmentId, Map<String, String> environmentTagsById) {

        if (host.region() != null && !host.region().isBlank()) {

            return host.region();

        }

        if (environmentId != null && environmentTagsById != null) {

            String tag = environmentTagsById.get(environmentId.toLowerCase(Locale.ROOT));

            if (tag != null && !tag.isBlank()) {

                return tag;

            }

        }

        return "Unassigned";

    }



    public static String resolveRegion(MonitoredHost host, String environmentId, Map<String, String> environmentTagsById) {

        return resolveRegion(WatchlistEntry.fromUat(host), environmentId, environmentTagsById);

    }



    private static SystemHealthView downView(

            WatchlistEntry host,

            Instant fetchedAt,

            String message,

            String region,

            String deploymentTier

    ) {

        return new SystemHealthView(

                host.id(),

                host.name(),

                host.host(),

                host.port(),

                host.environment(),

                region,

                HealthStatus.DOWN,

                HealthStatus.DOWN.displayLabel(),

                fetchedAt,

                0L,

                message,

                null,

                null,

                0L,

                0L,

                null,

                null,

                null,

                deploymentTier

        );

    }



    private record WatchlistEntry(

            Long id,

            String name,

            String host,

            int port,

            String environment,

            String region,

            String modelHubEnvironmentId

    ) {

        static WatchlistEntry fromUat(MonitoredHost host) {

            return new WatchlistEntry(

                    host.getId(),

                    host.getName(),

                    host.getHost(),

                    host.getPort(),

                    host.getEnvironment(),

                    host.getRegion(),

                    host.getModelHubEnvironmentId()

            );

        }



        static WatchlistEntry fromProd(MonitoredHostProd host) {

            return new WatchlistEntry(

                    host.getId(),

                    host.getName(),

                    host.getHost(),

                    host.getPort(),

                    host.getEnvironment(),

                    host.getRegion(),

                    host.getModelHubEnvironmentId()

            );

        }

    }

}


