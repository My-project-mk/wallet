package com.java.wallet.utils.helpers.exceptions.web.rest;

import com.java.wallet.utils.helpers.exceptions.BadRequestRestApiException;
import lombok.Getter;

//Base exception for API errors
@Getter
public class RestApiException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final RestApiError restApiError;

    public RestApiException(RestApiHttpStatus httpStatus) {
        this.restApiError = new RestApiError(httpStatus);
    }

    public RestApiException userMessage(String userMessage) {
        this.restApiError.setUserMessage(userMessage);
        return this;
    }

    public RestApiException developerMessage(String developerMessage, Object... developerMessageArgs) {
        this.restApiError.setDeveloperMessage(String.format(developerMessage, developerMessageArgs));
        return this;
    }

    @Override
    public String getMessage() {
        return restApiError.getDeveloperMessage();
    }
}
