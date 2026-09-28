package com.example.webtalk.chat.dto;

import jakarta.validation.constraints.NotNull;

/**
 * POST /api/chat-rooms/direct 요청 본문 (부록 B).
 */
public record ChatRoomDirectRequest(@NotNull(message = "대화 상대를 선택해 주세요.") Long targetUserId) {
}
