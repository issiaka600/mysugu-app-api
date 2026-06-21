package ma.mysuguclientapp.controllers;

import lombok.RequiredArgsConstructor;
import ma.mysuguclientapp.dtos.FiltreDTO;
import ma.mysuguclientapp.services.interfaces.FiltreService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/filtres")
@RequiredArgsConstructor
public class FiltreController {

    private final FiltreService filtreService;

    /** Filtres actifs d'un contexte, triés, pour l'app users. */
    @GetMapping
    public ResponseEntity<List<FiltreDTO>> getFiltres(@RequestParam String contexte) {
        return ResponseEntity.ok(filtreService.getByContexte(contexte, false));
    }
}
