package com.example.webtalk.chat.dto;

import java.time.OffsetDateTime;

/**
 * GET /api/chat-rooms 목록 항목. FR10(안 읽은 수 표시, 확장 우선순위 1)을 위해
 * unreadCount를 함께 내려주지만 화면에서 사용하지 않아도 동작에는 영향이 없다.
 */
public record ChatRoomListItemResponse(
        Long roomId,
        PeerSummary peer,
        String lastMessageContent,
        OffsetDateTime lastMessageAt,
        long unreadCount
) {
}
