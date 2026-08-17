package ma.mysuguclientapp.legacy.seller;

import ma.mysuguclientapp.dtos.CommandeDTO;
import ma.mysuguclientapp.dtos.LigneCommandeDTO;
import ma.mysuguclientapp.dtos.LocalisationDTO;
import ma.mysuguclientapp.dtos.PlatDTO;
import ma.mysuguclientapp.dtos.RestaurantDTO;
import ma.mysuguclientapp.dtos.UserDTO;
import ma.mysuguclientapp.enumerations.StatutCommande;
import ma.mysuguclientapp.enumerations.StatutPaiement;
import ma.mysuguclientapp.legacy.seller.mapper.OrderSellerMapper;
import ma.mysuguclientapp.legacy.seller.mapper.OrderStatusMapper;
import ma.mysuguclientapp.legacy.seller.mapper.ProductSellerMapper;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for OrderSellerMapper: pure CommandeDTO -> 6valley order / order-details JSON
 * translation (no Spring context needed). See
 * docs/superpowers/plans/2026-07-10-vendor-3d-orders.md 3d.2 and spec §4.
 */
class OrderSellerMapperTest {

    private final OrderSellerMapper mapper =
            new OrderSellerMapper(new OrderStatusMapper(), new ProductSellerMapper());

    private CommandeDTO buildCommande() {
        CommandeDTO dto = new CommandeDTO();
        dto.setId(7L);
        dto.setNumeroCommande("CMD-7");
        dto.setStatut(StatutCommande.EN_COURS.name());
        dto.setStatutPaiement(StatutPaiement.PAYE.name());
        dto.setMethodePaiement("CARTE_BANCAIRE");
        dto.setMontantFinal(new BigDecimal("120.00"));
        dto.setFraisLivraison(new BigDecimal("15.00"));
        dto.setMontantCommissionTotal(new BigDecimal("10.00"));
        dto.setMontantVendeur(new BigDecimal("95.00"));
        dto.setMontantRemise(new BigDecimal("5.00"));
        dto.setModeReception("LIVRAISON");
        dto.setCommentaire("Sonner à l'interphone");

        RestaurantDTO restaurant = new RestaurantDTO();
        restaurant.setId(3L);
        restaurant.setNom("Boutique Test");
        dto.setRestaurant(restaurant);

        UserDTO client = new UserDTO();
        client.setId(9L);
        client.setNom("Alaoui");
        client.setPrenom("Sara");
        client.setEmail("sara@test.mysugu");
        client.setTelephone("0600000000");
        dto.setClient(client);

        UserDTO livreur = new UserDTO();
        livreur.setId(15L);
        livreur.setNom("Bennani");
        livreur.setPrenom("Yassine");
        dto.setLivreur(livreur);

        LocalisationDTO adresse = new LocalisationDTO();
        adresse.setLatitude(33.5731);
        adresse.setLongitude(-7.5898);
        adresse.setAdresse("12 Rue Test");
        adresse.setVille("Casablanca");
        dto.setAdresseLivraison(adresse);

        LigneCommandeDTO ligne1 = new LigneCommandeDTO();
        ligne1.setId(101L);
        ligne1.setQuantite(2);
        ligne1.setPrixUnitaire(new BigDecimal("30.00"));
        PlatDTO plat1 = new PlatDTO();
        plat1.setId(201L);
        plat1.setNom("Tajine");
        ligne1.setPlat(plat1);

        LigneCommandeDTO ligne2 = new LigneCommandeDTO();
        ligne2.setId(102L);
        ligne2.setQuantite(1);
        ligne2.setPrixUnitaire(new BigDecimal("60.00"));
        PlatDTO plat2 = new PlatDTO();
        plat2.setId(202L);
        plat2.setNom("Couscous");
        ligne2.setPlat(plat2);

        dto.setLignesCommande(List.of(ligne1, ligne2));
        return dto;
    }

    @Test
    void toOrder_maps_commande_fields_to_6valley_order_json() {
        Map<String, Object> order = mapper.toOrder(buildCommande());

        assertThat(order.get("id")).isEqualTo(7L);
        assertThat(order.get("order_status")).isEqualTo("out_for_delivery");
        assertThat(order.get("payment_status")).isEqualTo("paid");
        assertThat(order.get("order_amount")).isEqualTo(new BigDecimal("120.00"));
        assertThat(order.get("montantFinal")).isEqualTo(new BigDecimal("120.00"));
        assertThat(order.get("shipping_cost")).isEqualTo(new BigDecimal("15.00"));
        assertThat(order.get("discount_amount")).isEqualTo(new BigDecimal("5.00"));
        assertThat(order.get("montant_vendeur")).isEqualTo(new BigDecimal("95.00"));
        assertThat(order.get("montant_commission_total")).isEqualTo(new BigDecimal("10.00"));
        assertThat(order.get("delivery_man_id")).isEqualTo(15L);

        @SuppressWarnings("unchecked")
        Map<String, Object> shippingAddress = (Map<String, Object>) order.get("shipping_address_data");
        assertThat(shippingAddress.get("latitude")).isInstanceOf(String.class);
        assertThat(shippingAddress.get("latitude")).isEqualTo("33.5731");
        assertThat(shippingAddress.get("longitude")).isEqualTo("-7.5898");
    }

    @Test
    void toOrder_never_nulls_numeric_fields_app_calls_toDouble_on() {
        CommandeDTO dto = new CommandeDTO();
        dto.setId(1L);
        dto.setStatut(StatutCommande.EN_ATTENTE.name());
        Map<String, Object> order = mapper.toOrder(dto);

        assertThat(order.get("order_amount")).isNotNull();
        assertThat(order.get("shipping_cost")).isNotNull();
        assertThat(order.get("discount_amount")).isNotNull();
        assertThat(order.get("payment_status")).isEqualTo("unpaid");
    }

    @Test
    void toListEnvelope_wraps_page_with_6valley_pagination() {
        Page<CommandeDTO> page = new PageImpl<>(List.of(buildCommande()), PageRequest.of(0, 10), 1);

        Map<String, Object> envelope = mapper.toListEnvelope(page, 10, 0);

        assertThat(envelope.get("total_size")).isEqualTo(1);
        assertThat(envelope).containsKeys("limit", "offset", "orders");
        assertThat((List<?>) envelope.get("orders")).hasSize(1);
    }

    @Test
    void toOrderDetails_returns_one_line_item_per_ligne_commande() {
        List<Map<String, Object>> details = mapper.toOrderDetails(buildCommande());

        assertThat(details).hasSize(2);
        for (Map<String, Object> line : details) {
            assertThat(line.get("price")).isNotNull();
            assertThat(line.get("tax")).isNotNull();
            assertThat(line.get("discount")).isNotNull();
            assertThat(line.get("product_details")).isNotNull();
            assertThat(line.get("order")).isNotNull();
        }
        assertThat(details.get(0).get("product_id")).isEqualTo(201L);
        assertThat(details.get(0).get("qty")).isEqualTo(2);
    }
}
