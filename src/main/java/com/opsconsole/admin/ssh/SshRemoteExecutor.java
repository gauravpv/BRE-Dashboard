package com.opsconsole.admin.ssh;

import com.opsconsole.admin.domain.ManagedServer;
import com.opsconsole.admin.domain.SshCommandResult;

public interface SshRemoteExecutor {

    SshCommandResult execute(ManagedServer server, String command);

    SshCommandResult executeWithInput(ManagedServer server, String command, String stdin);
}
