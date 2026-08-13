package ma.mysuguclientapp;

import ma.mysuguclientapp.enumerations.Vertical;
import ma.mysuguclientapp.repositories.CategorieProduitRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

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
}
