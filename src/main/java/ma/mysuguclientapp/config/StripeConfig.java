package ma.mysuguclientapp.config;

import com.stripe.Stripe;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
@Slf4j
public class StripeConfig {

    @Value("${stripe.enabled:false}")
    private boolean enabled;

    @Value("${stripe.secret-key:}")
    private String secretKey;

    @Getter
    @Value("${stripe.webhook-secret:}")
    private String webhookSecret;

    @Getter
    @Value("${stripe.currency:mad}")
    private String currency;

    @PostConstruct
    public void init() {
        if (enabled && secretKey != null && !secretKey.isBlank()) {
            Stripe.apiKey = secretKey;
            log.info("Stripe configuré et activé (devise: {})", currency);
        } else {
            log.info("Stripe désactivé ou clé API non configurée — paiements par carte ignorés");
        }
    }

    public boolean isEnabled() {
        return enabled && secretKey != null && !secretKey.isBlank();
    }
}
