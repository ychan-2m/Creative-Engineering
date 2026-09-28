package com.example.webtalk.message;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * 텍스트 메시지와 전송 시각 (5.4 MESSAGES). client_message_id는 재전송 중복 저장을 막는 유일 키다 (NFR03, E09, T06).
 */
@Entity
@Table(name = "messages")
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "chat_room_id", nullable = false)
    private Long chatRoomId;

    @Column(name = "sender_id", nullable = false)
    private Long senderId;

    @Column(name = "client_message_id", nullable = false, unique = true, length = 36)
    private String clientMessageId;

    @Column(nullable = false, length = 500)
    private String content;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    protected Message() {
        // JPA
    }

    public Message(Long chatRoomId, Long senderId, String clientMessageId, String content) {
        this.chatRoomId = chatRoomId;
        this.senderId = senderId;
        this.clientMessageId = clientMessageId;
        this.content = content;
        this.createdAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getChatRoomId() {
        return chatRoomId;
    }

    public Long getSenderId() {
        return senderId;
    }

    public String getClientMessageId() {
        return clientMessageId;
    }

    public String getContent() {
        return content;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
