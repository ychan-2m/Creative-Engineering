package com.example.webtalk.user.dto;

import com.example.webtalk.user.User;

public record UserSummaryResponse(Long userId, String loginId, String nickname) {

    public static UserSummaryResponse from(User user) {
        return new UserSummaryResponse(user.getId(), user.getLoginId(), user.getNickname());
    }
}
