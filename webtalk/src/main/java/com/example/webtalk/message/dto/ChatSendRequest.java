package com.example.webtalk.message.dto;

/**
 * STOMP SEND /app/chat.send 페이로드 (5.6). content의 trim/길이 검사는
 * MessageService에서 5.7 순서대로 직접 수행한다(빈 값·500자 초과를 단순 애노테이션이 아니라
 * 보고서에 적힌 절차대로 명시적으로 처리하기 위함).
 */
public record ChatSendRequest(Long roomId, String clientMessageId, String content) {
}
