package com.example.webtalk.message;

import com.example.webtalk.chat.ChatRoomService;
import com.example.webtalk.common.ChatRoomForbiddenException;
import com.example.webtalk.common.InvalidMessageException;
import com.example.webtalk.message.dto.ChatSendRequest;
import com.example.webtalk.user.User;
import com.example.webtalk.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"debug=false", "spring.datasource.hikari.maximum-pool-size=4", "spring.datasource.hikari.connection-timeout=2000"})
class MessageFlowTests {
    @Autowired MessageService service;
    @Autowired MessageRepository messages;
    @Autowired UserRepository users;
    @Autowired ChatRoomService rooms;
    User alice, bob, other;
    Long room;

    @BeforeEach void accounts() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        alice = users.save(new User("flow_a_" + suffix, "unused", "Alice"));
        bob = users.save(new User("flow_b_" + suffix, "unused", "Bob"));
        other = users.save(new User("flow_c_" + suffix, "unused", "Other"));
        room = rooms.getOrCreateDirectRoom(alice.getLoginId(), bob.getId()).roomId();
    }

    @Test void historyPagesAndCatchupCoverMoreThanFiftyWithoutGaps() {
        List<Message> records = new ArrayList<>();
        for (int i = 0; i < 125; i++) records.add(new Message(room, bob.getId(), UUID.randomUUID().toString(), "메시지 " + i));
        records = messages.saveAll(records);
        var latest = service.getHistory(alice.getLoginId(), room);
        assertEquals(50, latest.size());
        assertEquals(records.get(75).getId(), latest.get(0).messageId());
        var older = service.getHistory(alice.getLoginId(), room, latest.get(0).messageId(), null);
        assertEquals(records.get(25).getId(), older.get(0).messageId());
        var oldest = service.getHistory(alice.getLoginId(), room, older.get(0).messageId(), null);
        assertEquals(25, oldest.size());
        assertEquals(records.get(0).getId(), oldest.get(0).messageId());
        List<Long> recovered = new ArrayList<>();
        long cursor = 0;
        while (true) {
            var page = service.getHistory(alice.getLoginId(), room, null, cursor);
            if (page.isEmpty()) break;
            recovered.addAll(page.stream().map(m -> m.messageId()).toList());
            cursor = page.get(page.size() - 1).messageId();
        }
        assertEquals(records.stream().map(Message::getId).toList(), recovered);
    }

    @Test void historyChecksMembershipAndCursorValues() {
        assertThrows(ChatRoomForbiddenException.class, () -> service.getHistory(other.getLoginId(), room, null, 0L));
        assertThrows(InvalidMessageException.class, () -> service.getHistory(alice.getLoginId(), room, 1L, 1L));
        assertThrows(InvalidMessageException.class, () -> service.getHistory(alice.getLoginId(), room, 0L, null));
        assertThrows(InvalidMessageException.class, () -> service.getHistory(alice.getLoginId(), room, null, -1L));
    }

    @Test void retryMustMatchOriginalSenderRoomAndContent() {
        String id = UUID.randomUUID().toString();
        var request = new ChatSendRequest(room, id, "원본");
        var saved = service.send(alice.getLoginId(), request);
        assertEquals(saved.messageId(), service.send(alice.getLoginId(), request).messageId());
        assertThrows(InvalidMessageException.class, () -> service.send(bob.getLoginId(), request));
        assertThrows(InvalidMessageException.class, () -> service.send(alice.getLoginId(), new ChatSendRequest(room, id, "변경")));
        Long anotherRoom = rooms.getOrCreateDirectRoom(alice.getLoginId(), other.getId()).roomId();
        assertThrows(InvalidMessageException.class, () -> service.send(alice.getLoginId(), new ChatSendRequest(anotherRoom, id, "원본")));
        assertEquals(1, messages.countByChatRoomId(room));
        assertEquals(0, messages.countByChatRoomId(anotherRoom));
    }

    @Test void invalidClientIdIsRejectedBeforeDatabaseWrite() {
        assertThrows(InvalidMessageException.class, () -> service.send(alice.getLoginId(), new ChatSendRequest(room, "x".repeat(37), "내용")));
        assertEquals(0, messages.countByChatRoomId(room));
    }

    @Test void concurrentRetriesStoreExactlyOneMessage() throws Exception {
        var request = new ChatSendRequest(room, UUID.randomUUID().toString(), "동시 재전송");
        List<Long> ids = concurrently(() -> service.send(alice.getLoginId(), request).messageId(),
                () -> service.send(alice.getLoginId(), request).messageId());
        assertEquals(ids.get(0), ids.get(1));
        assertEquals(1, messages.countByChatRoomId(room));
    }

    @Test void simultaneousDirectRoomRequestsReturnTheSameRoom() throws Exception {
        List<Long> ids = concurrently(
                () -> rooms.getOrCreateDirectRoom(alice.getLoginId(), other.getId()).roomId(),
                () -> rooms.getOrCreateDirectRoom(other.getLoginId(), alice.getId()).roomId());
        assertEquals(ids.get(0), ids.get(1));
    }

    @Test void burstSendingDoesNotExhaustConnectionsDuringCommitNotifications() throws Exception {
        var pool = Executors.newFixedThreadPool(12);
        try {
            List<java.util.concurrent.Future<Long>> results = new ArrayList<>();
            for (int i = 0; i < 24; i++) {
                results.add(pool.submit(() -> service.send(alice.getLoginId(),
                        new ChatSendRequest(room, UUID.randomUUID().toString(), "연속 전송")).messageId()));
            }
            for (var result : results) assertNotNull(result.get(10, TimeUnit.SECONDS));
            assertEquals(24, messages.countByChatRoomId(room));
        } finally { pool.shutdownNow(); }
    }

    @Test void roomListKeepsMostRecentlyActiveRoomFirst() {
        Long anotherRoom = rooms.getOrCreateDirectRoom(alice.getLoginId(), other.getId()).roomId();
        service.send(alice.getLoginId(), new ChatSendRequest(anotherRoom, UUID.randomUUID().toString(), "먼저"));
        service.send(alice.getLoginId(), new ChatSendRequest(room, UUID.randomUUID().toString(), "최근"));
        assertEquals(room, rooms.listRooms(alice.getLoginId()).get(0).roomId());
    }

    private List<Long> concurrently(Callable<Long> first, Callable<Long> second) throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        var barrier = new CyclicBarrier(2);
        try {
            var a = pool.submit(() -> { barrier.await(5, TimeUnit.SECONDS); return first.call(); });
            var b = pool.submit(() -> { barrier.await(5, TimeUnit.SECONDS); return second.call(); });
            return List.of(a.get(10, TimeUnit.SECONDS), b.get(10, TimeUnit.SECONDS));
        } finally { pool.shutdownNow(); }
    }
}
