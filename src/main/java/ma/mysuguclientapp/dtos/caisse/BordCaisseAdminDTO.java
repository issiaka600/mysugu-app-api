package ma.mysuguclientapp.dtos.caisse;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/** Vue d'ensemble financière pour l'admin */
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class BordCaisseAdminDTO {

    /** Total espèces détenues par tous les livreurs en ce moment */
    private BigDecimal totalEspecesLivreurs;

    /** Total des gains non encore versés aux livreurs */
    private BigDecimal totalGainsDusLivreurs;

    /**
     * Net que les livreurs doivent remettre à la plateforme
     * = totalEspecesLivreurs - totalGainsDusLivreurs
     */
    private BigDecimal netDuParLivreurs;

    /** Total des dettes envers les restaurants (commandes non encore payées) */
    private BigDecimal totalDetteRestaurants;

    /** Livreurs ayant déclenché une alerte plafond ou intervalle */
    private int nombreLivreursEnAlerte;

    /** Détail par livreur */
    private List<CaisseLivreurDTO> detailLivreurs;
}
