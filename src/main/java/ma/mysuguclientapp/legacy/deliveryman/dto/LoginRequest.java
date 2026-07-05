package ma.mysuguclientapp.legacy.deliveryman.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** POST /auth/login — {country_code, phone, password}. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LoginRequest(
        @JsonProperty("country_code") String countryCode,
        @JsonProperty("phone") String phone,
        @JsonProperty("password") String password) {
}
