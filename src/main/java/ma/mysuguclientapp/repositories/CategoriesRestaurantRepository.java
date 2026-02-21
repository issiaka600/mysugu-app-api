package ma.mysuguclientapp.repositories;

import ma.mysuguclientapp.entities.CategorieRestaurant;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CategoriesRestaurantRepository extends JpaRepository<CategorieRestaurant, Long> {
    Optional<CategorieRestaurant> findByNom(String nom);
}
