package ma.mysuguclientapp.dtos.auth;

import lombok.Data;

@Data
public class VerifyOtpDTO {
    private String email;
    private String otp;
}