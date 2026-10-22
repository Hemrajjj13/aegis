package com.aegis.core.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Immutable canonical payment event received by Aegis Ingest API or Kafka mirrors.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentEvent(
    @JsonProperty("event_id") String eventId,
    @JsonProperty("event_time") Instant eventTime,
    @JsonProperty("sequence_number") long sequenceNumber,
    @JsonProperty("tenant_id") UUID tenantId,
    @JsonProperty("account_id_hash") String accountIdHash,
    @JsonProperty("card_token") String cardToken,
    @JsonProperty("amount_minor_units") long amountMinorUnits,
    @JsonProperty("currency") String currency,
    @JsonProperty("payment_method") String paymentMethod,
    @JsonProperty("merchant_id") String merchantId,
    @JsonProperty("merchant_category_code") String merchantCategoryCode,
    @JsonProperty("merchant_name") String merchantName,
    @JsonProperty("channel") String channel,
    @JsonProperty("ip_address") String ipAddress,
    @JsonProperty("device_id") String deviceId,
    @JsonProperty("user_agent") String userAgent,
    @JsonProperty("upi_vpa_hash") String upiVpaHash,
    @JsonProperty("custom_fields") Map<String, String> customFields
) {
    public PaymentEvent {
        if (eventId == null || eventId.isBlank()) {
            throw new IllegalArgumentException("event_id must not be blank");
        }
        if (tenantId == null) {
            throw new IllegalArgumentException("tenant_id must not be null");
        }
        if (eventTime == null) {
            throw new IllegalArgumentException("event_time must not be null");
        }
        if (currency == null || currency.isBlank()) {
            currency = "INR";
        }
        if (customFields == null) {
            customFields = Map.of();
        } else {
            customFields = Map.copyOf(customFields);
        }
    }

    /**
     * Calculates amount in major currency units (e.g. INR from paise).
     * 5,000,000 minor units -> 50,000.00 INR.
     */
    public double getAmountInr() {
        return amountMinorUnits / 100.0;
    }
}
