package com.htht.multisource.domain.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
class TaskIdJsonTest {
    @Test
    void serializesTaskIdsAsScalarValuesForHttpPathReuse() throws Exception {
        var node = new ObjectMapper().readTree(
            new ObjectMapper().writeValueAsString(TaskId.of(2105228908755554304L))
        );

        assertThat(node.isValueNode()).isTrue();
        assertThat(node.asText()).isEqualTo("2105228908755554304");
    }
}
