package com.example.webtalk.user;

import com.example.webtalk.common.DuplicateLoginIdException;
import com.example.webtalk.common.UserNotFoundException;
import com.example.webtalk.user.dto.SignupRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public User signup(SignupRequest request) {
        // AC07 / NFR02: 비밀번호 원문은 저장하지 않고 해시만 저장한다.
        if (userRepository.existsByLoginId(request.loginId())) {
            throw new DuplicateLoginIdException(request.loginId());
        }
        String passwordHash = passwordEncoder.encode(request.password());
        User user = new User(request.loginId(), passwordHash, request.nickname());
        return userRepository.save(user);
    }

    public User getByLoginId(String loginId) {
        return userRepository.findByLoginId(loginId)
                .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다: " + loginId));
    }

    public User getById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new UserNotFoundException("사용자를 찾을 수 없습니다: id=" + userId));
    }

    // FR04 사용자 검색: 본인 계정은 제외한다.
    public List<User> search(String query, Long currentUserId) {
        String trimmed = query == null ? "" : query.trim();
        if (trimmed.isEmpty()) {
            return List.of();
        }
        return userRepository.searchByQueryExcludingSelf(trimmed, currentUserId);
    }
}
