package ma.mysuguclientapp.dtos.admin;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Vue détaillée d'un livreur pour l'admin : identité + statistiques + argent.
 * Complète le UserDTO générique (trop pauvre pour de la gestion de flotte).
 */
@Data
public class LivreurDetailDTO {
    private Long id;
    private String nom;
    private String prenom;
    private String email;
    private String telephone;
    private String avatar;
    private Boolean isActive;
    private Boolean livreurDisponible;
    private LocalDateTime createdAt;

    // Statistiques
    private Long nombreLivraisons;
    private Long commandesEnCours;
    private Double noteMoyenne;

    // Argent (mêmes formules que le bloc /info legacy, §7 techspec)
    private BigDecimal soldeActuel;          // current_balance
    private BigDecimal especeEnCaisse;       // cash_in_hand
    private BigDecimal montantEnAttenteRetrait; // pending_withdraw
    private BigDecimal totalRetireApprouve;  // total_withdraw
}