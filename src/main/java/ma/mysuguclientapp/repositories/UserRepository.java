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

    /**
     * Propriétaires d'un établissement d'une verticale donnée.
     *
     * <p>Comme partout où {@code vertical} intervient, la requête est en JPQL et rattrape
     * explicitement {@code IS NULL} pour {@code RESTAURANT} : les établissements créés avant
     * l'ajout de la colonne ne doivent pas disparaître de la liste.
     */
    @Query("SELECT u FROM User u WHERE u.role = ma.mysuguclientapp.enumerations.UserRole.RESTAURANT_OWNER AND " +
           "EXISTS (SELECT 1 FROM Restaurant r WHERE r.owner = u AND " +
           "  ((:vertical = ma.mysuguclientapp.enumerations.Vertical.RESTAURANT AND r.vertical IS NULL) " +
           "   OR r.vertical = :vertical))")
    Page<User> findProprietairesByVertical(@Param("vertical") ma.mysuguclientapp.enumerations.Vertical vertical,
                                           Pageable pageable);

    /**
     * Variante avec recherche. Deux méthodes plutôt qu'un {@code :search IS NULL} dans une seule :
     * passer {@code null} à un paramètre uniquement utilisé dans des fonctions texte empêche
     * PostgreSQL d'en inférer le type et fait échouer la requête sur
     * {@code function lower(bytea) does not exist}. Même précédent que {@code getUsersByRole}.
     */
    @Query("SELECT u FROM User u WHERE u.role = ma.mysuguclientapp.enumerations.UserRole.RESTAURANT_OWNER AND " +
           "EXISTS (SELECT 1 FROM Restaurant r WHERE r.owner = u AND " +
           "  ((:vertical = ma.mysuguclientapp.enumerations.Vertical.RESTAURANT AND r.vertical IS NULL) " +
           "   OR r.vertical = :vertical)) AND " +
           "(LOWER(u.email) LIKE LOWER(CONCAT('%', :search, '%')) " +
           " OR LOWER(u.nom) LIKE LOWER(CONCAT('%', :search, '%')) " +
           " OR LOWER(u.prenom) LIKE LOWER(CONCAT('%', :search, '%')))")
    Page<User> findProprietairesByVerticalAndSearch(@Param("vertical") ma.mysuguclientapp.enumerations.Vertical vertical,
                                                    @Param("search") String search,
                                                    Pageable pageable);

    long countByRole(UserRole role);
    long countByRoleAndIsActive(UserRole role, Boolean isActive);

    // --- Legacy livreur (shim Tiktak) : login/reset par téléphone ---
    List<User> findByTelephoneAndRole(String telephone, UserRole role);
    List<User> findByTelephoneInAndRole(java.util.Collection<String> telephones, UserRole role);
    List<User> findByTelephone(String telephone);
}
