package ma.mysuguclientapp.enumerations;

/**
 * Statut d'approbation d'un restaurant dans le workflow d'onboarding restaurateur.
 *
 * <p>Flux : un restaurateur s'inscrit puis soumet son restaurant ({@link #EN_ATTENTE}).
 * L'admin peut alors l'approuver ({@link #APPROUVE}), le rejeter ({@link #REJETE})
 * ou demander des justificatifs complémentaires ({@link #COMPLEMENT_REQUIS}).</p>
 */
public enum StatutRestaurant {
    /** Soumis par le restaurateur, en attente de revue par l'admin. */
    EN_ATTENTE,
    /** L'admin a demandé des justificatifs/éléments supplémentaires. */
    COMPLEMENT_REQUIS,
    /** Validé par l'admin : le restaurant peut être activé et devient visible. */
    APPROUVE,
    /** Refusé par l'admin. */
    REJETE
}
