package ma.mysuguclientapp.enumerations;

/**
 * Statut d'une demande de retrait livreur (shim legacy).
 * Correspond aux codes 6valley `withdraw_requests.approved` : 0=EN_ATTENTE, 1=APPROUVE, 2=REFUSE.
 */
public enum StatutRetrait {
    EN_ATTENTE,
    APPROUVE,
    REFUSE
}
