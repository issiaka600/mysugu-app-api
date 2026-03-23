package ma.mysuguclientapp.services.implementations;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.mysuguclientapp.config.security.JwtTokenProvider;
import ma.mysuguclientapp.dtos.FavoriDTO;
import ma.mysuguclientapp.entities.Favori;
import ma.mysuguclientapp.entities.Restaurant;
import ma.mysuguclientapp.entities.User;
import ma.mysuguclientapp.exceptions.BadRequestException;
import ma.mysuguclientapp.exceptions.ResourceNotFoundException;
import ma.mysuguclientapp.repositories.FavoriRepository;
import ma.mysuguclientapp.repositories.RestaurantRepository;
import ma.mysuguclientapp.repositories.UserRepository;
import ma.mysuguclientapp.services.interfaces.FavoriService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class FavoriServiceImpl implements FavoriService {

    private final FavoriRepository favoriRepository;
    private final UserRepository userRepository;
    private final RestaurantRepository restaurantRepository;
    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public FavoriDTO ajouterFavori(String accessToken, Long restaurantId) {
        User user = getUserFromToken(accessToken);
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant non trouvé"));

        if (favoriRepository.existsByUserIdAndRestaurantId(user.getId(), restaurantId)) {
            throw new BadRequestException("Ce restaurant est déjà dans vos favoris");
        }

        Favori favori = Favori.builder().user(user).restaurant(restaurant).build();
        return toDTO(favoriRepository.save(favori));
    }

    @Override
    public void supprimerFavori(String accessToken, Long restaurantId) {
        User user = getUserFromToken(accessToken);
        if (!favoriRepository.existsByUserIdAndRestaurantId(user.getId(), restaurantId)) {
            throw new ResourceNotFoundException("Ce restaurant n'est pas dans vos favoris");
        }
        favoriRepository.deleteByUserIdAndRestaurantId(user.getId(), restaurantId);
    }

    @Override
    public FavoriDTO toggleFavori(String accessToken, Long restaurantId) {
        User user = getUserFromToken(accessToken);

        Optional<Favori> existing = favoriRepository.findByUserIdAndRestaurantId(user.getId(), restaurantId);
        if (existing.isPresent()) {
            favoriRepository.delete(existing.get());
            return null; // supprimé
        }
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant non trouvé"));
        return toDTO(favoriRepository.save(Favori.builder().user(user).restaurant(restaurant).build()));
    }

    @Override
    @Transactional(readOnly = true)
    public List<FavoriDTO> getMesFavoris(String accessToken) {
        User user = getUserFromToken(accessToken);
        return favoriRepository.findByUserIdOrderByCreatedAtDesc(user.getId())
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean estFavori(String accessToken, Long restaurantId) {
        User user = getUserFromToken(accessToken);
        return favoriRepository.existsByUserIdAndRestaurantId(user.getId(), restaurantId);
    }

    @Override
    @Transactional(readOnly = true)
    public Long getNombreFavoris(Long restaurantId) {
        return favoriRepository.countByRestaurantId(restaurantId);
    }

    private User getUserFromToken(String bearerToken) {
        String jwt = bearerToken != null && bearerToken.startsWith("Bearer ")
                ? bearerToken.substring(7) : bearerToken;
        String email = jwtTokenProvider.getEmailFromToken(jwt);
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur non trouvé"));
    }

    private FavoriDTO toDTO(Favori f) {
        return FavoriDTO.builder()
                .id(f.getId())
                .userId(f.getUser().getId())
                .restaurantId(f.getRestaurant().getId())
                .restaurantNom(f.getRestaurant().getNom())
                .restaurantLogoUrl(f.getRestaurant().getLogoUrl())
                .restaurantAppreciation(f.getRestaurant().getAppreciation())
                .restaurantIsActive(f.getRestaurant().getIsActive())
                .createdAt(f.getCreatedAt())
                .build();
    }
}
