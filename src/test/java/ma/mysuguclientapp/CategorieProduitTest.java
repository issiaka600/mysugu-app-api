package ma.mysuguclientapp;

import ma.mysuguclientapp.enumerations.Vertical;
import ma.mysuguclientapp.repositories.CategorieProduitRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class CategorieProduitTest {

    @Autowired CategorieProduitRepository repository;

    @Test
    void seedContientLesRayonsAlimentaires() {
        var rayons = repository.findByVerticalAndActifTrueOrderByOrdreAsc(Vertical.ALIMENTAIRE);
        assertThat(rayons).extracting("code")
                .containsExactly("fruits_legumes", "epicerie", "boissons", "produits_frais");
        assertThat(rayons).extracting("libelle").first().isEqualTo("Fruits & légumes");
    }

    @Test
    void seedContientLesRayonsCosmetiques() {
        var rayons = repository.findByVerticalAndActifTrueOrderByOrdreAsc(Vertical.COSMETIQUE);
        assertThat(rayons).extracting("code")
                .containsExactly("soin_visage", "soin_corps", "parfums", "cheveux");
    }

    @Test
    void aucunRayonPourLaVerticaleRestaurant() {
        assertThat(repository.findByVerticalAndActifTrueOrderByOrdreAsc(Vertical.RESTAURANT)).isEmpty();
    }

    @Autowired ma.mysuguclientapp.services.interfaces.CategorieProduitService service;

    @Test
    void listerPublicRenvoieValueEtLabel() {
        var options = service.listerPublic("ALIMENTAIRE");
        assertThat(options).extracting("value")
                .containsExactly("fruits_legumes", "epicerie", "boissons", "produits_frais");
        assertThat(options.get(0).getLabel()).isEqualTo("Fruits & légumes");
    }

    @Test
    void listerPublicEstInsensibleALaCasse() {
        assertThat(service.listerPublic("alimentaire")).hasSize(4);
    }

    @Test
    void listerPublicRenvoieVideSiVerticaleInconnue() {
        assertThat(service.listerPublic("PHARMACIE")).isEmpty();
    }

    @Test
    @Transactional
    void creerPuisModifierPuisDesactiverUnRayon() {
        // Défensif : si une exécution antérieure à l'introduction de @Transactional a laissé
        // une ligne "maquillage" désactivée en base (existsByVerticalAndCode ne filtre pas sur
        // actif), on la retire avant de créer, pour que le test reste rejouable quel que soit
        // l'état initial de la base.
        repository.findByVerticalAndCode(Vertical.COSMETIQUE, "maquillage")
                .ifPresent(repository::delete);

        var creation = new ma.mysuguclientapp.dtos.CategorieProduitDTO();
        creation.setVertical("COSMETIQUE");
        creation.setCode("maquillage");
        creation.setLibelle("Maquillage");
        creation.setOrdre(9);
        var cree = service.creer(creation);
        assertThat(cree.getId()).isNotNull();
        assertThat(cree.getActif()).isTrue();

        var modification = new ma.mysuguclientapp.dtos.CategorieProduitDTO();
        modification.setLibelle("Maquillage & teint");
        assertThat(service.modifier(cree.getId(), modification).getLibelle())
                .isEqualTo("Maquillage & teint");

        service.supprimer(cree.getId());
        assertThat(service.listerPublic("COSMETIQUE")).extracting("value").doesNotContain("maquillage");
        assertThat(service.listerAdmin("COSMETIQUE")).extracting("code").contains("maquillage");
    }

    @Test
    void refuseUnCodeDejaPrisDansLaMemeVerticale() {
        var doublon = new ma.mysuguclientapp.dtos.CategorieProduitDTO();
        doublon.setVertical("ALIMENTAIRE");
        doublon.setCode("epicerie");
        doublon.setLibelle("Épicerie bis");
        org.junit.jupiter.api.Assertions.assertThrows(
                ma.mysuguclientapp.exceptions.BadRequestException.class,
                () -> service.creer(doublon));
    }
}
