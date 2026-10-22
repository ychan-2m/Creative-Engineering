package com.example.webtalk.chat;

/** DB 커밋 이후 대화 목록을 다시 조회하도록 구성원에게 알린다. */
public record RoomChangedEvent(Long roomId, java.util.List<String> memberLoginIds) {}
