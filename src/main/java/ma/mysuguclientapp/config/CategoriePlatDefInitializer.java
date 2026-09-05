package ma.mysuguclientapp.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.mysuguclientapp.entities.CategoriePlatDef;
import ma.mysuguclientapp.repositories.CategoriePlatDefRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * Sème les catégories de plats par défaut (les anciennes valeurs de l'enum CategoriePlat) au
 * premier démarrage. Idempotent : ne fait rien si la table est non vide. Les codes reprennent
 * exactement les noms d'enum historiques afin que les plats existants restent valides.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(21)
public class CategoriePlatDefInitializer implements CommandLineRunner {

    private final CategoriePlatDefRepository repository;

    @Override
    @Transactional
    public void run(String... args) {
        if (repository.count() > 0) {
            return;
        }
        List<CategoriePlatDef> defauts = new ArrayList<>();
        defauts.add(c("ENTREE", "Entrée", 0, "🥗"));
        defauts.add(c("PLAT_PRINCIPAL", "Plat principal", 1, "🍽️"));
        defauts.add(c("ACCOMPAGNEMENT", "Accompagnement", 2, "🍟"));
        defauts.add(c("DESSERT", "Dessert", 3, "🍰"));
        defauts.add(c("BOISSON", "Boisson", 4, "🧃"));
        repository.saveAll(defauts);
        log.info("Catégories de plats par défaut semées: {}", defauts.size());
    }

    private CategoriePlatDef c(String code, String libelle, int ordre, String icone) {
        CategoriePlatDef categorie = new CategoriePlatDef();
        categorie.setCode(code);
        categorie.setLibelle(libelle);
        categorie.setOrdre(ordre);
        categorie.setActif(true);
        categorie.setIcone(icone);
        return categorie;
    }
}