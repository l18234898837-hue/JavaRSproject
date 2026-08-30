package com.htht.multisource.infrastructure.worker;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class FixtureWorker {

    private FixtureWorker() {
    }

    public static void main(String[] arguments) throws Exception {
        String mode = arguments[0];
        Path request = argumentPath(arguments, "--request");
        Path response = argumentPath(arguments, "--response");
        for (int index = 0; index < arguments.length - 1; index++) {
            if ("--asset-manifest".equals(arguments[index])) {
                Files.writeString(response.getParent().resolve("manifest-seen.txt"), arguments[index + 1]);
            }
        }
        if ("sleep".equals(mode)) {
            Files.writeString(response.getParent().resolve("worker.pid"), Long.toString(ProcessHandle.current().pid()));
            Thread.sleep(5_000L);
            return;
        }
        if ("missing".equals(mode)) {
            return;
        }
        String requestJson = Files.readString(request);
        String requestId = requestJson.replaceFirst(".*\\\"requestId\\\":\\\"([^\\\"]+)\\\".*", "$1");
        if ("invalid-manifest".equals(mode)) {
            Files.writeString(response, "{\"schemaVersion\":\"1.0\",\"operation\":\"inspect\",\"requestId\":\""
                + requestId + "\",\"error\":{\"code\":\"INVALID_ASSET_MANIFEST\",\"message\":\"manifest is expired\",\"retryable\":true}}");
            System.exit(2);
        }
        Files.writeString(response, "{\"schemaVersion\":\"1.0\",\"operation\":\"inspect\",\"requestId\":\"" + requestId + "\",\"payload\":{}}");
    }

    private static Path argumentPath(String[] arguments, String name) {
        for (int index = 0; index < arguments.length - 1; index++) {
            if (name.equals(arguments[index])) {
                return Path.of(arguments[index + 1]);
            }
        }
        throw new IllegalArgumentException("Missing argument " + name);
    }
}
