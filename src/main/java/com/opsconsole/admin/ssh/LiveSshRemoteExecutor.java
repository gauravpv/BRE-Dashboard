package com.opsconsole.admin.ssh;

import com.opsconsole.admin.config.AdminProperties;
import com.opsconsole.admin.domain.ManagedServer;
import com.opsconsole.admin.domain.SshCommandResult;

import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.channel.ClientChannel;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.keyprovider.FileKeyPairProvider;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;

public class LiveSshRemoteExecutor implements SshRemoteExecutor {

    private final AdminProperties adminProperties;

    public LiveSshRemoteExecutor(AdminProperties adminProperties) {
        this.adminProperties = adminProperties;
    }

    @Override
    public SshCommandResult execute(ManagedServer server, String command) {
        return run(server, command, null);
    }

    @Override
    public SshCommandResult executeWithInput(ManagedServer server, String command, String stdin) {
        return run(server, command, stdin);
    }

    private SshCommandResult run(ManagedServer server, String command, String stdin) {
        String keyPath = adminProperties.getSsh().getPrivateKeyPath();
        if (keyPath == null || keyPath.isBlank()) {
            return new SshCommandResult(1, "", "OPS_SSH_KEY_PATH / opsconsole.admin.ssh.private-key-path is not configured");
        }

        Duration connectTimeout = Duration.ofMillis(adminProperties.getSsh().getConnectTimeoutMs());
        Duration commandTimeout = Duration.ofMillis(adminProperties.getSsh().getCommandTimeoutMs());

        try (SshClient client = SshClient.setUpDefaultClient()) {
            client.start();
            try (ClientSession session = client.connect(server.getSshUser(), server.getHost(), server.getSshPort())
                    .verify(connectTimeout)
                    .getSession()) {
                session.setKeyIdentityProvider(new FileKeyPairProvider(Path.of(keyPath)));
                session.auth().verify(connectTimeout);

                try (ClientChannel channel = session.createExecChannel(command)) {
                    ByteArrayOutputStream stdout = new ByteArrayOutputStream();
                    ByteArrayOutputStream stderr = new ByteArrayOutputStream();
                    channel.setOut(stdout);
                    channel.setErr(stderr);
                    if (stdin != null) {
                        channel.setIn(new java.io.ByteArrayInputStream(stdin.getBytes(StandardCharsets.UTF_8)));
                    }
                    channel.open().verify(connectTimeout);
                    channel.waitFor(
                            java.util.Set.of(org.apache.sshd.client.channel.ClientChannelEvent.CLOSED),
                            commandTimeout
                    );
                    Integer exitStatus = channel.getExitStatus();
                    return new SshCommandResult(
                            exitStatus == null ? 1 : exitStatus,
                            stdout.toString(StandardCharsets.UTF_8),
                            stderr.toString(StandardCharsets.UTF_8)
                    );
                }
            }
        } catch (IOException ex) {
            return new SshCommandResult(1, "", ex.getMessage());
        }
    }
}
