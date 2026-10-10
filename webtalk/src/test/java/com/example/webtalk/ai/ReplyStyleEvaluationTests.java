package com.example.webtalk.ai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import tools.jackson.databind.ObjectMapper;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** 실제 모델의 말투 품질 점검. 일반 테스트에서는 네트워크/모델 호출을 하지 않는다. */
@EnabledIfEnvironmentVariable(named = "WEBTALK_RUN_LLM_EVAL", matches = "true")
class ReplyStyleEvaluationTests {
    private List<String> suggest(List<String> answers, String peer, String draft) {
        var examples = new ArrayList<Map<String, String>>();
        for (int i = 0; i < answers.size(); i++) {
            examples.add(Map.of("question", StyleProfile.QUESTIONS.get(i), "answer", answers.get(i)));
        }
        String context = new ObjectMapper().writeValueAsString(Map.of(
                "styleExamples", examples, "relationship", "친한 친구", "draft", draft,
                "conversation", List.of(Map.of("speaker", "peer", "text", peer))));
        var model = new OllamaReplyModel(System.getenv().getOrDefault("WEBTALK_AI_URL", "http://localhost:11434"),
                System.getenv().getOrDefault("WEBTALK_AI_MODEL", "gemma3:12b"));
        var results = model.suggest(context);
        System.out.println("STYLE EVAL " + answers.get(1) + " -> " + results);
        return results;
    }

    @Test void preservesPersonalQuestionEnding() {
        var result = suggest(List.of("걍 있음 넌 뭐 할 거?", "저녁에 뭐 먹을 거?", "담에 언제 볼 거?",
                "오 축하 뭐 할 거?", "뭐 도와주면 되는 거?"), "저녁 같이 먹자", "오늘 저녁에 뭐 먹을래?");
        assertTrue(result.stream().anyMatch(s -> s.contains("먹을 거?")), result.toString());
        assertTrue(result.stream().noneMatch(s -> s.contains("먹을래?")), result.toString());
    }

    @Test void prioritizesPoliteExamplesOverFriendRelationship() {
        var result = suggest(List.of("잠시 쉬고 있어요.", "어떤 음식 드시고 싶으세요?", "괜찮아요. 다음에 만나요.",
                "정말 축하드려요!", "어떤 도움이 필요하세요?"), "저녁 같이 먹자", "저녁에 뭐 먹을래?");
        assertTrue(result.stream().allMatch(s -> s.matches("(?s).*(요|니다)[.!?~ ]*$")), result.toString());
    }

    @Test void preservesAbbreviatedCelebration() {
        var result = suggest(List.of("걍 있음", "ㅇㅋ 뭐먹", "ㄱㅊ 담에봐", "오 ㅊㅋㅊㅋ", "ㅇㅇ 뭔데"),
                "나 오늘 면접 붙었어!", "");
        assertTrue(result.stream().anyMatch(s -> s.contains("ㅊㅋ")), result.toString());
        assertTrue(result.stream().allMatch(s -> s.length() <= 25), result.toString());
    }
}
