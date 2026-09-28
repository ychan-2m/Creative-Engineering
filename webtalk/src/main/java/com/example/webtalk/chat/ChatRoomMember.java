package com.example.webtalk.chat;

import com.example.webtalk.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * 방과 사용자 관계 (5.4 CHAT_ROOM_MEMBERS). 서버 권한 검사(5.7)는 항상 이 테이블로
 * "로그인 사용자가 이 방의 구성원인가"를 확인한다.
 */
@Entity
@Table(name = "chat_room_members")
public class ChatRoomMember {

    @EmbeddedId
    private ChatRoomMemberId id;

    @ManyToOne(optional = false)
    @MapsId("chatRoomId")
    @JoinColumn(name = "chat_room_id")
    private ChatRoom chatRoom;

    @ManyToOne(optional = false)
    @MapsId("userId")
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "joined_at", nullable = false)
    private OffsetDateTime joinedAt;

    @Column(name = "last_read_message_id")
    private Long lastReadMessageId;

    protected ChatRoomMember() {
        // JPA
    }

    public ChatRoomMember(ChatRoom chatRoom, User user) {
        this.chatRoom = chatRoom;
        this.user = user;
        this.id = new ChatRoomMemberId(chatRoom.getId(), user.getId());
        this.joinedAt = OffsetDateTime.now();
    }

    public ChatRoomMemberId getId() {
        return id;
    }

    public ChatRoom getChatRoom() {
        return chatRoom;
    }

    public User getUser() {
        return user;
    }

    public OffsetDateTime getJoinedAt() {
        return joinedAt;
    }

    public Long getLastReadMessageId() {
        return lastReadMessageId;
    }

    public void updateLastReadMessageId(Long messageId) {
        this.lastReadMessageId = messageId;
    }
}
