package com.example.webtalk.auth;

import com.example.webtalk.auth.dto.LoginRequest;
import com.example.webtalk.user.User;
import com.example.webtalk.user.UserService;
import com.example.webtalk.user.dto.SignupRequest;
import com.example.webtalk.user.dto.UserSummaryResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.*;

/**
 * FR01 회원가입, FR02 로그인, FR03 로그아웃, GET /api/me(5.5 REST API 명세)를 담당한다.
 */
@RestController
@RequestMapping("/api")
public class AuthController {

    private final UserService userService;
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;

    public AuthController(UserService userService,
                           AuthenticationManager authenticationManager,
                           SecurityContextRepository securityContextRepository) {
        this.userService = userService;
        this.authenticationManager = authenticationManager;
        this.securityContextRepository = securityContextRepository;
    }

    @PostMapping("/auth/signup")
    public ResponseEntity<UserSummaryResponse> signup(@Valid @RequestBody SignupRequest request) {
        User created = userService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(UserSummaryResponse.from(created));
    }

    @PostMapping("/auth/login")
    public ResponseEntity<UserSummaryResponse> login(@Valid @RequestBody LoginRequest request,
                                                       HttpServletRequest httpRequest,
                                                       HttpServletResponse httpResponse) {
        // BadCredentialsException은 GlobalExceptionHandler가 401로 변환한다 (E01).
        Authentication authRequest =
                new UsernamePasswordAuthenticationToken(request.loginId(), request.password());
        Authentication authResult = authenticationManager.authenticate(authRequest);

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authResult);
        SecurityContextHolder.setContext(context);
        // 세션에 SecurityContext를 저장해 REST 요청과 WebSocket 핸드셰이크가 같은 로그인 상태를 공유한다.
        securityContextRepository.saveContext(context, httpRequest, httpResponse);

        WebtalkUserPrincipal principal = (WebtalkUserPrincipal) authResult.getPrincipal();
        User user = userService.getById(principal.getUserId());
        return ResponseEntity.ok(UserSummaryResponse.from(user));
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        SecurityContextHolder.clearContext();
        var session = request.getSession(false);
        if (session != null) {
            session.invalidate(); // 공용 PC에서 세션 흔적을 남기지 않기 위해 명시적으로 무효화한다 (1.5 보안, 5.8).
        }
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<UserSummaryResponse> me(@AuthenticationPrincipal WebtalkUserPrincipal principal) {
        User user = userService.getById(principal.getUserId());
        return ResponseEntity.ok(UserSummaryResponse.from(user));
    }
}
