package com.java.wallet.utils.helpers.exceptions;

import com.java.wallet.utils.helpers.exceptions.web.rest.RestApiException;
import com.java.wallet.utils.helpers.exceptions.web.rest.RestApiHttpStatus;

public class ResourceNotFoundRestApiException extends RestApiException {

    private static final long serialVersionUID = 1L;

    public ResourceNotFoundRestApiException() {
        super(RestApiHttpStatus.NOT_FOUND);
    }
}
