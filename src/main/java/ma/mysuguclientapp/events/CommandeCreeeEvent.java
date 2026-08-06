package ma.mysuguclientapp.events;

/** Publié dans la transaction de création pour déclencher les effets externes après le commit. */
public record CommandeCreeeEvent(Long commandeId) {
}
