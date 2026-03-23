package ma.mysuguclientapp.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.mysuguclientapp.entities.CaisseLivreur;
import ma.mysuguclientapp.entities.ParametresCaisse;
import ma.mysuguclientapp.repositories.CaisseLivreurRepository;
import ma.mysuguclientapp.repositories.ParametresCaisseRepository;
import ma.mysuguclientapp.services.implementations.NotificationServiceImpl;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class AlerteCaisseScheduler {

    private final CaisseLivreurRepository caisseLivreurRepository;
    private final ParametresCaisseRepository parametresCaisseRepository;
    private final NotificationServiceImpl notificationService;

    /**
     * Vérifie toutes les heures les livreurs dépassant le seuil d'intervalle de réconciliation.
     */
    @Scheduled(cron = "0 0 * * * *") // every hour
    @Transactional
    public void verifierIntervallesReconciliation() {
        ParametresCaisse params = getParams();
        LocalDateTime seuilIntervalle = LocalDateTime.now().minusHours(params.getIntervalleReconciliationHeures());

        List<CaisseLivreur> caissesEnRetard = caisseLivreurRepository.findDepassantIntervalle(seuilIntervalle);

        for (CaisseLivreur caisse : caissesEnRetard) {
            if (caisse.getSoldeCourant().compareTo(BigDecimal.ZERO) <= 0) continue;
            if (Boolean.TRUE.equals(caisse.getAlerteIntervalleEnvoyee())) continue;

            caisse.setAlerteIntervalleEnvoyee(true);
            caisseLivreurRepository.save(caisse);

            notificationService.envoyerNotificationSysteme(
                    caisse.getLivreur().getId(),
                    "Réconciliation requise",
                    "Vous avez " + caisse.getSoldeCourant() + " MAD dans votre caisse. " +
                            "Merci de vous présenter à l'agence pour la réconciliation."
            );

            log.info("Alerte intervalle réconciliation envoyée au livreur {} (caisse: {} MAD)",
                    caisse.getLivreur().getId(), caisse.getSoldeCourant());
        }
    }

    /**
     * Vérifie toutes les 30 minutes les livreurs dépassant le plafond.
     */
    @Scheduled(cron = "0 */30 * * * *") // every 30 minutes
    @Transactional
    public void verifierPlafondsCaisse() {
        ParametresCaisse params = getParams();

        List<CaisseLivreur> caissesActives = caisseLivreurRepository.findAvecSoldePositif();

        for (CaisseLivreur caisse : caissesActives) {
            BigDecimal plafond = caisse.getPlafondPersonnalise() != null ?
                    caisse.getPlafondPersonnalise() : params.getPlafondCaisseLivreur();

            // Alerte dépassement plafond absolu (pas juste le seuil %)
            if (caisse.getSoldeCourant().compareTo(plafond) > 0) {
                notificationService.envoyerNotificationSysteme(
                        caisse.getLivreur().getId(),
                        "URGENT: Plafond caisse dépassé",
                        "Votre caisse (" + caisse.getSoldeCourant() + " MAD) dépasse le plafond autorisé (" +
                                plafond + " MAD). Présentez-vous immédiatement pour réconciliation."
                );
                log.warn("ALERTE PLAFOND DÉPASSÉ: livreur {} caisse={} MAD plafond={} MAD",
                        caisse.getLivreur().getId(), caisse.getSoldeCourant(), plafond);
            }
        }
    }

    private ParametresCaisse getParams() {
        return parametresCaisseRepository.findById(1L).orElseGet(() -> {
            ParametresCaisse defaults = ParametresCaisse.builder()
                    .id(1L)
                    .plafondCaisseLivreur(new BigDecimal("500.00"))
                    .seuilAlertePourcentage(80)
                    .intervalleReconciliationHeures(48)
                    .tauxCommissionPlateforme(new BigDecimal("0.1500"))
                    .periodicitePaiementRestaurantJours(7)
                    .build();
            return parametresCaisseRepository.save(defaults);
        });
    }
}
