package ma.mysuguclientapp.repositories;

import ma.mysuguclientapp.entities.AppSupportConfiguration;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface AppSupportConfigurationRepository extends JpaRepository<AppSupportConfiguration, Long> {
    Optional<AppSupportConfiguration> findByAppKey(String appKey);
}
