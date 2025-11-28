package com.flipkart.drift.commons.utils;

import java.net.InetAddress;

public class MachineHelper {
    private static String machineName = null;
    private static final String defaultMachineName = "machine";

    public static String getMachineName() {

        if (machineName == null) {
            try {
                machineName = InetAddress.getLocalHost().getHostAddress();
                if (machineName.contains("local")) machineName = "local";
            } catch (Exception e) {
                machineName = defaultMachineName;
            }
        }
        return machineName;
    }
}