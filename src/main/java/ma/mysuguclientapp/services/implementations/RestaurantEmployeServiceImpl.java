package ma.mysuguclientapp.services.implementations;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.mysuguclientapp.dtos.restaurant.AjouterEmployeDTO;
import ma.mysuguclientapp.dtos.restaurant.RestaurantEmployeDTO;
import ma.mysuguclientapp.entities.Restaurant;
import ma.mysuguclientapp.entities.RestaurantEmploye;
import ma.mysuguclientapp.entities.User;
import ma.mysuguclientapp.repositories.RestaurantEmployeRepository;
import ma.mysuguclientapp.repositories.RestaurantRepository;
import ma.mysuguclientapp.repositories.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RestaurantEmployeServiceImpl {

    private final RestaurantEmployeRepository employeRepository;
    private final RestaurantRepository restaurantRepository;
    private final UserRepository userRepository;

    @Transactional
    public RestaurantEmployeDTO ajouterEmploye(Long restaurantId, AjouterEmployeDTO dto) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Restaurant introuvable"));
        User user = userRepository.findById(dto.getUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Utilisateur introuvable"));

        if (employeRepository.existsByRestaurantIdAndUserId(restaurantId, dto.getUserId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cet employé est déjà associé au restaurant");
        }

        RestaurantEmploye employe = RestaurantEmploye.builder()
                .restaurant(restaurant)
                .user(user)
                .poste(dto.getPoste())
                .isActive(true)
                .build();

        return toDTO(employeRepository.save(employe));
    }

    @Transactional(readOnly = true)
    public List<RestaurantEmployeDTO> getEmployes(Long restaurantId) {
        return employeRepository.findByRestaurantIdAndIsActiveTrue(restaurantId)
                .stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Transactional
    public void retirerEmploye(Long restaurantId, Long employeId) {
        RestaurantEmploye employe = employeRepository.findById(employeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Employé introuvable"));
        if (!employe.getRestaurant().getId().equals(restaurantId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cet employé n'appartient pas à ce restaurant");
        }
        employe.setIsActive(false);
        employeRepository.save(employe);
    }

    private RestaurantEmployeDTO toDTO(RestaurantEmploye e) {
        RestaurantEmployeDTO dto = new RestaurantEmployeDTO();
        dto.setId(e.getId());
        dto.setPoste(e.getPoste());
        dto.setIsActive(e.getIsActive());
        dto.setCreatedAt(e.getCreatedAt());
        if (e.getRestaurant() != null) {
            dto.setRestaurantId(e.getRestaurant().getId());
            dto.setRestaurantNom(e.getRestaurant().getNom());
        }
        if (e.getUser() != null) {
            dto.setUserId(e.getUser().getId());
            dto.setUserNom(e.getUser().getNom());
            dto.setUserPrenom(e.getUser().getPrenom());
            dto.setUserEmail(e.getUser().getEmail());
        }
        return dto;
    }
}
