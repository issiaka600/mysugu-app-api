package ma.mysuguclientapp.services.implementations;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.mysuguclientapp.dtos.ContactUrgenceCreateDTO;
import ma.mysuguclientapp.dtos.ContactUrgenceDTO;
import ma.mysuguclientapp.dtos.ContactUrgenceStatutUpdateDTO;
import ma.mysuguclientapp.entities.ContactUrgence;
import ma.mysuguclientapp.entities.Restaurant;
import ma.mysuguclientapp.exceptions.BadRequestException;
import ma.mysuguclientapp.exceptions.ResourceNotFoundException;
import ma.mysuguclientapp.repositories.ContactUrgenceRepository;
import ma.mysuguclientapp.repositories.RestaurantRepository;
import ma.mysuguclientapp.services.interfaces.ContactUrgenceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class ContactUrgenceServiceImpl implements ContactUrgenceService {

    private final ContactUrgenceRepository contactUrgenceRepository;
    private final RestaurantRepository restaurantRepository;

    @Override
    @Transactional(readOnly = true)
    public List<ContactUrgenceDTO> getAll(Long restaurantId) {
        List<ContactUrgence> contacts = contactUrgenceRepository.findAll();
        return contacts.stream()
                .filter(c -> restaurantId == null
                        || (c.getRestaurant() != null && c.getRestaurant().getId().equals(restaurantId)))
                .map(this::toDTO)
                .toList();
    }

    @Override
    public ContactUrgenceDTO create(ContactUrgenceCreateDTO dto) {
        validate(dto);

        ContactUrgence contact = new ContactUrgence();
        contact.setNom(dto.getNom());
        contact.setTelephone(dto.getTelephone());
        contact.setActif(true);
        contact.setRestaurant(resolveRestaurant(dto.getRestaurantId()));

        ContactUrgence saved = contactUrgenceRepository.save(contact);
        log.info("Contact d'urgence '{}' cree (restaurant={})", saved.getNom(), dto.getRestaurantId());
        return toDTO(saved);
    }

    @Override
    public ContactUrgenceDTO update(Long id, ContactUrgenceCreateDTO dto) {
        validate(dto);
        ContactUrgence contact = findContact(id);

        contact.setNom(dto.getNom());
        contact.setTelephone(dto.getTelephone());
        contact.setRestaurant(resolveRestaurant(dto.getRestaurantId()));

        ContactUrgence saved = contactUrgenceRepository.save(contact);
        log.info("Contact d'urgence {} mis a jour", id);
        return toDTO(saved);
    }

    @Override
    public ContactUrgenceDTO updateStatut(Long id, ContactUrgenceStatutUpdateDTO dto) {
        if (dto.getActif() == null) {
            throw new BadRequestException("Le champ 'actif' est requis");
        }
        ContactUrgence contact = findContact(id);
        contact.setActif(dto.getActif());
        ContactUrgence saved = contactUrgenceRepository.save(contact);
        log.info("Contact d'urgence {} - actif={}", id, dto.getActif());
        return toDTO(saved);
    }

    @Override
    public void delete(Long id) {
        ContactUrgence contact = findContact(id);
        contactUrgenceRepository.delete(contact);
        log.info("Contact d'urgence {} supprime", id);
    }

    private void validate(ContactUrgenceCreateDTO dto) {
        if (dto.getNom() == null || dto.getNom().isBlank()) {
            throw new BadRequestException("Le nom du contact est requis");
        }
        if (dto.getTelephone() == null || dto.getTelephone().isBlank()) {
            throw new BadRequestException("Le telephone du contact est requis");
        }
    }

    private Restaurant resolveRestaurant(Long restaurantId) {
        if (restaurantId == null) {
            return null; // contact global plateforme
        }
        return restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new ResourceNotFoundException("Restaurant non trouve avec l'ID: " + restaurantId));
    }

    private ContactUrgence findContact(Long id) {
        return contactUrgenceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Contact d'urgence non trouve avec l'ID: " + id));
    }

    private ContactUrgenceDTO toDTO(ContactUrgence contact) {
        ContactUrgenceDTO dto = new ContactUrgenceDTO();
        dto.setId(contact.getId());
        dto.setNom(contact.getNom());
        dto.setTelephone(contact.getTelephone());
        dto.setActif(contact.getActif());
        dto.setCreatedAt(contact.getCreatedAt());
        if (contact.getRestaurant() != null) {
            dto.setRestaurantId(contact.getRestaurant().getId());
            dto.setRestaurantNom(contact.getRestaurant().getNom());
        }
        return dto;
    }
}
