package ma.mysuguclientapp.repositories;

import ma.mysuguclientapp.entities.MessageContact;
import ma.mysuguclientapp.enumerations.StatutMessageContact;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MessageContactRepository extends JpaRepository<MessageContact, Long> {

    Page<MessageContact> findByStatutOrderByCreatedAtDesc(StatutMessageContact statut, Pageable pageable);

    long countByStatut(StatutMessageContact statut);

    @Query("SELECT m FROM MessageContact m WHERE " +
           "(:statut IS NULL OR m.statut = :statut) AND (" +
           "LOWER(m.nom)     LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(m.email)   LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(m.sujet)   LIKE LOWER(CONCAT('%', :q, '%')) OR " +
           "LOWER(m.message) LIKE LOWER(CONCAT('%', :q, '%'))) " +
           "ORDER BY m.createdAt DESC")
    Page<MessageContact> search(@Param("q") String q,
                                @Param("statut") StatutMessageContact statut,
                                Pageable pageable);
}
