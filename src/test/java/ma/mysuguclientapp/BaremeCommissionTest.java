package ma.mysuguclientapp;

import ma.mysuguclientapp.enumerations.TypeCommission;
import ma.mysuguclientapp.util.BaremeCommission;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Règles de calcul d'une commission, en pourcentage comme en montant fixe.
 *
 * Test unitaire pur : le barème ne dépend d'aucun contexte Spring, et c'est la brique
 * qu'utilisent les trois commissions de la plateforme.
 */
class BaremeCommissionTest {

    @Test
    @DisplayName("Un pourcentage s'applique au montant de la ligne")
    void pourcentageSurMontant() {
        BaremeCommission bareme = BaremeCommission.resoudre(
                TypeCommission.POURCENTAGE, new BigDecimal("15"), null);

        assertThat(bareme.calculer(new BigDecimal("200.00"), 2))
                .isEqualByComparingTo("30.00");
        assertThat(bareme.getPourcentage()).isEqualByComparingTo("15");
        assertThat(bareme.getMontantFixe()).isNull();
    }

    @Test
    @DisplayName("Un montant fixe est prélevé par article, pas par ligne")
    void montantFixeParArticle() {
        BaremeCommission bareme = BaremeCommission.resoudre(
                TypeCommission.FIXE, null, new BigDecimal("50"));

        // 3 articles à 100 = 300 de ligne, 50 par article ⇒ 150, pas 50.
        assertThat(bareme.calculer(new BigDecimal("300.00"), 3))
                .isEqualByComparingTo("150.00");
        assertThat(bareme.getMontantFixe()).isEqualByComparingTo("50");
        assertThat(bareme.getPourcentage()).isNull();
    }

    @Test
    @DisplayName("Un type absent vaut pourcentage : les données antérieures gardent leur calcul")
    void typeNullVautPourcentage() {
        BaremeCommission bareme = BaremeCommission.resoudre(null, new BigDecimal("10"), null);

        assertThat(bareme.getType()).isEqualTo(TypeCommission.POURCENTAGE);
        assertThat(bareme.calculer(new BigDecimal("500.00"), 1)).isEqualByComparingTo("50.00");
    }

    @Test
    @DisplayName("Un barème mal paramétré ne prélève rien plutôt que de faire échouer la commande")
    void baremeIncompletVautZero() {
        assertThat(BaremeCommission.resoudre(TypeCommission.FIXE, null, null)
                .calculer(new BigDecimal("300.00"), 3)).isEqualByComparingTo("0.00");
        assertThat(BaremeCommission.resoudre(TypeCommission.POURCENTAGE, null, null)
                .calculer(new BigDecimal("300.00"), 3)).isEqualByComparingTo("0.00");
        assertThat(BaremeCommission.neant().calculer(new BigDecimal("300.00"), 3))
                .isEqualByComparingTo("0.00");
    }

    @Test
    @DisplayName("La commission ne peut pas dépasser le montant sur lequel elle porte")
    void montantFixeAberrantEstPlafonne() {
        BaremeCommission bareme = BaremeCommission.resoudre(
                TypeCommission.FIXE, null, new BigDecimal("10000"));

        // Sans plafond, le net vendeur deviendrait négatif et fausserait la comptabilité.
        assertThat(bareme.calculer(new BigDecimal("500.00"), 1)).isEqualByComparingTo("500.00");
    }

    @Test
    @DisplayName("Sur un montant sans quantité, un montant fixe est prélevé une seule fois")
    void surMontantPreleveUneFois() {
        BaremeCommission fixe = BaremeCommission.resoudre(
                TypeCommission.FIXE, null, new BigDecimal("200"));
        assertThat(fixe.calculerSurMontant(new BigDecimal("1000.00"))).isEqualByComparingTo("200.00");

        BaremeCommission pourcentage = BaremeCommission.resoudre(
                TypeCommission.POURCENTAGE, new BigDecimal("15"), null);
        assertThat(pourcentage.calculerSurMontant(new BigDecimal("1000.00"))).isEqualByComparingTo("150.00");
    }

    @Test
    @DisplayName("Les arrondis se font au centime, au plus proche")
    void arrondiAuCentime() {
        BaremeCommission bareme = BaremeCommission.resoudre(
                TypeCommission.POURCENTAGE, new BigDecimal("7.5"), null);

        // 333.33 × 7.5 % = 24.99975 ⇒ 25.00
        assertThat(bareme.calculer(new BigDecimal("333.33"), 1)).isEqualByComparingTo("25.00");
    }

    @Test
    @DisplayName("Une quantité nulle ou absurde ne produit pas de commission négative")
    void quantiteNegativeNeProduitRien() {
        BaremeCommission bareme = BaremeCommission.resoudre(
                TypeCommission.FIXE, null, new BigDecimal("50"));

        assertThat(bareme.calculer(new BigDecimal("300.00"), 0)).isEqualByComparingTo("0.00");
        assertThat(bareme.calculer(new BigDecimal("300.00"), -2)).isEqualByComparingTo("0.00");
    }
}
