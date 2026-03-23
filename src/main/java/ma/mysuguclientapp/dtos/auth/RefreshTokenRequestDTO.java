package ma.mysuguclientapp.dtos.auth;

import lombok.Data;

@Data
public class RefreshTokenRequestDTO {
    private String refreshToken;
}
