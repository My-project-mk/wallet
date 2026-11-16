package com.java.wallet.utils.helpers.exceptions;

import com.java.wallet.utils.helpers.exceptions.web.rest.RestApiException;
import com.java.wallet.utils.helpers.exceptions.web.rest.RestApiHttpStatus;

public class BadRequestRestApiException extends RestApiException {

    private static final long serialVersionUID = 1L;

    public BadRequestRestApiException() {
        super(RestApiHttpStatus.BAD_REQUEST);
    }
}
