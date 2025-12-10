package com.ktb.chatapp.dto;

import com.ktb.chatapp.validation.ValidPassword;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UpdatePasswordRequest {

    @NotBlank(message = "비밀번호를 입력해주세요.")
    @ValidPassword
    private String newPassword;

    @ValidPassword
    private String confirmPassword;
}
