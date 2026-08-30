package com.clinic.config;

import com.clinic.dto.response.ClinicSummary;
import com.clinic.dto.response.DoctorResponse;
import com.clinic.dto.response.DoctorSummary;
import com.clinic.dto.response.PagedResponse;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * How cached values survive a trip through Redis.
 *
 * <p>Written because of a bug it would have caught. The cache first used an
 * untyped JSON serializer, which writes no type information, so every value
 * came back as a {@code LinkedHashMap} and the cast to the DTO threw. The
 * effect in production was that every cache <em>miss</em> returned 200 and
 * every cache <em>hit</em> returned 500 - the endpoint worked exactly once per
 * ten-minute TTL. Checking that a key appeared in Redis, which is what the
 * original verification did, cannot see this. Only reading the value back can.
 */
class CacheSerializerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private DoctorResponse doctor() {
        return new DoctorResponse(
                UUID.randomUUID().toString(),
                "Dr. Sharma",
                "Dentist",
                new BigDecimal("500.00"),
                new ClinicSummary(UUID.randomUUID().toString(), "Sharma Dental Clinic",
                        "MG Road, Chennai", "+919876543210"));
    }

    private PagedResponse<DoctorSummary> page() {
        return new PagedResponse<>(
                List.of(new DoctorSummary(UUID.randomUUID().toString(), "Dr. Sharma", "Dentist",
                        new BigDecimal("500.00"))),
                0, 10, 1, 1);
    }

    @Test
    void aDoctorProfileSurvivesTheRoundTripAsItsOwnType() {
        var serializer = new JacksonJsonRedisSerializer<>(objectMapper, DoctorResponse.class);
        DoctorResponse original = doctor();

        DoctorResponse restored = serializer.deserialize(serializer.serialize(original));

        assertThat(restored).isInstanceOf(DoctorResponse.class).isEqualTo(original);
        assertThat(restored.clinic().phone()).isEqualTo("+919876543210");
        assertThat(restored.consultationFee()).isEqualByComparingTo("500.00");
    }

    @Test
    void aDoctorPageSurvivesWithItsElementsIntact() {
        // The generic case is the harder one: the element type has to be
        // declared, not just the outer class, or the list comes back as maps.
        JavaType type = objectMapper.getTypeFactory()
                .constructParametricType(PagedResponse.class, DoctorSummary.class);
        var serializer = new JacksonJsonRedisSerializer<PagedResponse<DoctorSummary>>(objectMapper, type);
        PagedResponse<DoctorSummary> original = page();

        PagedResponse<DoctorSummary> restored = serializer.deserialize(serializer.serialize(original));

        assertThat(restored.totalElements()).isEqualTo(1);
        assertThat(restored.content()).hasSize(1);
        // The element is really a DoctorSummary, not a map with the right keys.
        assertThat(restored.content().getFirst()).isInstanceOf(DoctorSummary.class);
        assertThat(restored.content().getFirst().name()).isEqualTo("Dr. Sharma");
    }

    @Test
    void anUntypedSerializerLosesTheTypeAndIsWhyTheTypedOneIsUsed() {
        // The regression itself, pinned. Swap the typed serializer for a
        // generic one and this is what the cache hands back: a map, which the
        // caller then fails to cast.
        var untyped = new GenericJacksonJsonRedisSerializer(objectMapper);

        Object restored = untyped.deserialize(untyped.serialize(doctor()));

        assertThat(restored).isNotInstanceOf(DoctorResponse.class);
        assertThat(restored).isInstanceOf(java.util.Map.class);
    }

    @Test
    void storedEntriesAreReadableJsonRatherThanOpaqueBlobs() {
        // Being able to read a cache entry in redis-cli is worth keeping.
        var serializer = new JacksonJsonRedisSerializer<>(objectMapper, DoctorResponse.class);

        String stored = new String(serializer.serialize(doctor()));

        assertThat(stored).startsWith("{").contains("\"name\":\"Dr. Sharma\"");
    }
}
