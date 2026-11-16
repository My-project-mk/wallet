package com.java.wallet.utils.helpers.exceptions.web.rest;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

//Translates every exception into a {@link RestApiError} response.
@Slf4j
@ControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RestControllerAdvice {

    @ExceptionHandler(RestApiException.class)
    public ResponseEntity<RestApiError> handleRestApiException(RestApiException e) {
        log.warn("Api error: {}", e.getMessage());
        return createResponseEntity(e.getRestApiError());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<RestApiError> handleMethodArgumentNotValidException(MethodArgumentNotValidException e) {
        RestApiError restApiError = badRequest("The request contains invalid fields", "Request body validation failed");
        restApiError.setValidationErrors(toValidationErrors(e.getBindingResult().getFieldErrors()));
        return createResponseEntity(restApiError);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<RestApiError> handleHandlerMethodValidationException(HandlerMethodValidationException e) {
        return createResponseEntity(badRequest("The request contains invalid parameters", e.getMessage()));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<RestApiError> handleUnreadableRequest(Exception e) {
        return createResponseEntity(badRequest("The request could not be read", e.getMessage()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<RestApiError> handleNoResourceFoundException(NoResourceFoundException e) {
        RestApiError restApiError = new RestApiError(RestApiHttpStatus.NOT_FOUND);
        restApiError.setUserMessage("The requested resource was not found");
        restApiError.setDeveloperMessage(e.getMessage());
        return createResponseEntity(restApiError);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<RestApiError> handleException(Exception e) {
        log.error("Unhandled api error", e);
        RestApiError restApiError = new RestApiError(RestApiHttpStatus.INTERNAL_SERVER_ERROR);
        restApiError.setUserMessage("The server encountered an error and could not complete the request");
        restApiError.setDeveloperMessage(e.getMessage());
        return createResponseEntity(restApiError);
    }

    private RestApiError badRequest(String userMessage, String developerMessage) {
        RestApiError restApiError = new RestApiError(RestApiHttpStatus.BAD_REQUEST);
        restApiError.setUserMessage(userMessage);
        restApiError.setDeveloperMessage(developerMessage);
        return restApiError;
    }

    private List<RestApiValidationError> toValidationErrors(List<FieldError> fieldErrors) {
        return fieldErrors.stream()
                .map(error -> new RestApiValidationError(error.getField(), error.getDefaultMessage()))
                .toList();
    }

    private ResponseEntity<RestApiError> createResponseEntity(RestApiError restApiError) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new ResponseEntity<>(restApiError, headers, HttpStatus.valueOf(restApiError.getHttpStatus()));
    }
}
