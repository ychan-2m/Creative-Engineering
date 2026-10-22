package com.example.webtalk.websocket;

import com.example.webtalk.chat.ChatRoomMemberId;
import com.example.webtalk.chat.ChatRoomMemberRepository;
import com.example.webtalk.user.User;
import com.example.webtalk.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class WebSocketAuthInterceptorTests {
    UserRepository users = mock(UserRepository.class);
    ChatRoomMemberRepository members = mock(ChatRoomMemberRepository.class);
    WebSocketAuthInterceptor interceptor = new WebSocketAuthInterceptor(users, members, mock(SimpMessagingTemplate.class));

    Message<byte[]> frame(StompCommand command, String destination, boolean authenticated) {
        var headers = StompHeaderAccessor.create(command);
        headers.setDestination(destination);
        if (authenticated) headers.setUser(() -> "alice");
        headers.setLeaveMutable(true);
        return MessageBuilder.createMessage(new byte[0], headers.getMessageHeaders());
    }

    @Test void onlyApplicationSendDestinationIsAllowed() {
        assertNotNull(interceptor.preSend(frame(StompCommand.SEND, "/app/chat.send", true), null));
        for (String path : new String[]{"/topic/chat/1", "/queue/errors", "/user/bob/queue/errors", "/app/other"}) {
            assertNull(interceptor.preSend(frame(StompCommand.SEND, path, true), null), path);
        }
        assertNull(interceptor.preSend(frame(StompCommand.SEND, "/app/chat.send", false), null));
    }

    @Test void wildcardAndRawQueueSubscriptionsCannotBypassMembership() {
        when(users.findByLoginId("alice")).thenReturn(Optional.empty());
        for (String path : new String[]{"/topic/**", "/topic/chat/*", "/topic/chat/01", "/queue/**", "/user/bob/queue/rooms", "/topic/chat/999999999999999999999"}) {
            assertNull(interceptor.preSend(frame(StompCommand.SUBSCRIBE, path, true), null), path);
        }
        verifyNoInteractions(members);
    }

    @Test void onlyAuthenticatedPersonalQueuesAndMemberRoomAreAllowed() {
        for (String path : new String[]{"/user/queue/errors", "/user/queue/rooms"}) {
            assertNotNull(interceptor.preSend(frame(StompCommand.SUBSCRIBE, path, true), null));
            assertNull(interceptor.preSend(frame(StompCommand.SUBSCRIBE, path, false), null));
        }
        User alice = mock(User.class);
        when(alice.getId()).thenReturn(7L);
        when(users.findByLoginId("alice")).thenReturn(Optional.of(alice));
        when(members.existsById(new ChatRoomMemberId(1L, 7L))).thenReturn(true);
        assertNotNull(interceptor.preSend(frame(StompCommand.SUBSCRIBE, "/topic/chat/1", true), null));
        assertNull(interceptor.preSend(frame(StompCommand.SUBSCRIBE, "/topic/chat/2", true), null));
    }
}
