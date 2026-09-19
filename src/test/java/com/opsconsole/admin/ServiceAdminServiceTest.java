package com.opsconsole.admin;

import com.opsconsole.activity.service.ActivityFeedService;
import com.opsconsole.admin.domain.AdminAction;
import com.opsconsole.admin.domain.ManagedServer;
import com.opsconsole.admin.domain.ManagedService;
import com.opsconsole.admin.domain.SshCommandResult;
import com.opsconsole.admin.exception.ServiceAdminException;
import com.opsconsole.admin.repository.AdminActionLogRepository;
import com.opsconsole.admin.repository.ManagedServerRepository;
import com.opsconsole.admin.repository.ManagedServiceRepository;
import com.opsconsole.admin.service.AdminActionLogger;
import com.opsconsole.admin.service.ServiceAdminService;
import com.opsconsole.admin.ssh.SshRemoteExecutor;
import com.opsconsole.admin.util.AdminPathValidator;
import com.opsconsole.auth.domain.AppUser;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceAdminServiceTest {

    @Mock
    private ManagedServerRepository serverRepository;

    @Mock
    private ManagedServiceRepository serviceRepository;

    @Mock
    private AdminActionLogRepository actionLogRepository;

    @Mock
    private AdminActionLogger actionLogger;

    @Mock
    private SshRemoteExecutor sshRemoteExecutor;

    @Mock
    private ActivityFeedService activityFeedService;

    private ServiceAdminService serviceAdminService;
    private ManagedServer server;
    private ManagedService service;
    private AppUser actor;

    @BeforeEach
    void setUp() {
        serviceAdminService = new ServiceAdminService(
                serverRepository,
                serviceRepository,
                actionLogRepository,
                actionLogger,
                sshRemoteExecutor,
                activityFeedService
        );
        server = new ManagedServer("dev-linux-01", "127.0.0.1", 22, "opsconsole");
        service = new ManagedService(
                server,
                "payment-api",
                "Payment API",
                "Payment Services",
                8081,
                "/opt/payment/bin/start.sh",
                "/opt/payment/bin/stop.sh",
                "/opt/payment/bin/restart.sh",
                "/opt/payment/config/application.properties"
        );
        actor = new AppUser("dev-admin", "admin@opsconsole.local", "Administrator", null);
    }

    @Test
    void validateScriptPath_rejectsShellMetacharacters() {
        assertThatThrownBy(() -> AdminPathValidator.validateScriptPath("/opt/bin/start;rm", "startScript"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("startScript");
    }

    @Test
    void startService_executesConfiguredScriptInDevMode() {
        when(serviceRepository.findByIdAndEnabledTrue(1L)).thenReturn(Optional.of(service));
        when(sshRemoteExecutor.execute(eq(server), eq("/opt/payment/bin/start.sh")))
                .thenReturn(new SshCommandResult(0, "started", ""));

        ServiceAdminService.OperationResult result = serviceAdminService.startService(1L, actor);

        assertThat(result.success()).isTrue();
        assertThat(result.message()).contains("started");
        verify(actionLogger).log(eq(actor), eq(service), eq(AdminAction.START), any(SshCommandResult.class));
        verify(activityFeedService).recordServiceControl(actor, "payment-api", AdminAction.START, true);
    }

    @Test
    void stopService_logsFailureWhenScriptFails() {
        when(serviceRepository.findByIdAndEnabledTrue(1L)).thenReturn(Optional.of(service));
        when(sshRemoteExecutor.execute(eq(server), eq("/opt/payment/bin/stop.sh")))
                .thenReturn(new SshCommandResult(1, "", "permission denied"));

        assertThatThrownBy(() -> serviceAdminService.stopService(1L, actor))
                .isInstanceOf(ServiceAdminException.class)
                .hasMessageContaining("STOP failed");

        verify(actionLogger).log(eq(actor), eq(service), eq(AdminAction.STOP), any(SshCommandResult.class));
        verify(activityFeedService).recordServiceControl(actor, "payment-api", AdminAction.STOP, false);
    }

    @Test
    void writeProperties_backsUpThenTeesContent() {
        when(serviceRepository.findByIdAndEnabledTrue(1L)).thenReturn(Optional.of(service));
        when(sshRemoteExecutor.execute(eq(server), any(String.class)))
                .thenReturn(new SshCommandResult(0, "", ""));
        when(sshRemoteExecutor.executeWithInput(eq(server), eq("sudo tee '/opt/payment/config/application.properties'"), eq("key=value\n")))
                .thenReturn(new SshCommandResult(0, "written", ""));

        ServiceAdminService.OperationResult result = serviceAdminService.writeProperties(1L, actor, "key=value\n");

        assertThat(result.success()).isTrue();
        verify(sshRemoteExecutor).execute(eq(server), org.mockito.ArgumentMatchers.contains("sudo cp"));
        verify(activityFeedService).recordServicePropertiesUpdated(actor, "payment-api");
    }

    @Test
    void readProperties_doesNotWriteAuditLog() {
        when(serviceRepository.findByIdAndEnabledTrue(1L)).thenReturn(Optional.of(service));
        when(sshRemoteExecutor.execute(eq(server), org.mockito.ArgumentMatchers.contains("sudo cat")))
                .thenReturn(new SshCommandResult(0, "key=value", ""));

        ServiceAdminService.PropertiesContent content = serviceAdminService.readProperties(1L);

        assertThat(content.content()).isEqualTo("key=value");
        verifyNoInteractions(actionLogger);
        verifyNoInteractions(activityFeedService);
    }

    @Test
    void probePort_returnsFalseForClosedPort() {
        assertThat(ServiceAdminService.probePort("127.0.0.1", 59999)).isFalse();
    }
}
