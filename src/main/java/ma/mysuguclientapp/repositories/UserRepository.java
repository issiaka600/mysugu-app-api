package ma.mysuguclientapp.repositories;

import ma.mysuguclientapp.entities.User;
import ma.mysuguclientapp.enumerations.UserRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    List<User> findByRoleAndIsActive(UserRole role, Boolean isActive);
    List<User> findByRole(UserRole role);
}
