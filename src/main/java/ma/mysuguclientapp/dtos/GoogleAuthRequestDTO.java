package ma.mysuguclientapp.dtos;

import lombok.Data;

@Data
public class GoogleAuthRequestDTO {
    private String idToken;
    private String role;
    private String telephone;
}
