package ma.mysuguclientapp.legacy.seller.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.mysuguclientapp.dtos.CommandeCreateDTO;
import ma.mysuguclientapp.dtos.CommandeDTO;
import ma.mysuguclientapp.dtos.LigneCommandeCreateDTO;
import ma.mysuguclientapp.dtos.PlatDTO;
import ma.mysuguclientapp.entities.Restaurant;
import ma.mysuguclientapp.entities.User;
import ma.mysuguclientapp.enumerations.MethodePaiement;
import ma.mysuguclientapp.enumerations.ModeReceptionCommande;
import ma.mysuguclientapp.legacy.seller.SellerContext;
import ma.mysuguclientapp.legacy.seller.mapper.ProductSellerMapper;
import ma.mysuguclientapp.services.interfaces.CommandeService;
import ma.mysuguclientapp.services.interfaces.PlatService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * POS (point-of-sale) du shim vendeur (contrat 6valley) : {@code /api/v3/seller/pos/*}.
 * mysugu est du meal-delivery, pas un POS e-commerce comptoir : la vente en magasin, la liste
 * de clients propre au vendeur et la facture de comptoir n'ont pas d'équivalent natif. Cette
 * tranche garde l'onglet POS de l'app bénin :
 * <ul>
 *   <li>{@code products}/{@code product-list} : PARTIAL — réutilise {@link PlatService} +
 *       {@link ProductSellerMapper} (même contrat que la tranche 3c), scopé au restaurant du
 *       vendeur via {@link SellerContext}.</li>
 *   <li>{@code place-order} : BUILD-MINIMAL — réutilise {@link CommandeService#createCommande}
 *       avec le vendeur authentifié comme client "walk-in" (JAMAIS d'utilisateur fictif
 *       inventé — voir {@link #placeOrder}).</li>
 *   <li>{@code customers}/{@code customer-store}/{@code get-invoice} : GAP — mysugu n'a pas de
 *       liste de clients propre au vendeur ni de facture de comptoir ({@code
 *       FacturationRestaurant*} est une dette plateforme, PAS une facture client, et n'est
 *       JAMAIS touché ici). Réponses bénignes, jamais de 404/500.</li>
 * </ul>
 * Voir docs/superpowers/specs/2026-07-10-vendor-3g-pos-design.md.
 */
@RestController
@RequestMapping("/api/v3/seller/pos")
@RequiredArgsConstructor
@Slf4j
public class SellerPosController {

    private final SellerContext sellerContext;
    private final PlatService platService;
    private final ProductSellerMapper mapper;
    private final CommandeService commandeService;

    // ── 3g.1 : products / product-list (PARTIAL: reuse PlatService, spec §3) ──────────────

    /**
     * GET pos/products : dans l'app réelle (Tiktak-vendor-app-moso/lib/features/pos/domain/
     * repository/cart_repository.dart#getProductFromScan), cette route n'est appelée QUE pour
     * une lecture par code-barres ({@code ?code=<id>}, où {@code id} == l'id du Plat stringifié
     * — cf. {@link ProductSellerMapper#toSixValley}) — jamais pour lister. Écart confirmé avec
     * le plan (qui supposait un alias direct de {@code product-list}). Sans {@code code}, on
     * répond quand même l'enveloppe paginée pour rester compatible avec le contrat générique
     * attendu par le plan 3g.1 et tout appelant futur.
     */
    @GetMapping({"/products", "/products/"})
    public Object products(@AuthenticationPrincipal String email,
                            @RequestParam(required = false) String code,
                            @RequestParam(value = "search", required = false) String search,
                            @RequestParam(value = "name", required = false) String name,
                            @RequestParam(defaultValue = "10") int limit,
                            @RequestParam(defaultValue = "0") int offset) {
        Restaurant restaurant = sellerContext.currentRestaurant(email);
        if (code != null && !code.isBlank()) {
            return productByCode(restaurant.getId(), code);
        }
        return productListEnvelope(restaurant.getId(), firstNonBlank(name, search), limit, offset);
    }

    /**
     * GET pos/product-list : grille POS paginée/recherchable. Confirmé sur le modèle Dart réel
     * (product_repository.dart#getSearchedPosProductList) : le paramètre de recherche est
     * {@code name} (PAS {@code search} comme supposé par le plan) — {@code search} reste
     * toléré en alias de compatibilité.
     */
    @GetMapping({"/product-list", "/product-list/"})
    public Map<String, Object> productList(@AuthenticationPrincipal String email,
                                            @RequestParam(value = "search", required = false) String search,
                                            @RequestParam(value = "name", required = false) String name,
                                            @RequestParam(defaultValue = "10") int limit,
                                            @RequestParam(defaultValue = "0") int offset) {
        Restaurant restaurant = sellerContext.currentRestaurant(email);
        return productListEnvelope(restaurant.getId(), firstNonBlank(name, search), limit, offset);
    }

    private Map<String, Object> productListEnvelope(Long restaurantId, String search, int limit, int offset) {
        List<PlatDTO> plats = platService.getPlatsByRestaurant(restaurantId);
        if (search != null && !search.isBlank()) {
            String needle = search.toLowerCase();
            plats = plats.stream()
                    .filter(p -> p.getNom() != null && p.getNom().toLowerCase().contains(needle))
                    .toList();
        }
        int total = plats.size();
        int safeLimit = Math.max(limit, 0);
        int from = Math.min(Math.max(offset, 0), total);
        int to = Math.min(from + safeLimit, total);
        List<Map<String, Object>> products = plats.subList(from, to).stream()
                .map(mapper::toSixValley)
                .toList();
        return mapper.rawEnvelope("products", total, limit, offset, products);
    }

    /**
     * Lecture par code-barres (== id du Plat stringifié). Jamais 404 : code inconnu, non
     * numérique, ou appartenant à un AUTRE restaurant -> produit bénin à zéro (jamais de fuite
     * cross-restaurant, jamais de crash — l'app force-unwrap {@code variation!} donc la clé
     * doit TOUJOURS être présente).
     */
    private Map<String, Object> productByCode(Long restaurantId, String code) {
        Long id = toLongOrNull(code);
        if (id != null) {
            try {
                PlatDTO plat = platService.getPlatById(id);
                if (plat.getRestaurantId() != null && plat.getRestaurantId().equals(restaurantId)) {
                    return withVariation(mapper.toSixValley(plat));
                }
            } catch (RuntimeException ignored) {
                // Plat introuvable -> repli bénin ci-dessous, jamais 404.
            }
        }
        return withVariation(benignEmptyProduct(code));
    }

    private Map<String, Object> withVariation(Map<String, Object> product) {
        // Product.fromJson (product_model.dart) ne force-unwrap PAS "variation" côté objet,
        // mais getProductFromScan côté app fait bien `scanProduct!.variation!.isNotEmpty` ->
        // la clé doit toujours être présente et non-nulle, même vide.
        product.put("variation", List.of());
        return product;
    }

    private Map<String, Object> benignEmptyProduct(String code) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", 0);
        m.put("name", "");
        m.put("price", BigDecimal.ZERO);
        m.put("unit_price", BigDecimal.ZERO);
        m.put("image", null);
        m.put("thumbnail", null);
        m.put("images", List.of());
        m.put("current_stock", 0);
        m.put("status", 0);
        m.put("product_type", "physical");
        m.put("choice_options", List.of());
        m.put("category_ids", List.of());
        m.put("tax", 0);
        m.put("discount", 0);
        m.put("added_by", "seller");
        m.put("code", code);
        m.put("sku", code);
        return m;
    }

    // ── 3g.2 : place-order (BUILD-MINIMAL: reuse CommandeService, spec §3) ────────────────

    /**
     * POST pos/place-order : mappe le panier POS -> {@link CommandeCreateDTO} et réutilise
     * {@link CommandeService#createCommande}. BUILD-MINIMAL retenu (pas de STUB) : le mapping
     * est propre — {@code clientId} est TOUJOURS le vendeur authentifié lui-même (un
     * utilisateur RÉEL et déjà persisté, jamais un client synthétique inventé — le
     * {@code customer_id} envoyé par l'app POS est un id "client POS" local à 6valley sans
     * AUCUNE correspondance avec un User mysugu, donc jamais utilisé comme {@code clientId}).
     * {@code modeReception} = RETRAIT_SUR_PLACE (vente comptoir). Toute erreur de
     * {@link CommandeService} (plat indisponible, restaurant fermé, panier vide/invalide...)
     * est absorbée -> réponse bénigne 200, JAMAIS 500 (contrainte globale de la tranche —
     * l'app 6valley ne vérifie que le status code 200, cf. cart_controller.dart#placeOrder).
     * // BUILD-MINIMAL (spec §3): reuse CommandeService.createCommande, owner-as-walk-in-client.
     */
    @PostMapping({"/place-order", "/place-order/"})
    public Map<String, Object> placeOrder(@AuthenticationPrincipal String email,
                                           @RequestBody(required = false) Map<String, Object> body) {
        User owner = sellerContext.requireOwner(email);
        Restaurant restaurant = sellerContext.currentRestaurant(email);

        List<Map<String, Object>> cartItems = extractCart(body);
        if (cartItems.isEmpty()) {
            return mapper.success("Panier vide.");
        }

        List<LigneCommandeCreateDTO> lignes = new ArrayList<>();
        for (Map<String, Object> item : cartItems) {
            Long platId = toLongOrNull(item.get("id"));
            if (platId == null) {
                continue;
            }
            Integer qty = toIntOrNull(item.get("quantity"));
            LigneCommandeCreateDTO ligne = new LigneCommandeCreateDTO();
            ligne.setPlatId(platId);
            ligne.setQuantite(qty != null && qty > 0 ? qty : 1);
            lignes.add(ligne);
        }
        if (lignes.isEmpty()) {
            return mapper.success("Panier vide.");
        }

        try {
            CommandeCreateDTO dto = new CommandeCreateDTO();
            dto.setClientId(owner.getId());
            dto.setRestaurantId(restaurant.getId());
            dto.setLignes(lignes);
            dto.setModeReception(ModeReceptionCommande.RETRAIT_SUR_PLACE.name());
            dto.setMethodePaiement(mapPaymentMethod(body != null ? (String) body.get("payment_method") : null).name());

            CommandeDTO created = commandeService.createCommande(dto);

            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("message", "Commande passée avec succès.");
            resp.put("order_id", created.getId());
            return resp;
        } catch (RuntimeException e) {
            log.warn("POS place-order non traité (restaurant={}): {}", restaurant.getId(), e.getMessage());
            return mapper.success("Commande non traitée.");
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractCart(Map<String, Object> body) {
        if (body == null) {
            return List.of();
        }
        Object cart = body.get("cart");
        if (!(cart instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> items = new ArrayList<>();
        for (Object o : list) {
            if (o instanceof Map<?, ?> map) {
                items.add((Map<String, Object>) map);
            }
        }
        return items;
    }

    private MethodePaiement mapPaymentMethod(String value) {
        if (value != null && value.trim().equalsIgnoreCase("card")) {
            return MethodePaiement.CARTE_BANCAIRE;
        }
        return MethodePaiement.ESPECES;
    }

    // ── 3g.3/3g.4 : customers / customer-store (GAP: benign, umbrella §4) ─────────────────

    /**
     * GET pos/customers : mysugu n'a pas de liste de clients propre au vendeur (les clients
     * sont des User CLIENT de la plateforme, pas des enregistrements du vendeur). Enveloppe
     * bénigne vide — JAMAIS 404 : {@code CustomerModel.fromJson(...).customers!} force-unwrap
     * côté app, la clé doit toujours être une liste (même vide), jamais absente/nulle.
     * // STUB: no vendor customer list in mysugu (umbrella §4 GAP).
     */
    @GetMapping({"/customers", "/customers/"})
    public Map<String, Object> customers(@AuthenticationPrincipal String email,
                                          @RequestParam(defaultValue = "10") int limit,
                                          @RequestParam(defaultValue = "0") int offset) {
        sellerContext.currentRestaurant(email); // 403 non-vendeur / 404 sans restaurant
        return mapper.emptyEnvelope("customers", limit, offset);
    }

    /**
     * POST pos/customer-store : mysugu ne crée pas de client propre au vendeur. No-op idempotent
     * — accepte le corps, ne persiste rien, renvoie un accusé bénin.
     * // STUB: no vendor-created customers (umbrella §4 GAP).
     */
    @PostMapping({"/customer-store", "/customer-store/"})
    public Map<String, Object> customerStore(@AuthenticationPrincipal String email,
                                              @RequestBody(required = false) Map<String, Object> body) {
        sellerContext.currentRestaurant(email); // 403 non-vendeur / 404 sans restaurant
        return mapper.success("Client ajouté.");
    }

    // ── 3g.5 : get-invoice (GAP: benign, umbrella §4) ──────────────────────────────────────

    /**
     * GET pos/get-invoice : mysugu n'a pas de facture de comptoir. Écart confirmé avec le plan
     * (qui supposait {@code /get-invoice/{id}} ou {@code ?order_id=}) : l'app réelle
     * (cart_repository.dart#getInvoiceData) appelle {@code GET pos/get-invoice?id=<orderId>}.
     * Réponse bénigne — JAMAIS de {@code FacturationRestaurant*} (dette plateforme, pas une
     * facture client) — JAMAIS 404/500 : {@code details} DOIT être une liste (jamais nulle),
     * l'app force-unwrap {@code invoice!.details!.length} sans garde côté contrôleur.
     * // STUB: no counter invoice in mysugu; NOT FacturationRestaurant (umbrella §4 GAP, spec §3).
     */
    @GetMapping({"/get-invoice", "/get-invoice/", "/get-invoice/{orderId}"})
    public Map<String, Object> getInvoice(@AuthenticationPrincipal String email,
                                           @RequestParam(value = "id", required = false) Long idParam,
                                           @RequestParam(value = "order_id", required = false) Long orderIdParam,
                                           @PathVariable(required = false) Long orderId) {
        sellerContext.currentRestaurant(email); // 403 non-vendeur / 404 sans restaurant
        Long resolvedId = idParam != null ? idParam : (orderIdParam != null ? orderIdParam : orderId);

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("order_id", resolvedId);
        m.put("order_amount", BigDecimal.ZERO);
        m.put("created_at", null);
        m.put("discount_amount", BigDecimal.ZERO);
        m.put("extra_discount", BigDecimal.ZERO);
        m.put("payment_method", "cash");
        m.put("details", List.of());
        return m;
    }

    // ── helpers ─────────────────────────────────────────────────────────────────────────

    private static String firstNonBlank(String a, String b) {
        if (a != null && !a.isBlank()) {
            return a;
        }
        return b;
    }

    private static Long toLongOrNull(Object o) {
        if (o == null) {
            return null;
        }
        try {
            return Long.parseLong(o.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Integer toIntOrNull(Object o) {
        if (o == null) {
            return null;
        }
        try {
            return (int) Double.parseDouble(o.toString().trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
