package ma.mysuguclientapp.services.integrations;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.mysuguclientapp.dtos.integration.TikTakIdMappingDTO;
import ma.mysuguclientapp.dtos.integration.TikTakOrderStatusSyncDTO;
import ma.mysuguclientapp.dtos.integration.TikTakSharedIdsDTO;
import ma.mysuguclientapp.entities.Commande;
import ma.mysuguclientapp.entities.LigneCommande;
import ma.mysuguclientapp.enumerations.StatutCommande;
import ma.mysuguclientapp.repositories.CommandeRepository;
import ma.mysuguclientapp.repositories.UserRepository;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class TikTakOrderIntegrationService {
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final CommandeRepository commandeRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final OkHttpClient httpClient = new OkHttpClient.Builder()
            .connectTimeout(Duration.ofSeconds(15))
            .readTimeout(Duration.ofSeconds(20))
            .build();

    @Value("${tiktak.integration.enabled:false}")
    private boolean enabled;

    @Value("${tiktak.integration.base-url:https://dashboard.atlantique21service.one}")
    private String baseUrl;

    @Value("${tiktak.integration.token:}")
    private String integrationToken;

    @Value("${tiktak.integration.restaurant-seller-map:}")
    private String restaurantSellerMap;

    @Value("${tiktak.integration.plat-product-map:}")
    private String platProductMap;

    @Value("${tiktak.integration.delivery-man-map:}")
    private String deliveryManMap;

    @Value("${tiktak.integration.identity-fallback:true}")
    private boolean identityFallback;

    public void pushCreatedOrder(Commande commande) {
        if (!enabled || integrationToken == null || integrationToken.isBlank()) {
            return;
        }
        if (commande.getTiktakOrderId() != null) {
            return;
        }

        Long sellerId = mapId(restaurantSellerMap, commande.getRestaurant().getId());
        if (sellerId == null) {
            markSyncStatus(commande, "MISSING_SELLER_MAPPING");
            return;
        }

        try {
            Map<String, Object> payload = buildCreatePayload(commande, sellerId);
            String body = objectMapper.writeValueAsString(payload);
            Request request = new Request.Builder()
                    .url(trimSlash(baseUrl) + "/api/v1/integration/mysuku/orders")
                    .header("X-Integration-Token", integrationToken)
                    .post(RequestBody.create(body, JSON))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                String responseBody = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) {
                    log.warn("TikTak order sync failed for commande {}: HTTP {} {}",
                            commande.getId(), response.code(), responseBody);
                    markSyncStatus(commande, "CREATE_FAILED_" + response.code());
                    return;
                }

                JsonNode json = objectMapper.readTree(responseBody);
                Long tiktakOrderId = json.path("order_id").isNumber() ? json.path("order_id").asLong() : null;
                if (tiktakOrderId == null) {
                    markSyncStatus(commande, "CREATE_NO_ORDER_ID");
                    return;
                }

                commande.setTiktakOrderId(tiktakOrderId);
                commande.setTiktakSyncStatus("CREATED");
                commandeRepository.save(commande);
            }
        } catch (Exception e) {
            log.warn("TikTak order sync exception for commande {}: {}", commande.getId(), e.getMessage());
            markSyncStatus(commande, "CREATE_EXCEPTION");
        }
    }

    @Transactional
    public void applyStatusFromTikTak(TikTakOrderStatusSyncDTO dto) {
        if (dto.getTiktakOrderId() == null) {
            throw new IllegalArgumentException("tiktakOrderId is required");
        }

        Commande commande = commandeRepository.findByTiktakOrderId(dto.getTiktakOrderId())
                .orElseThrow(() -> new IllegalArgumentException("Commande MySuku introuvable"));

        StatutCommande statut = mapTikTakStatus(dto.getStatus());
        if (statut != null) {
            commande.setStatut(statut);
        }

        if (dto.getReason() != null && !dto.getReason().isBlank()) {
            commande.setRaisonAnnulation(dto.getReason());
        }

        if (dto.getTiktakDeliveryManId() != null) {
            Long mysukuLivreurId = mapId(deliveryManMap, dto.getTiktakDeliveryManId());
            if (mysukuLivreurId != null) {
                userRepository.findById(mysukuLivreurId).ifPresent(commande::setLivreur);
            }
            if (statut == null
                    || statut == StatutCommande.EN_ATTENTE
                    || statut == StatutCommande.CONFIRMEE
                    || statut == StatutCommande.EN_PREPARATION) {
                commande.setStatut(StatutCommande.ASSIGNEE_LIVREUR);
            }
        }

        commande.setTiktakSyncStatus("STATUS_" + dto.getStatus());
        commandeRepository.save(commande);
    }

    public boolean isValidToken(String token) {
        return integrationToken != null && !integrationToken.isBlank() && integrationToken.equals(token);
    }

    public TikTakSharedIdsDTO getSharedIds() {
        return new TikTakSharedIdsDTO(
                parseMapping(restaurantSellerMap),
                parseMapping(deliveryManMap),
                identityFallback
        );
    }

    private Map<String, Object> buildCreatePayload(Commande commande, Long sellerId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("mysuku_order_id", commande.getId());
        payload.put("order_number", commande.getNumeroCommande());
        payload.put("seller_id", sellerId);
        payload.put("order_note", commande.getCommentaire());
        payload.put("payment_method", "cash_on_delivery");
        payload.put("order_amount", amount(commande.getMontantFinal() != null ? commande.getMontantFinal() : commande.getMontantTotal()));
        payload.put("shipping_cost", amount(commande.getFraisLivraison()));
        Map<String, Object> customer = new HashMap<>();
        customer.put("id", commande.getClient().getId());
        customer.put("first_name", nullToEmpty(commande.getClient().getPrenom()));
        customer.put("last_name", nullToEmpty(commande.getClient().getNom()));
        customer.put("email", nullToEmpty(commande.getClient().getEmail()));
        customer.put("phone", nullToEmpty(commande.getClient().getTelephone()));
        payload.put("customer", customer);

        Map<String, Object> shippingAddress = new HashMap<>();
        shippingAddress.put("address", commande.getAdresseLivraison() != null ? nullToEmpty(commande.getAdresseLivraison().getAdresse()) : "");
        shippingAddress.put("city", commande.getAdresseLivraison() != null ? nullToEmpty(commande.getAdresseLivraison().getVille()) : "");
        shippingAddress.put("latitude", commande.getAdresseLivraison() != null ? commande.getAdresseLivraison().getLatitude() : null);
        shippingAddress.put("longitude", commande.getAdresseLivraison() != null ? commande.getAdresseLivraison().getLongitude() : null);
        payload.put("shipping_address", shippingAddress);
        payload.put("items", buildItems(commande.getLignesCommande(), sellerId));
        return payload;
    }

    private List<Map<String, Object>> buildItems(List<LigneCommande> lignes, Long sellerId) {
        return lignes.stream().map(ligne -> {
            Long productId = ligne.getPlat() != null ? mapId(platProductMap, ligne.getPlat().getId()) : null;
            Map<String, Object> productDetails = new HashMap<>();
            productDetails.put("id", productId);
            productDetails.put("name", ligne.getPlat() != null ? ligne.getPlat().getNom() : "Article MySuku");
            productDetails.put("thumbnail", ligne.getPlat() != null ? ligne.getPlat().getImageUrl() : null);
            productDetails.put("unit_price", amount(ligne.getPrixUnitaire()));
            productDetails.put("current_stock", 999999);

            Map<String, Object> item = new HashMap<>();
            item.put("product_id", productId);
            item.put("seller_id", sellerId);
            item.put("product_details", productDetails);
            item.put("qty", ligne.getQuantite());
            item.put("price", amount(ligne.getPrixUnitaire()));
            item.put("discount", 0);
            item.put("tax", 0);
            item.put("tax_model", "include");
            item.put("remark", ligne.getRemarque());
            return item;
        }).toList();
    }

    private Long mapId(String rawMap, Long sourceId) {
        if (sourceId == null) return null;
        if (rawMap != null && !rawMap.isBlank()) {
            for (String pair : rawMap.split(",")) {
                String[] parts = pair.split(":");
                if (parts.length != 2) continue;
                try {
                    long from = Long.parseLong(parts[0].trim());
                    long to = Long.parseLong(parts[1].trim());
                    if (from == sourceId) return to;
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return identityFallback ? sourceId : null;
    }

    private StatutCommande mapTikTakStatus(String status) {
        if (status == null) return null;
        return switch (status) {
            case "pending" -> StatutCommande.EN_ATTENTE;
            case "confirmed" -> StatutCommande.CONFIRMEE;
            case "processing" -> StatutCommande.EN_PREPARATION;
            case "out_for_delivery" -> StatutCommande.EN_COURS;
            case "delivered" -> StatutCommande.LIVREE;
            case "canceled", "returned", "failed" -> StatutCommande.ANNULEE;
            default -> null;
        };
    }

    private List<TikTakIdMappingDTO> parseMapping(String rawMap) {
        if (rawMap == null || rawMap.isBlank()) {
            return List.of();
        }

        return rawMap.lines()
                .flatMap(line -> List.of(line.split(",")).stream())
                .map(String::trim)
                .filter(pair -> !pair.isBlank())
                .map(pair -> {
                    String[] parts = pair.split(":");
                    if (parts.length != 2) {
                        return null;
                    }
                    try {
                        return new TikTakIdMappingDTO(
                                Long.parseLong(parts[0].trim()),
                                Long.parseLong(parts[1].trim())
                        );
                    } catch (NumberFormatException e) {
                        return null;
                    }
                })
                .filter(mapping -> mapping != null)
                .toList();
    }

    private void markSyncStatus(Commande commande, String status) {
        commande.setTiktakSyncStatus(status);
        commandeRepository.save(commande);
    }

    private double amount(BigDecimal value) {
        return value == null ? 0 : value.doubleValue();
    }

    private String trimSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
