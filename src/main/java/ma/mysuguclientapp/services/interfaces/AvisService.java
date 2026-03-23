package ma.mysuguclientapp.services.interfaces;

import ma.mysuguclientapp.dtos.AvisCreateDTO;
import ma.mysuguclientapp.dtos.AvisDTO;
import ma.mysuguclientapp.dtos.ModerationAvisDTO;

import java.util.List;

public interface AvisService {
    AvisDTO soumettreAvis(String accessToken, AvisCreateDTO dto);
    AvisDTO getAvisById(Long id);
    List<AvisDTO> getAvisRestaurant(Long restaurantId);
    List<AvisDTO> getAvisLivreur(Long livreurId);
    List<AvisDTO> getMesAvis(String accessToken);
    AvisDTO moderAvis(Long avisId, ModerationAvisDTO dto);
    List<AvisDTO> getAvisEnAttente();
    void supprimerAvis(Long avisId, String accessToken);
}
