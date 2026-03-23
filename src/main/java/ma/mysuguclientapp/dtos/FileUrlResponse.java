package ma.mysuguclientapp.dtos;

import lombok.Data;

@Data
public class FileUrlResponse {
    private String url;
    private String downloadUrl;
    private String presignedUrl;
    private String objectName;
    private String bucket;
    private String fileName;
    private int expiresIn;
}
