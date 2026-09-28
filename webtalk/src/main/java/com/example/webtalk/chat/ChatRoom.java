package com.example.webtalk.chat;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * 1대1 대화방 기본 정보 (5.4 CHAT_ROOMS). 이번 범위에서는 room_type이 항상 DIRECT다.
 */
@Entity
@Table(name = "chat_rooms")
public class ChatRoom {

    public static final String ROOM_TYPE_DIRECT = "DIRECT";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_type", nullable = false, length = 20)
    private String roomType;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected ChatRoom() {
        // JPA
    }

    public ChatRoom(String roomType) {
        this.roomType = roomType;
        this.createdAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getRoomType() {
        return roomType;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
