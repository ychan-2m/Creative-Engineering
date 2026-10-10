package com.example.webtalk.ai;

import com.example.webtalk.message.MessageService;
import com.example.webtalk.message.MessageRepository;
import org.springframework.data.domain.PageRequest;
import com.example.webtalk.common.InvalidMessageException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.concurrent.Semaphore;

@Service
public class ReplySuggestionService {
    private final JdbcTemplate db;
    private final MessageService messages;
    private final ReplyModel model;
    private final MessageRepository repository;
    private final ObjectMapper json = new ObjectMapper();
    private final Semaphore slots = new Semaphore(2);

    public ReplySuggestionService(JdbcTemplate db, MessageService messages, ReplyModel model, MessageRepository repository) {
        this.db = db;
        this.messages = messages;
        this.model = model;
        this.repository = repository;
    }

    public StyleProfile profile(long userId) {
        var rows = db.query("SELECT profile_json FROM reply_style_profiles WHERE user_id = ?",
                (rs, row) -> json.readValue(rs.getString(1), StyleProfile.class), userId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    public StyleProfile profile(long userId, String relationship) {
        if (relationship == null) return profile(userId);
        String key = StyleProfile.normalizeRelationship(relationship);
        var rows = db.query("SELECT profile_json FROM reply_style_relationship_profiles WHERE user_id = ? AND relationship = ?",
                (rs, row) -> json.readValue(rs.getString(1), StyleProfile.class), userId, key);
        if (!rows.isEmpty()) return rows.get(0);
        var old = profile(userId);
        return old != null && key.equals(StyleProfile.normalizeRelationship(old.relationship())) ? old : null;
    }

    @org.springframework.transaction.annotation.Transactional
    public void save(long userId, StyleProfile profile) {
        // 처음 관계별로 저장할 때 이전 단일 질문지도 해당 관계에 보존한다.
        var old = profile(userId);
        if (old != null) {
            String oldKey = StyleProfile.normalizeRelationship(old.relationship());
            Integer count = db.queryForObject("SELECT COUNT(*) FROM reply_style_relationship_profiles WHERE user_id = ? AND relationship = ?",
                    Integer.class, userId, oldKey);
            if (count == 0) db.update("INSERT INTO reply_style_relationship_profiles (user_id, relationship, profile_json) VALUES (?, ?, ?)",
                    userId, oldKey, json.writeValueAsString(old));
        }
        db.update("MERGE INTO reply_style_relationship_profiles (user_id, relationship, profile_json) KEY (user_id, relationship) VALUES (?, ?, ?)",
                userId, StyleProfile.normalizeRelationship(profile.relationship()), json.writeValueAsString(profile));
        db.update("MERGE INTO reply_style_profiles (user_id, profile_json) KEY (user_id) VALUES (?, ?)",
                userId, json.writeValueAsString(profile));
    }

    @org.springframework.transaction.annotation.Transactional
    public void delete(long userId, String relationship) {
        if (relationship == null) {
            db.update("DELETE FROM reply_style_relationship_profiles WHERE user_id = ?", userId);
            delete(userId);
            return;
        }
        String key = StyleProfile.normalizeRelationship(relationship);
        db.update("DELETE FROM reply_style_relationship_profiles WHERE user_id = ? AND relationship = ?", userId, key);
        var old = profile(userId);
        if (old != null && key.equals(StyleProfile.normalizeRelationship(old.relationship()))) delete(userId);
    }

    public void delete(long userId) {
        db.update("DELETE FROM reply_style_profiles WHERE user_id = ?", userId);
    }

    public List<String> suggest(long userId, String loginId, long roomId, String draft, Long replyToMessageId) {
        return suggest(userId, loginId, roomId, draft, replyToMessageId, null);
    }

    public List<String> suggest(long userId, String loginId, long roomId, String draft, Long replyToMessageId, String relationship) {
        // 다른 방의 메시지가 모델에 전달되기 전에 기존 구성원 검사를 수행한다.
        var history = messages.getHistory(loginId, roomId);
        Map<String, Object> target = Map.of();
        List<Map<String, String>> recent;
        if (replyToMessageId != null) {
            var selected = repository.findById(replyToMessageId)
                    .filter(m -> m.getChatRoomId() == roomId && m.getSenderId() != userId)
                    .orElseThrow(() -> new InvalidMessageException("이 방에서 상대방이 보낸 메시지를 선택해 주세요."));
            target = Map.of("messageId", selected.getId(), "text", selected.getContent(), "speaker", "peer");
            var before = new ArrayList<>(repository.findByChatRoomIdAndIdLessThanEqualOrderByIdDesc(
                    roomId, selected.getId(), PageRequest.of(0, 10)));
            Collections.reverse(before);
            recent = before.stream().map(m -> Map.of("speaker", m.getSenderId() == userId ? "me" : "peer",
                    "text", m.getContent())).toList();
        } else {
            recent = history.subList(Math.max(0, history.size() - 10), history.size()).stream()
                    .map(m -> Map.of("speaker", m.senderId() == userId ? "me" : "peer", "text", m.content())).toList();
        }
        var style = profile(userId, relationship);
        if (style == null) throw new InvalidMessageException("먼저 내 말투 설정에서 질문지 답변을 저장해 주세요.");
        if (!slots.tryAcquire()) throw new InvalidMessageException("다른 답장을 생성 중입니다. 잠시 후 다시 시도해 주세요.");
        try {
            var examples = new ArrayList<Map<String, String>>();
            for (int i = 0; i < style.answers().size(); i++) {
                examples.add(Map.of("question", StyleProfile.questions(style.relationship()).get(i), "answer", style.answers().get(i)));
            }
            var context = new LinkedHashMap<String, Object>();
            context.put("relationship", style.relationship());
            context.put("styleExamples", examples);
            context.put("conversation", recent);
            context.put("draft", draft);
            context.put("replyTo", target);
            context.put("preferences", style.preferences());
            context.put("avoidedExpressions", style.avoidedExpressions());
            var rewrites = new ArrayList<Map<String, String>>();
            for (int i = 0; i < style.rewrites().size(); i++) {
                rewrites.add(Map.of("original", StyleProfile.rewritePrompts(style.relationship()).get(i), "rewritten", style.rewrites().get(i)));
            }
            context.put("rewriteExamples", rewrites);
            return model.suggest(json.writeValueAsString(context));
        } finally {
            slots.release();
        }
    }
}
