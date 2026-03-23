package ma.mysuguclientapp.services.interfaces;

import ma.mysuguclientapp.dtos.FavoriDTO;

import java.util.List;

public interface FavoriService {
    FavoriDTO ajouterFavori(String accessToken, Long restaurantId);
    void supprimerFavori(String accessToken, Long restaurantId);
    FavoriDTO toggleFavori(String accessToken, Long restaurantId);
    List<FavoriDTO> getMesFavoris(String accessToken);
    boolean estFavori(String accessToken, Long restaurantId);
    Long getNombreFavoris(Long restaurantId);
}
