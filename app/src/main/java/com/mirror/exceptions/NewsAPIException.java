package com.mirror.exceptions;
import com.mirror.models.v1.APINewsResponse;
import com.mirror.models.v1.NewsAPIStatusCodes;
public class NewsAPIException extends Exception {
    final NewsAPIStatusCodes statusCode;


    public NewsAPIException(String message,NewsAPIStatusCodes code) {
        super(message);
        this.statusCode = code;
    }

    public NewsAPIStatusCodes getStatusCode() {
        return this.statusCode;
    }
}