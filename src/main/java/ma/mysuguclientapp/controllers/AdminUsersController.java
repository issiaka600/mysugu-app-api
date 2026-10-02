package ma.mysuguclientapp.controllers;

import lombok.RequiredArgsConstructor;
import ma.mysuguclientapp.dtos.UserDTO;
import ma.mysuguclientapp.services.interfaces.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminUsersController {

    private final UserService userService;

    /**
     * GET /api/admin/users?role=CLIENT&page=0&size=10&search=
     * Liste paginée d'utilisateurs filtrés par rôle, avec recherche optionnelle.
     */
    @GetMapping
    public ResponseEntity<Page<UserDTO>> getUsers(
            @RequestParam(defaultValue = "CLIENT") String role,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<UserDTO> users = userService.getUsersByRole(role, search, pageable);
        return ResponseEntity.ok(users);
    }

    /**
     * GET /api/admin/users/proprietaires?vertical=ALIMENTAIRE&page&size&search
     * Propriétaires d'établissement d'une verticale. NULL en base vaut RESTAURANT.
     */
    @GetMapping("/proprietaires")
    public ResponseEntity<Page<UserDTO>> getProprietaires(
            @RequestParam ma.mysuguclientapp.enumerations.Vertical vertical,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) String search) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(userService.getProprietairesParVerticale(vertical, search, pageable));
    }

    /**
     * PUT /api/admin/users/{id}
     * Met à jour les coordonnées. Ni rôle, ni mot de passe, ni email.
     */
    @PutMapping("/{id}")
    public ResponseEntity<UserDTO> updateUser(@PathVariable Long id,
                                              @RequestBody ma.mysuguclientapp.dtos.auth.AdminUserUpdateDTO dto) {
        return ResponseEntity.ok(userService.mettreAJourUtilisateur(id, dto));
    }

    /**
     * POST /api/admin/users/{id}/relancer-invitation
     * Renvoie le lien de définition de mot de passe au propriétaire.
     */
    @PostMapping("/{id}/relancer-invitation")
    public ResponseEntity<java.util.Map<String, String>> relancerInvitation(@PathVariable Long id) {
        userService.relancerInvitation(id);
        return ResponseEntity.ok(java.util.Map.of("message", "Invitation renvoyée"));
    }

    /**
     * POST /api/admin/users/{id}/password
     * Définit ou réinitialise le mot de passe d'un propriétaire d'établissement.
     * Corps : { "generer": true } pour que le serveur tire un mot de passe, ou
     * { "motDePasse": "..." } pour une valeur choisie par l'administrateur.
     * La réponse contient le mot de passe en clair, à afficher une seule fois.
     */
    @PostMapping("/{id}/password")
    public ResponseEntity<java.util.Map<String, String>> definirMotDePasse(
            @PathVariable Long id,
            @RequestBody ma.mysuguclientapp.dtos.auth.AdminPasswordSetDTO dto) {
        String motDePasse = userService.definirMotDePasse(id, dto);
        return ResponseEntity.ok(java.util.Map.of(
                "message", "Mot de passe défini. Communiquez-le au propriétaire : il ne sera plus jamais affiché.",
                "motDePasse", motDePasse));
    }

    /**
     * GET /api/admin/users/{id}/password
     * Relit le mot de passe défini par l'administration, pour le dicter au propriétaire.
     * 404 quand il n'y a rien à relire : clé de coffre absente, compte né d'une invitation,
     * ou propriétaire ayant changé son mot de passe lui-même depuis.
     */
    @GetMapping("/{id}/password")
    public ResponseEntity<java.util.Map<String, String>> lireMotDePasse(@PathVariable Long id) {
        String motDePasse = userService.lireMotDePasseAdmin(id);
        if (motDePasse == null) {
            throw new ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND,
                    "Aucun mot de passe à afficher : il n'a jamais été défini par l'administration, "
                  + "ou le propriétaire l'a changé lui-même. Utilisez « Générer » pour en créer un nouveau.");
        }
        return ResponseEntity.ok(java.util.Map.of("motDePasse", motDePasse));
    }

    /**
     * PATCH /api/admin/users/{id}/toggle
     * Active ou désactive un utilisateur.
     */
    @PatchMapping("/{id}/toggle")
    public ResponseEntity<UserDTO> toggleUser(@PathVariable Long id) {
        UserDTO user = userService.toggleUserStatus(id);
        return ResponseEntity.ok(user);
    }

    /**
     * GET /api/admin/users/{id}
     * Récupère un utilisateur par son ID.
     */
    @GetMapping("/{id}")
    public ResponseEntity<UserDTO> getUser(@PathVariable Long id) {
        UserDTO user = userService.getUserById(id);
        return ResponseEntity.ok(user);
    }
}
