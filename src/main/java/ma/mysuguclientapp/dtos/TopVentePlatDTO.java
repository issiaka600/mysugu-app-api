package ma.mysuguclientapp.dtos;

import lombok.Data;

import java.math.BigDecimal;

/**
 * Ligne du tableau admin « Top des ventes » : un plat, ses ventes livrées cumulées et
 * l'origine de son statut (manuel choisi par le commerçant, automatique via le seuil, les deux).
 */
@Data
public class TopVentePlatDTO {
    private Long id;
    private String nom;
    private BigDecimal prix;
    private Long restaurantId;
    private String restaurantNom;
    /** Statut effectif : manuel OU dépassement du seuil. */
    private Boolean topVente;
    /** Flag choisi manuellement par le commerçant (Plat.topVente). */
    private Boolean topVenteManuel;
    /** Automatique : ventes livrées ≥ seuil configuré. */
    private Boolean topVenteAuto;
    /** Quantités vendues cumulées (commandes LIVREES uniquement). */
    private Long nombreVentes;
}