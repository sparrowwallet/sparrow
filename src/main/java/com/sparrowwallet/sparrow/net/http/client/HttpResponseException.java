package com.sparrowwallet.sparrow.net.http.client;

public class HttpResponseException extends HttpException {
    private final String responseBody;
    private final int statusCode;

    public HttpResponseException(Exception cause, String responseBody, int statusCode) {
        super(cause);
        this.responseBody = responseBody;
        this.statusCode = statusCode;
    }

    public HttpResponseException(String message, String responseBody, int statusCode) {
        super(message);
        this.responseBody = responseBody;
        this.statusCode = statusCode;
    }

    public HttpResponseException(String responseBody, int statusCode) {
        this("response statusCode=" + statusCode, responseBody, statusCode);
    }

    public String getResponseBody() {
        return responseBody;
    }

    public int getStatusCode() {
        return statusCode;
    }

    @Override
    public String toString() {
        return "HttpResponseException{" +
                "message=" + getMessage() + ", " +
                "responseBody='" + responseBody + '\'' +
                '}';
    }
}
