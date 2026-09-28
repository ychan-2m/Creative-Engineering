package com.example.webtalk.auth;

import com.example.webtalk.user.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;

/**
 * Spring Security의 인증 주체이자 STOMP {@link java.security.Principal}로도 쓰인다.
 * getName()(=getUsername())이 loginId를 반환하므로 WebSocket 쪽에서도 같은 식별자로
 * 사용자를 조회할 수 있다 (5.7 서버 권한 검사 순서 1단계).
 */
public class WebtalkUserPrincipal implements UserDetails {

    private final Long userId;
    private final String loginId;
    private final String passwordHash;
    private final String nickname;

    public WebtalkUserPrincipal(User user) {
        this.userId = user.getId();
        this.loginId = user.getLoginId();
        this.passwordHash = user.getPasswordHash();
        this.nickname = user.getNickname();
    }

    public Long getUserId() {
        return userId;
    }

    public String getNickname() {
        return nickname;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return AuthorityUtils.createAuthorityList("ROLE_USER");
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return loginId;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
