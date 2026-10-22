package com.example.webtalk.chat;

import com.example.webtalk.chat.dto.ChatRoomListItemResponse;
import com.example.webtalk.chat.dto.ChatRoomResponse;
import com.example.webtalk.chat.dto.PeerSummary;
import com.example.webtalk.common.InvalidMessageException;
import com.example.webtalk.common.ChatRoomForbiddenException;
import com.example.webtalk.common.UserNotFoundException;
import com.example.webtalk.message.Message;
import com.example.webtalk.message.MessageRepository;
import com.example.webtalk.user.User;
import com.example.webtalk.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * FR05 1대1 대화방 생성/조회. E05: 이미 있는 방이면 새로 만들지 않고 기존 방을 반환한다.
 */
@Service
public class ChatRoomService {

    private final ChatRoomRepository chatRoomRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final UserRepository userRepository;
    private final MessageRepository messageRepository;
    private final ApplicationEventPublisher events;

    public ChatRoomService(ChatRoomRepository chatRoomRepository,
                            ChatRoomMemberRepository chatRoomMemberRepository,
                            UserRepository userRepository,
                            MessageRepository messageRepository, ApplicationEventPublisher events) {
        this.chatRoomRepository = chatRoomRepository;
        this.chatRoomMemberRepository = chatRoomMemberRepository;
        this.userRepository = userRepository;
        this.messageRepository = messageRepository;
        this.events = events;
    }

    @Transactional
    public ChatRoomResponse getOrCreateDirectRoom(String loginId, Long targetUserId) {
        User self = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다: " + loginId));

        if (self.getId().equals(targetUserId)) {
            throw new InvalidMessageException("자기 자신과는 대화방을 만들 수 없습니다.");
        }
        User target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new UserNotFoundException("대화 상대를 찾을 수 없습니다: id=" + targetUserId));

        // 양쪽 사용자가 동시에 요청해도 같은 쌍은 작은 ID의 사용자 행을 먼저 잠근다.
        userRepository.lockById(Math.min(self.getId(), target.getId())).orElseThrow(ChatRoomForbiddenException::new);
        Optional<Long> existingRoomId = chatRoomMemberRepository.findDirectRoomId(self.getId(), target.getId());
        if (existingRoomId.isPresent()) {
            return new ChatRoomResponse(existingRoomId.get(), PeerSummary.from(target), false);
        }

        ChatRoom room = chatRoomRepository.save(new ChatRoom(ChatRoom.ROOM_TYPE_DIRECT));
        chatRoomMemberRepository.save(new ChatRoomMember(room, self));
        chatRoomMemberRepository.save(new ChatRoomMember(room, target));
        events.publishEvent(new RoomChangedEvent(room.getId(), List.of(self.getLoginId(), target.getLoginId())));

        return new ChatRoomResponse(room.getId(), PeerSummary.from(target), true);
    }

    @Transactional(readOnly = true)
    public List<ChatRoomListItemResponse> listRooms(String loginId) {
        User self = userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다: " + loginId));

        List<ChatRoomMember> myMemberships = chatRoomMemberRepository.findByIdUserIdOrderByJoinedAtDesc(self.getId());

        return myMemberships.stream()
                .map(membership -> toListItem(self, membership))
                .filter(java.util.Objects::nonNull)
                .sorted(java.util.Comparator.comparing(ChatRoomListItemResponse::lastMessageAt,
                        java.util.Comparator.nullsLast(java.util.Comparator.reverseOrder())))
                .toList();
    }

    @Transactional
    public void markRead(String loginId, Long roomId, Long messageId) {
        User self = userRepository.findByLoginId(loginId).orElseThrow(ChatRoomForbiddenException::new);
        ChatRoomMemberId memberId = new ChatRoomMemberId(roomId, self.getId());
        if (!chatRoomMemberRepository.existsById(memberId)) throw new ChatRoomForbiddenException();
        Message message = messageRepository.findById(messageId)
                .filter(m -> m.getChatRoomId().equals(roomId))
                .orElseThrow(() -> new InvalidMessageException("이 대화방의 메시지만 읽음 처리할 수 있습니다."));
        if (chatRoomMemberRepository.advanceRead(roomId, self.getId(), message.getId()) > 0) {
            events.publishEvent(new RoomChangedEvent(roomId, chatRoomMemberRepository.findMemberLoginIds(roomId)));
        }
    }

    private ChatRoomListItemResponse toListItem(User self, ChatRoomMember myMembership) {
        Long roomId = myMembership.getId().getChatRoomId();

        // 1대1 방이므로 나를 제외한 나머지 구성원 한 명이 상대다.
        Optional<User> peer = chatRoomMemberRepository.findByIdChatRoomId(roomId).stream()
                .map(ChatRoomMember::getUser)
                .filter(user -> !user.getId().equals(self.getId()))
                .findFirst();
        if (peer.isEmpty()) {
            return null;
        }

        Optional<Message> lastMessage = messageRepository.findFirstByChatRoomIdOrderByCreatedAtDescIdDesc(roomId);
        long unreadCount = messageRepository.countByChatRoomIdAndSenderIdNotAndIdGreaterThan(
                roomId, self.getId(), myMembership.getLastReadMessageId() == null ? 0L : myMembership.getLastReadMessageId());

        return new ChatRoomListItemResponse(
                roomId,
                PeerSummary.from(peer.get()),
                lastMessage.map(Message::getContent).orElse(null),
                lastMessage.map(Message::getCreatedAt).orElse(null),
                unreadCount
        );
    }
}
