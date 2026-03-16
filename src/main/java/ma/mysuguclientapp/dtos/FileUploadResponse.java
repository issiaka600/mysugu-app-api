package ma.mysuguclientapp.dtos;

import lombok.Data;

@Data
public class FileUploadResponse {
    private String objectName;
    private String bucket;
    private String fileName;
    private long size;
    private String contentType;
    private String url;
    private String downloadUrl;
    private String presignedUrl;
    private int expiresIn;
}
