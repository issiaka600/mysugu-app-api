package ma.mysuguclientapp.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class GoogleTokenInfoDTO {
    private String aud;
    private String azp;
    private String email;

    @JsonProperty("email_verified")
    private String emailVerified;

    private String exp;
    private String sub;
    private String name;
    private String picture;
    private String givenName;
    private String familyName;

    @JsonProperty("given_name")
    public void setGivenName(String givenName) {
        this.givenName = givenName;
    }

    @JsonProperty("family_name")
    public void setFamilyName(String familyName) {
        this.familyName = familyName;
    }
}
