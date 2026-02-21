package ma.mysuguclientapp.enumerations;

public enum StatutCommande {
    EN_ATTENTE,        // Commande créée mais non confirmée
    CONFIRMEE,         // Confirmée par le restaurant
    EN_PREPARATION,    // En cours de préparation
    EN_COURS,          // En livraison
    LIVREE,            // Livrée avec succès
    ANNULEE,           // Annulée
    NON_FINALISEE      // Panier non finalisé


    // status to display to client : EN_PREPARATION includes {EN_ATTENTE, CONFIRMEE, EN_PREPARATION}, EN_COURS, LIVREE, ANNULEE
}
