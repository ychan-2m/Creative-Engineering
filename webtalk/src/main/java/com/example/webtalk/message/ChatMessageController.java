package com.example.webtalk.message;

import com.example.webtalk.auth.WebtalkUserPrincipal;
import com.example.webtalk.message.dto.MessageResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * FR07 대화 기록 조회 (GET /api/chat-rooms/{id}/messages, 5.5 REST API 명세).
 */
@RestController
@RequestMapping("/api/chat-rooms")
public class ChatMessageController {

    private final MessageService messageService;

    public ChatMessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    @GetMapping("/{roomId}/messages")
    public List<MessageResponse> history(@PathVariable Long roomId,
                                          @AuthenticationPrincipal WebtalkUserPrincipal principal) {
        return messageService.getHistory(principal.getUsername(), roomId);
    }
}
