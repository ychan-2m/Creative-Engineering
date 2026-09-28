package com.example.webtalk.common;

/**
 * 방 구성원이 아닌 사용자가 대화방에 접근할 때 발생한다 (5.7 권한 검사, 5.8 보안 기준, E06/T10).
 */
public class ChatRoomForbiddenException extends RuntimeException {
    public ChatRoomForbiddenException() {
        super("이 대화방에 접근할 수 없습니다.");
    }
}
