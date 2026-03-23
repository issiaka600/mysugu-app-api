package ma.mysuguclientapp.enumerations;

public enum TypeTransactionCaisse {
    /** Livreur encaisse le paiement client (espèces) */
    COLLECTE_CLIENT,
    /** Livreur paie le restaurant à la récupération du plat */
    PAIEMENT_RESTAURANT,
    /** Livreur remet des espèces à la plateforme (réconciliation) */
    REMISE_PLATEFORME,
    /** Plateforme avance de la liquidité au livreur (fond de caisse) */
    AVANCE_LIVREUR,
    /** Ajustement manuel par l'admin */
    AJUSTEMENT_ADMIN,
    /** Gains versés au livreur lors de la réconciliation */
    VERSEMENT_GAINS
}
