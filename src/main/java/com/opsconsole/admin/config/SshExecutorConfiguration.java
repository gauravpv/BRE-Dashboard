package com.opsconsole.admin.config;

import com.opsconsole.admin.ssh.DevSshRemoteExecutor;
import com.opsconsole.admin.ssh.LiveSshRemoteExecutor;
import com.opsconsole.admin.ssh.SshRemoteExecutor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SshExecutorConfiguration {

    @Bean
    DevSshRemoteExecutor devSshRemoteExecutor() {
        return new DevSshRemoteExecutor();
    }

    @Bean
    LiveSshRemoteExecutor liveSshRemoteExecutor(AdminProperties adminProperties) {
        return new LiveSshRemoteExecutor(adminProperties);
    }

    @Bean
    public SshRemoteExecutor sshRemoteExecutor(
            AdminProperties adminProperties,
            DevSshRemoteExecutor devExecutor,
            LiveSshRemoteExecutor liveExecutor
    ) {
        if (adminProperties.isDevMode()) {
            return devExecutor;
        }
        return liveExecutor;
    }
}
