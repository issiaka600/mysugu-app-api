package ma.mysuguclientapp.enumerations;

public enum ModePaiementRestaurant {
    /**
     * La plateforme paie le restaurant à intervalles réguliers (ex: chaque semaine).
     * Le livreur encaisse tout chez le client et remet la totalité à la plateforme.
     */
    PERIODIQUE,
    /**
     * Le livreur paie directement le restaurant lors de la récupération du plat.
     * La plateforme ne lui verse que ses frais de livraison nets.
     */
    PAR_COMMANDE
}
