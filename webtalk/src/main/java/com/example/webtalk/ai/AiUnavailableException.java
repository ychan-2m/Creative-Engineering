package com.example.webtalk.ai;

public class AiUnavailableException extends RuntimeException {
    public AiUnavailableException(String message) { super(message); }

    public AiUnavailableException() {
        super("답장 생성에 연결하지 못했습니다. 서버의 Ollama 실행 상태와 모델 설치를 확인하고 다시 시도해 주세요.");
    }
}
