package com.bredashboard.admin.ssh;

import com.bredashboard.admin.domain.ManagedServer;
import com.bredashboard.admin.domain.SshCommandResult;

public interface SshRemoteExecutor {

    SshCommandResult execute(ManagedServer server, String command);

    SshCommandResult executeWithInput(ManagedServer server, String command, String stdin);
}
