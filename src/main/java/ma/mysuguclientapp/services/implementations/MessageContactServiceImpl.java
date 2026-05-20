package ma.mysuguclientapp.services.implementations;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.mysuguclientapp.dtos.MessageContactCreateDTO;
import ma.mysuguclientapp.dtos.MessageContactDTO;
import ma.mysuguclientapp.dtos.MessageContactReponseDTO;
import ma.mysuguclientapp.dtos.MessageContactStatsDTO;
import ma.mysuguclientapp.dtos.MessageContactStatutUpdateDTO;
import ma.mysuguclientapp.entities.MessageContact;
import ma.mysuguclientapp.enumerations.StatutMessageContact;
import ma.mysuguclientapp.exceptions.BadRequestException;
import ma.mysuguclientapp.exceptions.ResourceNotFoundException;
import ma.mysuguclientapp.repositories.MessageContactRepository;
import ma.mysuguclientapp.services.interfaces.MessageContactService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class MessageContactServiceImpl implements MessageContactService {

    private final MessageContactRepository repository;
    private final EmailService emailService;

    @Override
    public MessageContactDTO soumettre(MessageContactCreateDTO dto, String ip, String userAgent) {
        MessageContact saved = repository.save(MessageContact.builder()
                .nom(dto.getNom().trim())
                .email(dto.getEmail().trim().toLowerCase())
                .telephone(dto.getTelephone())
                .sujet(dto.getSujet().trim())
                .message(dto.getMessage().trim())
                .statut(StatutMessageContact.NOUVEAU)
                .ipOrigin(ip)
                .userAgent(truncate(userAgent, 500))
                .build());

        log.info("Nouveau message de contact #{} de {} - sujet: {}",
                saved.getId(), saved.getEmail(), saved.getSujet());

        try {
            emailService.envoyerAccuseReceptionContact(saved.getEmail(), saved.getNom(), saved.getSujet());
        } catch (Exception e) {
            log.warn("Accuse de reception non envoye pour le message {}: {}", saved.getId(), e.getMessage());
        }

        return toDTO(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<MessageContactDTO> rechercher(String q, StatutMessageContact statut, Pageable pageable) {
        Page<MessageContact> page = (q == null || q.isBlank())
                ? (statut == null
                        ? repository.findAll(pageable)
                        : repository.findByStatutOrderByCreatedAtDesc(statut, pageable))
                : repository.search(q.trim(), statut, pageable);
        return page.map(this::toDTO);
    }

    @Override
    @Transactional(readOnly = true)
    public MessageContactDTO getById(Long id) {
        return toDTO(find(id));
    }

    @Override
    public MessageContactDTO updateStatut(Long id, MessageContactStatutUpdateDTO dto) {
        MessageContact m = find(id);
        m.setStatut(dto.getStatut());
        return toDTO(repository.save(m));
    }

    @Override
    public MessageContactDTO repondre(Long id, MessageContactReponseDTO dto, String adminEmail) {
        if (dto.getReponse() == null || dto.getReponse().isBlank()) {
            throw new BadRequestException("La reponse ne peut pas etre vide");
        }
        MessageContact m = find(id);
        m.setReponse(dto.getReponse().trim());
        m.setReponduPar(adminEmail);
        m.setReponduAt(LocalDateTime.now());
        m.setStatut(StatutMessageContact.REPONDU);
        MessageContact saved = repository.save(m);

        if (dto.isEnvoyerEmail()) {
            try {
                emailService.envoyerReponseContact(saved.getEmail(), saved.getNom(), saved.getSujet(), saved.getReponse());
            } catch (Exception e) {
                log.warn("Reponse #{} non envoyee par email: {}", saved.getId(), e.getMessage());
            }
        }
        log.info("Reponse #{} envoyee a {} par {}", saved.getId(), saved.getEmail(), adminEmail);
        return toDTO(saved);
    }

    @Override
    public void supprimer(Long id) {
        repository.delete(find(id));
    }

    @Override
    @Transactional(readOnly = true)
    public MessageContactStatsDTO stats() {
        long total     = repository.count();
        long nouveaux  = repository.countByStatut(StatutMessageContact.NOUVEAU);
        long lus       = repository.countByStatut(StatutMessageContact.LU);
        long repondus  = repository.countByStatut(StatutMessageContact.REPONDU);
        long archives  = repository.countByStatut(StatutMessageContact.ARCHIVE);
        return MessageContactStatsDTO.builder()
                .total(total)
                .nouveaux(nouveaux)
                .lus(lus)
                .repondus(repondus)
                .archives(archives)
                .build();
    }

    private MessageContact find(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Message de contact non trouve"));
    }

    private MessageContactDTO toDTO(MessageContact m) {
        return MessageContactDTO.builder()
                .id(m.getId())
                .nom(m.getNom())
                .email(m.getEmail())
                .telephone(m.getTelephone())
                .sujet(m.getSujet())
                .message(m.getMessage())
                .statut(m.getStatut().name())
                .reponse(m.getReponse())
                .reponduPar(m.getReponduPar())
                .reponduAt(m.getReponduAt())
                .createdAt(m.getCreatedAt())
                .updatedAt(m.getUpdatedAt())
                .build();
    }

    private String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }
}
