package ma.mysuguclientapp.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DeviceTokenRegisterDTO {

    @NotNull
    private Long userId;

    @NotBlank
    private String token;

    @NotBlank
    private String platform; // ANDROID, IOS, WEB
}
