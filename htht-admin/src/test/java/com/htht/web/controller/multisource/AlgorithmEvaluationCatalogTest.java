package com.htht.web.controller.multisource;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("dev")
class AlgorithmEvaluationCatalogTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void mergesWorkerEvaluationReportIntoExistingMetricCatalog() throws Exception {
        var report = mapper.readTree("""
            {
              "reportVersion": "algorithm-evaluation-report-v1",
              "profileCode": "GF-6-WFV-L1A",
              "metrics": [
                {"key":"bounds_valid","status":"OBSERVED","value":true,"reason":"已从产物检查结果中观测到"},
                {"key":"rpc_geolocation_error","status":"OBSERVED","value":2.31,"reason":"已根据参考数据计算"},
                {"key":"projection_error","status":"REQUIRES_REFERENCE","value":null,"reason":"需要 GF-6 控制点参考数据后才能评价"}
              ]
            }
            """);

        var evaluation = AlgorithmEvaluationCatalog.fromReport(
            "GF-6-WFV-L1A", report, Map.of());
        var metrics = evaluation.metrics().stream()
            .collect(java.util.stream.Collectors.toMap(
                value -> value.key(), value -> value));

        assertThat(metrics.get("bounds_valid").status()).isEqualTo("OBSERVED");
        assertThat(metrics.get("rpc_geolocation_error").observedValue()).isEqualTo("2.31");
        assertThat(metrics.get("rpc_geolocation_error").message()).isEqualTo("已根据参考数据计算");
        assertThat(metrics.get("projection_error").status()).isEqualTo("REQUIRES_REFERENCE");
    }

    @Test
    void rejectsReportForAnotherProfileOrUnknownStatus() throws Exception {
        var wrongProfile = mapper.readTree("""
            {"profileCode":"HY-2D-ALTIMETER","metrics":[]}
            """);
        assertThatThrownBy(() -> AlgorithmEvaluationCatalog.fromReport(
            "GF-6-WFV-L1A", wrongProfile, Map.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("profile");

        var unknownStatus = mapper.readTree("""
            {"reportVersion":"algorithm-evaluation-report-v1",
             "profileCode":"GF-6-WFV-L1A",
             "metrics":[{"key":"bounds_valid","status":"PASS","value":true}]}
            """);
        assertThatThrownBy(() -> AlgorithmEvaluationCatalog.fromReport(
            "GF-6-WFV-L1A", unknownStatus, Map.of()))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("status");
    }
}
