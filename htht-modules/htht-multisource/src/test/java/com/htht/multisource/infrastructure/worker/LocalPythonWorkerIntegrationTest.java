package com.htht.multisource.infrastructure.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("integration")
class LocalPythonWorkerIntegrationTest {

    @Test
    void pythonWorkerReturnsGfBrowseCandidatesWithoutAbsolutePaths(@TempDir Path tempRoot) throws Exception {
        String sampleRootValue = System.getenv("MULTISOURCE_GF_SAMPLE_ROOT");
        String python = System.getenv("MULTISOURCE_WORKER_PYTHON");
        Assumptions.assumeTrue(sampleRootValue != null && python != null,
            "requires MULTISOURCE_GF_SAMPLE_ROOT and MULTISOURCE_WORKER_PYTHON");
        Path sampleRoot = Path.of(sampleRootValue).toAbsolutePath().normalize();
        Path workerRoot = findRepositoryRoot().resolve("rs-algorithm-worker");
        Path invocation = tempRoot.resolve("req-01");
        Path output = invocation.resolve("output");
        Files.createDirectories(output);
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode request = mapper.createObjectNode();
        request.put("schemaVersion", "1.0");
        request.put("operation", "inspect");
        request.put("requestId", "req-01");
        ArrayNode requestInputs = request.putObject("payload").putArray("inputs");
        ArrayNode manifestInputs = mapper.createArrayNode();
        int ordinal = 0;
        for (Path image : Files.list(sampleRoot).filter(path -> path.toString().toLowerCase().endsWith(".jpg"))
            .sorted().toList()) {
            for (String suffix : new String[]{".jpg", ".jgw", ".json"}) {
                Path file = image.resolveSibling(image.getFileName().toString().replaceFirst("\\.jpg$", suffix));
                String uri = "rs://gf/" + file.getFileName();
                requestInputs.addObject().putObject("storageUriSnapshot").put("value", uri);
                ObjectNode input = manifestInputs.addObject();
                input.put("storageUri", uri).put("role", suffix.equals(".jpg") ? "BROWSE_IMAGE"
                    : suffix.equals(".jgw") ? "GEOREFERENCE" : "METADATA");
                input.put("ordinal", ordinal++).put("path", file.toString()).put("checksum", "sha256:test");
            }
        }
        ObjectNode manifest = mapper.createObjectNode();
        manifest.put("manifestVersion", "1.0").put("requestId", "req-01").put("grantReference", "grant-01")
            .put("expiresAt", Instant.now().plus(5, ChronoUnit.MINUTES).toString());
        manifest.set("inputs", manifestInputs);
        manifest.putObject("output").put("path", output.toString());
        Path requestFile = invocation.resolve("request.json");
        Path manifestFile = invocation.resolve("asset-manifest.json");
        Path responseFile = invocation.resolve("response.json");
        mapper.writeValue(requestFile.toFile(), request);
        mapper.writeValue(manifestFile.toFile(), manifest);

        Process process = new ProcessBuilder(python, "-m", "rs_algorithm_worker.cli",
            "--request", requestFile.toString(), "--asset-manifest", manifestFile.toString(),
            "--response", responseFile.toString())
            .directory(workerRoot.toFile()).redirectErrorStream(true).start();
        assertThat(process.waitFor()).isZero();
        var response = mapper.readTree(Files.readString(responseFile));
        assertThat(response.at("/payload/candidates").size()).isEqualTo(2);
        assertThat(response.toString()).doesNotContain(sampleRoot.toString());
    }

    private static Path findRepositoryRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isDirectory(current.resolve("rs-algorithm-worker"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("repository root could not be located");
    }
}
