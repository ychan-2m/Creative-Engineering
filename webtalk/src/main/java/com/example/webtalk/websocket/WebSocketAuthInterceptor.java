package com.example.webtalk.websocket;

import com.example.webtalk.chat.ChatRoomMemberId;
import com.example.webtalk.chat.ChatRoomMemberRepository;
import com.example.webtalk.common.ErrorResponse;
import com.example.webtalk.user.User;
import com.example.webtalk.user.UserRepository;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.Optional;

/**
 * 5.7 서버 권한 검사 순서 중 "구독" 단계를 담당한다. /topic/chat/{roomId}를 구독하려는
 * 사용자가 해당 방의 구성원이 아니면 구독을 막고, /user/queue/errors로 오류를 알린다
 * (5.6 개인 오류 경로, 5.8 "다른 방 열람" 대응).
 */
@Component
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    private static final String ROOM_TOPIC_PREFIX = "/topic/chat/";

    private final UserRepository userRepository;
    private final ChatRoomMemberRepository chatRoomMemberRepository;
    private final SimpMessagingTemplate messagingTemplate;

    public WebSocketAuthInterceptor(UserRepository userRepository,
                                     ChatRoomMemberRepository chatRoomMemberRepository,
                                     @Lazy SimpMessagingTemplate messagingTemplate) {
        // 이 인터셉터는 WebSocketConfig(메시지 브로커 설정)에 등록되는데, 브로커가 만드는
        // SimpMessagingTemplate(brokerMessagingTemplate)이 바로 그 설정이 끝나야 생기므로
        // 즉시 주입하면 순환 참조가 발생한다. @Lazy로 실제 사용 시점까지 생성을 미룬다.
        this.userRepository = userRepository;
        this.chatRoomMemberRepository = chatRoomMemberRepository;
        this.messagingTemplate = messagingTemplate;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null) {
            return message;
        }

        Principal principal = accessor.getUser();

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            // /ws 엔드포인트 자체가 인증을 요구하므로(SecurityConfig) 원칙적으로 principal이 없을 수 없지만,
            // 방어적으로 한 번 더 막는다.
            if (principal == null) {
                return null;
            }
            return message;
        }

        if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
            String destination = accessor.getDestination();
            if (destination == null || !destination.startsWith(ROOM_TOPIC_PREFIX)) {
                return message; // 개인 오류 큐 등 다른 구독은 그대로 통과시킨다.
            }
            if (principal == null) {
                return null;
            }

            Long roomId = parseRoomId(destination);
            Optional<User> user = userRepository.findByLoginId(principal.getName());
            boolean isMember = roomId != null && user.isPresent()
                    && chatRoomMemberRepository.existsById(new ChatRoomMemberId(roomId, user.get().getId()));

            if (!isMember) {
                messagingTemplate.convertAndSendToUser(
                        principal.getName(),
                        "/queue/errors",
                        new ErrorResponse("CHAT_ROOM_FORBIDDEN", "이 대화방에 접근할 수 없습니다.")
                );
                return null; // SUBSCRIBE 프레임을 취소해 구독 자체를 막는다.
            }
        }

        return message;
    }

    private Long parseRoomId(String destination) {
        try {
            return Long.parseLong(destination.substring(ROOM_TOPIC_PREFIX.length()));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
