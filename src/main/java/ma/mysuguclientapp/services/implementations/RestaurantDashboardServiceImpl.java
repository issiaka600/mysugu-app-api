package ma.mysuguclientapp.services.implementations;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.mysuguclientapp.dtos.restaurant.RestaurantDashboardDTO;
import ma.mysuguclientapp.entities.Restaurant;
import ma.mysuguclientapp.enumerations.StatutCommande;
import ma.mysuguclientapp.repositories.CommandeRepository;
import ma.mysuguclientapp.repositories.RestaurantRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class RestaurantDashboardServiceImpl {

    private final RestaurantRepository restaurantRepository;
    private final CommandeRepository commandeRepository;

    @Transactional(readOnly = true)
    public RestaurantDashboardDTO getDashboard(Long restaurantId) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Restaurant introuvable"));

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime debutJour = LocalDate.now().atStartOfDay();
        LocalDateTime debutSemaine = now.minusDays(7);
        LocalDateTime debutMois = now.minusDays(30);
        LocalDateTime debut2099 = LocalDateTime.of(2099, 1, 1, 0, 0);
        LocalDateTime debut1970 = LocalDateTime.of(1970, 1, 1, 0, 0);

        return RestaurantDashboardDTO.builder()
                .restaurantId(restaurantId)
                .restaurantNom(restaurant.getNom())
                .commandesAujourdhui(commandeRepository.countByRestaurantIdAndPeriode(restaurantId, debutJour, now))
                .commandesLivreesAujourdhui(commandeRepository.countByRestaurantIdAndStatutAndPeriode(restaurantId, StatutCommande.LIVREE, debutJour, now))
                .commandesAnnuleesAujourdhui(commandeRepository.countByRestaurantIdAndStatutAndPeriode(restaurantId, StatutCommande.ANNULEE, debutJour, now))
                .chiffreAffairesAujourdhui(commandeRepository.sumChiffreAffairesRestaurant(restaurantId, debutJour, now))
                .commandesSemaine(commandeRepository.countByRestaurantIdAndPeriode(restaurantId, debutSemaine, now))
                .chiffreAffairesSemaine(commandeRepository.sumChiffreAffairesRestaurant(restaurantId, debutSemaine, now))
                .commandesMois(commandeRepository.countByRestaurantIdAndPeriode(restaurantId, debutMois, now))
                .chiffreAffairesMois(commandeRepository.sumChiffreAffairesRestaurant(restaurantId, debutMois, now))
                .appreciation(restaurant.getAppreciation())
                .nombreAvis(restaurant.getNombreAvis())
                .totalCommandes(commandeRepository.countByRestaurantIdAndPeriode(restaurantId, debut1970, debut2099))
                .totalChiffreAffaires(commandeRepository.sumChiffreAffairesRestaurant(restaurantId, debut1970, debut2099))
                .commandesEnCours(commandeRepository.countEnCoursRestaurant(restaurantId))
                .commandesEnPreparation(commandeRepository.countByRestaurantIdAndStatutAndPeriode(restaurantId, StatutCommande.EN_PREPARATION, debutJour, now))
                .build();
    }
}
