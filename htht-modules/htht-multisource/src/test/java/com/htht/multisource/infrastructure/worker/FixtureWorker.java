package com.htht.multisource.infrastructure.worker;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class FixtureWorker {

    private FixtureWorker() {
    }

    public static void main(String[] arguments) throws Exception {
        String mode = arguments[0];
        Path request = Path.of(arguments[2]);
        Path response = Path.of(arguments[4]);
        if ("sleep".equals(mode)) {
            Thread.sleep(5_000L);
            return;
        }
        if ("missing".equals(mode)) {
            return;
        }
        String requestJson = Files.readString(request);
        String requestId = requestJson.replaceFirst(".*\\\"requestId\\\":\\\"([^\\\"]+)\\\".*", "$1");
        Files.writeString(response, "{\"schemaVersion\":\"1.0\",\"operation\":\"inspect\",\"requestId\":\"" + requestId + "\",\"payload\":{}}");
    }
}
