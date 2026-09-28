package com.example.webtalk.websocket;

import com.example.webtalk.common.ChatRoomForbiddenException;
import com.example.webtalk.common.ErrorResponse;
import com.example.webtalk.common.InvalidMessageException;
import com.example.webtalk.message.MessageService;
import com.example.webtalk.message.dto.ChatSendRequest;
import com.example.webtalk.message.dto.MessageResponse;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;

/**
 * STOMP /app/chat.send 처리 (5.6, 6.4). 저장은 MessageService의 트랜잭션 안에서 끝내고,
 * 트랜잭션이 커밋된 뒤(메서드 반환 후) 구독자에게 발행한다.
 */
@Controller
public class ChatWebSocketController {

    private final MessageService messageService;
    private final SimpMessagingTemplate messagingTemplate;

    public ChatWebSocketController(MessageService messageService, SimpMessagingTemplate messagingTemplate) {
        this.messageService = messageService;
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping("/chat.send")
    public void send(@Payload ChatSendRequest request, Principal principal) {
        try {
            MessageResponse saved = messageService.send(principal.getName(), request);
            messagingTemplate.convertAndSend("/topic/chat/" + request.roomId(), saved);
        } catch (ChatRoomForbiddenException ex) {
            sendError(principal, "CHAT_ROOM_FORBIDDEN", ex.getMessage());
        } catch (InvalidMessageException ex) {
            sendError(principal, "INVALID_MESSAGE", ex.getMessage());
        } catch (RuntimeException ex) {
            // 내부 원인은 서버 로그로만 남기고, 클라이언트에는 일반화된 안내만 전달한다 (6.6 "오류에 내부 정보 노출 금지").
            sendError(principal, "SEND_FAILED", "메시지를 전송하지 못했습니다.");
        }
    }

    private void sendError(Principal principal, String code, String message) {
        messagingTemplate.convertAndSendToUser(principal.getName(), "/queue/errors", new ErrorResponse(code, message));
    }
}
