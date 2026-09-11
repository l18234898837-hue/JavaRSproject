package com.htht.config.multisource;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.htht.multisource.application.usecase.model.TaskArtifactView;
import com.htht.multisource.application.usecase.model.TaskDetailView;
import com.htht.multisource.domain.model.AccessSubject;

import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Converts bounded Worker raster responses into browser-safe metadata and PNG bytes. */
public final class WorkerArtifactRasterPreviewService implements ArtifactRasterPreviewService {
    private static final Duration METADATA_TTL = Duration.ofMinutes(5);
    private static final int MAX_TILE_CACHE_ENTRIES = 512;

    private final ArtifactInspectionService inspectionService;
    private final ObjectMapper objectMapper;
    private final ConcurrentMap<MetadataKey, CachedPreview> metadataCache = new ConcurrentHashMap<>();
    private final ConcurrentMap<TileKey, byte[]> tileCache = new ConcurrentHashMap<>();

    public WorkerArtifactRasterPreviewService(ArtifactInspectionService inspectionService, ObjectMapper objectMapper) {
        this.inspectionService = Objects.requireNonNull(inspectionService, "inspectionService");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    }

    @Override
    public RasterPreview describe(TaskDetailView detail, AccessSubject subject, TaskArtifactView artifact, String format) {
        MetadataKey key = new MetadataKey(detail.id().value(), artifact.type(), artifact.checksum());
        CachedPreview cached = metadataCache.get(key);
        if (cached != null && cached.expiresAt().isAfter(Instant.now())) {
            return cached.preview();
        }
        RasterPreview preview = toPreview(inspectionService.inspectRaster(detail, subject, artifact, format));
        metadataCache.put(key, new CachedPreview(preview, Instant.now().plus(METADATA_TTL)));
        return preview;
    }

    @Override
    public byte[] renderTile(TaskDetailView detail, AccessSubject subject, TaskArtifactView artifact, String format,
                              RasterPreview preview, int z, int x, int y) {
        if (!preview.renderable() || preview.style() == null) {
            throw new IllegalArgumentException(preview.reason() == null ? "TIFF 暂不支持地图查看" : preview.reason());
        }
        TileKey key = new TileKey(artifact.checksum(), preview.style().toString(), z, x, y);
        byte[] cached = tileCache.get(key);
        if (cached != null) return cached.clone();
        JsonNode payload = inspectionService.renderRasterTile(detail, subject, artifact, format, z, x, y, preview.style());
        if (!"image/png".equals(payload.path("contentType").asText()) || !payload.path("pngBase64").isTextual()) {
            throw new IllegalStateException("Worker returned an invalid raster tile.");
        }
        byte[] png = Base64.getDecoder().decode(payload.path("pngBase64").asText());
        if (png.length == 0 || png.length > 512 * 1024) {
            throw new IllegalStateException("Worker returned an invalid raster tile.");
        }
        if (tileCache.size() >= MAX_TILE_CACHE_ENTRIES) tileCache.clear();
        tileCache.put(key, png);
        return png.clone();
    }

    private RasterPreview toPreview(JsonNode response) {
        if (!"raster".equals(response.path("kind").asText())) {
            throw new IllegalStateException("Worker returned invalid raster metadata.");
        }
        if (!response.path("renderable").asBoolean(false)) {
            String reason = response.path("reason").asText("TIFF 暂不支持地图查看");
            return new RasterPreview(false, reason, null, null, null, null, null, null);
        }
        JsonNode bounds = response.path("bounds");
        if (!response.path("crs").isTextual() || !bounds.isArray() || bounds.size() != 4 || !response.path("style").isObject()) {
            throw new IllegalStateException("Worker returned invalid raster metadata.");
        }
        List<Double> values = java.util.stream.StreamSupport.stream(bounds.spliterator(), false)
            .map(JsonNode::asDouble).toList();
        if (values.stream().anyMatch(value -> !Double.isFinite(value))) {
            throw new IllegalStateException("Worker returned invalid raster metadata.");
        }
        Map<String, Object> style = objectMapper.convertValue(response.path("style"), new TypeReference<>() { });
        return new RasterPreview(true, null, response.path("crs").asText(), values,
            response.path("minZoom").isInt() ? response.path("minZoom").asInt() : null,
            response.path("maxZoom").isInt() ? response.path("maxZoom").asInt() : null,
            Map.copyOf(style), response.get("legend"));
    }

    private record MetadataKey(long taskId, String type, String checksum) {
    }

    private record CachedPreview(RasterPreview preview, Instant expiresAt) {
    }

    private record TileKey(String checksum, String style, int z, int x, int y) {
    }
}
