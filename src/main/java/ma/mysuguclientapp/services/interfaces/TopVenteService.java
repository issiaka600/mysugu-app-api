package ma.mysuguclientapp.services.interfaces;

import ma.mysuguclientapp.dtos.TopVenteConfigDTO;
import ma.mysuguclientapp.dtos.TopVentePlatDTO;
import ma.mysuguclientapp.entities.Plat;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Map;

/**
 * Rubrique « Top des ventes » : seuil de ventes configurable par l'admin et calcul
 * automatique par plat. Le flag manuel {@code Plat.topVente} (choisi par le commerçant)
 * s'ajoute au calcul automatique — il n'est jamais écrasé.
 */
public interface TopVenteService {

    /** Seuil configuré (défaut 5 si le singleton n'est pas encore initialisé). */
    int seuilVentes();

    /** Quantités vendues cumulées par plat (commandes LIVREES uniquement). */
    Map<Long, Long> ventesParPlatLivrees();

    /** Statut effectif : manuel OU ventes livrées ≥ seuil. */
    boolean estTopVente(Long platId, Boolean manuel, Map<Long, Long> ventes);

    TopVenteConfigDTO getConfig();

    TopVenteConfigDTO updateConfig(TopVenteConfigDTO dto);

    /** Page de plats pour le tableau admin, avec ventes cumulées et origine du statut. */
    Page<TopVentePlatDTO> getPlatsTopVentes(Pageable pageable, Boolean topVente);

    TopVentePlatDTO toTopVentePlatDTO(Plat plat, Map<Long, Long> ventes);
}