package com.mirror.models.v1;
import java.util.Arrays;
import java.util.Optional;
public enum NewsAPIStatusCodes {
        OK (200),
        BAD_REQUEST (400),
        UNAUTHORIZED (401),
        THROTTLING (429),
        SERVER_ERROR (500),
        //Custom to Service
        INTERNAL_INTERRUPT(999);

        final int statusCode;

        NewsAPIStatusCodes(int code) {
            this.statusCode = code;
        }

        public int getCode() {
            return this.statusCode;
        }

        public static Optional<NewsAPIStatusCodes> fromInt(int code) {
    return Arrays.stream(values())
                 .filter(s -> s.statusCode == code)
                 .findFirst();
}
}