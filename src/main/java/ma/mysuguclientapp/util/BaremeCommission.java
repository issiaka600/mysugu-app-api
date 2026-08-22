package ma.mysuguclientapp.util;

import ma.mysuguclientapp.enumerations.TypeCommission;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Barème de commission résolu : un mode de calcul et la valeur qui va avec.
 *
 * <p>Regroupe en un seul endroit la règle « pourcentage ou montant fixe », qui s'applique
 * à trois commissions distinctes (celle négociée avec l'établissement, la commission minimum
 * globale sur les petits articles, et celle prélevée sur les frais de livraison). Sans ça,
 * la même branche serait recopiée trois fois et divergerait à la première évolution.</p>
 *
 * <p>Un barème absent ou incohérent (type FIXE sans montant, POURCENTAGE sans taux) vaut zéro
 * plutôt que de faire échouer la commande : une commission mal paramétrée ne doit pas empêcher
 * un client de commander.</p>
 */
public final class BaremeCommission {

    private static final BigDecimal CENT = new BigDecimal("100");

    private final TypeCommission type;
    private final BigDecimal valeur;

    private BaremeCommission(TypeCommission type, BigDecimal valeur) {
        this.type = type;
        this.valeur = valeur;
    }

    /**
     * Résout le barème à partir des colonnes stockées.
     *
     * @param type         mode négocié ; {@code null} vaut POURCENTAGE (données antérieures)
     * @param pourcentage  taux en % utilisé en mode POURCENTAGE
     * @param montantFixe  montant unitaire en devise utilisé en mode FIXE
     */
    public static BaremeCommission resoudre(TypeCommission type, BigDecimal pourcentage, BigDecimal montantFixe) {
        if (type == TypeCommission.FIXE) {
            return new BaremeCommission(TypeCommission.FIXE, montantFixe != null ? montantFixe : BigDecimal.ZERO);
        }
        return new BaremeCommission(TypeCommission.POURCENTAGE, pourcentage != null ? pourcentage : BigDecimal.ZERO);
    }

    /** Barème neutre : ne prélève rien. */
    public static BaremeCommission neant() {
        return new BaremeCommission(TypeCommission.POURCENTAGE, BigDecimal.ZERO);
    }

    public TypeCommission getType() {
        return type;
    }

    /** Le taux en %, ou {@code null} si le barème est un montant fixe. */
    public BigDecimal getPourcentage() {
        return type == TypeCommission.POURCENTAGE ? valeur : null;
    }

    /** Le montant unitaire, ou {@code null} si le barème est un pourcentage. */
    public BigDecimal getMontantFixe() {
        return type == TypeCommission.FIXE ? valeur : null;
    }

    /**
     * Commission due sur une ligne.
     *
     * @param montantLigne montant de la ligne, options comprises
     * @param quantite     nombre d'articles ; le montant fixe est prélevé par article
     * @return la commission, arrondie au centime, jamais négative ni supérieure au montant de la ligne
     */
    public BigDecimal calculer(BigDecimal montantLigne, int quantite) {
        BigDecimal base = montantLigne != null ? montantLigne : BigDecimal.ZERO;
        BigDecimal brute = type == TypeCommission.FIXE
                ? valeur.multiply(BigDecimal.valueOf(Math.max(quantite, 0)))
                : base.multiply(valeur).divide(CENT, 2, RoundingMode.HALF_UP);
        return plafonner(brute, base);
    }

    /**
     * Commission due sur un montant sans quantité (frais de livraison) : un montant fixe
     * est prélevé une seule fois.
     */
    public BigDecimal calculerSurMontant(BigDecimal montant) {
        return calculer(montant, 1);
    }

    /**
     * Un montant fixe mal paramétré (10 000 F de commission sur un article à 500 F) rendrait
     * le net vendeur négatif et fausserait toute la comptabilité en aval. On le borne au
     * montant sur lequel il porte.
     */
    private static BigDecimal plafonner(BigDecimal commission, BigDecimal base) {
        BigDecimal arrondie = commission.setScale(2, RoundingMode.HALF_UP);
        if (arrondie.signum() < 0) {
            return BigDecimal.ZERO;
        }
        return arrondie.min(base.setScale(2, RoundingMode.HALF_UP));
    }
}
