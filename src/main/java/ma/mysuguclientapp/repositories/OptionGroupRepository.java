package ma.mysuguclientapp.repositories;

import ma.mysuguclientapp.entities.OptionGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OptionGroupRepository extends JpaRepository<OptionGroup, Long> {
    List<OptionGroup> findByPlatIdOrderByOrdreAsc(Long platId);
}
