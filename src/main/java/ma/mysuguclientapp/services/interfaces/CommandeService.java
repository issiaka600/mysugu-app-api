package ma.mysuguclientapp.services.interfaces;

import ma.mysuguclientapp.dtos.CommandeCreateDTO;
import ma.mysuguclientapp.dtos.CommandeDTO;
import ma.mysuguclientapp.dtos.CommandeUpdateStatusDTO;
import ma.mysuguclientapp.enumerations.StatutCommande;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Map;

public interface CommandeService {
    Page<CommandeDTO> getAllCommandes(Long clientId, Long restaurantId, StatutCommande statutCommande, Pageable pageable);
    CommandeDTO getCommandeById(Long id);
    CommandeDTO getCommandeByNumero(String numeroCommande);
    List<CommandeDTO> getCommandesByClient(Long clientId);
    List<CommandeDTO> getCommandesByRestaurant(Long restaurantId);
    List<CommandeDTO> getCommandesByLivreur(Long livreurId);
    List<CommandeDTO> getCommandesEnCours();
    CommandeDTO createCommande(CommandeCreateDTO commandeCreateDTO);
    CommandeDTO updateCommandeStatus(Long id, CommandeUpdateStatusDTO commandeUpdateStatusDTO);
    CommandeDTO assignLivreur(Long id, Long livreurId);
    CommandeDTO cancelCommande(Long id);
//    CommandeDTO getCommandeTracking(Long id);
    Map<String, Object> getCommandeTracking(Long id);
}
