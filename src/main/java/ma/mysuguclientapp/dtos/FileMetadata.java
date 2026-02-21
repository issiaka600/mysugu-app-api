package ma.mysuguclientapp.dtos;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class FileMetadata {
    private String objectName;
    private String bucket;
    private long size;
    private String contentType;
    private LocalDateTime lastModified;
}