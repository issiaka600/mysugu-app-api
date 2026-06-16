package ma.mysuguclientapp.dtos.integration;

import lombok.Data;

@Data
public class TikTakMessageNotificationDTO {
    private Long tiktakCustomerId;
    private String customerEmail;
    private String title;
    private String message;
    private String senderType;
    private Long senderId;
}
