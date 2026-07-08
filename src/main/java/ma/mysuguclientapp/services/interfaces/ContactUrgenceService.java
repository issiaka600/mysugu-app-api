package ma.mysuguclientapp.services.interfaces;

import ma.mysuguclientapp.dtos.ContactUrgenceCreateDTO;
import ma.mysuguclientapp.dtos.ContactUrgenceDTO;
import ma.mysuguclientapp.dtos.ContactUrgenceStatutUpdateDTO;

import java.util.List;

public interface ContactUrgenceService {
    List<ContactUrgenceDTO> getAll(Long restaurantId);
    ContactUrgenceDTO create(ContactUrgenceCreateDTO dto);
    ContactUrgenceDTO update(Long id, ContactUrgenceCreateDTO dto);
    ContactUrgenceDTO updateStatut(Long id, ContactUrgenceStatutUpdateDTO dto);
    void delete(Long id);
}