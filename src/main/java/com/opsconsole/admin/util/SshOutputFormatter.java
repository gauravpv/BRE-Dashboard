package com.opsconsole.admin.util;

import com.opsconsole.admin.domain.SshCommandResult;

public final class SshOutputFormatter {

    private SshOutputFormatter() {
    }

    public static String summarize(SshCommandResult result) {
        String out = result.stdout() == null ? "" : result.stdout().trim();
        String err = result.stderr() == null ? "" : result.stderr().trim();
        if (!out.isBlank() && !err.isBlank()) {
            return out + " | " + err;
        }
        if (!out.isBlank()) {
            return out;
        }
        if (!err.isBlank()) {
            return err;
        }
        return "exit " + result.exitCode();
    }
}
