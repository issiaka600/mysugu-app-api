package ma.mysuguclientapp.controllers;

import lombok.RequiredArgsConstructor;
import ma.mysuguclientapp.dtos.FiltreDTO;
import ma.mysuguclientapp.services.interfaces.FiltreService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/filtres")
@RequiredArgsConstructor
public class AdminFiltreController {

    private final FiltreService filtreService;

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<FiltreDTO>> list(@RequestParam String contexte) {
        return ResponseEntity.ok(filtreService.getByContexte(contexte, true));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<FiltreDTO> create(@RequestBody FiltreDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(filtreService.create(dto));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<FiltreDTO> update(@PathVariable Long id, @RequestBody FiltreDTO dto) {
        return ResponseEntity.ok(filtreService.update(id, dto));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        filtreService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
