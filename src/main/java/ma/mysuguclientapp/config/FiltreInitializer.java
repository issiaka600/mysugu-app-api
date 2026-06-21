package ma.mysuguclientapp.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.mysuguclientapp.entities.Filtre;
import ma.mysuguclientapp.enumerations.FiltreComportement;
import ma.mysuguclientapp.enumerations.FiltreContexte;
import ma.mysuguclientapp.repositories.FiltreRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/** Sème les filtres par défaut au premier démarrage (idempotent : ne fait rien si la table contient déjà des filtres). */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(20)
public class FiltreInitializer implements CommandLineRunner {

    private final FiltreRepository filtreRepository;

    @Override
    @Transactional
    public void run(String... args) {
        if (filtreRepository.count() > 0) {
            return;
        }
        List<Filtre> defaults = new ArrayList<>();

        // RESTAURANT
        defaults.add(f(FiltreContexte.RESTAURANT, FiltreComportement.TOUS, "Tous", "Flame", 0));
        defaults.add(f(FiltreContexte.RESTAURANT, FiltreComportement.PROMOTIONS, "Promotions", "Zap", 1));
        defaults.add(f(FiltreContexte.RESTAURANT, FiltreComportement.MIEUX_NOTES, "Mieux notés", "Star", 2));
        defaults.add(f(FiltreContexte.RESTAURANT, FiltreComportement.PLUS_PROCHES, "Plus proches", "MapPin", 3));
        defaults.add(f(FiltreContexte.RESTAURANT, FiltreComportement.PLUS_RAPIDES, "Plus rapides", "Clock", 4));
        defaults.add(f(FiltreContexte.RESTAURANT, FiltreComportement.OUVERTS, "Ouverts", "Zap", 5));

        // ALIMENTAIRE
        defaults.add(f(FiltreContexte.ALIMENTAIRE, FiltreComportement.TOUS, "Tous", "Apple", 0));
        defaults.add(f(FiltreContexte.ALIMENTAIRE, FiltreComportement.PROMOTIONS, "Promotions", "Tag", 1));
        defaults.add(f(FiltreContexte.ALIMENTAIRE, FiltreComportement.PLUS_PROCHES, "Plus proches", "MapPin", 2));
        defaults.add(f(FiltreContexte.ALIMENTAIRE, FiltreComportement.PLUS_RAPIDES, "Plus rapides", "Clock", 3));

        // COSMETIQUE
        defaults.add(f(FiltreContexte.COSMETIQUE, FiltreComportement.TOUS, "Tous", "Sparkles", 0));
        defaults.add(f(FiltreContexte.COSMETIQUE, FiltreComportement.PROMOTIONS, "Promotions", "Tag", 1));
        defaults.add(f(FiltreContexte.COSMETIQUE, FiltreComportement.PLUS_PROCHES, "Plus proches", "MapPin", 2));
        defaults.add(f(FiltreContexte.COSMETIQUE, FiltreComportement.PLUS_RAPIDES, "Plus rapides", "Clock", 3));

        filtreRepository.saveAll(defaults);
        log.info("Filtres par défaut semés: {}", defaults.size());
    }

    private Filtre f(FiltreContexte ctx, FiltreComportement comp, String libelle, String icone, int ordre) {
        Filtre filtre = new Filtre();
        filtre.setContexte(ctx);
        filtre.setComportement(comp);
        filtre.setLibelle(libelle);
        filtre.setIcone(icone);
        filtre.setOrdre(ordre);
        filtre.setActif(true);
        return filtre;
    }
}
