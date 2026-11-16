package com.java.wallet.utils.helpers.exceptions.web.rest;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.annotation.JsonNaming;

import java.util.List;

//The standard error body returned by the API.
@Getter
@Setter
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class RestApiError {

    private final int httpStatus;
    private String userMessage;
    private String developerMessage;
    private List<RestApiValidationError> validationErrors;

    public RestApiError(RestApiHttpStatus httpStatus) {
        this.httpStatus = httpStatus.getStatusCode();
    }
}
