package ma.mysuguclientapp.enumerations;

/**
 * Anciennes catégories de plat, remplacées par la table {@code categorie_plat_defs}
 * (configurable depuis le dashboard). Conservée uniquement pour le mapping legacy
 * 6valley (Tiktak-vendor) qui traduit ces codes en identifiants numériques stables.
 * Les codes des défauts en base reprennent exactement ces noms.
 */
public enum CategoriePlat {
    ENTREE,
    PLAT_PRINCIPAL,
    DESSERT,
    BOISSON,
    ACCOMPAGNEMENT
}