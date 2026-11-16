package com.java.wallet.utils.helpers.exceptions.web.rest;

import lombok.Getter;

//The subset of HTTP status codes this API returns for errors
@Getter
public enum RestApiHttpStatus {

//   {@code 400} Something is wrong with the client's input, e.g. invalid data or insufficient funds
    BAD_REQUEST(400, "Bad Request"),

//    {@code 404} The requested resource does not exist
    NOT_FOUND(404, "Not Found"),

//    {@code 409} The request conflicts with an earlier one, e.g. an idempotency key reused with another payload
    CONFLICT(409, "Conflict"),

//    {@code 425} An identical request is still being processed; the client should retry shortly
    TOO_EARLY(425, "Too Early"),

//    {@code 500} A problem on the server that the client did not cause
    INTERNAL_SERVER_ERROR(500, "Internal Server Error");

    private final int statusCode;
    private final String description;

    RestApiHttpStatus(int statusCode, String description) {
        this.statusCode = statusCode;
        this.description = description;
    }
}
