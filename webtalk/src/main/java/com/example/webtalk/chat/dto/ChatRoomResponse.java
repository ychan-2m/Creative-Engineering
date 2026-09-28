package com.example.webtalk.chat.dto;

/**
 * POST /api/chat-rooms/direct 응답 (부록 B). created=false면 기존 방을 그대로 반환한 것이다.
 */
public record ChatRoomResponse(Long roomId, PeerSummary peer, boolean created) {
}
