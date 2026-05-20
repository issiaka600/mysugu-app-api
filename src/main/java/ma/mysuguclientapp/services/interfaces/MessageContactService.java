package ma.mysuguclientapp.services.interfaces;

import ma.mysuguclientapp.dtos.MessageContactCreateDTO;
import ma.mysuguclientapp.dtos.MessageContactDTO;
import ma.mysuguclientapp.dtos.MessageContactReponseDTO;
import ma.mysuguclientapp.dtos.MessageContactStatsDTO;
import ma.mysuguclientapp.dtos.MessageContactStatutUpdateDTO;
import ma.mysuguclientapp.enumerations.StatutMessageContact;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface MessageContactService {

    MessageContactDTO soumettre(MessageContactCreateDTO dto, String ip, String userAgent);

    Page<MessageContactDTO> rechercher(String q, StatutMessageContact statut, Pageable pageable);

    MessageContactDTO getById(Long id);

    MessageContactDTO updateStatut(Long id, MessageContactStatutUpdateDTO dto);

    MessageContactDTO repondre(Long id, MessageContactReponseDTO dto, String adminEmail);

    void supprimer(Long id);

    MessageContactStatsDTO stats();
}
