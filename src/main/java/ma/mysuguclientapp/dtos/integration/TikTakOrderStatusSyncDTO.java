package ma.mysuguclientapp.dtos.integration;

import lombok.Data;

@Data
public class TikTakOrderStatusSyncDTO {
    private Long tiktakOrderId;
    private String status;
    private Long tiktakDeliveryManId;
    private String reason;
}
