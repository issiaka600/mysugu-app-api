package ma.mysuguclientapp.controllers;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.mysuguclientapp.dtos.PlatAvailabilityUpdateDTO;
import ma.mysuguclientapp.dtos.PlatCreateDTO;
import ma.mysuguclientapp.dtos.PlatDTO;
import ma.mysuguclientapp.services.interfaces.PlatService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/plats")
@RequiredArgsConstructor
@Slf4j
public class PlatController {

    private final PlatService platService;

    @GetMapping
    public ResponseEntity<Page<PlatDTO>> getAllPlats(
            @RequestParam(required = false) Long restaurantId,
            @RequestParam(required = false) String categorie,
            @RequestParam(required = false) Boolean available,
            Pageable pageable) {

        return ResponseEntity.ok(platService.getAllPlats(restaurantId, categorie, available, pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<PlatDTO> getPlatById(@PathVariable Long id) {
        return ResponseEntity.ok(platService.getPlatById(id));
    }

    @GetMapping("/restaurant/{restaurantId}")
    public ResponseEntity<List<PlatDTO>> getPlatsByRestaurant(@PathVariable Long restaurantId) {
        return ResponseEntity.ok(platService.getPlatsByRestaurant(restaurantId));
    }

    @GetMapping("/search")
    public ResponseEntity<List<PlatDTO>> searchPlats(@RequestParam String keyword) {
        return ResponseEntity.ok(platService.searchPlats(keyword));
    }

    // Le corps est un multipart/form-data À PLAT (un champ par propriété de PlatCreateDTO + `image`),
    // et NON un part JSON `platDTO`. On documente explicitement le schéma aplati pour que Swagger et
    // les clients générés envoient le bon format (sinon springdoc rendait PlatCreateDTO comme un objet
    // JSON imbriqué à cause des champs non-string -> 500 au binding @ModelAttribute).
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @io.swagger.v3.oas.annotations.media.Content(
            mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
            schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = PlatCreateDTO.class)))
    public ResponseEntity<PlatDTO> createPlat(
            @Valid @ModelAttribute PlatCreateDTO platDTO,
            @RequestParam(value = "image", required = false) MultipartFile image) {

        return ResponseEntity.status(HttpStatus.CREATED).body(platService.createPlat(platDTO, image));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @io.swagger.v3.oas.annotations.parameters.RequestBody(content = @io.swagger.v3.oas.annotations.media.Content(
            mediaType = MediaType.MULTIPART_FORM_DATA_VALUE,
            schema = @io.swagger.v3.oas.annotations.media.Schema(implementation = PlatCreateDTO.class)))
    public ResponseEntity<PlatDTO> updatePlat(
            @PathVariable Long id,
            @Valid @ModelAttribute PlatCreateDTO platDTO,
            @RequestParam(value = "image", required = false) MultipartFile image) {
        log.info("Plat Update Object : {}", platDTO);
        return ResponseEntity.ok(platService.updatePlat(id, platDTO, image));
    }

    @PatchMapping(value = "/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PlatDTO> updatePlatImage(
            @PathVariable Long id,
            @RequestParam("image") MultipartFile image) {
        PlatCreateDTO platDTO = new PlatCreateDTO();
        return ResponseEntity.ok(platService.updatePlat(id, platDTO, image));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deletePlat(@PathVariable Long id) {
        platService.deletePlat(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/availability")
    public ResponseEntity<PlatDTO> updatePlatAvailability(
            @PathVariable Long id,
            @RequestBody(required = false) PlatAvailabilityUpdateDTO availabilityDTO) {
        return ResponseEntity.ok(platService.updateAvailability(id, availabilityDTO));
    }
}
