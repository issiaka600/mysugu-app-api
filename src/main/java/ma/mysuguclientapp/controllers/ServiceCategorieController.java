package ma.mysuguclientapp.controllers;

import lombok.RequiredArgsConstructor;
import ma.mysuguclientapp.dtos.ServiceCategorieDTO;
import ma.mysuguclientapp.services.interfaces.ServiceCategorieService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/services")
@RequiredArgsConstructor
public class ServiceCategorieController {

    private final ServiceCategorieService serviceCategorieService;

    @GetMapping
    public ResponseEntity<List<ServiceCategorieDTO>> getActiveServices() {
        return ResponseEntity.ok(serviceCategorieService.getActiveServices());
    }

    @GetMapping("/admin")
    public ResponseEntity<List<ServiceCategorieDTO>> getAllServices() {
        return ResponseEntity.ok(serviceCategorieService.getAllServices());
    }

    @GetMapping("/{id}")
    public ResponseEntity<ServiceCategorieDTO> getServiceById(@PathVariable Long id) {
        return ResponseEntity.ok(serviceCategorieService.getServiceById(id));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ServiceCategorieDTO> createService(
            @RequestParam String nom,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String icon,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Integer ordre,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(required = false) MultipartFile imageTop,
            @RequestParam(required = false) MultipartFile imageBanner) {
        ServiceCategorieDTO created = serviceCategorieService.createService(
                nom,
                tag,
                description,
                icon,
                type,
                ordre,
                isActive,
                imageTop,
                imageBanner);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ServiceCategorieDTO> updateService(
            @PathVariable Long id,
            @RequestParam String nom,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String icon,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Integer ordre,
            @RequestParam(required = false) Boolean isActive,
            @RequestParam(required = false) MultipartFile imageTop,
            @RequestParam(required = false) MultipartFile imageBanner) {
        ServiceCategorieDTO updated = serviceCategorieService.updateService(
                id,
                nom,
                tag,
                description,
                icon,
                type,
                ordre,
                isActive,
                imageTop,
                imageBanner);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteService(@PathVariable Long id) {
        serviceCategorieService.deleteService(id);
        return ResponseEntity.noContent().build();
    }
}
