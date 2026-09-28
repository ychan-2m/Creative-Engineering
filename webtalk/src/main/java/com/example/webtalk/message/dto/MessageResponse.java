package com.example.webtalk.message.dto;

import java.time.OffsetDateTime;

/**
 * 5.6의 MESSAGE /topic/chat/{roomId} 예시에 clientMessageId를 추가했다.
 * 보고서 예시에는 없지만, 발신자 화면이 자신이 보낸 SENDING 말풍선을 이 메시지와
 * 맞춰 SENT로 바꾸려면(4.4 메시지 상태 규칙) 상관관계를 알려줄 값이 필요하다.
 */
public record MessageResponse(
        Long messageId,
        Long roomId,
        Long senderId,
        String senderNickname,
        String content,
        OffsetDateTime createdAt,
        String clientMessageId
) {
}
