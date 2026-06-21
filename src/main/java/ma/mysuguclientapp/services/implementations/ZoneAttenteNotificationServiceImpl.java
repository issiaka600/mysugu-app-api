package ma.mysuguclientapp.services.implementations;

import lombok.RequiredArgsConstructor;
import ma.mysuguclientapp.dtos.ZoneAttenteNotificationCreateDTO;
import ma.mysuguclientapp.dtos.ZoneAttenteNotificationDTO;
import ma.mysuguclientapp.entities.ZoneAttenteNotification;
import ma.mysuguclientapp.repositories.ZoneAttenteNotificationRepository;
import ma.mysuguclientapp.services.interfaces.ZoneAttenteNotificationService;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ZoneAttenteNotificationServiceImpl implements ZoneAttenteNotificationService {

    private final ZoneAttenteNotificationRepository repository;

    @Override
    @Transactional
    public ZoneAttenteNotificationDTO enregistrer(ZoneAttenteNotificationCreateDTO dto) {
        ZoneAttenteNotification entity = ZoneAttenteNotification.builder()
                .email(dto.getEmail())
                .telephone(dto.getTelephone())
                .latitude(dto.getLatitude())
                .longitude(dto.getLongitude())
                .ville(dto.getVille())
                .pays(dto.getPays())
                .notifie(false)
                .build();
        return toDTO(repository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ZoneAttenteNotificationDTO> listerDemandes() {
        return repository.findAllByOrderByCreatedAtDesc().stream().map(this::toDTO).toList();
    }

    private ZoneAttenteNotificationDTO toDTO(ZoneAttenteNotification e) {
        ZoneAttenteNotificationDTO dto = new ZoneAttenteNotificationDTO();
        BeanUtils.copyProperties(e, dto);
        return dto;
    }
}
