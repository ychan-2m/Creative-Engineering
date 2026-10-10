package com.example.webtalk.ai;

import java.util.List;

public interface ReplyModel {
    List<String> suggest(String context);
}
