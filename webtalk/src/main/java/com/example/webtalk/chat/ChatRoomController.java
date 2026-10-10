package com.example.webtalk.chat;

import com.example.webtalk.auth.WebtalkUserPrincipal;
import com.example.webtalk.chat.dto.ChatRoomDirectRequest;
import com.example.webtalk.chat.dto.ChatRoomListItemResponse;
import com.example.webtalk.chat.dto.ChatRoomResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * FR05 1대1 대화방 생성/조회 (5.5 REST API 명세).
 */
@RestController
@RequestMapping("/api/chat-rooms")
public class ChatRoomController {

    private final ChatRoomService chatRoomService;

    public ChatRoomController(ChatRoomService chatRoomService) {
        this.chatRoomService = chatRoomService;
    }

    @PostMapping("/direct")
    public ChatRoomResponse getOrCreateDirect(@Valid @RequestBody ChatRoomDirectRequest request,
                                               @AuthenticationPrincipal WebtalkUserPrincipal principal) {
        return chatRoomService.getOrCreateDirectRoom(principal.getUsername(), request.targetUserId());
    }

    public record ReadRequest(@NotNull @Positive Long messageId) {}

    @PostMapping("/{roomId}/read")
    public ResponseEntity<Void> read(@PathVariable Long roomId, @Valid @RequestBody ReadRequest request,
                                    @AuthenticationPrincipal WebtalkUserPrincipal principal) {
        chatRoomService.markRead(principal.getUsername(), roomId, request.messageId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public List<ChatRoomListItemResponse> myRooms(@AuthenticationPrincipal WebtalkUserPrincipal principal) {
        return chatRoomService.listRooms(principal.getUsername());
    }
}
