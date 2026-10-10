package com.example.webtalk.ai;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class OllamaReplyModelTests {
    @Test void validatesProviderOutputAndUsesNonStreamingJson() throws Exception {
        var json = new ObjectMapper();
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        var captured = new AtomicReference<String>();
        var generation = new AtomicReference<String>();
        var rewritten = new AtomicReference<>("좋아요.");
        var content = new AtomicReference<>("{\"suggestions\":[\"안녕\",\"안녕\",\"\",42,\"반가워\"]}");
        server.createContext("/api/chat", exchange -> {
            captured.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            boolean isRewrite = !json.readTree(captured.get()).has("format");
            if (!isRewrite) generation.set(captured.get());
            byte[] response = json.writeValueAsBytes(Map.of("message", Map.of("content", isRewrite ? rewritten.get() : content.get())));
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            var model = new OllamaReplyModel("http://127.0.0.1:" + server.getAddress().getPort(), "test-model");
            String context = json.writeValueAsString(Map.of("styleExamples", List.of(
                    Map.of("question", "뭐해?", "answer", "걍 있음"),
                    Map.of("question", "나 시험 붙었어", "answer", "오 ㅊㅋㅊㅋ")),
                    "conversation", List.of(Map.of("speaker", "peer", "text", "면접 붙었어")),
                    "draft", "", "relationship", "친한 친구"));
            assertEquals(List.of("안녕", "반가워"), model.suggest(context));
            var request = json.readTree(captured.get());
            assertFalse(request.path("stream").asBoolean());
            assertEquals("object", request.path("format").path("type").asText());
            assertEquals(3, request.path("format").path("properties").path("suggestions").path("maxItems").asInt());
            var messages = request.path("messages");
            assertEquals("assistant", messages.get(2).path("role").asText());
            assertEquals("걍 있음", messages.get(2).path("content").asText());
            assertEquals("오 ㅊㅋㅊㅋ", messages.get(4).path("content").asText());
            var last = json.readTree(messages.get(5).path("content").asText());
            assertEquals("", last.path("draft").asText());
            assertEquals("면접 붙었어", last.path("conversation").get(0).path("text").asText());
            assertFalse(last.has("styleExamples"));
            String endingContext = json.writeValueAsString(Map.of("styleExamples", List.of(
                    Map.of("question", "저녁 먹을래?", "answer", "저녁에 뭐 먹을 거?"),
                    Map.of("question", "언제 만날래?", "answer", "담에 언제 볼 거?")),
                    "conversation", List.of(), "draft", "저녁 뭐 먹을래?", "relationship", "친구"));
            content.set("{\"suggestions\":[\"저녁 뭐 먹을래?\",\"저녁 뭐 먹을 거?\"]}");
            rewritten.set("저녁 뭐 먹을 거?");
            assertEquals(List.of("저녁 뭐 먹을 거?"), model.suggest(endingContext));
            var endingRequest = json.readTree(captured.get());
            assertFalse(endingRequest.has("format"));
            String rewritePrompt = endingRequest.path("messages").get(1).path("content").asText();
            assertTrue(rewritePrompt.contains("저녁에 뭐 먹을 거?"));
            assertTrue(rewritePrompt.contains("고칠 문장: 저녁 뭐 먹을래?"));
            rewritten.set("저녁 뭐 먹을래?");
            assertThrows(AiUnavailableException.class, () -> model.suggest(endingContext));
            rewritten.set("좋아요.");
            String politeContext = json.writeValueAsString(Map.of("styleExamples", List.of(
                    Map.of("question", "저녁 먹을래?", "answer", "뭘 드시고 싶으세요?"),
                    Map.of("question", "언제 만날래?", "answer", "언제 만나면 좋을까요?")),
                    "conversation", List.of(), "draft", "", "relationship", "친한 친구"));
            content.set("{\"suggestions\":[\"좋아\",\"좋아요.\"]}");
            assertEquals(List.of("좋아요."), model.suggest(politeContext));
            var selectedContext = (tools.jackson.databind.node.ObjectNode) json.readTree(context);
            selectedContext.put("draft", "저녁");
            selectedContext.set("replyTo", json.valueToTree(Map.of("messageId", 42, "text", "밥 먹을래?")));
            selectedContext.set("preferences", json.valueToTree(new StyleProfile.Preferences(
                    "casual", "short", "compact", "ㅇㅇ", "none", "none")));
            selectedContext.put("avoidedExpressions", "웅");
            selectedContext.set("rewriteExamples", json.valueToTree(List.of(Map.of("original", "안녕", "rewritten", "ㅎㅇ"))));
            content.set("{\"suggestions\":[\"웅 좋아\",\"ㅋㅋ 좋아\",\"뭐 먹을 거?\"]}");
            rewritten.set("뭐 먹을 거?");
            assertEquals(List.of("뭐 먹을 거?"), model.suggest(json.writeValueAsString(selectedContext)));
            var sentMessages = json.readTree(generation.get()).path("messages");
            var selectedData = json.readTree(sentMessages.get(sentMessages.size() - 1).path("content").asText());
            assertEquals(42, selectedData.path("replyTo").path("messageId").asInt());
            assertEquals("저녁", selectedData.path("draft").asText());
            assertFalse(selectedData.has("styleExamples"));
            assertFalse(selectedData.has("rewriteExamples"));
            String finalRewrite = json.readTree(captured.get()).path("messages").get(1).path("content").asText();
            assertTrue(finalRewrite.contains("ㅎㅇ"));
            assertTrue(finalRewrite.contains("none"));
            content.set("not json");
            assertThrows(AiUnavailableException.class, () -> model.suggest(context));
            content.set("{\"suggestions\":[]}");
            assertThrows(AiUnavailableException.class, () -> model.suggest(context));
        } finally { server.stop(0); }
    }
}
