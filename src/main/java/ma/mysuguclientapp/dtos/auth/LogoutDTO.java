package ma.mysuguclientapp.dtos.auth;

import lombok.Data;

@Data
public class LogoutDTO {
    private String refreshToken;
}
