package com.waenhancer.utils;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;

public final class RootUtils {
    private RootUtils() {
    }

    public static boolean hasRootAccess() {
        String output = runRootCommand("id");
        return output != null && (output.contains("uid=0") || output.contains("root"));
    }

    public static String runRootCommand(String command) {
        Process process = null;
        try {
            process = new ProcessBuilder("su", "-c", command).redirectErrorStream(true).start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) output.append(line).append('\n');
            }
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return null;
            }
            return output.toString().trim();
        } catch (Exception ignored) {
            return null;
        } finally {
            if (process != null) process.destroy();
        }
    }
}
