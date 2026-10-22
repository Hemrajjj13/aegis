package com.aegis.decision.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * RFC 7807 Problem Details for HTTP APIs.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ProblemDetailResponse(
    @JsonProperty("type") String type,
    @JsonProperty("title") String title,
    @JsonProperty("status") int status,
    @JsonProperty("detail") String detail,
    @JsonProperty("instance") String instance,
    @JsonProperty("error_code") String errorCode,
    @JsonProperty("invalid_parameters") List<InvalidParameter> invalidParameters
) {
    public record InvalidParameter(String name, String reason) {}
}
