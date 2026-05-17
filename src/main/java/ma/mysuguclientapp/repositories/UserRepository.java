package ma.mysuguclientapp.repositories;

import ma.mysuguclientapp.entities.User;
import ma.mysuguclientapp.enumerations.UserRole;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByAppleSub(String appleSub);
    List<User> findByRoleAndIsActive(UserRole role, Boolean isActive);
    List<User> findByRoleAndIsActiveAndLivreurDisponible(UserRole role, Boolean isActive, Boolean livreurDisponible);
    List<User> findByRole(UserRole role);
    Page<User> findByRole(UserRole role, Pageable pageable);

    @Query("SELECT u FROM User u WHERE u.role = :role AND " +
           "(LOWER(u.nom) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(u.prenom) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<User> findByRoleAndSearch(@Param("role") UserRole role, @Param("search") String search, Pageable pageable);

    long countByRole(UserRole role);
    long countByRoleAndIsActive(UserRole role, Boolean isActive);
}
