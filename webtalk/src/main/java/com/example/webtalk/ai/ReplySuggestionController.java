package com.example.webtalk.ai;

import com.example.webtalk.auth.WebtalkUserPrincipal;
import com.example.webtalk.common.ErrorResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api")
public class ReplySuggestionController {
    private final ReplySuggestionService service;
    public ReplySuggestionController(ReplySuggestionService service) { this.service = service; }
    public record ProfileResponse(List<String> questions, List<String> rewritePrompts, StyleProfile profile, List<String> relationships) {}
    public record SuggestRequest(@NotNull @Size(max = 500) String draft, @Positive Long replyToMessageId, @Size(max = 40) String relationship) {}

    @GetMapping("/me/reply-style")
    public ProfileResponse get(@AuthenticationPrincipal WebtalkUserPrincipal user,
                               @RequestParam(required = false) String relationship) {
        var profile = service.profile(user.getUserId(), relationship);
        String selected = relationship != null ? relationship : profile != null ? profile.relationship() : "친한 친구";
        return new ProfileResponse(StyleProfile.questions(selected), StyleProfile.rewritePrompts(selected), profile, StyleProfile.RELATIONSHIPS);
    }
    @PostMapping("/me/reply-style")
    public ResponseEntity<Void> save(@AuthenticationPrincipal WebtalkUserPrincipal user,
                                     @Valid @RequestBody StyleProfile profile) {
        service.save(user.getUserId(), profile);
        return ResponseEntity.noContent().build();
    }
    @DeleteMapping("/me/reply-style")
    public ResponseEntity<Void> delete(@AuthenticationPrincipal WebtalkUserPrincipal user,
                                       @RequestParam(required = false) String relationship) {
        service.delete(user.getUserId(), relationship);
        return ResponseEntity.noContent().build();
    }
    @PostMapping("/chat-rooms/{roomId}/reply-suggestions")
    public Map<String, List<String>> suggest(@AuthenticationPrincipal WebtalkUserPrincipal user,
             @PathVariable long roomId, @Valid @RequestBody SuggestRequest request) {
        return Map.of("suggestions", service.suggest(user.getUserId(), user.getUsername(), roomId, request.draft(), request.replyToMessageId(), request.relationship()));
    }
    @ExceptionHandler(AiUnavailableException.class)
    public ResponseEntity<ErrorResponse> unavailable(AiUnavailableException ex) {
        return ResponseEntity.status(503).body(new ErrorResponse("AI_UNAVAILABLE", ex.getMessage()));
    }
}
