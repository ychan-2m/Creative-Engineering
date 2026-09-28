package com.example.webtalk.common;

/**
 * 빈 메시지(E03) 또는 500자 초과 메시지(E04, T11)일 때 발생한다.
 */
public class InvalidMessageException extends RuntimeException {
    public InvalidMessageException(String message) {
        super(message);
    }
}
