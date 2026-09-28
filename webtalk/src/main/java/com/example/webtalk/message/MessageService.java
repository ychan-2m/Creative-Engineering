package com.example.webtalk.message;

import com.example.webtalk.chat.ChatRoomMemberId;
import com.example.webtalk.chat.ChatRoomMemberRepository;
import com.example.webtalk.common.ChatRoomForbiddenException;
import com.example.webtalk.common.InvalidMessageException;
import com.example.webtalk.message.dto.ChatSendRequest;
import com.example.webtalk.message.dto.MessageResponse;
import com.example.webtalk.user.User;
import com.example.webtalk.user.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class MessageService {

    private static final int MAX_CONTENT_LENGTH = 500;
    private static final int HISTORY_LIMIT = 50;

    private final MessageRepository messageRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final UserRepository userRepository;

    public MessageService(MessageRepository messageRepository,
                           ChatRoomMemberRepository chatRoomMemberRepository,
                           UserRepository userRepository) {
        this.messageRepository = messageRepository;
        this.chatRoomMemberRepository = chatRoomMemberRepository;
        this.userRepository = userRepository;
    }

    /**
     * 5.7 서버 권한 검사 순서를 그대로 구현한다. 저장까지만 트랜잭션으로 묶고,
     * 실시간 발행(messagingTemplate.convertAndSend)은 이 메서드가 커밋된 뒤
     * 호출자(WebSocket 컨트롤러)가 별도로 수행한다 (6.3 "먼저 REST로... WebSocket을 붙이는 이유",
     * 6.4 코드 뼈대 설명: "메시지 발행은 DB 트랜잭션 성공 뒤 실행되도록").
     */
    @Transactional
    public MessageResponse send(String senderLoginId, ChatSendRequest request) {
        // 1. 로그인 사용자 확인
        User sender = userRepository.findByLoginId(senderLoginId)
                .orElseThrow(ChatRoomForbiddenException::new);

        // 2~3. roomId 존재 및 방 구성원 여부 확인
        if (request.roomId() == null) {
            throw new ChatRoomForbiddenException();
        }
        ChatRoomMemberId memberId = new ChatRoomMemberId(request.roomId(), sender.getId());
        if (!chatRoomMemberRepository.existsById(memberId)) {
            throw new ChatRoomForbiddenException();
        }

        // 4. content trim 후 길이 검사 (E03 빈 메시지, E04/T11 500자 초과)
        String content = request.content() == null ? "" : request.content().trim();
        if (content.isEmpty()) {
            throw new InvalidMessageException("빈 메시지는 전송할 수 없습니다.");
        }
        if (content.length() > MAX_CONTENT_LENGTH) {
            throw new InvalidMessageException("메시지는 " + MAX_CONTENT_LENGTH + "자 이하로 입력해 주세요.");
        }
        if (request.clientMessageId() == null || request.clientMessageId().isBlank()) {
            throw new InvalidMessageException("clientMessageId가 필요합니다.");
        }

        // 5. 중복 요청이면 기존 메시지를 그대로 반환 (E09, T06, NFR03)
        var existing = messageRepository.findByClientMessageId(request.clientMessageId());
        if (existing.isPresent()) {
            return toResponse(existing.get(), sender.getNickname());
        }

        // 6. 새 메시지 저장
        Message saved = messageRepository.save(
                new Message(request.roomId(), sender.getId(), request.clientMessageId(), content));
        return toResponse(saved, sender.getNickname());
    }

    /**
     * FR07 대화 기록 조회. 방 구성원이 아니면 403에 해당하는 예외를 던진다 (E06, T10).
     */
    @Transactional(readOnly = true)
    public List<MessageResponse> getHistory(String loginId, Long roomId) {
        User user = userRepository.findByLoginId(loginId)
                .orElseThrow(ChatRoomForbiddenException::new);
        if (!chatRoomMemberRepository.existsById(new ChatRoomMemberId(roomId, user.getId()))) {
            throw new ChatRoomForbiddenException();
        }

        List<Message> recentDesc = messageRepository
                .findByChatRoomIdOrderByCreatedAtDescIdDesc(roomId, PageRequest.of(0, HISTORY_LIMIT));

        Map<Long, String> nicknameByUserId = userRepository
                .findAllById(recentDesc.stream().map(Message::getSenderId).distinct().toList())
                .stream()
                .collect(Collectors.toMap(User::getId, User::getNickname));

        List<Message> ascending = new java.util.ArrayList<>(recentDesc);
        Collections.reverse(ascending);
        return ascending.stream()
                .map(m -> toResponse(m, nicknameByUserId.getOrDefault(m.getSenderId(), "알 수 없음")))
                .toList();
    }

    private MessageResponse toResponse(Message message, String senderNickname) {
        return new MessageResponse(
                message.getId(),
                message.getChatRoomId(),
                message.getSenderId(),
                senderNickname,
                message.getContent(),
                message.getCreatedAt(),
                message.getClientMessageId()
        );
    }
}
