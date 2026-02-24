package ma.mysuguclientapp.dtos;

import lombok.Data;

@Data
public class FileUrlResponse {
    private String url;
    private String objectName;
    private int expiresIn; // en secondes
}