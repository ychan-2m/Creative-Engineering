package com.example.webtalk.ai;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;

public record StyleProfile(
        @NotBlank @Size(max = 40) String relationship,
        @NotNull @Size(min = 5, max = 6) List<@NotBlank @Size(max = 300) String> answers,
        @Valid Preferences preferences,
        @Size(max = 3) List<@NotBlank @Size(max = 300) String> rewrites,
        @Size(max = 300) String avoidedExpressions) {
    public StyleProfile {
        rewrites = rewrites == null ? List.of() : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(rewrites));
        avoidedExpressions = avoidedExpressions == null ? "" : avoidedExpressions;
    }
    // 기존 5문항 프로필은 그대로 읽고 사용할 수 있다.
    public StyleProfile(String relationship, List<String> answers) {
        this(relationship, answers, null, List.of(), "");
    }
    @AssertTrue(message = "상황 질문 6개, 선택 항목 6개, 고쳐 쓰기 3개를 모두 작성해 주세요.")
    @JsonIgnore
    public boolean isCompleteSurvey() {
        if (answers == null) return false;
        return (answers.size() == 5 && preferences == null && rewrites.isEmpty())
                || (answers.size() == 6 && preferences != null && rewrites.size() == 3);
    }
    public record Preferences(
            @NotNull @Pattern(regexp = "examples|casual|polite") String formality,
            @NotNull @Pattern(regexp = "short|balanced|detailed") String length,
            @NotNull @Pattern(regexp = "standard|relaxed|compact") String spacing,
            @NotNull @Pattern(regexp = "examples|ㅇㅇ|응|웅|네") String acknowledgment,
            @NotNull @Pattern(regexp = "none|ㅋㅋ|ㅎㅎ|examples") String laughter,
            @NotNull @Pattern(regexp = "none|sometimes|often") String emoji) {}

    public static final List<String> QUESTIONS = List.of(
            "뭐해?", "오늘 저녁 같이 먹을래?", "미안, 오늘 못 만날 것 같아",
            "나 시험 붙었어!", "그거 좀 도와줄 수 있어?",
            "친구가 주말에 만나자고 했지만 갈 수 없어요. 어떻게 거절할까요?");
    public static final List<String> RELATIONSHIPS = List.of("친한 친구", "어른", "선배·동료", "후배", "처음 만난 사람");

    public static String normalizeRelationship(String relationship) {
        return "친구".equals(relationship) ? "친한 친구" : "선배".equals(relationship) ? "선배·동료" : relationship;
    }

    public static List<String> questions(String relationship) {
        return switch (normalizeRelationship(relationship)) {
            case "어른" -> List.of("요즘 어떻게 지내니?", "오늘 저녁 같이 먹을 수 있니?", "미안하지만 오늘은 만나기 어렵겠구나.",
                    "좋은 일이 생겼단다!", "이것 좀 도와줄 수 있겠니?", "어른이 주말에 만나자고 하셨지만 갈 수 없습니다. 어떻게 말씀드릴까요?");
            case "선배·동료" -> List.of("지금 뭐 하고 있어요?", "오늘 저녁 같이 먹을까요?", "미안해요, 오늘은 못 만날 것 같아요.",
                    "이번에 합격했어요!", "이 작업 좀 도와줄 수 있어요?", "선배가 주말 모임에 오라고 했지만 갈 수 없습니다. 어떻게 거절할까요?");
            case "후배" -> List.of("선배, 지금 뭐 하세요?", "선배, 오늘 저녁 같이 드실래요?", "죄송해요, 오늘 못 갈 것 같아요.",
                    "선배, 저 합격했어요!", "선배, 이것 좀 도와주실 수 있나요?", "후배가 주말에 만나자고 했지만 갈 수 없습니다. 어떻게 거절할까요?");
            case "처음 만난 사람" -> List.of("안녕하세요. 요즘 어떻게 지내세요?", "오늘 저녁 함께 식사하실래요?", "죄송하지만 오늘은 만나기 어려울 것 같아요.",
                    "이번에 시험에 합격했어요!", "혹시 이것 좀 도와주실 수 있나요?", "처음 만난 분이 주말에 만나자고 했지만 갈 수 없습니다. 어떻게 거절할까요?");
            default -> QUESTIONS;
        };
    }

    public static List<String> rewritePrompts(String relationship) {
        if ("친한 친구".equals(normalizeRelationship(relationship))) return REWRITE_PROMPTS;
        return List.of("오늘 저녁에 무엇을 드시고 싶으세요?", "죄송하지만 오늘은 약속에 참석하기 어려울 것 같아요.", "정말 축하드립니다! 열심히 준비하신 보람이 있네요.");
    }

    public static final List<String> REWRITE_PROMPTS = List.of(
            "오늘 저녁에 뭐 먹을래?", "미안해. 오늘은 약속에 못 갈 것 같아.", "정말 축하해! 열심히 준비한 보람이 있네.");
}
