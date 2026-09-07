package ma.mysuguclientapp.legacy.deliveryman.mapper;

import ma.mysuguclientapp.entities.Commande;
import ma.mysuguclientapp.entities.OffreLivraison;
import ma.mysuguclientapp.entities.User;
import ma.mysuguclientapp.enumerations.StatutCommande;
import ma.mysuguclientapp.enumerations.StatutOffreLivraison;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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

    @Test
    void exposeLesStatutsEtLEtatDAffectationSansAmbiguite() {
        Commande commande = commandeMinimale();
        commande.setStatut(StatutCommande.PRETE);
        User livreur = new User();
        livreur.setId(18L);
        OffreLivraison offre = OffreLivraison.builder()
                .id(123L)
                .commande(commande)
                .livreur(livreur)
                .statut(StatutOffreLivraison.PROPOSEE)
                .expiresAt(LocalDateTime.now().plusSeconds(30))
                .build();

        Map<String, Object> offered = mapper.toOrderMap(commande, false, offre);

        assertThat(offered).containsEntry("order_status", "ready")
                .containsEntry("assignment_state", "offered")
                .containsEntry("delivery_offer_id", 123L);
        assertThat((Long) offered.get("offer_remaining_seconds")).isBetween(0L, 30L);
        assertThat(offered.get("offer_expires_at")).asString().endsWith("Z");

        commande.setStatut(StatutCommande.ASSIGNEE_LIVREUR);
        commande.setLivreur(livreur);
        offre.setStatut(StatutOffreLivraison.ACCEPTEE);
        Map<String, Object> accepted = mapper.toOrderMap(commande, false, offre);
        assertThat(accepted).containsEntry("order_status", "assigned")
                .containsEntry("assignment_state", "accepted")
                .containsEntry("delivery_man_id", 18L)
                .containsEntry("delivery_offer_id", 123L)
                .containsEntry("offer_remaining_seconds", 0L);
    }

    private Commande commandeMinimale() {
        Commande commande = new Commande();
        commande.setId(51L);
        commande.setMontantFinal(BigDecimal.ZERO);
        commande.setMontantTotal(BigDecimal.ZERO);
        commande.setFraisLivraison(BigDecimal.ZERO);
        commande.setMontantCommissionTotal(BigDecimal.ZERO);
        commande.setMontantRemise(BigDecimal.ZERO);
        return commande;
    }
}
