package ma.mysuguclientapp.enumerations;

public enum StatutDetteRestaurant {
    /** Dette créée, pas encore payée */
    EN_ATTENTE,
    /** Livreur a payé le restaurant directement (mode PAR_COMMANDE) */
    PAYE_PAR_LIVREUR,
    /** Incluse dans un virement/paiement groupé périodique */
    INCLUS_DANS_VIREMENT,
    /** Payée par la plateforme */
    PAYE
}
