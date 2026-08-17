package ma.mysuguclientapp.legacy.deliveryman.mapper;

import ma.mysuguclientapp.entities.Commande;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class LegacyOrderAmountMapperTest {

    private final LegacyOrderMapper mapper = new LegacyOrderMapper();

    @Test
    void exposeLesMontantsDefinitifsSansRecalculMobile() {
        Commande commande = new Commande();
        commande.setId(2030L);
        commande.setMontantFinal(new BigDecimal("75.00"));
        commande.setMontantTotal(new BigDecimal("75.00"));
        commande.setFraisLivraison(new BigDecimal("15.00"));
        commande.setMontantCommissionTotal(new BigDecimal("10.00"));
        commande.setMontantRemise(BigDecimal.ZERO);

        Map<String, Object> response = mapper.toOrderMap(commande, false);

        assertThat(response).containsEntry("montantFinal", new BigDecimal("75.00"))
                .containsEntry("order_amount", new BigDecimal("75.00"))
                .containsEntry("shipping_cost", new BigDecimal("15.00"))
                .containsEntry("discount_amount", BigDecimal.ZERO)
                .containsEntry("montant_commission_total", new BigDecimal("10.00"))
                .containsEntry("montant_vendeur", new BigDecimal("50.00"))
                .containsEntry("seller_total", new BigDecimal("50.00"));
    }
}
