package ma.mysuguclientapp.enumerations;

public enum StatutCommande {
    EN_ATTENTE,
    CONFIRMEE,
    EN_PREPARATION,
    PRETE,
    ASSIGNEE_LIVREUR,
    EN_COURS,
    LIVREE,
    ANNULEE,
    NON_FINALISEE,
    /** Commande livrée puis retournée (retour vendeur/client). Distincte d'ANNULEE. */
    RETOURNEE,
    /** Tentative de livraison ayant échoué (client absent, adresse injoignable, etc.). Distincte d'ANNULEE. */
    ECHEC_LIVRAISON
}
