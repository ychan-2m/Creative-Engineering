package com.example.webtalk.chat.dto;

import com.example.webtalk.user.User;

public record PeerSummary(Long userId, String nickname) {

    public static PeerSummary from(User user) {
        return new PeerSummary(user.getId(), user.getNickname());
    }
}
