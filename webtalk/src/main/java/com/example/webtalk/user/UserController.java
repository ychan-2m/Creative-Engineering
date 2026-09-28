package com.example.webtalk.user;

import com.example.webtalk.auth.WebtalkUserPrincipal;
import com.example.webtalk.user.dto.UserSummaryResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * FR04 사용자 검색 (GET /api/users?query=, 5.5 REST API 명세).
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public List<UserSummaryResponse> search(@RequestParam(defaultValue = "") String query,
                                             @AuthenticationPrincipal WebtalkUserPrincipal principal) {
        return userService.search(query, principal.getUserId()).stream()
                .map(UserSummaryResponse::from)
                .toList();
    }
}
