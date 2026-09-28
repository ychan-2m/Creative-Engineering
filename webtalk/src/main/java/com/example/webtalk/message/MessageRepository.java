package com.example.webtalk.message;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface MessageRepository extends JpaRepository<Message, Long> {

    // NFR03 / E09 / T06: 같은 clientMessageId로 재전송해도 새로 저장하지 않고 기존 결과를 돌려준다.
    Optional<Message> findByClientMessageId(String clientMessageId);

    // FR07 대화 기록 조회: 최근 메시지부터 가져온 뒤 서비스에서 시간순으로 뒤집는다.
    List<Message> findByChatRoomIdOrderByCreatedAtDescIdDesc(Long chatRoomId, Pageable pageable);

    long countByChatRoomIdAndIdGreaterThan(Long chatRoomId, Long messageId);

    long countByChatRoomId(Long chatRoomId);

    Optional<Message> findFirstByChatRoomIdOrderByCreatedAtDescIdDesc(Long chatRoomId);
}
