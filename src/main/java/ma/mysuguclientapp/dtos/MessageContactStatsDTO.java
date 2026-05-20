package ma.mysuguclientapp.dtos;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageContactStatsDTO {
    private long total;
    private long nouveaux;
    private long lus;
    private long repondus;
    private long archives;
}
