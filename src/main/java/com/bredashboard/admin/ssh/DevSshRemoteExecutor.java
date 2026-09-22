package com.bredashboard.admin.ssh;

import com.bredashboard.admin.domain.ManagedServer;
import com.bredashboard.admin.domain.SshCommandResult;

import java.util.concurrent.ConcurrentHashMap;

public class DevSshRemoteExecutor implements SshRemoteExecutor {

    private final ConcurrentHashMap<String, String> propertiesStore = new ConcurrentHashMap<>();

    @Override
    public SshCommandResult execute(ManagedServer server, String command) {
        if (command.startsWith("sudo cat ")) {
            String path = command.substring("sudo cat ".length()).replace("'", "");
            String content = propertiesStore.getOrDefault(path, defaultProperties(path));
            return new SshCommandResult(0, content, "");
        }
        return new SshCommandResult(0, "[dev] Executed on " + server.getHost() + ": " + command, "");
    }

    @Override
    public SshCommandResult executeWithInput(ManagedServer server, String command, String stdin) {
        if (command.startsWith("sudo tee ")) {
            String path = command.substring("sudo tee ".length()).replace("'", "");
            propertiesStore.put(path, stdin == null ? "" : stdin);
            int lines = stdin == null ? 0 : stdin.split("\n", -1).length;
            return new SshCommandResult(
                    0,
                    "[dev] Wrote " + lines + " line(s) to " + path + " on " + server.getHost(),
                    ""
            );
        }
        int lines = stdin == null ? 0 : stdin.split("\n", -1).length;
        return new SshCommandResult(
                0,
                "[dev] Wrote " + lines + " line(s) via " + command + " on " + server.getHost(),
                ""
        );
    }

    private static String defaultProperties(String path) {
        return "# Dev-mode properties for " + path + "\nserver.port=8080\nspring.application.name=ops-service\n";
    }
}
