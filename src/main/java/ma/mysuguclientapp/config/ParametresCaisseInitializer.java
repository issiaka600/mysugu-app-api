package ma.mysuguclientapp.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.mysuguclientapp.entities.ParametresCaisse;
import ma.mysuguclientapp.repositories.ParametresCaisseRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Cree le singleton ParametresCaisse (id=1) UNE fois au demarrage, de maniere idempotente.
 *
 * Sans cela, getParams() dans CaisseServiceImpl ET AlerteCaisseScheduler tentent tous deux
 * un findById(1).orElseGet(insert id=1) ; au demarrage, deux appels concurrents inserent id=1
 * en meme temps -> "duplicate key parametres_caisse_pkey" -> la collecte caisse (donc la
 * livraison COD, native comme shim livreur) echoue en 500. Cet initialiseur s'execute sur le
 * thread principal avant que les taches planifiees ne demarrent, garantissant que la ligne
 * existe et que getParams() ne l'insere plus jamais.
 */
@Component
@Order(5)
@RequiredArgsConstructor
@Slf4j
public class ParametresCaisseInitializer implements ApplicationRunner {

    private final ParametresCaisseRepository parametresCaisseRepository;

    @Override
    public void run(ApplicationArguments args) {
        if (parametresCaisseRepository.existsById(1L)) {
            return;
        }
        try {
            ParametresCaisse defaults = ParametresCaisse.builder()
                    .id(1L)
                    .plafondCaisseLivreur(new BigDecimal("500.00"))
                    .seuilAlertePourcentage(80)
                    .intervalleReconciliationHeures(48)
                    .tauxCommissionPlateforme(new BigDecimal("0.1500"))
                    .seuilPrixCommission(new BigDecimal("10.00"))
                    .commissionMinPourcentage(new BigDecimal("20.00"))
                    .periodicitePaiementRestaurantJours(7)
                    .build();
            parametresCaisseRepository.save(defaults);
            log.info("ParametresCaisse singleton (id=1) initialise.");
        } catch (Exception e) {
            // Deja cree par une autre voie entre-temps : sans gravite.
            log.info("ParametresCaisse deja present (init concurrent) : {}", e.getMessage());
        }
    }
}
