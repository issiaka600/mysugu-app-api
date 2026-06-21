package ma.mysuguclientapp.services.interfaces;

import ma.mysuguclientapp.dtos.ZoneAttenteNotificationCreateDTO;
import ma.mysuguclientapp.dtos.ZoneAttenteNotificationDTO;

import java.util.List;

public interface ZoneAttenteNotificationService {
    ZoneAttenteNotificationDTO enregistrer(ZoneAttenteNotificationCreateDTO dto);
    List<ZoneAttenteNotificationDTO> listerDemandes();
}
