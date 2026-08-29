package com.clinic.service;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Slot generation settings.
 *
 * <p>The horizon - how far ahead concrete slots are materialised from recurring
 * availability - is not specified in the API contract
 * (product-description.md 22, open question 2), so it is configurable with a
 * 30-day default rather than hardcoded.
 */
@ConfigurationProperties(prefix = "clinic.slots")
public record SlotProperties(int generationHorizonDays) {
}
