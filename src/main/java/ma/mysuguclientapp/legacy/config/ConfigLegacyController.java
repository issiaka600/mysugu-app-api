package ma.mysuguclientapp.legacy.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * GET /api/v1/config — endpoint de configuration appelé par l'app Tiktak (moso) au splash.
 *
 * Reproduit la forme 6valley minimale nécessaire au démarrage (voir
 * docs/legacy-contracts/CONTRACT-REFERENCE.md §1.7). Deux clés sont CRITIQUES car
 * l'app fait `json['language'].cast<String>()` / `json['unit'].cast<String>()` sans garde :
 * `language` et `unit` DOIVENT être des tableaux de chaînes présents. Tout le reste est
 * null-safe côté app. Cet endpoint ne doit JAMAIS renvoyer 500.
 */
@RestController
@RequestMapping("/api/v1")
public class ConfigLegacyController {

    @Value("${app.public-base-url:http://localhost:8083}")
    private String publicBaseUrl;

    @GetMapping("/config")
    public Map<String, Object> config() {
        String files = publicBaseUrl + "/api/files";

        Map<String, Object> baseUrls = new LinkedHashMap<>();
        baseUrls.put("delivery_man_image_url", files);
        baseUrls.put("chatting_image_url", files);
        baseUrls.put("customer_image_url", files);
        baseUrls.put("shop_image_url", files);
        baseUrls.put("seller_image_url", files);
        baseUrls.put("notification_image_url", files);
        baseUrls.put("review_image_url", files);
        baseUrls.put("order_delivery_verification_image_url", files);

        Map<String, Object> cfg = new LinkedHashMap<>();
        // Clés fragiles : tableaux de chaînes, toujours présents.
        cfg.put("language", List.of("fr", "en"));
        cfg.put("unit", List.of());
        // Config générale (toutes null-safe côté app).
        cfg.put("system_default_currency", 1);
        cfg.put("currency_symbol_position", "right");
        cfg.put("currency_model", "single_currency");
        cfg.put("maintenance_mode", false);
        cfg.put("email_verification", false);
        cfg.put("phone_verification", false);
        cfg.put("forgot_password_verification", "email");
        cfg.put("decimal_point_settings", 2);
        cfg.put("country_code", "MA");
        cfg.put("company_name", "MySugu");
        cfg.put("company_phone", "");
        cfg.put("company_email", "");
        cfg.put("company_logo", "");
        cfg.put("upload_picture_on_delivery", 1);
        cfg.put("order_verification", 1);
        cfg.put("digital_payment", true);
        cfg.put("base_urls", baseUrls);
        return cfg;
    }
}
