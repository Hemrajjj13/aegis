package com.aegis.decision.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * RFC 7807 ErrorResponse conforming to OpenAPI 3.1 #/components/schemas/ErrorResponse.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProblemDetailResponse(
    @JsonProperty("type") String type,
    @JsonProperty("title") String title,
    @JsonProperty("status") int status,
    @JsonProperty("detail") String detail,
    @JsonProperty("instance") String instance,
    @JsonProperty("invalid_params") List<InvalidParameter> invalidParams
) {
    public record InvalidParameter(
        @JsonProperty("name") String name,
        @JsonProperty("reason") String reason
    ) {}
}
