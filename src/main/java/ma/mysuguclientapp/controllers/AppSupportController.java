package ma.mysuguclientapp.controllers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import ma.mysuguclientapp.dtos.AppSupportDTO;
import ma.mysuguclientapp.dtos.EmergencyContactDTO;
import ma.mysuguclientapp.entities.AppSupportConfiguration;
import ma.mysuguclientapp.repositories.AppSupportConfigurationRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Locale;

@RestController
@RequiredArgsConstructor
public class AppSupportController {
    private final AppSupportConfigurationRepository repository;
    private final ObjectMapper objectMapper;

    @GetMapping("/api/app-support")
    public ResponseEntity<AppSupportDTO> getPublic(@RequestParam String app) {
        return ResponseEntity.ok(toDto(load(app)));
    }

    @GetMapping("/api/admin/app-support")
    public ResponseEntity<AppSupportDTO> getAdmin(@RequestParam String app) {
        return ResponseEntity.ok(toDto(load(app)));
    }

    @PutMapping("/api/admin/app-support")
    public ResponseEntity<AppSupportDTO> update(@RequestParam String app,
                                                 @RequestBody AppSupportDTO dto) {
        String key = normalize(app);
        AppSupportConfiguration config = repository.findByAppKey(key)
                .orElseGet(() -> AppSupportConfiguration.builder().appKey(key).build());
        config.setPhone(dto.getPhone());
        config.setWhatsapp(dto.getWhatsapp());
        config.setEmail(dto.getEmail());
        config.setAddress(dto.getAddress());
        config.setWorkingHours(dto.getWorkingHours());
        config.setWebsite(dto.getWebsite());
        try {
            config.setEmergencyContactsJson(objectMapper.writeValueAsString(
                    dto.getEmergencyContacts() == null ? List.of() : dto.getEmergencyContacts()));
        } catch (Exception e) {
            throw new IllegalArgumentException("emergencyContacts invalide", e);
        }
        return ResponseEntity.ok(toDto(repository.save(config)));
    }

    private AppSupportConfiguration load(String app) {
        return repository.findByAppKey(normalize(app))
                .orElseGet(() -> AppSupportConfiguration.builder()
                        .appKey(normalize(app)).emergencyContactsJson("[]").build());
    }

    private AppSupportDTO toDto(AppSupportConfiguration c) {
        List<EmergencyContactDTO> contacts = List.of();
        try {
            if (c.getEmergencyContactsJson() != null && !c.getEmergencyContactsJson().isBlank()) {
                contacts = objectMapper.readValue(c.getEmergencyContactsJson(),
                        new TypeReference<List<EmergencyContactDTO>>() {});
            }
        } catch (Exception ignored) { }
        return AppSupportDTO.builder().app(c.getAppKey()).phone(c.getPhone()).whatsapp(c.getWhatsapp())
                .email(c.getEmail()).address(c.getAddress()).workingHours(c.getWorkingHours())
                .website(c.getWebsite()).emergencyContacts(contacts).build();
    }

    private String normalize(String app) {
        String key = app == null ? "" : app.trim().toLowerCase(Locale.ROOT);
        if (!key.equals("delivery") && !key.equals("customer")) {
            throw new IllegalArgumentException("app doit être delivery ou customer");
        }
        return key;
    }
}
