# Options de plats (#2) — Phase 2 (Définition) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Permettre de définir, par plat, des sections d'options (accompagnements, suppléments, sauces…) avec items et prix, gérables côté dashboard/restaurateur et affichées sur la fiche plat de l'app users. (Sélection client + impact panier/commande = Phase 3, hors périmètre.)

**Architecture:** Deux nouvelles entités backend `OptionGroup` (1—* `OptionItem`) rattachées à `Plat`. Endpoints JSON dédiés : `GET /api/plats/{platId}/options` (public) et `PUT /api/plats/{platId}/options` (replace-all, ADMIN ou RESTAURANT_OWNER propriétaire). Le dashboard ajoute un éditeur de sections par plat ; l'app users affiche les sections (lecture seule en Phase 2).

**Tech Stack:** Spring Boot 4 / JPA / Postgres ; React + Vite + TypeScript + Tailwind.

## Global Constraints

- Backend Java 21 ; build via `cmd.exe /c "C:\Users\Issiaka traoré\Desktop\systalink\mysugu\_build_local.bat" > /tmp/winbuild.log 2>&1; tail -5 /tmp/winbuild.log` → succès = `BUILD_EXIT=0`. Jamais `mvn`/`mvnw` direct (java du PATH = shim Windows).
- Backend local : `_run_local.bat` (postgres :5433, minio :9100, `TEST_DATA_ENABLED=true`) ; joignable depuis WSL via IP passerelle (`http://$(ip route|grep default|awk '{print $3}'):8083`), PAS localhost. Admin local seedé : `admin.demo@mysuku.ma`/`demo1234` ; restaurateur seedé : `owner.demo@mysuku.ma`/`demo1234`.
- Front : `npx tsc --noEmit` (admin) / `npx tsc -b --noEmit` (app users) → succès = `TSC_EXIT:0`.
- Enums `@Enumerated(EnumType.STRING)`.
- Invariants options : `SINGLE` ⇒ `maxSelections=1` ; `obligatoire` ⇒ `minSelections≥1` ; si `maxSelections` défini, `minSelections ≤ maxSelections` ; `prixSupplement` ≥ 0 (0 = inclus/gratuit).
- Sécurité : `GET /api/plats/{id}/options` public (déjà couvert par `GET /api/plats/**` permitAll) ; `PUT` = ADMIN ou RESTAURANT_OWNER propriétaire du restaurant du plat (contrôle d'appartenance dans le service).
- Branches dédiées `feat/plat-options` : backend depuis `wip/restaurateur-onboarding`, admin/app users depuis `feat/filtres`. N'ajouter QUE les fichiers listés par tâche (jamais `git add -A`).
- Conventions : commentaires français ; DTO `@Data` Lombok ; services `@RequiredArgsConstructor`.

---

### Task 1 : Enum + entités `OptionGroup`/`OptionItem` + repository (backend)

**Files:**
- Create: `MySuguClientApp/src/main/java/ma/mysuguclientapp/enumerations/OptionSelectionMode.java`
- Create: `MySuguClientApp/src/main/java/ma/mysuguclientapp/entities/OptionGroup.java`
- Create: `MySuguClientApp/src/main/java/ma/mysuguclientapp/entities/OptionItem.java`
- Modify: `MySuguClientApp/src/main/java/ma/mysuguclientapp/entities/Plat.java` (ajouter la relation `optionGroups`)
- Create: `MySuguClientApp/src/main/java/ma/mysuguclientapp/repositories/OptionGroupRepository.java`

**Interfaces:**
- Produces: enum `OptionSelectionMode{SINGLE,MULTIPLE}` ; entités `OptionGroup`, `OptionItem` ; `Plat.getOptionGroups()` ; `OptionGroupRepository.findByPlatIdOrderByOrdreAsc(Long)`.

- [ ] **Step 1: Créer l'enum**

`OptionSelectionMode.java` :
```java
package ma.mysuguclientapp.enumerations;

/** Mode de sélection d'une section d'options de plat. */
public enum OptionSelectionMode {
    SINGLE,    // choix unique (radio) — maxSelections = 1
    MULTIPLE   // choix multiple (cases à cocher)
}
```

- [ ] **Step 2: Créer `OptionGroup.java`**

```java
package ma.mysuguclientapp.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import ma.mysuguclientapp.enumerations.OptionSelectionMode;

import java.util.ArrayList;
import java.util.List;

/** Section d'options d'un plat (ex: « Accompagnement », « Suppléments », « Sauces »). */
@Entity
@Table(name = "plat_option_groups")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OptionGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plat_id", nullable = false)
    private Plat plat;

    @Column(nullable = false)
    private String nom;

    @Enumerated(EnumType.STRING)
    @Column(name = "selection_mode", nullable = false)
    private OptionSelectionMode selectionMode = OptionSelectionMode.SINGLE;

    @Column(nullable = false)
    private Boolean obligatoire = false;

    @Column(name = "min_selections", nullable = false)
    private Integer minSelections = 0;

    /** Null = illimité (sauf SINGLE où max = 1). */
    @Column(name = "max_selections")
    private Integer maxSelections;

    @Column(nullable = false)
    private Integer ordre = 0;

    @OneToMany(mappedBy = "group", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("ordre ASC")
    private List<OptionItem> items = new ArrayList<>();
}
```

- [ ] **Step 3: Créer `OptionItem.java`**

```java
package ma.mysuguclientapp.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Choix au sein d'une section d'options (ex: « Frites », « Fromage »). */
@Entity
@Table(name = "plat_option_items")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OptionItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private OptionGroup group;

    @Column(nullable = false)
    private String nom;

    /** Supplément de prix ; 0 = inclus/gratuit. */
    @Column(name = "prix_supplement", nullable = false, precision = 10, scale = 2)
    private BigDecimal prixSupplement = BigDecimal.ZERO;

    @Column(nullable = false)
    private Boolean disponible = true;

    @Column(nullable = false)
    private Integer ordre = 0;
}
```

- [ ] **Step 4: Ajouter la relation dans `Plat.java`**

Dans `entities/Plat.java`, après le champ `categorieProduit` (dernier champ avant l'accolade fermante de la classe), ajouter :
```java

    @OneToMany(mappedBy = "plat", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("ordre ASC")
    private List<OptionGroup> optionGroups = new ArrayList<>();
```
Vérifier que `import java.util.List;`, `import java.util.ArrayList;` et `jakarta.persistence.*` sont déjà présents (ils le sont — `ingredients` les utilise).

- [ ] **Step 5: Créer `OptionGroupRepository.java`**

```java
package ma.mysuguclientapp.repositories;

import ma.mysuguclientapp.entities.OptionGroup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OptionGroupRepository extends JpaRepository<OptionGroup, Long> {
    List<OptionGroup> findByPlatIdOrderByOrdreAsc(Long platId);
}
```

- [ ] **Step 6: Build**

Run: `cmd.exe /c "C:\Users\Issiaka traoré\Desktop\systalink\mysugu\_build_local.bat" > /tmp/winbuild.log 2>&1; tail -3 /tmp/winbuild.log`
Expected: `BUILD_EXIT=0`

- [ ] **Step 7: Commit**

```bash
cd "/mnt/c/Users/Issiaka traoré/Desktop/systalink/mysugu/MySuguClientApp"
git add src/main/java/ma/mysuguclientapp/enumerations/OptionSelectionMode.java \
        src/main/java/ma/mysuguclientapp/entities/OptionGroup.java \
        src/main/java/ma/mysuguclientapp/entities/OptionItem.java \
        src/main/java/ma/mysuguclientapp/entities/Plat.java \
        src/main/java/ma/mysuguclientapp/repositories/OptionGroupRepository.java
git commit -m "feat(plat-options): enum + entites OptionGroup/OptionItem + relation Plat + repo"
```

---

### Task 2 : DTOs + enrichissement `PlatDTO` (backend)

**Files:**
- Create: `MySuguClientApp/src/main/java/ma/mysuguclientapp/dtos/OptionItemDTO.java`
- Create: `MySuguClientApp/src/main/java/ma/mysuguclientapp/dtos/OptionGroupDTO.java`
- Modify: `MySuguClientApp/src/main/java/ma/mysuguclientapp/dtos/PlatDTO.java` (ajouter `optionGroups`)
- Modify: `MySuguClientApp/src/main/java/ma/mysuguclientapp/services/implementations/PlatServiceImpl.java` (mapper `optionGroups` dans `convertToDTO`)

**Interfaces:**
- Consumes: entités Task 1.
- Produces: `OptionItemDTO{id,nom,prixSupplement,disponible,ordre}`, `OptionGroupDTO{id,nom,selectionMode,obligatoire,minSelections,maxSelections,ordre,items}`, `PlatDTO.optionGroups`.

- [ ] **Step 1: Créer `OptionItemDTO.java`**

```java
package ma.mysuguclientapp.dtos;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class OptionItemDTO {
    private Long id;
    private String nom;
    private BigDecimal prixSupplement;
    private Boolean disponible;
    private Integer ordre;
}
```

- [ ] **Step 2: Créer `OptionGroupDTO.java`**

```java
package ma.mysuguclientapp.dtos;

import lombok.Data;

import java.util.List;

@Data
public class OptionGroupDTO {
    private Long id;
    private String nom;
    private String selectionMode;   // SINGLE | MULTIPLE
    private Boolean obligatoire;
    private Integer minSelections;
    private Integer maxSelections;
    private Integer ordre;
    private List<OptionItemDTO> items;
}
```

- [ ] **Step 3: Ajouter `optionGroups` à `PlatDTO.java`**

Dans `dtos/PlatDTO.java`, après `private String restaurantNom;`, ajouter :
```java
    private List<OptionGroupDTO> optionGroups;
```
(`import java.util.List;` est déjà présent.)

- [ ] **Step 4: Mapper `optionGroups` dans `PlatServiceImpl.convertToDTO`**

Dans `services/implementations/PlatServiceImpl.java`, méthode privée `convertToDTO(Plat plat)` (vers ligne 280), juste avant le `return dto;`, ajouter le mapping :
```java
        if (plat.getOptionGroups() != null) {
            dto.setOptionGroups(plat.getOptionGroups().stream().map(g -> {
                ma.mysuguclientapp.dtos.OptionGroupDTO gd = new ma.mysuguclientapp.dtos.OptionGroupDTO();
                gd.setId(g.getId());
                gd.setNom(g.getNom());
                gd.setSelectionMode(g.getSelectionMode() != null ? g.getSelectionMode().name() : null);
                gd.setObligatoire(g.getObligatoire());
                gd.setMinSelections(g.getMinSelections());
                gd.setMaxSelections(g.getMaxSelections());
                gd.setOrdre(g.getOrdre());
                gd.setItems(g.getItems() == null ? java.util.List.of() : g.getItems().stream().map(it -> {
                    ma.mysuguclientapp.dtos.OptionItemDTO id = new ma.mysuguclientapp.dtos.OptionItemDTO();
                    id.setId(it.getId());
                    id.setNom(it.getNom());
                    id.setPrixSupplement(it.getPrixSupplement());
                    id.setDisponible(it.getDisponible());
                    id.setOrdre(it.getOrdre());
                    return id;
                }).collect(java.util.stream.Collectors.toList()));
                return gd;
            }).collect(java.util.stream.Collectors.toList()));
        }
```
Note : `convertToDTO` est appelé dans des contextes `@Transactional` (getPlatById, getPlatsByRestaurant) ; l'accès lazy à `optionGroups`/`items` y est résolu. (Si une `LazyInitializationException` apparaît au runtime sur un chemin non transactionnel, basculer le mapping options pour utiliser `OptionGroupRepository.findByPlatIdOrderByOrdreAsc` — mais par défaut le mapping lazy suffit dans les chemins transactionnels existants.)

- [ ] **Step 5: Build**

Run: `cmd.exe /c "C:\Users\Issiaka traoré\Desktop\systalink\mysugu\_build_local.bat" > /tmp/winbuild.log 2>&1; tail -3 /tmp/winbuild.log`
Expected: `BUILD_EXIT=0`

- [ ] **Step 6: Commit**

```bash
git add src/main/java/ma/mysuguclientapp/dtos/OptionItemDTO.java \
        src/main/java/ma/mysuguclientapp/dtos/OptionGroupDTO.java \
        src/main/java/ma/mysuguclientapp/dtos/PlatDTO.java \
        src/main/java/ma/mysuguclientapp/services/implementations/PlatServiceImpl.java
git commit -m "feat(plat-options): DTOs OptionGroup/OptionItem + PlatDTO.optionGroups + mapping"
```

---

### Task 3 : Service + endpoints de gestion des options + sécurité (backend)

**Files:**
- Create: `MySuguClientApp/src/main/java/ma/mysuguclientapp/services/interfaces/PlatOptionService.java`
- Create: `MySuguClientApp/src/main/java/ma/mysuguclientapp/services/implementations/PlatOptionServiceImpl.java`
- Create: `MySuguClientApp/src/main/java/ma/mysuguclientapp/controllers/PlatOptionController.java`

**Interfaces:**
- Consumes: `OptionGroupDTO`/`OptionItemDTO` (Task 2), `PlatRepository`, `UserRepository`, entités Task 1.
- Produces: `GET /api/plats/{platId}/options` → `List<OptionGroupDTO>` ; `PUT /api/plats/{platId}/options` (body `List<OptionGroupDTO>`, replace-all) → `List<OptionGroupDTO>`.

- [ ] **Step 1: Créer l'interface `PlatOptionService.java`**

```java
package ma.mysuguclientapp.services.interfaces;

import ma.mysuguclientapp.dtos.OptionGroupDTO;

import java.util.List;

public interface PlatOptionService {
    /** Sections d'options d'un plat (lecture publique). */
    List<OptionGroupDTO> getOptions(Long platId);

    /**
     * Remplace toute la structure d'options d'un plat (replace-all).
     * @param userEmail utilisateur authentifié — doit être ADMIN ou propriétaire du restaurant du plat.
     */
    List<OptionGroupDTO> replaceOptions(Long platId, List<OptionGroupDTO> groups, String userEmail);
}
```

- [ ] **Step 2: Créer `PlatOptionServiceImpl.java`**

```java
package ma.mysuguclientapp.services.implementations;

import lombok.RequiredArgsConstructor;
import ma.mysuguclientapp.dtos.OptionGroupDTO;
import ma.mysuguclientapp.dtos.OptionItemDTO;
import ma.mysuguclientapp.entities.OptionGroup;
import ma.mysuguclientapp.entities.OptionItem;
import ma.mysuguclientapp.entities.Plat;
import ma.mysuguclientapp.entities.User;
import ma.mysuguclientapp.enumerations.OptionSelectionMode;
import ma.mysuguclientapp.enumerations.UserRole;
import ma.mysuguclientapp.exceptions.BadRequestException;
import ma.mysuguclientapp.exceptions.ResourceNotFoundException;
import ma.mysuguclientapp.repositories.PlatRepository;
import ma.mysuguclientapp.repositories.UserRepository;
import ma.mysuguclientapp.services.interfaces.PlatOptionService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class PlatOptionServiceImpl implements PlatOptionService {

    private final PlatRepository platRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public List<OptionGroupDTO> getOptions(Long platId) {
        Plat plat = platRepository.findById(platId)
                .orElseThrow(() -> new ResourceNotFoundException("Plat non trouvé"));
        return plat.getOptionGroups().stream().map(this::toGroupDTO).collect(Collectors.toList());
    }

    @Override
    public List<OptionGroupDTO> replaceOptions(Long platId, List<OptionGroupDTO> groups, String userEmail) {
        Plat plat = platRepository.findById(platId)
                .orElseThrow(() -> new ResourceNotFoundException("Plat non trouvé"));
        assertCanManage(plat, userEmail);

        // replace-all : on vide la collection (orphanRemoval supprime les anciens groupes/items)
        plat.getOptionGroups().clear();
        if (groups != null) {
            int gOrdre = 0;
            for (OptionGroupDTO gd : groups) {
                OptionGroup g = new OptionGroup();
                g.setPlat(plat);
                g.setNom(requireText(gd.getNom(), "Le nom de la section est obligatoire"));
                g.setSelectionMode(parseMode(gd.getSelectionMode()));
                g.setObligatoire(Boolean.TRUE.equals(gd.getObligatoire()));
                g.setMinSelections(gd.getMinSelections() != null ? gd.getMinSelections() : 0);
                g.setMaxSelections(gd.getMaxSelections());
                g.setOrdre(gd.getOrdre() != null ? gd.getOrdre() : gOrdre);
                normaliserEtValider(g);

                int iOrdre = 0;
                if (gd.getItems() != null) {
                    for (OptionItemDTO it : gd.getItems()) {
                        OptionItem item = new OptionItem();
                        item.setGroup(g);
                        item.setNom(requireText(it.getNom(), "Le nom d'un choix est obligatoire"));
                        BigDecimal prix = it.getPrixSupplement() != null ? it.getPrixSupplement() : BigDecimal.ZERO;
                        if (prix.compareTo(BigDecimal.ZERO) < 0) {
                            throw new BadRequestException("Le supplément de prix ne peut pas être négatif");
                        }
                        item.setPrixSupplement(prix);
                        item.setDisponible(it.getDisponible() == null || it.getDisponible());
                        item.setOrdre(it.getOrdre() != null ? it.getOrdre() : iOrdre);
                        g.getItems().add(item);
                        iOrdre++;
                    }
                }
                plat.getOptionGroups().add(g);
                gOrdre++;
            }
        }
        Plat saved = platRepository.save(plat);
        return saved.getOptionGroups().stream().map(this::toGroupDTO).collect(Collectors.toList());
    }

    private void assertCanManage(Plat plat, String userEmail) {
        User user = userRepository.findByEmail(userEmail)
                .orElseThrow(() -> new AccessDeniedException("Utilisateur non authentifié"));
        if (user.getRole() == UserRole.ADMIN) {
            return;
        }
        boolean estProprietaire = plat.getRestaurant() != null
                && plat.getRestaurant().getOwner() != null
                && plat.getRestaurant().getOwner().getId().equals(user.getId());
        if (!estProprietaire) {
            throw new AccessDeniedException("Vous ne gérez pas ce plat");
        }
    }

    private void normaliserEtValider(OptionGroup g) {
        if (g.getSelectionMode() == OptionSelectionMode.SINGLE) {
            g.setMaxSelections(1);
        }
        if (Boolean.TRUE.equals(g.getObligatoire()) && g.getMinSelections() < 1) {
            g.setMinSelections(1);
        }
        if (g.getMinSelections() < 0) {
            throw new BadRequestException("minSelections ne peut pas être négatif");
        }
        if (g.getMaxSelections() != null && g.getMinSelections() > g.getMaxSelections()) {
            throw new BadRequestException("minSelections ne peut pas dépasser maxSelections");
        }
    }

    private OptionSelectionMode parseMode(String s) {
        if (s == null) return OptionSelectionMode.SINGLE;
        try { return OptionSelectionMode.valueOf(s.trim().toUpperCase()); }
        catch (Exception e) { throw new BadRequestException("Mode de sélection invalide: " + s); }
    }

    private String requireText(String s, String msg) {
        if (s == null || s.isBlank()) throw new BadRequestException(msg);
        return s.trim();
    }

    private OptionGroupDTO toGroupDTO(OptionGroup g) {
        OptionGroupDTO dto = new OptionGroupDTO();
        dto.setId(g.getId());
        dto.setNom(g.getNom());
        dto.setSelectionMode(g.getSelectionMode() != null ? g.getSelectionMode().name() : null);
        dto.setObligatoire(g.getObligatoire());
        dto.setMinSelections(g.getMinSelections());
        dto.setMaxSelections(g.getMaxSelections());
        dto.setOrdre(g.getOrdre());
        List<OptionItemDTO> items = new ArrayList<>();
        if (g.getItems() != null) {
            for (OptionItem it : g.getItems()) {
                OptionItemDTO id = new OptionItemDTO();
                id.setId(it.getId());
                id.setNom(it.getNom());
                id.setPrixSupplement(it.getPrixSupplement());
                id.setDisponible(it.getDisponible());
                id.setOrdre(it.getOrdre());
                items.add(id);
            }
        }
        dto.setItems(items);
        return dto;
    }
}
```

- [ ] **Step 3: Créer `PlatOptionController.java`**

```java
package ma.mysuguclientapp.controllers;

import lombok.RequiredArgsConstructor;
import ma.mysuguclientapp.dtos.OptionGroupDTO;
import ma.mysuguclientapp.services.interfaces.PlatOptionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/plats/{platId}/options")
@RequiredArgsConstructor
public class PlatOptionController {

    private final PlatOptionService platOptionService;

    /** Lecture publique des sections d'options d'un plat. */
    @GetMapping
    public ResponseEntity<List<OptionGroupDTO>> getOptions(@PathVariable Long platId) {
        return ResponseEntity.ok(platOptionService.getOptions(platId));
    }

    /** Remplace toute la structure d'options (ADMIN ou restaurateur propriétaire). */
    @PutMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'RESTAURANT_OWNER')")
    public ResponseEntity<List<OptionGroupDTO>> replaceOptions(
            @PathVariable Long platId,
            @RequestBody List<OptionGroupDTO> groups,
            @AuthenticationPrincipal String email) {
        return ResponseEntity.ok(platOptionService.replaceOptions(platId, groups, email));
    }
}
```
Note sécurité : `GET /api/plats/{platId}/options` est déjà public via la règle existante `requestMatchers(HttpMethod.GET, "/api/plats/**").permitAll()`. Le `PUT` est protégé par `@PreAuthorize` (rôle) + contrôle d'appartenance dans le service. Aucune modification de `SecurityConfig` n'est nécessaire — VÉRIFIER toutefois qu'aucune règle `PUT /api/plats/**` plus restrictive n'intercepte (il existe `requestMatchers(HttpMethod.PUT, "/api/plats/**").hasAnyRole("RESTAURANT_OWNER","ADMIN")` : compatible, elle autorise les deux rôles, le service affine l'appartenance).

- [ ] **Step 4: Build**

Run: `cmd.exe /c "C:\Users\Issiaka traoré\Desktop\systalink\mysugu\_build_local.bat" > /tmp/winbuild.log 2>&1; tail -3 /tmp/winbuild.log`
Expected: `BUILD_EXIT=0`

- [ ] **Step 5: Vérifier en runtime (redémarrage backend + curl)**

```bash
cmd.exe /c "taskkill /F /IM java.exe" 2>/dev/null
cmd.exe /c "C:\Users\Issiaka traoré\Desktop\systalink\mysugu\_run_local.bat" > /tmp/apprun.log 2>&1 &
until grep -qE "Started MySuguClientApp|APPLICATION FAILED TO START" /tmp/apprun.log; do sleep 3; done
BASE=http://$(ip route | grep default | awk '{print $3}'):8083
OWN=$(curl -s -X POST $BASE/auth/login -H "Content-Type: application/json" -d '{"email":"owner.demo@mysuku.ma","password":"demo1234"}' | python3 -c "import sys,json;print(json.load(sys.stdin)['token'])")
# Trouver un plat du restaurant du owner.demo (via son dashboard) — sinon prendre un platId existant
PLAT=$(curl -s "$BASE/api/plats?size=1" | python3 -c "import sys,json;d=json.load(sys.stdin);print((d.get('content') or [{}])[0].get('id'))")
echo "platId=$PLAT"
# PUT options
curl -s -X PUT "$BASE/api/plats/$PLAT/options" -H "Authorization: Bearer $OWN" -H "Content-Type: application/json" \
 -d '[{"nom":"Accompagnement","selectionMode":"SINGLE","obligatoire":true,"items":[{"nom":"Frites","prixSupplement":0},{"nom":"Salade","prixSupplement":0}]},{"nom":"Suppléments","selectionMode":"MULTIPLE","items":[{"nom":"Fromage","prixSupplement":5},{"nom":"Bacon","prixSupplement":7}]}]' | python3 -m json.tool | head -40
# GET options (public)
curl -s "$BASE/api/plats/$PLAT/options" | python3 -c "import sys,json;d=json.load(sys.stdin);print('groupes:',len(d));[print(' -',g['nom'],g['selectionMode'],'items=',len(g['items'])) for g in d]"
```
Expected : PUT renvoie 2 groupes (Accompagnement SINGLE avec maxSelections=1 & obligatoire→min=1 ; Suppléments MULTIPLE) ; GET public renvoie la même structure.

Note : si `owner.demo` n'a pas de plat à lui, le PUT renverra 403 (bon signe de sécurité) — refaire le test avec le token admin (`admin.demo@mysuku.ma`/`demo1234`) qui passe le contrôle d'appartenance.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/ma/mysuguclientapp/services/interfaces/PlatOptionService.java \
        src/main/java/ma/mysuguclientapp/services/implementations/PlatOptionServiceImpl.java \
        src/main/java/ma/mysuguclientapp/controllers/PlatOptionController.java
git commit -m "feat(plat-options): service replace-all + endpoints GET public / PUT securise"
```

---

### Task 4 : Éditeur de sections d'options (dashboard admin)

**Files:**
- Modify: `mysugu-admin/src/types/index.ts` (types options)
- Modify: `mysugu-admin/src/api/index.ts` (getPlatOptions / updatePlatOptions)
- Create: `mysugu-admin/src/components/PlatOptionsEditor.tsx`
- Modify: `mysugu-admin/src/pages/RestaurantMenu.tsx` (bouton « Options » par plat ouvrant l'éditeur)

**Interfaces:**
- Consumes: backend `GET/PUT /api/plats/{id}/options`.
- Produces: composant `PlatOptionsEditor` (modal) ; types `OptionGroupDTO`/`OptionItemDTO` (admin).

- [ ] **Step 1: Ajouter les types dans `types/index.ts`**

À la fin du fichier :
```ts
// ─── Options de plats ─────────────────────────────────────────────────────────
export interface OptionItemDTO {
  id?: number
  nom: string
  prixSupplement: number
  disponible: boolean
  ordre?: number
}
export interface OptionGroupDTO {
  id?: number
  nom: string
  selectionMode: "SINGLE" | "MULTIPLE"
  obligatoire: boolean
  minSelections: number
  maxSelections?: number | null
  ordre?: number
  items: OptionItemDTO[]
}
```

- [ ] **Step 2: Ajouter les fonctions API dans `api/index.ts`**

Ajouter `OptionGroupDTO` à l'import de types existant (`import type { ... } from "../types"`), puis en fin de fichier :
```ts
// ─── Options de plats ─────────────────────────────────────────────────────────
export const getPlatOptions = (platId: number) =>
  apiClient.get<OptionGroupDTO[]>(`/api/plats/${platId}/options`).then(r => r.data)

export const updatePlatOptions = (platId: number, groups: OptionGroupDTO[]) =>
  apiClient.put<OptionGroupDTO[]>(`/api/plats/${platId}/options`, groups).then(r => r.data)
```

- [ ] **Step 3: Créer `PlatOptionsEditor.tsx`**

```tsx
import { useEffect, useState } from "react"
import { Plus, Trash2, X } from "lucide-react"
import { Button } from "./ui/button"
import { Input } from "./ui/input"
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "./ui/dialog"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "./ui/select"
import { getPlatOptions, updatePlatOptions } from "../api"
import type { OptionGroupDTO } from "../types"
import toast from "react-hot-toast"

const newGroup = (): OptionGroupDTO => ({
  nom: "", selectionMode: "SINGLE", obligatoire: false, minSelections: 0, maxSelections: null, items: [],
})

export function PlatOptionsEditor({ platId, platNom, open, onClose }: {
  platId: number; platNom: string; open: boolean; onClose: () => void
}) {
  const [groups, setGroups] = useState<OptionGroupDTO[]>([])
  const [loading, setLoading] = useState(false)
  const [saving, setSaving] = useState(false)

  useEffect(() => {
    if (!open) return
    setLoading(true)
    getPlatOptions(platId)
      .then(setGroups)
      .catch(() => toast.error("Impossible de charger les options"))
      .finally(() => setLoading(false))
  }, [open, platId])

  const update = (gi: number, patch: Partial<OptionGroupDTO>) =>
    setGroups(prev => prev.map((g, i) => i === gi ? { ...g, ...patch } : g))
  const updateItem = (gi: number, ii: number, patch: Partial<OptionGroupDTO["items"][number]>) =>
    setGroups(prev => prev.map((g, i) => i !== gi ? g : { ...g, items: g.items.map((it, j) => j === ii ? { ...it, ...patch } : it) }))

  const handleSave = async () => {
    for (const g of groups) {
      if (!g.nom.trim()) { toast.error("Chaque section doit avoir un nom"); return }
      if (g.items.some(it => !it.nom.trim())) { toast.error("Chaque choix doit avoir un nom"); return }
    }
    setSaving(true)
    try {
      const saved = await updatePlatOptions(platId, groups)
      setGroups(saved)
      toast.success("Options enregistrées")
      onClose()
    } catch { toast.error("Erreur lors de l'enregistrement") }
    finally { setSaving(false) }
  }

  return (
    <Dialog open={open} onOpenChange={onClose}>
      <DialogContent className="max-w-2xl max-h-[85vh] overflow-y-auto">
        <DialogHeader><DialogTitle>Options — {platNom}</DialogTitle></DialogHeader>
        {loading ? <p className="text-sm text-muted-foreground p-4">Chargement…</p> : (
          <div className="space-y-4 mt-2">
            {groups.map((g, gi) => (
              <div key={gi} className="rounded-xl border border-surface-600 p-4 space-y-3">
                <div className="flex items-center gap-2">
                  <Input value={g.nom} onChange={e => update(gi, { nom: e.target.value })} placeholder="Nom de la section (ex: Accompagnement)" />
                  <Button variant="ghost" size="icon" className="h-8 w-8 text-red-400" onClick={() => setGroups(prev => prev.filter((_, i) => i !== gi))}><Trash2 className="w-4 h-4" /></Button>
                </div>
                <div className="grid grid-cols-2 gap-3">
                  <Select value={g.selectionMode} onValueChange={v => update(gi, { selectionMode: v as "SINGLE" | "MULTIPLE", maxSelections: v === "SINGLE" ? 1 : g.maxSelections })}>
                    <SelectTrigger><SelectValue /></SelectTrigger>
                    <SelectContent>
                      <SelectItem value="SINGLE">Choix unique</SelectItem>
                      <SelectItem value="MULTIPLE">Choix multiple</SelectItem>
                    </SelectContent>
                  </Select>
                  <label className="flex items-center gap-2 text-sm">
                    <input type="checkbox" checked={g.obligatoire} onChange={e => update(gi, { obligatoire: e.target.checked })} /> Obligatoire
                  </label>
                </div>
                {g.selectionMode === "MULTIPLE" && (
                  <div className="grid grid-cols-2 gap-3">
                    <Input type="number" min={0} value={g.minSelections} onChange={e => update(gi, { minSelections: Number(e.target.value) })} placeholder="min" />
                    <Input type="number" min={1} value={g.maxSelections ?? ""} onChange={e => update(gi, { maxSelections: e.target.value === "" ? null : Number(e.target.value) })} placeholder="max (vide = illimité)" />
                  </div>
                )}
                <div className="space-y-2">
                  {g.items.map((it, ii) => (
                    <div key={ii} className="flex items-center gap-2">
                      <Input value={it.nom} onChange={e => updateItem(gi, ii, { nom: e.target.value })} placeholder="Nom du choix" />
                      <Input type="number" min={0} step={0.5} className="w-28" value={it.prixSupplement} onChange={e => updateItem(gi, ii, { prixSupplement: Number(e.target.value) })} placeholder="prix (0=inclus)" />
                      <label className="flex items-center gap-1 text-xs whitespace-nowrap">
                        <input type="checkbox" checked={it.disponible} onChange={e => updateItem(gi, ii, { disponible: e.target.checked })} /> dispo
                      </label>
                      <Button variant="ghost" size="icon" className="h-7 w-7 text-red-400" onClick={() => update(gi, { items: g.items.filter((_, j) => j !== ii) })}><X className="w-3.5 h-3.5" /></Button>
                    </div>
                  ))}
                  <Button variant="outline" size="sm" onClick={() => update(gi, { items: [...g.items, { nom: "", prixSupplement: 0, disponible: true }] })}><Plus className="w-3.5 h-3.5" /> Ajouter un choix</Button>
                </div>
              </div>
            ))}
            <Button variant="outline" onClick={() => setGroups(prev => [...prev, newGroup()])}><Plus className="w-4 h-4" /> Ajouter une section</Button>
            <div className="flex justify-end gap-2 pt-2">
              <Button variant="ghost" onClick={onClose}>Annuler</Button>
              <Button onClick={handleSave} disabled={saving}>{saving ? "…" : "Enregistrer"}</Button>
            </div>
          </div>
        )}
      </DialogContent>
    </Dialog>
  )
}
```

- [ ] **Step 4: Ajouter le bouton « Options » par plat dans `RestaurantMenu.tsx`**

Importer le composant et un état d'ouverture, et ajouter un bouton sur chaque carte de plat (à côté du crayon inactif et du toggle disponibilité). Concrètement :
- Import : `import { PlatOptionsEditor } from "../components/PlatOptionsEditor"` et `import { SlidersHorizontal } from "lucide-react"` (ajouter à l'import lucide existant).
- État : `const [optionsPlat, setOptionsPlat] = useState<PlatDTO | null>(null)`.
- Sur chaque carte de plat, à côté des actions existantes, ajouter :
```tsx
<button onClick={() => setOptionsPlat(p)} title="Options" className="p-1.5 rounded hover:bg-surface-700">
  <SlidersHorizontal className="w-4 h-4" />
</button>
```
- En bas du composant (avant la dernière balise fermante), monter l'éditeur :
```tsx
{optionsPlat && (
  <PlatOptionsEditor platId={optionsPlat.id} platNom={optionsPlat.nom} open={!!optionsPlat} onClose={() => setOptionsPlat(null)} />
)}
```
(`PlatDTO` est déjà importé dans `RestaurantMenu.tsx` ; `p` est l'objet plat de la boucle de rendu.)

- [ ] **Step 5: Type-check**

Run: `cd "/mnt/c/Users/Issiaka traoré/Desktop/systalink/mysugu/mysugu-admin" && npx tsc --noEmit; echo "TSC_EXIT:$?"`
Expected: `TSC_EXIT:0`

- [ ] **Step 6: Commit**

```bash
cd "/mnt/c/Users/Issiaka traoré/Desktop/systalink/mysugu/mysugu-admin"
git add src/types/index.ts src/api/index.ts src/components/PlatOptionsEditor.tsx src/pages/RestaurantMenu.tsx
git commit -m "feat(plat-options): editeur de sections d'options par plat (dashboard)"
```

---

### Task 5 : Affichage des sections d'options sur la fiche plat (app users)

**Files:**
- Create: `mysugu-frontend/src/api/platOptions.api.ts`
- Modify: `mysugu-frontend/src/pages/RestaurantDetailPage.tsx` (afficher les options du plat sélectionné — lecture seule)

**Interfaces:**
- Consumes: backend `GET /api/plats/{id}/options`.
- Produces: `platOptionsApi.get(platId)` ; affichage des sections sur la fiche plat (Phase 2 = lecture seule, la sélection + panier sont Phase 3).

- [ ] **Step 1: Créer `platOptions.api.ts`**

```ts
import apiClient from './apiClient'

export interface ApiOptionItem {
  id: number
  nom: string
  prixSupplement: number
  disponible: boolean
  ordre: number
}
export interface ApiOptionGroup {
  id: number
  nom: string
  selectionMode: 'SINGLE' | 'MULTIPLE'
  obligatoire: boolean
  minSelections: number
  maxSelections?: number | null
  ordre: number
  items: ApiOptionItem[]
}

export const platOptionsApi = {
  get: (platId: number) => apiClient.get<ApiOptionGroup[]>(`/api/plats/${platId}/options`),
}
```

- [ ] **Step 2: Afficher les sections sur la fiche plat dans `RestaurantDetailPage.tsx`**

Repérer le composant/zone affichant le détail d'un plat sélectionné (là où s'affichent nom/description/ingrédients/prix avant l'ajout au panier). Y ajouter le chargement et l'affichage en lecture seule des sections :
- Imports : `import { useState, useEffect } from 'react'` (déjà présents) et `import { platOptionsApi, type ApiOptionGroup } from '@/api/platOptions.api'`.
- État + effet (clé = id du plat affiché ; adapter le nom de la variable au plat sélectionné réel de la page) :
```tsx
const [platOptions, setPlatOptions] = useState<ApiOptionGroup[]>([])
useEffect(() => {
  if (!selectedPlat?.id) { setPlatOptions([]); return }
  platOptionsApi.get(selectedPlat.id)
    .then(r => setPlatOptions(r.data))
    .catch(() => setPlatOptions([]))
}, [selectedPlat?.id])
```
- Rendu (sous les ingrédients, au-dessus du bouton d'ajout au panier) :
```tsx
{platOptions.map(g => (
  <div key={g.id} className="mt-4">
    <div className="flex items-center gap-2 mb-1">
      <h4 className="font-semibold text-sm">{g.nom}</h4>
      {g.obligatoire && <span className="text-[10px] uppercase tracking-wide text-brand-500">obligatoire</span>}
      <span className="text-[10px] text-warm-400">{g.selectionMode === 'SINGLE' ? 'choisir 1' : 'choix multiple'}</span>
    </div>
    <ul className="space-y-1">
      {g.items.map(it => (
        <li key={it.id} className="flex justify-between text-sm text-warm-600">
          <span className={it.disponible ? '' : 'line-through opacity-50'}>{it.nom}</span>
          <span>{it.prixSupplement > 0 ? `+${it.prixSupplement} DH` : 'inclus'}</span>
        </li>
      ))}
    </ul>
  </div>
))}
```
Note : adapter `selectedPlat` au nom réel de l'état du plat affiché dans `RestaurantDetailPage.tsx` (lire le fichier pour trouver la variable du plat en cours d'affichage dans la modale/section détail). Phase 2 = AFFICHAGE seulement ; ne pas brancher la sélection sur le panier (Phase 3).

- [ ] **Step 3: Type-check**

Run: `cd "/mnt/c/Users/Issiaka traoré/Desktop/systalink/mysugu/mysugu-frontend" && npx tsc -b --noEmit; echo "TSC_EXIT:$?"`
Expected: `TSC_EXIT:0`

- [ ] **Step 4: Commit**

```bash
cd "/mnt/c/Users/Issiaka traoré/Desktop/systalink/mysugu/mysugu-frontend"
git add src/api/platOptions.api.ts src/pages/RestaurantDetailPage.tsx
git commit -m "feat(plat-options): affichage des sections d'options sur la fiche plat (lecture seule)"
```

---

## Notes d'exécution
- Branches dédiées `feat/plat-options` : backend depuis `wip/restaurateur-onboarding`, admin + app users depuis `feat/filtres`. Créer la branche AVANT le premier commit de chaque repo.
- Phase 3 (sélection client + validation + prix dans panier/commande) fera l'objet d'un plan séparé.

## Self-review (couverture spec, section #2 « définition »)
- Modèle riche (SINGLE/MULTIPLE, obligatoire, min/max, items + prix) : Task 1. ✓
- DTOs + PlatDTO.optionGroups : Task 2. ✓
- API replace-all GET public / PUT sécurisé ADMIN|propriétaire + invariants : Task 3. ✓
- Éditeur dashboard (admin + restaurateur via mêmes endpoints) : Task 4. ✓
- Affichage app users (lecture seule, sans panier) : Task 5. ✓
- Hors périmètre (Phase 3) : sélection client, validation au panier, prix répercuté, snapshots commande. ✓ (explicitement exclus)
