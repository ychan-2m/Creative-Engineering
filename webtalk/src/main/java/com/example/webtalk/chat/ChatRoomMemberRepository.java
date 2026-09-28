package com.example.webtalk.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatRoomMemberRepository extends JpaRepository<ChatRoomMember, ChatRoomMemberId> {

    List<ChatRoomMember> findByIdUserIdOrderByJoinedAtDesc(Long userId);

    List<ChatRoomMember> findByIdChatRoomId(Long chatRoomId);

    // 두 사용자가 이미 함께 있는 1대1 방을 찾는다 (E05: 있으면 새로 만들지 않고 기존 방을 반환).
    @Query("""
            SELECT m1.id.chatRoomId FROM ChatRoomMember m1
            JOIN ChatRoomMember m2 ON m1.id.chatRoomId = m2.id.chatRoomId
            WHERE m1.id.userId = :userId1 AND m2.id.userId = :userId2
            """)
    Optional<Long> findDirectRoomId(@Param("userId1") Long userId1, @Param("userId2") Long userId2);
}
