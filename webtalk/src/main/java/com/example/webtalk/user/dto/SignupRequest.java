package com.example.webtalk.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * FR01 회원가입 입력. 비밀번호 8자 이상 조건은 3.3 "입력과 기대 출력"을 따른다.
 */
public record SignupRequest(

        @NotBlank(message = "아이디를 입력해 주세요.")
        @Size(min = 3, max = 40, message = "아이디는 3자 이상 40자 이하로 입력해 주세요.")
        @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "아이디는 영문, 숫자, -, _ 만 사용할 수 있습니다.")
        String loginId,

        @NotBlank(message = "닉네임을 입력해 주세요.")
        @Size(min = 1, max = 40, message = "닉네임은 40자 이하로 입력해 주세요.")
        String nickname,

        @NotBlank(message = "비밀번호를 입력해 주세요.")
        @Size(min = 8, max = 100, message = "비밀번호는 8자 이상으로 입력해 주세요.")
        String password
) {
}
