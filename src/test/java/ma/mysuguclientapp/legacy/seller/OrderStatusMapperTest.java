package ma.mysuguclientapp.legacy.seller;

import ma.mysuguclientapp.enumerations.StatutCommande;
import ma.mysuguclientapp.enumerations.StatutPaiement;
import ma.mysuguclientapp.legacy.seller.mapper.OrderStatusMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for OrderStatusMapper: pure StatutCommande/StatutPaiement <-> 6valley string
 * translation (no Spring context needed). Normative table: spec §3 / §3b.
 * See docs/superpowers/plans/2026-07-10-vendor-3d-orders.md 3d.1.
 */
class OrderStatusMapperTest {

    private final OrderStatusMapper mapper = new OrderStatusMapper();

    // ---- Outbound status: native -> 6valley (spec §3) ----

    @Test
    void toSixValleyStatus_maps_every_native_status() {
        assertThat(mapper.toSixValleyStatus(StatutCommande.EN_ATTENTE)).isEqualTo("pending");
        assertThat(mapper.toSixValleyStatus(StatutCommande.CONFIRMEE)).isEqualTo("confirmed");
        assertThat(mapper.toSixValleyStatus(StatutCommande.EN_PREPARATION)).isEqualTo("processing");
        assertThat(mapper.toSixValleyStatus(StatutCommande.PRETE)).isEqualTo("processing");
        assertThat(mapper.toSixValleyStatus(StatutCommande.ASSIGNEE_LIVREUR)).isEqualTo("out_for_delivery");
        assertThat(mapper.toSixValleyStatus(StatutCommande.EN_COURS)).isEqualTo("out_for_delivery");
        assertThat(mapper.toSixValleyStatus(StatutCommande.LIVREE)).isEqualTo("delivered");
        assertThat(mapper.toSixValleyStatus(StatutCommande.ANNULEE)).isEqualTo("canceled");
        assertThat(mapper.toSixValleyStatus(StatutCommande.NON_FINALISEE)).isEqualTo("failed");
    }

    // ---- Inbound status: 6valley -> native (spec §3) ----

    @Test
    void fromSixValleyStatus_maps_every_app_status() {
        assertThat(mapper.fromSixValleyStatus("pending")).isEqualTo(StatutCommande.EN_ATTENTE);
        assertThat(mapper.fromSixValleyStatus("confirmed")).isEqualTo(StatutCommande.CONFIRMEE);
        assertThat(mapper.fromSixValleyStatus("processing")).isEqualTo(StatutCommande.EN_PREPARATION);
        assertThat(mapper.fromSixValleyStatus("out_for_delivery")).isEqualTo(StatutCommande.EN_COURS);
        assertThat(mapper.fromSixValleyStatus("delivered")).isEqualTo(StatutCommande.LIVREE);
        assertThat(mapper.fromSixValleyStatus("canceled")).isEqualTo(StatutCommande.ANNULEE);
        assertThat(mapper.fromSixValleyStatus("returned")).isEqualTo(StatutCommande.ANNULEE);
        assertThat(mapper.fromSixValleyStatus("failed")).isEqualTo(StatutCommande.ANNULEE);
    }

    @Test
    void fromSixValleyStatus_unknown_throws_400() {
        assertThatThrownBy(() -> mapper.fromSixValleyStatus("bogus-status"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
    }

    // ---- Payment status (spec §3b) ----

    @Test
    void toSixValleyPayment_maps_every_native_status() {
        assertThat(mapper.toSixValleyPayment(StatutPaiement.PAYE)).isEqualTo("paid");
        assertThat(mapper.toSixValleyPayment(StatutPaiement.EN_ATTENTE)).isEqualTo("unpaid");
        assertThat(mapper.toSixValleyPayment(StatutPaiement.ECHOUE)).isEqualTo("unpaid");
        assertThat(mapper.toSixValleyPayment(StatutPaiement.REMBOURSE)).isEqualTo("unpaid");
    }

    @Test
    void fromSixValleyPayment_maps_every_app_status() {
        assertThat(mapper.fromSixValleyPayment("paid")).isEqualTo(StatutPaiement.PAYE);
        assertThat(mapper.fromSixValleyPayment("unpaid")).isEqualTo(StatutPaiement.EN_ATTENTE);
    }

    @Test
    void fromSixValleyPayment_unknown_throws_400() {
        assertThatThrownBy(() -> mapper.fromSixValleyPayment("bogus-payment"))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("400");
    }
}
