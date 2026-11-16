package com.github.dev34.videoengine.resourcepack;

import org.bukkit.Bukkit;

public enum PackVersion {

    V1_18(8, "1.18", "1.18.1", "1.18.2"),
    V1_19(9, "1.19", "1.19.1", "1.19.2"),
    V1_19_3(12, "1.19.3"),
    V1_19_4(13, "1.19.4"),
    V1_20(15, "1.20", "1.20.1"),
    V1_20_2(18, "1.20.2"),
    V1_20_3(22, "1.20.3", "1.20.4"),
    V1_20_5(32, "1.20.5", "1.20.6"),
    V1_21(34, "1.21", "1.21.1"),
    V1_21_2(42, "1.21.2", "1.21.3"),
    V1_21_4(46, "1.21.4"),
    V1_21_5(55, "1.21.5"),
    V1_21_6(63, "1.21.6"),
    V1_21_7(64, "1.21.7", "1.21.8"),
    V1_21_9(69, "1.21.9", "1.21.10"),
    V1_21_11(75, "1.21.11"),
    V26_2(88, "26.2"),
    V26_3(97, "26.3");

    private static final PackVersion LATEST = V26_3;

    private final int format;
    private final String[] versions;

    PackVersion(int format, String... versions) {
        this.format = format;
        this.versions = versions;
    }

    public int getFormat() {
        return format;
    }

    public static int resolve(String input) {
        if (input == null) return -1;

        String version = input.trim();
        int dash = version.indexOf('-');
        if (dash > 0) {
            version = version.substring(0, dash);
        }

        for (PackVersion v : values()) {
            for (String s : v.versions) {
                if (s.equals(version)) return v.format;
            }
        }

        return -1;
    }

    public static int forServer() {
        try {
            int f = resolve(Bukkit.getMinecraftVersion());
            if (f > 0) return f;
        } catch (Exception | LinkageError ignored) {
        }
        return LATEST.format;
    }
}