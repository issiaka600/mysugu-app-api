package ma.mysuguclientapp.enumerations;

/**
 * Mode de calcul d'une commission.
 *
 * <p>Historiquement toutes les commissions de la plateforme étaient exprimées en pourcentage.
 * {@link #FIXE} permet de négocier un montant en devise à la place, ce que réclament les
 * établissements dont le panier moyen est bas : 15 % sur un article à 200 F rapporte moins que
 * le coût de traitement de la commande.</p>
 */
public enum TypeCommission {

    /** La valeur négociée est un pourcentage (ex. 15 → 15 %) appliqué au montant de la ligne. */
    POURCENTAGE,

    /**
     * La valeur négociée est un montant en devise prélevé <b>par article</b> :
     * la commission d'une ligne vaut {@code montantFixe × quantité}, comme un pourcentage
     * croît avec la quantité. Pour la commission sur les frais de livraison, où il n'y a pas
     * de quantité, le montant est prélevé une fois par course.
     */
    FIXE
}
