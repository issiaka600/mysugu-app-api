package ma.mysuguclientapp;

import ma.mysuguclientapp.entities.Plat;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StockDecrementTest {

    private Plat plat(Boolean disponible, Integer stock) {
        Plat p = new Plat();
        p.setNom("Produit");
        p.setIsAvailable(disponible);
        p.setQuantiteStock(stock);
        return p;
    }

    @Test
    void platSansStockGereSuitLeFlagVendeur() {
        assertThat(plat(true, null).isEffectivementDisponible()).isTrue();
        assertThat(plat(false, null).isEffectivementDisponible()).isFalse();
    }

    @Test
    void stockEpuiseRendIndisponibleMemeSiFlagActif() {
        assertThat(plat(true, 0).isEffectivementDisponible()).isFalse();
    }

    @Test
    void stockPositifEtFlagActifDonneDisponible() {
        assertThat(plat(true, 3).isEffectivementDisponible()).isTrue();
    }

    @Test
    void flagVendeurDesactiveGagneSurLeStock() {
        assertThat(plat(false, 100).isEffectivementDisponible()).isFalse();
    }
}
