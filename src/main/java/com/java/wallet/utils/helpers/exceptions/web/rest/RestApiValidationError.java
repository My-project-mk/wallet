package com.java.wallet.utils.helpers.exceptions.web.rest;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class RestApiValidationError {

    private String field;
    private String message;
}
