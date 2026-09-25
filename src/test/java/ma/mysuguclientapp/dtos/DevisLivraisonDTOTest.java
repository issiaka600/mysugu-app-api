package ma.mysuguclientapp.dtos;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class DevisLivraisonDTOTest {

    @Test
    void exposeLeSousTotalLesFraisEtLesTotauxOfficiels() {
        DevisLivraisonDTO devis = new DevisLivraisonDTO(
                new BigDecimal("40"), new BigDecimal("15"), BigDecimal.ZERO,
                new BigDecimal("55"), BigDecimal.ZERO);

        assertThat(devis.getSousTotal()).isEqualByComparingTo("40");
        assertThat(devis.getFraisLivraison()).isEqualByComparingTo("15");
        assertThat(devis.getMontantTotal()).isEqualByComparingTo("55");
        assertThat(devis.getMontantRemise()).isEqualByComparingTo("0");
        assertThat(devis.getMontantFinal()).isEqualByComparingTo("55");
    }
}
