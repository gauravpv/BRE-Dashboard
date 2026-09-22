package com.bredashboard.admin.domain;

public record SshCommandResult(int exitCode, String stdout, String stderr) {
    public boolean success() {
        return exitCode == 0;
    }
}
