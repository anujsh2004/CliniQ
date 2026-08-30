package com.clinic.config;

import com.clinic.dto.response.DoctorResponse;
import com.clinic.dto.response.DoctorSummary;
import com.clinic.dto.response.PagedResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.cache.autoconfigure.RedisCacheManagerBuilderCustomizer;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.SimpleCacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.ObjectMapper;

/**
 * Read-through caching for the read-heavy, less volatile endpoints
 * (tech-stack.md 3, Caching).
 *
 * <p>Two rules govern what may be cached here:
 *
 * <ol>
 *   <li><b>Never slot availability at booking time.</b> The database
 *       transaction is the only authority on whether a slot is free; a cache
 *       that answered that question could hand two patients the same slot.</li>
 *   <li><b>Never anything patient-specific.</b> Appointments and profiles are
 *       per-caller data, and a shared cache is the wrong place for them.</li>
 * </ol>
 *
 * <p>What is left is exactly what the doctor list and profile are: the same
 * answer for everyone, changing rarely.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);

    /** Doctor list pages. */
    public static final String DOCTOR_LIST = "doctorList";

    /** Individual doctor profiles, including their clinic block. */
    public static final String DOCTOR_DETAIL = "doctorDetail";

    /**
     * Gives each cache a serializer that knows the exact type it holds.
     *
     * <p>Entries are stored as JSON rather than Java-serialised blobs, so the
     * cached DTOs need no {@code Serializable} marker and an entry can be read
     * straight out of {@code redis-cli}.
     *
     * <p>The type has to be declared per cache. Plain JSON carries no type
     * information, so a generic serializer reads every entry back as a
     * {@code LinkedHashMap} and the cast to the DTO fails - meaning every cache
     * <em>hit</em> returns a 500 while every miss succeeds. Naming the type per
     * cache fixes that without resorting to embedded type metadata, which would
     * mean enabling polymorphic deserialisation for anything that can reach
     * Redis.
     */
    @Bean
    @ConditionalOnProperty(name = "spring.cache.type", havingValue = "redis")
    public RedisCacheManagerBuilderCustomizer typedCacheSerializers(ObjectMapper objectMapper) {
        JavaType pagedDoctorSummaries = objectMapper.getTypeFactory()
                .constructParametricType(PagedResponse.class, DoctorSummary.class);

        return builder -> builder
                .withCacheConfiguration(DOCTOR_LIST, builder.cacheDefaults()
                        .serializeValuesWith(SerializationPair.fromSerializer(
                                new JacksonJsonRedisSerializer<>(objectMapper, pagedDoctorSummaries))))
                .withCacheConfiguration(DOCTOR_DETAIL, builder.cacheDefaults()
                        .serializeValuesWith(SerializationPair.fromSerializer(
                                new JacksonJsonRedisSerializer<>(objectMapper, DoctorResponse.class))));
    }

    /**
     * A cache failure must never fail a request.
     *
     * <p>Redis is an additive side-channel: if it is down or slow, the
     * application logs it and reads from the database, which is the source of
     * truth anyway. The default handler would propagate the error and take the
     * endpoint down with the cache.
     */
    @Bean
    @ConditionalOnProperty(name = "spring.cache.type", havingValue = "redis")
    public CacheErrorHandler cacheErrorHandler() {
        return new SimpleCacheErrorHandler() {

            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                log.warn("Cache read failed for {}; falling back to the database", cache.getName(), exception);
            }

            @Override
            public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
                log.warn("Cache write failed for {}; continuing", cache.getName(), exception);
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                // Worth a louder log: a missed eviction means stale reads until
                // the entry expires.
                log.error("Cache eviction failed for {}; entries may be stale until TTL",
                        cache.getName(), exception);
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                log.error("Cache clear failed for {}", cache.getName(), exception);
            }
        };
    }
}
