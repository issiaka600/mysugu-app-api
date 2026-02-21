package ma.mysuguclientapp.dtos;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class CommandeDTO {
    private Long id;
    private String numeroCommande;
    private UserDTO client;
    private RestaurantDTO restaurant;
    private UserDTO livreur;
    private List<LigneCommandeDTO> lignesCommande;
    private String statut;
    private LocalisationDTO adresseLivraison;
    private BigDecimal montantTotal;
    private BigDecimal fraisLivraison;
    private Integer tempsLivraisonEstime;
    private String commentaire;
    private String methodePaiement;
    private String statutPaiement;
    private LocalDateTime createdAt;
    private LocalDateTime livreeAt;
}