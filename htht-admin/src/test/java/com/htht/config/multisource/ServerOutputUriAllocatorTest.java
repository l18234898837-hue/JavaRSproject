package com.htht.config.multisource;

import com.htht.multisource.domain.model.AccessSubject;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("dev")
public class ServerOutputUriAllocatorTest {

    @Test
    void createsAnOpaqueServerSideOutputUriForTheAuthenticatedUser() {
        ServerOutputUriAllocator allocator = new ServerOutputUriAllocator(() -> "request-uuid");

        var outputUri = allocator.allocate(new AccessSubject(7L));

        assertThat(outputUri.value()).isEqualTo("rs://output/7/request-uuid");
    }
}
