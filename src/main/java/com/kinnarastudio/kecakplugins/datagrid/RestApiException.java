package com.kinnarastudio.kecakplugins.datagrid;

public class RestApiException extends Exception {
    private final int errorCode;

    public RestApiException(int errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public RestApiException(int errorCode, Throwable cause) {
        super(cause);
        this.errorCode = errorCode;
    }

    /**
     * Get errorCode
     * @return
     */
    public int getErrorCode() {
        return errorCode;
    }
}
