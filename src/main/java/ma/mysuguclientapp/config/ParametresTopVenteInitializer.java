package ma.mysuguclientapp.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.mysuguclientapp.entities.ParametresTopVente;
import ma.mysuguclientapp.repositories.ParametresTopVenteRepository;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Cree le singleton ParametresTopVente (id=1) UNE fois au demarrage, de maniere idempotente.
 * Même motif que ParametresCaisseInitializer : la ligne doit exister avant le premier
 * accès concurrent, sinon les orElseGet(insert id=1) explosent en duplicate key.
 */
@Component
@Order(6)
@RequiredArgsConstructor
@Slf4j
public class ParametresTopVenteInitializer implements ApplicationRunner {

    private final ParametresTopVenteRepository parametresTopVenteRepository;

    @Override
    public void run(ApplicationArguments args) {
        if (parametresTopVenteRepository.existsById(1L)) {
            return;
        }
        try {
            ParametresTopVente defaults = ParametresTopVente.builder()
                    .id(1L)
                    .seuilVentes(5)
                    .build();
            parametresTopVenteRepository.save(defaults);
            log.info("ParametresTopVente singleton (id=1) initialise (seuil par defaut = 5).");
        } catch (Exception e) {
            log.info("ParametresTopVente deja present (init concurrent) : {}", e.getMessage());
        }
    }
}