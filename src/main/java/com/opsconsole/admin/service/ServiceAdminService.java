package com.opsconsole.admin.service;

import com.opsconsole.activity.service.ActivityFeedService;
import com.opsconsole.admin.domain.AdminAction;
import com.opsconsole.admin.domain.AdminActionLog;
import com.opsconsole.admin.domain.ManagedServer;
import com.opsconsole.admin.domain.ManagedService;
import com.opsconsole.admin.domain.SshCommandResult;
import com.opsconsole.admin.exception.ServiceAdminException;
import com.opsconsole.admin.repository.AdminActionLogRepository;
import com.opsconsole.admin.repository.ManagedServerRepository;
import com.opsconsole.admin.repository.ManagedServiceRepository;
import com.opsconsole.admin.ssh.SshRemoteExecutor;
import com.opsconsole.admin.util.AdminPathValidator;
import com.opsconsole.admin.util.SshOutputFormatter;
import com.opsconsole.auth.domain.AppUser;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ServiceAdminService {

    private static final int PORT_PROBE_TIMEOUT_MS = 2000;

    private final ManagedServerRepository serverRepository;
    private final ManagedServiceRepository serviceRepository;
    private final AdminActionLogRepository actionLogRepository;
    private final AdminActionLogger actionLogger;
    private final SshRemoteExecutor sshRemoteExecutor;
    private final ActivityFeedService activityFeedService;

    public ServiceAdminService(
            ManagedServerRepository serverRepository,
            ManagedServiceRepository serviceRepository,
            AdminActionLogRepository actionLogRepository,
            AdminActionLogger actionLogger,
            SshRemoteExecutor sshRemoteExecutor,
            ActivityFeedService activityFeedService
    ) {
        this.serverRepository = serverRepository;
        this.serviceRepository = serviceRepository;
        this.actionLogRepository = actionLogRepository;
        this.actionLogger = actionLogger;
        this.sshRemoteExecutor = sshRemoteExecutor;
        this.activityFeedService = activityFeedService;
    }

    @Transactional(readOnly = true)
    public List<ServerView> listServers() {
        List<ManagedServer> servers = serverRepository.findByEnabledTrueOrderByNameAsc();
        Map<Long, List<ManagedService>> servicesByServerId = serviceRepository.findByEnabledTrueWithServer()
                .stream()
                .collect(Collectors.groupingBy(s -> s.getServer().getId(), LinkedHashMap::new, Collectors.toList()));

        List<ServerView> views = new ArrayList<>(servers.size());
        for (ManagedServer server : servers) {
            List<ManagedService> services = servicesByServerId.getOrDefault(server.getId(), List.of());
            String host = server.getHost();
            List<ServiceSummaryView> summaries = services.parallelStream()
                    .map(s -> new ServiceSummaryView(
                            s.getId(),
                            s.getName(),
                            s.getCategory(),
                            s.getPort(),
                            probePort(host, s.getPort())
                    ))
                    .toList();
            views.add(new ServerView(server.getId(), server.getName(), host, summaries));
        }
        return views;
    }

    @Transactional(readOnly = true)
    public ServiceDetailView getServiceDetail(Long serviceId) {
        ManagedService service = requireService(serviceId);
        ManagedServer server = service.getServer();
        boolean portUp = probePort(server.getHost(), service.getPort());
        return new ServiceDetailView(
                service.getId(),
                service.getName(),
                service.getDescription(),
                service.getCategory(),
                service.getPort(),
                server.getId(),
                server.getName(),
                server.getHost(),
                portUp,
                service.getStartScript(),
                service.getStopScript(),
                service.getRestartScript(),
                service.getPropertiesPath()
        );
    }

    public OperationResult startService(Long serviceId, AppUser actor) {
        return runScript(serviceId, actor, AdminAction.START, requireService(serviceId).getStartScript());
    }

    public OperationResult stopService(Long serviceId, AppUser actor) {
        return runScript(serviceId, actor, AdminAction.STOP, requireService(serviceId).getStopScript());
    }

    public OperationResult restartService(Long serviceId, AppUser actor) {
        return runScript(serviceId, actor, AdminAction.RESTART, requireService(serviceId).getRestartScript());
    }

    public PropertiesContent readProperties(Long serviceId) {
        ManagedService service = requireService(serviceId);
        AdminPathValidator.validatePropertiesPath(service.getPropertiesPath());
        String path = shellQuote(service.getPropertiesPath());
        SshCommandResult result = sshRemoteExecutor.execute(service.getServer(), "sudo cat " + path);
        if (!result.success()) {
            throw new ServiceAdminException("Failed to read properties: " + SshOutputFormatter.summarize(result));
        }
        return new PropertiesContent(result.stdout());
    }

    public OperationResult writeProperties(Long serviceId, AppUser actor, String content) {
        if (content == null) {
            throw new IllegalArgumentException("Properties content is required");
        }
        ManagedService service = requireService(serviceId);
        AdminPathValidator.validatePropertiesPath(service.getPropertiesPath());
        String path = shellQuote(service.getPropertiesPath());
        String backupSuffix = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
                .withZone(ZoneId.systemDefault())
                .format(Instant.now());
        SshCommandResult backup = sshRemoteExecutor.execute(
                service.getServer(),
                "sudo cp " + path + " " + path + ".bak." + backupSuffix
        );
        if (!backup.success()) {
            actionLogger.log(actor, service, AdminAction.PROPS_WRITE,
                    new SshCommandResult(backup.exitCode(), backup.stdout(), "Backup failed: " + backup.stderr()));
            throw new ServiceAdminException("Failed to backup properties: " + SshOutputFormatter.summarize(backup));
        }
        SshCommandResult write = sshRemoteExecutor.executeWithInput(
                service.getServer(),
                "sudo tee " + path,
                content
        );
        actionLogger.log(actor, service, AdminAction.PROPS_WRITE, write);
        if (!write.success()) {
            throw new ServiceAdminException("Failed to write properties: " + SshOutputFormatter.summarize(write));
        }
        activityFeedService.recordServicePropertiesUpdated(actor, service.getName());
        return OperationResult.ok(SshOutputFormatter.summarize(write));
    }

    @Transactional(readOnly = true)
    public List<AdminActionLog> recentActions(int limit) {
        if (limit <= 0 || limit > 100) {
            limit = 20;
        }
        return actionLogRepository.findByActionNotOrderByCreatedAtDesc(
                AdminAction.PROPS_READ,
                PageRequest.of(0, limit)
        );
    }

    @Transactional(readOnly = true)
    public ManagedService requireService(Long serviceId) {
        return serviceRepository.findByIdAndEnabledTrue(serviceId)
                .orElseThrow(() -> new IllegalArgumentException("Service not found"));
    }

    private OperationResult runScript(Long serviceId, AppUser actor, AdminAction action, String scriptPath) {
        AdminPathValidator.validateScriptPath(scriptPath, action.name().toLowerCase() + "Script");
        ManagedService service = requireService(serviceId);
        SshCommandResult result = sshRemoteExecutor.execute(service.getServer(), scriptPath);
        actionLogger.log(actor, service, action, result);
        activityFeedService.recordServiceControl(actor, service.getName(), action, result.success());
        if (!result.success()) {
            throw new ServiceAdminException(action.name() + " failed: " + SshOutputFormatter.summarize(result));
        }
        return OperationResult.ok(SshOutputFormatter.summarize(result));
    }

    private static String shellQuote(String path) {
        return "'" + path.replace("'", "'\"'\"'") + "'";
    }

    public static boolean probePort(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), PORT_PROBE_TIMEOUT_MS);
            return true;
        } catch (IOException ex) {
            return false;
        }
    }

    public record ServerView(Long id, String name, String host, List<ServiceSummaryView> services) {
    }

    public record ServiceSummaryView(Long id, String name, String category, int port, boolean portUp) {
    }

    public record ServiceDetailView(
            Long id,
            String name,
            String description,
            String category,
            int port,
            Long serverId,
            String serverName,
            String serverHost,
            boolean portUp,
            String startScript,
            String stopScript,
            String restartScript,
            String propertiesPath
    ) {
    }

    public record PropertiesContent(String content) {
    }

    public record OperationResult(boolean success, String message) {
        public static OperationResult ok(String message) {
            return new OperationResult(true, message);
        }
    }
}
