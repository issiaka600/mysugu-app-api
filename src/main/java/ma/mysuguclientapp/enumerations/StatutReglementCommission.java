package ma.mysuguclientapp.enumerations;

/**
 * Statut d'un règlement périodique de commission livreur (shim legacy).
 * Cycle 6valley `commission_history.status` : PENDING -> PAID (livreur marque payé) -> VALIDATED (admin).
 */
public enum StatutReglementCommission {
    PENDING,
    PAID,
    VALIDATED
}
