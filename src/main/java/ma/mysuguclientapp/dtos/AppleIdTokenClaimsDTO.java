package ma.mysuguclientapp.dtos;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AppleIdTokenClaimsDTO {
    String sub;
    String email;
    boolean emailVerified;
}
