package com.example.webtalk.common;

/**
 * 보고서 부록 B "오류 응답 형식"을 따르는 공통 오류 본문.
 * 내부 구현 정보는 담지 않고 code와 사용자 안내 message만 전달한다.
 */
public record ErrorResponse(String code, String message) {
}
