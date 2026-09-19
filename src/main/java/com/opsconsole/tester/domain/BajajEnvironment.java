package com.opsconsole.tester.domain;

public enum BajajEnvironment {
    UAT,
    PROD;

    public static BajajEnvironment parse(String value) {
        if (value != null && value.equalsIgnoreCase("PROD")) {
            return PROD;
        }
        if (value != null && value.equalsIgnoreCase("UAT")) {
            return UAT;
        }
        return null;
    }

    public static BajajEnvironment parseOrUat(String value) {
        BajajEnvironment environment = parse(value);
        return environment != null ? environment : UAT;
    }
}
