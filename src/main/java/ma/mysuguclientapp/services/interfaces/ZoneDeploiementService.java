package ma.mysuguclientapp.services.interfaces;

import ma.mysuguclientapp.dtos.ZoneDeploiementDTO;

import java.util.List;

public interface ZoneDeploiementService {

    /** Crée une nouvelle zone de déploiement (admin). */
    ZoneDeploiementDTO creerZone(ZoneDeploiementDTO dto);

    /** Met à jour une zone existante. */
    ZoneDeploiementDTO modifierZone(Long id, ZoneDeploiementDTO dto);

    /** Active ou désactive une zone. */
    ZoneDeploiementDTO toggleZone(Long id, boolean actif);

    /** Supprime une zone (uniquement si aucun restaurant ne l'utilise). */
    void supprimerZone(Long id);

    /** Retourne toutes les zones (actives + inactives) — vue admin. */
    List<ZoneDeploiementDTO> getAllZones();

    /** Retourne uniquement les zones actives — vue publique / sélecteur restaurant. */
    List<ZoneDeploiementDTO> getZonesActives();

    /** Retourne une zone par son identifiant. */
    ZoneDeploiementDTO getZone(Long id);
}
