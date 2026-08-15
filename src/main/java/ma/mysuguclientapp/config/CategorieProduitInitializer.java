package ma.mysuguclientapp.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.mysuguclientapp.entities.CategorieProduit;
import ma.mysuguclientapp.enumerations.Vertical;
import ma.mysuguclientapp.repositories.CategorieProduitRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/** Sème les rayons par défaut au premier démarrage. Idempotent : ne fait rien si la table est non vide. */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(21)
public class CategorieProduitInitializer implements CommandLineRunner {

    private final CategorieProduitRepository repository;

    @Override
    @Transactional
    public void run(String... args) {
        if (repository.count() > 0) {
            return;
        }
        List<CategorieProduit> defauts = new ArrayList<>();
        defauts.add(c(Vertical.ALIMENTAIRE, "fruits_legumes", "Fruits & légumes", 0));
        defauts.add(c(Vertical.ALIMENTAIRE, "epicerie", "Épicerie", 1));
        defauts.add(c(Vertical.ALIMENTAIRE, "boissons", "Boissons", 2));
        defauts.add(c(Vertical.ALIMENTAIRE, "produits_frais", "Produits frais", 3));
        defauts.add(c(Vertical.COSMETIQUE, "soin_visage", "Soin visage", 0));
        defauts.add(c(Vertical.COSMETIQUE, "soin_corps", "Soin corps", 1));
        defauts.add(c(Vertical.COSMETIQUE, "parfums", "Parfums", 2));
        defauts.add(c(Vertical.COSMETIQUE, "cheveux", "Cheveux", 3));
        repository.saveAll(defauts);
        log.info("Rayons par défaut semés: {}", defauts.size());
    }

    private CategorieProduit c(Vertical vertical, String code, String libelle, int ordre) {
        CategorieProduit categorie = new CategorieProduit();
        categorie.setVertical(vertical);
        categorie.setCode(code);
        categorie.setLibelle(libelle);
        categorie.setOrdre(ordre);
        categorie.setActif(true);
        return categorie;
    }
}
