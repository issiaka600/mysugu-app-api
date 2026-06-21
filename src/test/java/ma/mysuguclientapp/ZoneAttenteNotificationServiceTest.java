package ma.mysuguclientapp;

import ma.mysuguclientapp.dtos.ZoneAttenteNotificationCreateDTO;
import ma.mysuguclientapp.dtos.ZoneAttenteNotificationDTO;
import ma.mysuguclientapp.repositories.ZoneAttenteNotificationRepository;
import ma.mysuguclientapp.services.interfaces.ZoneAttenteNotificationService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ZoneAttenteNotificationServiceTest {

    @Autowired ZoneAttenteNotificationService service;
    @Autowired ZoneAttenteNotificationRepository repository;

    private Long createdId;

    @AfterAll
    void cleanup() {
        if (createdId != null) repository.deleteById(createdId);
    }

    @Test
    void enregistreEtRelitUneDemande() {
        ZoneAttenteNotificationCreateDTO dto = new ZoneAttenteNotificationCreateDTO();
        dto.setEmail("horszone-" + System.nanoTime() + "@test.mysugu");
        dto.setVille("Thiès");
        dto.setLatitude(14.79); dto.setLongitude(-16.92);

        ZoneAttenteNotificationDTO saved = service.enregistrer(dto);
        createdId = saved.getId();

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getNotifie()).isFalse();

        List<ZoneAttenteNotificationDTO> all = service.listerDemandes();
        assertThat(all).anyMatch(d -> d.getId().equals(createdId));
        assertThat(all.get(0).getCreatedAt()).isNotNull();
    }
}
