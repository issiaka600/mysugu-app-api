package ma.mysuguclientapp.services.interfaces;

import ma.mysuguclientapp.dtos.PlatDTO;
import ma.mysuguclientapp.dtos.RestaurantCreateDTO;
import ma.mysuguclientapp.dtos.RestaurantDTO;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface RestaurantService {
    /**
     * Obtenir tous les restaurants avec filtres et pagination
     */
    Page<RestaurantDTO> getAllRestaurants(Long categorieId, Double latitude, Double longitude,
                                          Double maxDistance, String vertical, Pageable pageable);

    /**
     * Obtenir un restaurant par ID
     */
    RestaurantDTO getRestaurantById(Long id);

    /**
     * Produits d'un établissement, filtrés par rayon (categorieProduit) si fourni.
     * La verticale est déjà déterminée par l'établissement demandé : pas de re-filtrage par verticale.
     */
    List<PlatDTO> getRestaurantPlats(Long id, String categorieProduit);

    /**
     * Rechercher des restaurants par mot-clé, filtré par verticale (convention de
     * {@code RestaurantServiceImpl.parseVertical} : null/absent -> RESTAURANT, "ALL" -> toutes verticales).
     */
    List<RestaurantDTO> searchRestaurants(String keyword, String vertical);

    /**
     * Obtenir les restaurants les mieux notés, filtré par verticale (même convention que ci-dessus).
     */
    List<RestaurantDTO> getTopRatedRestaurants(int limit, String vertical);

    /**
     * Obtenir les restaurants à proximité, filtré par verticale (même convention que ci-dessus).
     */
    List<RestaurantDTO> getNearbyRestaurants(Double latitude, Double longitude, Double radiusKm, String vertical);

    /**
     * Créer un nouveau restaurant
     */
    RestaurantDTO createRestaurant(RestaurantCreateDTO restaurantDTO, MultipartFile logo);

    /**
     * Mettre à jour un restaurant
     */
    RestaurantDTO updateRestaurant(Long id, RestaurantCreateDTO restaurantDTO, MultipartFile logo);

    /** Remplace la bannière de la boutique et retourne ses informations mises à jour. */
    RestaurantDTO updateRestaurantBanner(Long id, MultipartFile banner);

    /**
     * Transfère un établissement à un autre propriétaire.
     *
     * <p>Endpoint dédié plutôt qu'un passage par {@code updateRestaurant} : l'opération touche
     * à l'invariant « un propriétaire, un établissement » dont dépend l'authentification de
     * l'application vendeur. Refuse une cible qui possède déjà un établissement.
     */
    RestaurantDTO reaffecterProprietaire(Long restaurantId, Long nouveauProprietaireId);

    /**
     * Supprimer un restaurant
     */
    void deleteRestaurant(Long id);

    /**
     * Activer/désactiver un restaurant
     */
    RestaurantDTO toggleRestaurantStatus(Long id);

    /**
     * Définir le taux de commission négocié avec un restaurant (admin)
     */
    RestaurantDTO setCommissionPourcentage(Long id, java.math.BigDecimal pourcentage);

    // ===================== Onboarding restaurateur =====================

    /**
     * Soumission d'un restaurant par un restaurateur authentifié (app mobile).
     * Le restaurant est créé au statut {@code EN_ATTENTE} et reste invisible
     * (isActive = false) jusqu'à l'approbation de l'admin.
     */
    RestaurantDTO soumettreOnboarding(String ownerEmail, RestaurantCreateDTO dto,
                                      MultipartFile logo, List<MultipartFile> justificatifs);

    /**
     * Restaurant du restaurateur authentifié, avec son statut d'approbation.
     */
    RestaurantDTO getMonRestaurant(String ownerEmail);

    /**
     * Ajout de justificatifs complémentaires par le restaurateur
     * (typiquement après une demande de complément de l'admin).
     */
    RestaurantDTO ajouterJustificatifs(String ownerEmail, List<MultipartFile> justificatifs);

    // ===================== Revue admin =====================

    /** Liste des restaurants en attente de revue (EN_ATTENTE ou COMPLEMENT_REQUIS). */
    List<RestaurantDTO> getRestaurantsAReviser();

    /** Approuve un restaurant : statut APPROUVE, devient actif et visible. */
    RestaurantDTO approuverRestaurant(Long id, Long adminId);

    /** Rejette un restaurant avec un motif. */
    RestaurantDTO rejeterRestaurant(Long id, Long adminId, String motif);

    /** Demande des justificatifs complémentaires au restaurateur. */
    RestaurantDTO demanderComplement(Long id, Long adminId, String message);

}
