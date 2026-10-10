package com.example.webtalk.ai;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.ObjectMapper;
import java.net.*;
import java.net.http.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ReplySuggestionTests {
    @Value("${local.server.port}") int port;
    @MockitoBean ReplyModel model;
    @Autowired JdbcTemplate db;
    @Autowired com.example.webtalk.message.MessageRepository messages;
    ObjectMapper json = new ObjectMapper();

    HttpClient client() {
        return HttpClient.newBuilder().cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL)).build();
    }
    HttpResponse<String> request(HttpClient client, String method, String path, Object body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json");
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
        return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
    long account(HttpClient client, String login) throws Exception {
        var result = request(client, "POST", "/api/auth/signup",
                Map.of("loginId", login, "nickname", login, "password", "test-password"));
        assertEquals(201, result.statusCode());
        assertEquals(200, request(client, "POST", "/api/auth/login",
                Map.of("loginId", login, "password", "test-password")).statusCode());
        return json.readTree(result.body()).path("userId").asLong();
    }

    @Test void profileIsolationPermissionsAndDraftOnlyGeneration() throws Exception {
        var a = client(); var b = client(); var outsider = client();
        assertEquals(401, request(a, "GET", "/api/me/reply-style", null).statusCode());
        account(a, "ai_alice");
        long bId = account(b, "ai_bob");
        account(outsider, "ai_outsider");
        String base = "/api/me/reply-style";
        var style = new StyleProfile("친한 친구", List.of("그냥 ㅋㅋ", "좋지", "담에 보자", "오 축하해!", "뭔데?"));
        assertEquals(204, request(a, "POST", base, style).statusCode());
        assertTrue(json.readTree(request(b, "GET", base, null).body()).path("profile").isNull());
        assertEquals("그냥 ㅋㅋ", json.readTree(request(a, "GET", base, null).body())
                .path("profile").path("answers").get(0).asText());
        assertEquals(400, request(a, "POST", base, Map.of("relationship", "친구", "answers", List.of("하나"))).statusCode());
        long room = json.readTree(request(a, "POST", "/api/chat-rooms/direct", Map.of("targetUserId", bId)).body()).path("roomId").asLong();
        String path = "/api/chat-rooms/" + room + "/reply-suggestions";
        assertEquals(403, request(outsider, "POST", path, Map.of("draft", "")).statusCode());
        assertEquals(400, request(b, "POST", path, Map.of("draft", "")).statusCode());
        verifyNoInteractions(model);
        long count = db.queryForObject("SELECT COUNT(*) FROM messages", Long.class);
        when(model.suggest(anyString())).thenReturn(List.of("응 ㅋㅋ", "넌?"));
        var result = request(a, "POST", path, Map.of("draft", "응"));
        assertEquals(200, result.statusCode());
        assertEquals("응 ㅋㅋ", json.readTree(result.body()).path("suggestions").get(0).asText());
        assertEquals(count, db.queryForObject("SELECT COUNT(*) FROM messages", Long.class));
        verify(model).suggest(argThat(context -> context.contains("그냥 ㅋㅋ") && context.contains("draft")));
        // 저장한 말투를 바꾸면 다음 생성 요청에 즉시 적용되고 이전 답변은 섞이지 않는다.
        var revised = new StyleProfile("친한 친구", List.of("쉬고 있어요.", "좋아요.", "다음에 만나요.", "축하드려요!", "무엇을 도와드릴까요?"));
        assertEquals(204, request(a, "POST", base, revised).statusCode());
        assertEquals(200, request(a, "POST", path, Map.of("draft", "")).statusCode());
        verify(model).suggest(argThat(context -> context.contains("축하드려요!") && !context.contains("그냥 ㅋㅋ")));
        when(model.suggest(anyString())).thenThrow(new AiUnavailableException());
        assertEquals(503, request(a, "POST", path, Map.of("draft", "")).statusCode());
        assertEquals(204, request(a, "DELETE", base, null).statusCode());
        assertTrue(json.readTree(request(a, "GET", base, null).body()).path("profile").isNull());
    }
    @Test void detailedSurveyAndSelectedMessageAreScopedToCurrentUserAndRoom() throws Exception {
        var a = client(); var b = client(); var other = client();
        long aId = account(a, "target_alice");
        long bId = account(b, "target_bob");
        long otherId = account(other, "target_other");
        String base = "/api/me/reply-style";
        // 이전 JSON에는 새 필드가 없어도 답변 5개를 유지해야 한다.
        db.update("INSERT INTO reply_style_profiles (user_id, profile_json) VALUES (?, ?)", aId,
                json.writeValueAsString(Map.of("relationship", "친구", "answers", List.of("걍", "ㅇㅇ", "ㄱㅊ", "ㅊㅋ", "뭔데"))));
        var old = json.readTree(request(a, "GET", base, null).body());
        assertEquals(5, old.path("profile").path("answers").size());
        assertEquals(6, old.path("questions").size());
        assertEquals(3, old.path("rewritePrompts").size());
        var profile = new StyleProfile("친한 친구", List.of("쉬는 중", "뭐 먹을 거?", "담에 보자", "ㅊㅋ", "뭔데", "담에 보자"),
                new StyleProfile.Preferences("casual", "short", "relaxed", "ㅇㅇ", "none", "none"),
                List.of("저녁에 뭐 먹을 거?", "미안 오늘 못 갈 듯", "ㅊㅋ 고생했어"), "웅, ~용");
        assertEquals(204, request(a, "POST", base, profile).statusCode());
        var saved = json.readTree(request(a, "GET", base, null).body()).path("profile");
        assertEquals("short", saved.path("preferences").path("length").asText());
        assertEquals(3, saved.path("rewrites").size());
        // A structurally valid but incomplete survey must be rejected.
        assertEquals(400, request(a, "POST", base, new StyleProfile("친구", profile.answers(), profile.preferences(), List.of(), "")).statusCode());
        assertEquals(400, request(a, "POST", base, new StyleProfile("친구", profile.answers(),
                new StyleProfile.Preferences("unknown", "short", "relaxed", "응", "none", "none"), profile.rewrites(), "")).statusCode());
        long room = json.readTree(request(a, "POST", "/api/chat-rooms/direct", Map.of("targetUserId", bId)).body()).path("roomId").asLong();
        long otherRoom = json.readTree(request(a, "POST", "/api/chat-rooms/direct", Map.of("targetUserId", otherId)).body()).path("roomId").asLong();
        var selected = messages.save(new com.example.webtalk.message.Message(room, bId, UUID.randomUUID().toString(), "저녁 뭐 먹을래?"));
        for (int i = 0; i < 55; i++) messages.save(new com.example.webtalk.message.Message(room, bId, UUID.randomUUID().toString(), "뒤에 온 메시지 " + i));
        var mine = messages.save(new com.example.webtalk.message.Message(room, aId, UUID.randomUUID().toString(), "내 메시지"));
        var elsewhere = messages.save(new com.example.webtalk.message.Message(otherRoom, otherId, UUID.randomUUID().toString(), "다른 방 내용"));
        String path = "/api/chat-rooms/" + room + "/reply-suggestions";
        for (long id : List.of(mine.getId(), elsewhere.getId(), Long.MAX_VALUE)) {
            assertEquals(400, request(a, "POST", path, Map.of("draft", "", "replyToMessageId", id)).statusCode());
        }
        assertEquals(403, request(other, "POST", path, Map.of("draft", "", "replyToMessageId", selected.getId())).statusCode());
        verifyNoInteractions(model);
        when(model.suggest(anyString())).thenReturn(List.of("뭐 먹을 거?"));
        long count = messages.count();
        assertEquals(200, request(a, "POST", path, Map.of("draft", "", "replyToMessageId", selected.getId())).statusCode());
        var capture = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(model).suggest(capture.capture());
        var context = json.readTree(capture.getValue());
        assertEquals(selected.getId().longValue(), context.path("replyTo").path("messageId").asLong());
        assertEquals("저녁 뭐 먹을래?", context.path("replyTo").path("text").asText());
        assertFalse(capture.getValue().contains("뒤에 온 메시지"));
        assertFalse(capture.getValue().contains("다른 방 내용"));
        assertEquals("none", context.path("preferences").path("laughter").asText());
        assertEquals("저녁에 뭐 먹을 거?", context.path("rewriteExamples").get(0).path("rewritten").asText());
        assertEquals(count, messages.count());
    }

    @Test void unreadCountsExcludeOwnMessagesAndReadCursorCannotMoveBackwards() throws Exception {
        var a = client(); var b = client(); var outsider = client();
        long aId = account(a, "read_alice"), bId = account(b, "read_bob");
        account(outsider, "read_other");
        long room = json.readTree(request(a, "POST", "/api/chat-rooms/direct", Map.of("targetUserId", bId)).body()).path("roomId").asLong();
        var first = messages.save(new com.example.webtalk.message.Message(room, bId, UUID.randomUUID().toString(), "첫 메시지"));
        messages.save(new com.example.webtalk.message.Message(room, aId, UUID.randomUUID().toString(), "내 답장"));
        var second = messages.save(new com.example.webtalk.message.Message(room, bId, UUID.randomUUID().toString(), "둘째 메시지"));
        assertEquals(2, json.readTree(request(a, "GET", "/api/chat-rooms", null).body()).get(0).path("unreadCount").asInt());
        String path = "/api/chat-rooms/" + room + "/read";
        assertEquals(403, request(outsider, "POST", path, Map.of("messageId", second.getId())).statusCode());
        assertEquals(400, request(a, "POST", path, Map.of("messageId", Long.MAX_VALUE)).statusCode());
        assertEquals(204, request(a, "POST", path, Map.of("messageId", second.getId())).statusCode());
        assertEquals(204, request(a, "POST", path, Map.of("messageId", first.getId())).statusCode());
        assertEquals(0, json.readTree(request(a, "GET", "/api/chat-rooms", null).body()).get(0).path("unreadCount").asInt());
        messages.save(new com.example.webtalk.message.Message(room, bId, UUID.randomUUID().toString(), "새 메시지"));
        assertEquals(1, json.readTree(request(a, "GET", "/api/chat-rooms", null).body()).get(0).path("unreadCount").asInt());
        long otherRoom = json.readTree(request(b, "POST", "/api/chat-rooms/direct", Map.of("targetUserId", account(client(), "read_elsewhere"))).body()).path("roomId").asLong();
        var elsewhere = messages.save(new com.example.webtalk.message.Message(otherRoom, bId, UUID.randomUUID().toString(), "다른 방"));
        assertEquals(400, request(a, "POST", path, Map.of("messageId", elsewhere.getId())).statusCode());
    }

    @Test void relationshipProfilesPreserveLegacyAndUseSelectedSurvey() throws Exception {
        var a = client(); var b = client();
        long aId = account(a, "relation_alice"), bId = account(b, "relation_bob");
        String base = "/api/me/reply-style";
        db.update("INSERT INTO reply_style_profiles (user_id, profile_json) VALUES (?, ?)", aId,
                json.writeValueAsString(new StyleProfile("친구", List.of("걍", "ㅇㅇ", "ㄱㅊ", "ㅊㅋ", "뭔데"))));
        var elder = new StyleProfile("어른", List.of("잘 지내고 있어요", "네 좋아요", "괜찮습니다", "축하드립니다", "네 도와드릴게요", "죄송합니다"),
                new StyleProfile.Preferences("polite", "short", "standard", "네", "none", "none"),
                List.of("무엇을 드실래요?", "죄송합니다 오늘은 어렵습니다", "축하드립니다"), "ㅋㅋ");
        assertEquals(204, request(a, "POST", base, elder).statusCode());
        String friendPath = base + "?relationship=" + java.net.URLEncoder.encode("친한 친구", java.nio.charset.StandardCharsets.UTF_8);
        String elderPath = base + "?relationship=" + java.net.URLEncoder.encode("어른", java.nio.charset.StandardCharsets.UTF_8);
        assertEquals("걍", json.readTree(request(a, "GET", friendPath, null).body()).path("profile").path("answers").get(0).asText());
        assertTrue(json.readTree(request(b, "GET", elderPath, null).body()).path("profile").isNull());
        assertEquals("요즘 어떻게 지내니?", json.readTree(request(a, "GET", elderPath, null).body()).path("questions").get(0).asText());
        long room = json.readTree(request(a, "POST", "/api/chat-rooms/direct", Map.of("targetUserId", bId)).body()).path("roomId").asLong();
        when(model.suggest(anyString())).thenReturn(List.of("네 좋아요"));
        assertEquals(200, request(a, "POST", "/api/chat-rooms/" + room + "/reply-suggestions", Map.of("draft", "", "relationship", "어른")).statusCode());
        verify(model).suggest(argThat(context -> context.contains("요즘 어떻게 지내니?") && context.contains("축하드립니다") && !context.contains("걍")));
        assertEquals(204, request(a, "DELETE", elderPath, null).statusCode());
        assertTrue(json.readTree(request(a, "GET", elderPath, null).body()).path("profile").isNull());
        assertEquals("걍", json.readTree(request(a, "GET", friendPath, null).body()).path("profile").path("answers").get(0).asText());
    }

    @Test void websocketAcceptsPublicSameOriginThroughProxyAndRejectsOtherOrigins() throws Exception {
        var browser = client();
        account(browser, "origin_alice");
        URI uri = URI.create("ws://localhost:" + port + "/ws");
        var socket = browser.newWebSocketBuilder()
                .header("Origin", "https://chat.example.test")
                .header("X-Forwarded-Host", "chat.example.test")
                .header("X-Forwarded-Proto", "https")
                .buildAsync(uri, new WebSocket.Listener() {}).get(5, java.util.concurrent.TimeUnit.SECONDS);
        socket.sendClose(WebSocket.NORMAL_CLOSURE, "done").get(5, java.util.concurrent.TimeUnit.SECONDS);
        var error = assertThrows(java.util.concurrent.ExecutionException.class, () -> browser.newWebSocketBuilder()
                .header("Origin", "https://another.example.test")
                .header("X-Forwarded-Host", "chat.example.test")
                .header("X-Forwarded-Proto", "https")
                .buildAsync(uri, new WebSocket.Listener() {}).get(5, java.util.concurrent.TimeUnit.SECONDS));
        assertInstanceOf(WebSocketHandshakeException.class, error.getCause());
        assertEquals(403, ((WebSocketHandshakeException) error.getCause()).getResponse().statusCode());
    }

}
