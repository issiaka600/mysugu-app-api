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
