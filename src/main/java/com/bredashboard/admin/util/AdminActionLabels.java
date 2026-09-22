package com.bredashboard.admin.util;

import com.bredashboard.admin.domain.AdminAction;

public final class AdminActionLabels {

    private AdminActionLabels() {
    }

    public static String label(AdminAction action) {
        if (action == null) {
            return "";
        }
        return switch (action) {
            case START -> "Service Start";
            case STOP -> "Service Stop";
            case RESTART -> "Service Restart";
            case PROPS_READ -> "Config Read";
            case PROPS_WRITE -> "Config Update";
        };
    }
}
