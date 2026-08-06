package ma.mysuguclientapp.events;

/** Déclenche la recherche du prochain livreur après commit d'une décision vendeur/livreur. */
public record DispatchLivraisonEvent(Long commandeId) {
}
