package ma.mysuguclientapp.services.interfaces;

import ma.mysuguclientapp.dtos.CommandeCreateDTO;
import ma.mysuguclientapp.dtos.CommandeDTO;
import ma.mysuguclientapp.dtos.DevisLivraisonDTO;
import ma.mysuguclientapp.dtos.CommandeUpdateStatusDTO;
import ma.mysuguclientapp.dtos.AssignThirdPartyDeliveryDTO;
import ma.mysuguclientapp.dtos.UpdatePaymentStatusDTO;
import ma.mysuguclientapp.dtos.DeliveryChargeDateUpdateDTO;
import ma.mysuguclientapp.dtos.OrderWiseProductUploadDTO;
import ma.mysuguclientapp.enumerations.StatutCommande;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

public interface CommandeService {
    /**
     * Liste filtrée des commandes. Chaque critère à {@code null} est neutre.
     * {@code vertical} filtre sur la verticale de l'établissement, en traitant
     * {@code NULL} en base comme {@code RESTAURANT}.
     */
    Page<CommandeDTO> getAllCommandes(Long clientId, Long restaurantId, StatutCommande statutCommande,
                                      ma.mysuguclientapp.enumerations.Vertical vertical, Pageable pageable);
    CommandeDTO getCommandeById(Long id);
    CommandeDTO getCommandeByNumero(String numeroCommande);
    List<CommandeDTO> getCommandesByClient(Long clientId);
    List<CommandeDTO> getCommandesByRestaurant(Long restaurantId);
    List<CommandeDTO> getCommandesByLivreur(Long livreurId);
    List<CommandeDTO> getCommandesEnCours();
    CommandeDTO createCommande(CommandeCreateDTO commandeCreateDTO);
    /**
     * Devis (aperçu) des frais de livraison et de la remise automatique restaurant, calculé
     * avec exactement la même logique que createCommande — sans créer de commande ni
     * incrémenter le compteur d'utilisation de la promotion. Permet au panier client d'afficher
     * AVANT validation le même total que celui facturé APRÈS (correction PDF "Problème de
     * montant total").
     */
    DevisLivraisonDTO calculerDevis(Long restaurantId, Double latitude, Double longitude, BigDecimal sousTotal);
    CommandeDTO updateCommandeStatus(Long id, CommandeUpdateStatusDTO commandeUpdateStatusDTO);

    /**
     * Variante utilisée par les endpoints authentifiés (vendeur) qui connaissent l'auteur du
     * changement : {@code initiatorUserId} sert à (1) ne pas renvoyer à cette personne une
     * notification de statut qui ne fait que confirmer sa propre action ("messages inutiles",
     * correction PDF "Notifications de changement de statuts"), et (2) tracer l'auteur d'une
     * annulation (canceled_by) quand le nouveau statut est ANNULEE.
     */
    CommandeDTO updateCommandeStatus(Long id, CommandeUpdateStatusDTO commandeUpdateStatusDTO, Long initiatorUserId);
    CommandeDTO assignLivreur(Long id, Long livreurId);
    CommandeDTO cancelCommande(Long id);
    CommandeDTO cancelCommandeByCustomer(Long id, String customerEmail, String reason);
    //    CommandeDTO getCommandeTracking(Long id);
    Map<String, Object> getCommandeTracking(Long id);

    // Livraison par un tiers (coursier externe hors plateforme)
    CommandeDTO assignThirdPartyDelivery(Long id, AssignThirdPartyDeliveryDTO dto);

    // Mise à jour du statut de paiement
    CommandeDTO updatePaymentStatus(Long id, UpdatePaymentStatusDTO dto);

    // Mise à jour des frais de livraison et/ou de la date programmée
    CommandeDTO updateDeliveryChargeAndDate(Long id, DeliveryChargeDateUpdateDTO dto);

    // Déclaration des quantités réellement livrées (version simplifiée du POS legacy)
    CommandeDTO uploadOrderWiseProducts(Long id, OrderWiseProductUploadDTO dto);
}
