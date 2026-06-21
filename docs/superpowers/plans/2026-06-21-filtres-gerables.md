# Filtres gérables (#5) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rendre les filtres de l'app users (Mieux notés, Promotions, etc.) gérables depuis le dashboard admin (lister, ajouter, modifier, supprimer, réordonner), par verticale.

**Architecture:** Nouvelle entité backend `Filtre` (contexte + comportement prédéfini + libellé/icône/ordre/actif + categorieId optionnel). API publique en lecture (`GET /api/filtres?contexte=`) consommée par l'app users, API admin CRUD (`/api/admin/filtres`). Le **comportement** (tri/filtre) reste implémenté côté app, mappé par la valeur `comportement` ; seuls les filtres `CATEGORIE` sont des créations data libres. Un initializer idempotent sème les filtres actuels.

**Tech Stack:** Spring Boot 4 / JPA / Postgres (backend) ; React + Vite + TypeScript + Tailwind (mysugu-admin, mysugu-frontend) ; Lucide icons.

## Global Constraints

- Backend Java 21, Spring Boot 4.0.2 ; build via `cmd.exe /c "C:\Users\Issiaka traoré\Desktop\systalink\mysugu\_build_local.bat"` (toolchain Windows ; JDK Linux indisponible). Succès = ligne `BUILD_EXIT=0` dans `/tmp/winbuild.log`.
- Backend local lancé via `_run_local.bat` (docker postgres :5433 + minio :9100, `TEST_DATA_ENABLED=true`), joignable depuis WSL via l'IP de la passerelle par défaut (ex. `http://172.20.128.1:8083`).
- Front : vérif via `npx tsc --noEmit` (admin) / `npx tsc -b --noEmit` (app users). Succès = `TSC_EXIT:0`.
- Enums sérialisés en STRING (`@Enumerated(EnumType.STRING)`).
- Aucune régression : le seed reproduit les filtres actuels par contexte.
- Conventions existantes : commentaires en français ; DTO `@Data` Lombok ; services `@RequiredArgsConstructor`.
- Commits fréquents ; rien n'est poussé/déployé sans demande.

---

### Task 1 : Enum + entité `Filtre` + repository (backend)

**Files:**
- Create: `MySuguClientApp/src/main/java/ma/mysuguclientapp/enumerations/FiltreContexte.java`
- Create: `MySuguClientApp/src/main/java/ma/mysuguclientapp/enumerations/FiltreComportement.java`
- Create: `MySuguClientApp/src/main/java/ma/mysuguclientapp/entities/Filtre.java`
- Create: `MySuguClientApp/src/main/java/ma/mysuguclientapp/repositories/FiltreRepository.java`

**Interfaces:**
- Produces: enums `FiltreContexte{RESTAURANT,ALIMENTAIRE,COSMETIQUE}`, `FiltreComportement{TOUS,PROMOTIONS,MIEUX_NOTES,PLUS_PROCHES,PLUS_RAPIDES,OUVERTS,CATEGORIE}` ; entity `Filtre` ; `FiltreRepository` avec `findByContexteAndActifTrueOrderByOrdreAsc(FiltreContexte)`, `findByContexteOrderByOrdreAsc(FiltreContexte)`, `count()`.

- [ ] **Step 1: Créer les enums**

`FiltreContexte.java` :
```java
package ma.mysuguclientapp.enumerations;

public enum FiltreContexte {
    RESTAURANT,
    ALIMENTAIRE,
    COSMETIQUE
}
```

`FiltreComportement.java` :
```java
package ma.mysuguclientapp.enumerations;

/** Comportements de filtre connus, implémentés côté app users. */
public enum FiltreComportement {
    TOUS,
    PROMOTIONS,
    MIEUX_NOTES,
    PLUS_PROCHES,
    PLUS_RAPIDES,
    OUVERTS,
    CATEGORIE
}
```

- [ ] **Step 2: Créer l'entité `Filtre.java`**

```java
package ma.mysuguclientapp.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import ma.mysuguclientapp.enumerations.FiltreComportement;
import ma.mysuguclientapp.enumerations.FiltreContexte;

@Entity
@Table(name = "filtres")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Filtre {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Barre concernée (verticale). */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FiltreContexte contexte;

    /** Comportement connu, interprété côté app. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FiltreComportement comportement;

    /** Catégorie ciblée lorsque comportement = CATEGORIE. */
    @Column(name = "categorie_id")
    private Long categorieId;

    @Column(nullable = false)
    private String libelle;

    /** Clé d'icône Lucide (ex: "Star"). */
    private String icone;

    @Column(nullable = false)
    private Integer ordre = 0;

    @Column(nullable = false)
    private Boolean actif = true;
}
```

- [ ] **Step 3: Créer le repository `FiltreRepository.java`**

```java
package ma.mysuguclientapp.repositories;

import ma.mysuguclientapp.entities.Filtre;
import ma.mysuguclientapp.enumerations.FiltreContexte;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FiltreRepository extends JpaRepository<Filtre, Long> {
    List<Filtre> findByContexteAndActifTrueOrderByOrdreAsc(FiltreContexte contexte);
    List<Filtre> findByContexteOrderByOrdreAsc(FiltreContexte contexte);
}
```

- [ ] **Step 4: Build**

Run: `cmd.exe /c "C:\Users\Issiaka traoré\Desktop\systalink\mysugu\_build_local.bat" > /tmp/winbuild.log 2>&1; tail -3 /tmp/winbuild.log`
Expected: `BUILD_EXIT=0`

- [ ] **Step 5: Commit**

```bash
cd "/mnt/c/Users/Issiaka traoré/Desktop/systalink/mysugu/MySuguClientApp"
git add src/main/java/ma/mysuguclientapp/enumerations/FiltreContexte.java \
        src/main/java/ma/mysuguclientapp/enumerations/FiltreComportement.java \
        src/main/java/ma/mysuguclientapp/entities/Filtre.java \
        src/main/java/ma/mysuguclientapp/repositories/FiltreRepository.java
git commit -m "feat(filtres): enum contexte/comportement + entite Filtre + repository"
```

---

### Task 2 : DTO + service `Filtre` (backend)

**Files:**
- Create: `MySuguClientApp/src/main/java/ma/mysuguclientapp/dtos/FiltreDTO.java`
- Create: `MySuguClientApp/src/main/java/ma/mysuguclientapp/services/interfaces/FiltreService.java`
- Create: `MySuguClientApp/src/main/java/ma/mysuguclientapp/services/implementations/FiltreServiceImpl.java`

**Interfaces:**
- Consumes: `FiltreRepository`, enums (Task 1).
- Produces: `FiltreDTO{id,contexte,comportement,categorieId,libelle,icone,ordre,actif}` (String pour contexte/comportement) ; `FiltreService` : `List<FiltreDTO> getByContexte(String contexte, boolean inclureInactifs)`, `FiltreDTO create(FiltreDTO)`, `FiltreDTO update(Long, FiltreDTO)`, `void delete(Long)`.

- [ ] **Step 1: Créer `FiltreDTO.java`**

```java
package ma.mysuguclientapp.dtos;

import lombok.Data;

@Data
public class FiltreDTO {
    private Long id;
    private String contexte;       // RESTAURANT | ALIMENTAIRE | COSMETIQUE
    private String comportement;   // TOUS | PROMOTIONS | ...
    private Long categorieId;
    private String libelle;
    private String icone;
    private Integer ordre;
    private Boolean actif;
}
```

- [ ] **Step 2: Créer l'interface `FiltreService.java`**

```java
package ma.mysuguclientapp.services.interfaces;

import ma.mysuguclientapp.dtos.FiltreDTO;

import java.util.List;

public interface FiltreService {
    List<FiltreDTO> getByContexte(String contexte, boolean inclureInactifs);
    FiltreDTO create(FiltreDTO dto);
    FiltreDTO update(Long id, FiltreDTO dto);
    void delete(Long id);
}
```

- [ ] **Step 3: Créer `FiltreServiceImpl.java`**

```java
package ma.mysuguclientapp.services.implementations;

import lombok.RequiredArgsConstructor;
import ma.mysuguclientapp.dtos.FiltreDTO;
import ma.mysuguclientapp.entities.Filtre;
import ma.mysuguclientapp.enumerations.FiltreComportement;
import ma.mysuguclientapp.enumerations.FiltreContexte;
import ma.mysuguclientapp.exceptions.BadRequestException;
import ma.mysuguclientapp.exceptions.ResourceNotFoundException;
import ma.mysuguclientapp.repositories.FiltreRepository;
import ma.mysuguclientapp.services.interfaces.FiltreService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class FiltreServiceImpl implements FiltreService {

    private final FiltreRepository filtreRepository;

    @Override
    @Transactional(readOnly = true)
    public List<FiltreDTO> getByContexte(String contexte, boolean inclureInactifs) {
        FiltreContexte ctx = parseContexte(contexte);
        List<Filtre> list = inclureInactifs
                ? filtreRepository.findByContexteOrderByOrdreAsc(ctx)
                : filtreRepository.findByContexteAndActifTrueOrderByOrdreAsc(ctx);
        return list.stream().map(this::toDTO).collect(Collectors.toList());
    }

    @Override
    public FiltreDTO create(FiltreDTO dto) {
        Filtre f = new Filtre();
        applyDto(f, dto);
        return toDTO(filtreRepository.save(f));
    }

    @Override
    public FiltreDTO update(Long id, FiltreDTO dto) {
        Filtre f = filtreRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Filtre non trouvé"));
        applyDto(f, dto);
        return toDTO(filtreRepository.save(f));
    }

    @Override
    public void delete(Long id) {
        if (!filtreRepository.existsById(id)) {
            throw new ResourceNotFoundException("Filtre non trouvé");
        }
        filtreRepository.deleteById(id);
    }

    private void applyDto(Filtre f, FiltreDTO dto) {
        if (dto.getContexte() != null) f.setContexte(parseContexte(dto.getContexte()));
        if (dto.getComportement() != null) f.setComportement(parseComportement(dto.getComportement()));
        if (dto.getLibelle() != null) f.setLibelle(dto.getLibelle().trim());
        f.setIcone(dto.getIcone());
        f.setCategorieId(dto.getCategorieId());
        if (dto.getOrdre() != null) f.setOrdre(dto.getOrdre());
        if (dto.getActif() != null) f.setActif(dto.getActif());
        if (f.getLibelle() == null || f.getLibelle().isBlank()) {
            throw new BadRequestException("Le libellé est obligatoire");
        }
        if (f.getComportement() == FiltreComportement.CATEGORIE && f.getCategorieId() == null) {
            throw new BadRequestException("Un filtre CATEGORIE doit référencer une catégorie");
        }
    }

    private FiltreContexte parseContexte(String s) {
        try { return FiltreContexte.valueOf(s.trim().toUpperCase()); }
        catch (Exception e) { throw new BadRequestException("Contexte invalide: " + s); }
    }

    private FiltreComportement parseComportement(String s) {
        try { return FiltreComportement.valueOf(s.trim().toUpperCase()); }
        catch (Exception e) { throw new BadRequestException("Comportement invalide: " + s); }
    }

    private FiltreDTO toDTO(Filtre f) {
        FiltreDTO dto = new FiltreDTO();
        dto.setId(f.getId());
        dto.setContexte(f.getContexte() != null ? f.getContexte().name() : null);
        dto.setComportement(f.getComportement() != null ? f.getComportement().name() : null);
        dto.setCategorieId(f.getCategorieId());
        dto.setLibelle(f.getLibelle());
        dto.setIcone(f.getIcone());
        dto.setOrdre(f.getOrdre());
        dto.setActif(f.getActif());
        return dto;
    }
}
```

- [ ] **Step 4: Build**

Run: `cmd.exe /c "C:\Users\Issiaka traoré\Desktop\systalink\mysugu\_build_local.bat" > /tmp/winbuild.log 2>&1; tail -3 /tmp/winbuild.log`
Expected: `BUILD_EXIT=0` (vérifie au passage que `BadRequestException`/`ResourceNotFoundException` existent bien dans le package `exceptions` — ils sont déjà utilisés ailleurs).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/ma/mysuguclientapp/dtos/FiltreDTO.java \
        src/main/java/ma/mysuguclientapp/services/interfaces/FiltreService.java \
        src/main/java/ma/mysuguclientapp/services/implementations/FiltreServiceImpl.java
git commit -m "feat(filtres): DTO + service CRUD"
```

---

### Task 3 : Controllers public + admin + sécurité (backend)

**Files:**
- Create: `MySuguClientApp/src/main/java/ma/mysuguclientapp/controllers/FiltreController.java`
- Create: `MySuguClientApp/src/main/java/ma/mysuguclientapp/controllers/AdminFiltreController.java`
- Modify: `MySuguClientApp/src/main/java/ma/mysuguclientapp/config/security/SecurityConfig.java` (ajouter une règle GET publique)

**Interfaces:**
- Consumes: `FiltreService` (Task 2).
- Produces: routes `GET /api/filtres?contexte=`, `GET /api/admin/filtres?contexte=`, `POST/PUT/DELETE /api/admin/filtres`.

- [ ] **Step 1: Créer `FiltreController.java` (public, lecture)**

```java
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
```

- [ ] **Step 2: Créer `AdminFiltreController.java`**

```java
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
```

- [ ] **Step 3: Rendre `GET /api/filtres` public dans SecurityConfig**

Dans `SecurityConfig.java`, sous le bloc « Routes publiques en lecture seule », après la ligne `.requestMatchers(HttpMethod.GET, "/api/zones-deploiement/actives").permitAll()`, ajouter :
```java
                        .requestMatchers(HttpMethod.GET, "/api/filtres").permitAll()
```
(`/api/admin/filtres` est déjà couvert par la règle `/api/admin/**` = `hasRole("ADMIN")`.)

- [ ] **Step 4: Build**

Run: `cmd.exe /c "C:\Users\Issiaka traoré\Desktop\systalink\mysugu\_build_local.bat" > /tmp/winbuild.log 2>&1; tail -3 /tmp/winbuild.log`
Expected: `BUILD_EXIT=0`

- [ ] **Step 5: Commit**

```bash
git add src/main/java/ma/mysuguclientapp/controllers/FiltreController.java \
        src/main/java/ma/mysuguclientapp/controllers/AdminFiltreController.java \
        src/main/java/ma/mysuguclientapp/config/security/SecurityConfig.java
git commit -m "feat(filtres): controllers public /api/filtres + admin /api/admin/filtres + securite"
```

---

### Task 4 : Seed idempotent des filtres par défaut (backend)

**Files:**
- Create: `MySuguClientApp/src/main/java/ma/mysuguclientapp/config/FiltreInitializer.java`

**Interfaces:**
- Consumes: `FiltreRepository`, entité `Filtre`, enums.
- Produces: au démarrage, si `filtres` est vide, insère les filtres actuels des 3 barres.

- [ ] **Step 1: Créer `FiltreInitializer.java`**

```java
package ma.mysuguclientapp.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.mysuguclientapp.entities.Filtre;
import ma.mysuguclientapp.enumerations.FiltreComportement;
import ma.mysuguclientapp.enumerations.FiltreContexte;
import ma.mysuguclientapp.repositories.FiltreRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/** Sème les filtres par défaut au premier démarrage (idempotent : ne fait rien si la table contient déjà des filtres). */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(20)
public class FiltreInitializer implements CommandLineRunner {

    private final FiltreRepository filtreRepository;

    @Override
    @Transactional
    public void run(String... args) {
        if (filtreRepository.count() > 0) {
            return;
        }
        List<Filtre> defaults = new ArrayList<>();

        // RESTAURANT
        defaults.add(f(FiltreContexte.RESTAURANT, FiltreComportement.TOUS, "Tous", "Flame", 0));
        defaults.add(f(FiltreContexte.RESTAURANT, FiltreComportement.PROMOTIONS, "Promotions", "Zap", 1));
        defaults.add(f(FiltreContexte.RESTAURANT, FiltreComportement.MIEUX_NOTES, "Mieux notés", "Star", 2));
        defaults.add(f(FiltreContexte.RESTAURANT, FiltreComportement.PLUS_PROCHES, "Plus proches", "MapPin", 3));
        defaults.add(f(FiltreContexte.RESTAURANT, FiltreComportement.PLUS_RAPIDES, "Plus rapides", "Clock", 4));
        defaults.add(f(FiltreContexte.RESTAURANT, FiltreComportement.OUVERTS, "Ouverts", "Zap", 5));

        // ALIMENTAIRE
        defaults.add(f(FiltreContexte.ALIMENTAIRE, FiltreComportement.TOUS, "Tous", "Apple", 0));
        defaults.add(f(FiltreContexte.ALIMENTAIRE, FiltreComportement.PROMOTIONS, "Promotions", "Tag", 1));
        defaults.add(f(FiltreContexte.ALIMENTAIRE, FiltreComportement.PLUS_PROCHES, "Plus proches", "MapPin", 2));
        defaults.add(f(FiltreContexte.ALIMENTAIRE, FiltreComportement.PLUS_RAPIDES, "Plus rapides", "Clock", 3));

        // COSMETIQUE
        defaults.add(f(FiltreContexte.COSMETIQUE, FiltreComportement.TOUS, "Tous", "Sparkles", 0));
        defaults.add(f(FiltreContexte.COSMETIQUE, FiltreComportement.PROMOTIONS, "Promotions", "Tag", 1));
        defaults.add(f(FiltreContexte.COSMETIQUE, FiltreComportement.PLUS_PROCHES, "Plus proches", "MapPin", 2));
        defaults.add(f(FiltreContexte.COSMETIQUE, FiltreComportement.PLUS_RAPIDES, "Plus rapides", "Clock", 3));

        filtreRepository.saveAll(defaults);
        log.info("Filtres par défaut semés: {}", defaults.size());
    }

    private Filtre f(FiltreContexte ctx, FiltreComportement comp, String libelle, String icone, int ordre) {
        Filtre filtre = new Filtre();
        filtre.setContexte(ctx);
        filtre.setComportement(comp);
        filtre.setLibelle(libelle);
        filtre.setIcone(icone);
        filtre.setOrdre(ordre);
        filtre.setActif(true);
        return filtre;
    }
}
```

- [ ] **Step 2: Build**

Run: `cmd.exe /c "C:\Users\Issiaka traoré\Desktop\systalink\mysugu\_build_local.bat" > /tmp/winbuild.log 2>&1; tail -3 /tmp/winbuild.log`
Expected: `BUILD_EXIT=0`

- [ ] **Step 3: Lancer le backend local et vérifier l'endpoint**

Redémarrer le backend (tuer l'instance Windows existante puis relancer) :
```bash
cmd.exe /c "taskkill /F /IM java.exe" 2>/dev/null
cmd.exe /c "C:\Users\Issiaka traoré\Desktop\systalink\mysugu\_run_local.bat" > /tmp/apprun.log 2>&1 &
until grep -qE "Started MySuguClientApp|APPLICATION FAILED TO START" /tmp/apprun.log; do sleep 3; done
```
Puis (IP passerelle = `ip route | grep default | awk '{print $3}'`) :
```bash
BASE=http://$(ip route | grep default | awk '{print $3}'):8083
curl -s "$BASE/api/filtres?contexte=RESTAURANT" | python3 -m json.tool | head -40
```
Expected: liste de 6 filtres RESTAURANT (Tous, Promotions, Mieux notés, Plus proches, Plus rapides, Ouverts) avec `comportement`, `libelle`, `icone`, `ordre`, `actif:true`.

- [ ] **Step 4: Vérifier le CRUD admin (avec token admin local)**

```bash
ADMTOK=$(curl -s -X POST $BASE/auth/login -H "Content-Type: application/json" -d '{"email":"admin.demo@mysuku.ma","password":"demo1234"}' | python3 -c "import sys,json;print(json.load(sys.stdin)['token'])")
# create
curl -s -X POST $BASE/api/admin/filtres -H "Authorization: Bearer $ADMTOK" -H "Content-Type: application/json" \
  -d '{"contexte":"RESTAURANT","comportement":"CATEGORIE","categorieId":1,"libelle":"Pizza","icone":"Tag","ordre":6,"actif":true}'
# list (admin, inactifs inclus)
curl -s "$BASE/api/admin/filtres?contexte=RESTAURANT" -H "Authorization: Bearer $ADMTOK" | python3 -c "import sys,json;print(len(json.load(sys.stdin)),'filtres')"
```
Expected: création 201 + le compte admin ≥ 7.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/ma/mysuguclientapp/config/FiltreInitializer.java
git commit -m "feat(filtres): seed idempotent des filtres par defaut (3 contextes)"
```

---

### Task 5 : API + types côté dashboard admin

**Files:**
- Modify: `mysugu-admin/src/types/index.ts` (ajouter `FiltreDTO`, catalogues)
- Modify: `mysugu-admin/src/api/index.ts` (ajouter les fonctions filtres)

**Interfaces:**
- Produces: type `FiltreDTO` ; constantes `FILTRE_CONTEXTES`, `FILTRE_COMPORTEMENTS` ; fonctions `getFiltresAdmin(contexte)`, `createFiltre(data)`, `updateFiltre(id,data)`, `deleteFiltre(id)`.

- [ ] **Step 1: Ajouter le type + catalogues dans `types/index.ts`**

Ajouter à la fin du fichier :
```ts
// ─── Filtres (gérables) ───────────────────────────────────────────────────────
export interface FiltreDTO {
  id?: number
  contexte: string        // RESTAURANT | ALIMENTAIRE | COSMETIQUE
  comportement: string    // TOUS | PROMOTIONS | MIEUX_NOTES | PLUS_PROCHES | PLUS_RAPIDES | OUVERTS | CATEGORIE
  categorieId?: number | null
  libelle: string
  icone?: string
  ordre: number
  actif: boolean
}

export const FILTRE_CONTEXTES: { value: string; label: string }[] = [
  { value: "RESTAURANT", label: "Restaurants" },
  { value: "ALIMENTAIRE", label: "Alimentaire" },
  { value: "COSMETIQUE", label: "Cosmétique" },
]

export const FILTRE_COMPORTEMENTS: { value: string; label: string }[] = [
  { value: "TOUS", label: "Tous" },
  { value: "PROMOTIONS", label: "Promotions" },
  { value: "MIEUX_NOTES", label: "Mieux notés" },
  { value: "PLUS_PROCHES", label: "Plus proches" },
  { value: "PLUS_RAPIDES", label: "Plus rapides" },
  { value: "OUVERTS", label: "Ouverts" },
  { value: "CATEGORIE", label: "Par catégorie" },
]
```

- [ ] **Step 2: Ajouter les fonctions API dans `api/index.ts`**

Ajouter à la fin du fichier (et vérifier que `FiltreDTO` est importé depuis `../types` en haut — sinon ajouter à l'import existant `import type { ... } from "../types"`) :
```ts
// ─── Filtres (gérables) ───────────────────────────────────────────────────────
export const getFiltresAdmin = (contexte: string) =>
  apiClient.get<import("../types").FiltreDTO[]>("/api/admin/filtres", { params: { contexte } }).then(r => r.data)

export const createFiltre = (data: import("../types").FiltreDTO) =>
  apiClient.post<import("../types").FiltreDTO>("/api/admin/filtres", data).then(r => r.data)

export const updateFiltre = (id: number, data: import("../types").FiltreDTO) =>
  apiClient.put<import("../types").FiltreDTO>(`/api/admin/filtres/${id}`, data).then(r => r.data)

export const deleteFiltre = (id: number) =>
  apiClient.delete(`/api/admin/filtres/${id}`)
```

- [ ] **Step 3: Type-check**

Run: `cd "/mnt/c/Users/Issiaka traoré/Desktop/systalink/mysugu/mysugu-admin" && npx tsc --noEmit; echo "TSC_EXIT:$?"`
Expected: `TSC_EXIT:0`

- [ ] **Step 4: Commit**

```bash
cd "/mnt/c/Users/Issiaka traoré/Desktop/systalink/mysugu/mysugu-admin"
git add src/types/index.ts src/api/index.ts
git commit -m "feat(filtres): API admin + types cote dashboard"
```

---

### Task 6 : Page « Filtres » + route + Sidebar (dashboard admin)

**Files:**
- Create: `mysugu-admin/src/pages/Filtres.tsx`
- Modify: `mysugu-admin/src/App.tsx` (import + route `filtres`)
- Modify: `mysugu-admin/src/components/layout/Sidebar.tsx` (entrée de menu + import icône)

**Interfaces:**
- Consumes: `getFiltresAdmin/createFiltre/updateFiltre/deleteFiltre` + `FiltreDTO`, `FILTRE_CONTEXTES`, `FILTRE_COMPORTEMENTS` (Task 5) ; `getCategoriesRestaurant` (existant) pour le sélecteur de catégorie.
- Produces: page accessible à `/filtres`.

- [ ] **Step 1: Créer `Filtres.tsx`**

```tsx
import { useEffect, useState, useCallback } from "react"
import { Plus, Pencil, Trash2, RefreshCw, ArrowUp, ArrowDown } from "lucide-react"
import { Topbar } from "../components/layout/Topbar"
import { Button } from "../components/ui/button"
import { Input } from "../components/ui/input"
import { Badge } from "../components/ui/badge"
import { Dialog, DialogContent, DialogHeader, DialogTitle } from "../components/ui/dialog"
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "../components/ui/select"
import { getFiltresAdmin, createFiltre, updateFiltre, deleteFiltre, getCategoriesRestaurant } from "../api"
import type { FiltreDTO, CategorieRestaurantDTO } from "../types"
import { FILTRE_CONTEXTES, FILTRE_COMPORTEMENTS } from "../types"
import toast from "react-hot-toast"

const EMPTY = (contexte: string, ordre: number): FiltreDTO => ({
  contexte, comportement: "PROMOTIONS", categorieId: null, libelle: "", icone: "Tag", ordre, actif: true,
})

export function Filtres() {
  const [contexte, setContexte] = useState("RESTAURANT")
  const [filtres, setFiltres] = useState<FiltreDTO[]>([])
  const [categories, setCategories] = useState<CategorieRestaurantDTO[]>([])
  const [loading, setLoading] = useState(true)
  const [showModal, setShowModal] = useState(false)
  const [form, setForm] = useState<FiltreDTO>(EMPTY("RESTAURANT", 0))
  const [saving, setSaving] = useState(false)

  const fetchData = useCallback(async () => {
    setLoading(true)
    try {
      const [f, c] = await Promise.all([
        getFiltresAdmin(contexte),
        getCategoriesRestaurant().catch(() => [] as CategorieRestaurantDTO[]),
      ])
      setFiltres(f)
      setCategories(c)
    } catch {
      toast.error("Erreur lors du chargement des filtres")
    } finally {
      setLoading(false)
    }
  }, [contexte])

  useEffect(() => { fetchData() }, [fetchData])

  const openCreate = () => { setForm(EMPTY(contexte, filtres.length)); setShowModal(true) }
  const openEdit = (f: FiltreDTO) => { setForm({ ...f }); setShowModal(true) }

  const handleSave = async () => {
    if (!form.libelle.trim()) { toast.error("Le libellé est requis"); return }
    if (form.comportement === "CATEGORIE" && !form.categorieId) { toast.error("Choisissez une catégorie"); return }
    setSaving(true)
    try {
      if (form.id) {
        const up = await updateFiltre(form.id, form)
        setFiltres(prev => prev.map(x => x.id === up.id ? up : x))
        toast.success("Filtre mis à jour")
      } else {
        const cr = await createFiltre(form)
        setFiltres(prev => [...prev, cr])
        toast.success("Filtre créé")
      }
      setShowModal(false)
    } catch { toast.error("Erreur lors de l'enregistrement") }
    finally { setSaving(false) }
  }

  const handleDelete = async (f: FiltreDTO) => {
    if (!f.id || !confirm(`Supprimer le filtre "${f.libelle}" ?`)) return
    try {
      await deleteFiltre(f.id)
      setFiltres(prev => prev.filter(x => x.id !== f.id))
      toast.success("Filtre supprimé")
    } catch { toast.error("Erreur lors de la suppression") }
  }

  const move = async (index: number, dir: -1 | 1) => {
    const target = index + dir
    if (target < 0 || target >= filtres.length) return
    const a = filtres[index], b = filtres[target]
    const newOrdreA = b.ordre, newOrdreB = a.ordre
    try {
      const [ua, ub] = await Promise.all([
        updateFiltre(a.id!, { ...a, ordre: newOrdreA }),
        updateFiltre(b.id!, { ...b, ordre: newOrdreB }),
      ])
      setFiltres(prev => prev.map(x => x.id === ua.id ? ua : x.id === ub.id ? ub : x)
        .sort((m, n) => m.ordre - n.ordre))
    } catch { toast.error("Erreur lors du réordonnancement") }
  }

  return (
    <div className="flex flex-col h-full">
      <Topbar title="Filtres" subtitle="Filtres des barres de l'application utilisateurs"
        actions={
          <div className="flex gap-2">
            <Button size="sm" onClick={openCreate}><Plus className="w-4 h-4" /> Ajouter</Button>
            <Button size="sm" variant="ghost" onClick={fetchData}><RefreshCw className={`w-4 h-4 ${loading ? "animate-spin" : ""}`} /></Button>
          </div>
        }
      />
      <div className="p-6 space-y-4 overflow-y-auto">
        {/* Onglets de contexte */}
        <div className="flex gap-2">
          {FILTRE_CONTEXTES.map(c => (
            <button key={c.value} onClick={() => setContexte(c.value)}
              className={`px-4 py-2 rounded-full text-sm font-medium transition-colors ${contexte === c.value ? "bg-sugu-500 text-white" : "bg-surface-700 text-muted-foreground hover:bg-surface-600"}`}>
              {c.label}
            </button>
          ))}
        </div>

        {loading ? (
          <div className="space-y-2">{Array.from({ length: 5 }).map((_, i) => <div key={i} className="h-14 rounded-lg animate-shimmer" />)}</div>
        ) : filtres.length === 0 ? (
          <p className="text-sm text-muted-foreground">Aucun filtre pour ce contexte.</p>
        ) : (
          <div className="space-y-2">
            {filtres.map((f, i) => (
              <div key={f.id} className="flex items-center gap-3 rounded-xl border border-surface-600 bg-surface-800 px-4 py-3">
                <div className="flex flex-col">
                  <button onClick={() => move(i, -1)} disabled={i === 0} className="disabled:opacity-30"><ArrowUp className="w-3.5 h-3.5" /></button>
                  <button onClick={() => move(i, 1)} disabled={i === filtres.length - 1} className="disabled:opacity-30"><ArrowDown className="w-3.5 h-3.5" /></button>
                </div>
                <div className="flex-1 min-w-0">
                  <div className="flex items-center gap-2">
                    <span className="font-medium">{f.libelle}</span>
                    <Badge variant="secondary" className="text-[10px]">{f.comportement}</Badge>
                    {!f.actif && <Badge variant="destructive" className="text-[10px]">inactif</Badge>}
                  </div>
                  <p className="text-xs text-muted-foreground">icône: {f.icone || "—"}{f.comportement === "CATEGORIE" ? ` · catégorie #${f.categorieId}` : ""}</p>
                </div>
                <Button variant="ghost" size="icon" className="h-7 w-7" onClick={() => openEdit(f)}><Pencil className="w-3.5 h-3.5" /></Button>
                <Button variant="ghost" size="icon" className="h-7 w-7 text-red-400" onClick={() => handleDelete(f)}><Trash2 className="w-3.5 h-3.5" /></Button>
              </div>
            ))}
          </div>
        )}
      </div>

      <Dialog open={showModal} onOpenChange={setShowModal}>
        <DialogContent className="max-w-md">
          <DialogHeader><DialogTitle>{form.id ? "Modifier le filtre" : "Nouveau filtre"}</DialogTitle></DialogHeader>
          <div className="space-y-4 mt-2">
            <div>
              <label className="text-xs font-mono uppercase tracking-widest text-muted-foreground mb-1.5 block">Comportement</label>
              <Select value={form.comportement} onValueChange={v => setForm(f => ({ ...f, comportement: v }))}>
                <SelectTrigger><SelectValue /></SelectTrigger>
                <SelectContent>{FILTRE_COMPORTEMENTS.map(c => <SelectItem key={c.value} value={c.value}>{c.label}</SelectItem>)}</SelectContent>
              </Select>
            </div>
            {form.comportement === "CATEGORIE" && (
              <div>
                <label className="text-xs font-mono uppercase tracking-widest text-muted-foreground mb-1.5 block">Catégorie</label>
                <Select value={form.categorieId ? String(form.categorieId) : ""} onValueChange={v => setForm(f => ({ ...f, categorieId: Number(v) }))}>
                  <SelectTrigger><SelectValue placeholder="Choisir…" /></SelectTrigger>
                  <SelectContent>{categories.map(c => <SelectItem key={c.id} value={String(c.id)}>{c.nom}</SelectItem>)}</SelectContent>
                </Select>
              </div>
            )}
            <div>
              <label className="text-xs font-mono uppercase tracking-widest text-muted-foreground mb-1.5 block">Libellé</label>
              <Input value={form.libelle} onChange={e => setForm(f => ({ ...f, libelle: e.target.value }))} placeholder="Ex: Mieux notés" />
            </div>
            <div>
              <label className="text-xs font-mono uppercase tracking-widest text-muted-foreground mb-1.5 block">Icône (clé Lucide)</label>
              <Input value={form.icone ?? ""} onChange={e => setForm(f => ({ ...f, icone: e.target.value }))} placeholder="Ex: Star, Tag, MapPin, Clock, Zap, Flame" />
            </div>
            <label className="flex items-center gap-2 text-sm">
              <input type="checkbox" checked={form.actif} onChange={e => setForm(f => ({ ...f, actif: e.target.checked }))} />
              Actif (visible dans l'app)
            </label>
            <div className="flex justify-end gap-2 pt-2">
              <Button variant="ghost" onClick={() => setShowModal(false)}>Annuler</Button>
              <Button onClick={handleSave} disabled={saving}>{saving ? "…" : form.id ? "Mettre à jour" : "Créer"}</Button>
            </div>
          </div>
        </DialogContent>
      </Dialog>
    </div>
  )
}
```

- [ ] **Step 2: Déclarer la route dans `App.tsx`**

Ajouter l'import près des autres (`import { Filtres } from "./pages/Filtres"`) et, après la ligne `<Route path="categories" element={<Categories />} />`, ajouter :
```tsx
          <Route path="filtres" element={<Filtres />} />
```

- [ ] **Step 3: Ajouter l'entrée Sidebar**

Dans `Sidebar.tsx` : ajouter `SlidersHorizontal` à l'import lucide-react ; dans `navItems`, après l'entrée `Catégories`, ajouter :
```tsx
  { label: "Filtres", icon: SlidersHorizontal, href: "/filtres" },
```

- [ ] **Step 4: Type-check**

Run: `cd "/mnt/c/Users/Issiaka traoré/Desktop/systalink/mysugu/mysugu-admin" && npx tsc --noEmit; echo "TSC_EXIT:$?"`
Expected: `TSC_EXIT:0`

- [ ] **Step 5: Commit**

```bash
git add src/pages/Filtres.tsx src/App.tsx src/components/layout/Sidebar.tsx
git commit -m "feat(filtres): page dashboard Filtres (CRUD + reordonnancement) + route + sidebar"
```

---

### Task 7 : Module API filtres + mapping icônes (app users)

**Files:**
- Create: `mysugu-frontend/src/api/filtres.api.ts`
- Create: `mysugu-frontend/src/lib/filterIcons.tsx`

**Interfaces:**
- Produces: `filtresApi.getByContexte(contexte)` → `Promise<ApiFiltre[]>` ; type `ApiFiltre` ; `iconForKey(key?: string): LucideIcon`.

- [ ] **Step 1: Créer `filtres.api.ts`**

```ts
import apiClient from './apiClient'

export interface ApiFiltre {
  id: number
  contexte: string
  comportement: string
  categorieId?: number | null
  libelle: string
  icone?: string
  ordre: number
  actif: boolean
}

export const filtresApi = {
  getByContexte: (contexte: string) =>
    apiClient.get<ApiFiltre[]>('/api/filtres', { params: { contexte } }),
}
```

- [ ] **Step 2: Créer `filterIcons.tsx` (mapping clé → composant Lucide)**

```tsx
import {
  Flame, Tag, Zap, MapPin, Star, Clock, Apple, Carrot, Wheat, Milk, Sparkles, Filter,
} from 'lucide-react'
import type { LucideIcon } from 'lucide-react'

const ICONS: Record<string, LucideIcon> = {
  Flame, Tag, Zap, MapPin, Star, Clock, Apple, Carrot, Wheat, Milk, Sparkles, Filter,
}

/** Retourne le composant d'icône Lucide pour une clé donnée, ou une icône par défaut. */
export function iconForKey(key?: string): LucideIcon {
  return (key && ICONS[key]) || Filter
}
```

- [ ] **Step 3: Type-check**

Run: `cd "/mnt/c/Users/Issiaka traoré/Desktop/systalink/mysugu/mysugu-frontend" && npx tsc -b --noEmit; echo "TSC_EXIT:$?"`
Expected: `TSC_EXIT:0`

- [ ] **Step 4: Commit**

```bash
cd "/mnt/c/Users/Issiaka traoré/Desktop/systalink/mysugu/mysugu-frontend"
git add src/api/filtres.api.ts src/lib/filterIcons.tsx
git commit -m "feat(filtres): module API filtres + mapping icones (app users)"
```

---

### Task 8 : Brancher la barre de filtres dynamique sur `RestaurantsPage` (app users)

**Files:**
- Modify: `mysugu-frontend/src/pages/RestaurantsPage.tsx`

**Interfaces:**
- Consumes: `filtresApi.getByContexte` + `iconForKey` (Task 7).
- Produces: barre de filtres pilotée par l'API pour le contexte RESTAURANT, avec repli sur la liste actuelle.

Contexte : aujourd'hui `QUICK_FILTERS` est statique et `quickFilter` est un littéral d'union. On remplace par une liste chargée, en conservant la logique d'application existante (qui teste `quickFilter === 'promotions'`, `'top_rated'`, `'fastest'`, `'closest'`). On mappe `comportement → clé locale`.

- [ ] **Step 1: Ajouter le mapping comportement → clé locale et l'état chargé**

En haut de `RestaurantsPage.tsx`, après les imports, ajouter :
```tsx
import { filtresApi, type ApiFiltre } from '@/api/filtres.api'
import { iconForKey } from '@/lib/filterIcons'

// comportement backend -> clé de logique locale déjà implémentée dans cette page
const COMPORTEMENT_TO_KEY: Record<string, string> = {
  TOUS: 'all',
  PROMOTIONS: 'promotions',
  MIEUX_NOTES: 'top_rated',
  PLUS_PROCHES: 'closest',
  PLUS_RAPIDES: 'fastest',
  OUVERTS: 'open_now',
}

// repli si l'API échoue : reproduit la barre actuelle
const FALLBACK_FILTERS = [
  { id: 'all', label: 'Tous', iconKey: 'Flame' },
  { id: 'promotions', label: 'Promotions', iconKey: 'Zap' },
  { id: 'top_rated', label: 'Mieux notés', iconKey: 'Star' },
  { id: 'closest', label: 'Plus proches', iconKey: 'MapPin' },
  { id: 'fastest', label: 'Plus rapides', iconKey: 'Clock' },
  { id: 'open_now', label: 'Ouverts', iconKey: 'Zap' },
]
```

- [ ] **Step 2: Remplacer la constante `QUICK_FILTERS` statique par un chargement dynamique**

Supprimer le bloc `const QUICK_FILTERS = [...]` et, dans le composant, ajouter un état + un effet :
```tsx
  const [quickFilters, setQuickFilters] = useState<{ id: string; label: string; iconKey?: string; categorieId?: number | null }[]>(FALLBACK_FILTERS)

  useEffect(() => {
    filtresApi.getByContexte('RESTAURANT')
      .then(res => {
        const mapped = res.data
          .map((f: ApiFiltre) => ({
            id: f.comportement === 'CATEGORIE' ? `cat:${f.categorieId}` : (COMPORTEMENT_TO_KEY[f.comportement] || 'all'),
            label: f.libelle,
            iconKey: f.icone,
            categorieId: f.categorieId,
          }))
        if (mapped.length) setQuickFilters(mapped)
      })
      .catch(() => { /* garde le repli */ })
  }, [])
```
(Garder le type `QuickFilter` existant pour `quickFilter`/`setQuickFilter` ; les `id` mappés correspondent aux mêmes littéraux. Pour les filtres `cat:<id>`, voir Step 3.)

- [ ] **Step 3: Gérer le filtre par catégorie dans la logique d'application**

Dans le `useMemo` qui calcule la liste (là où sont déjà les `if (quickFilter === 'promotions')`, etc.), ajouter avant le `return list.sort(...)` :
```tsx
    if (quickFilter.startsWith('cat:')) {
      const catId = Number(quickFilter.slice(4))
      list = list.filter(r => r.categorieId === catId)
    }
```
(Adapter `r.categorieId` au champ réel exposé par le mapper restaurant ; si le champ se nomme autrement, utiliser celui présent dans le type `Restaurant` du front. Vérifier dans `src/api/mappers.ts`.)
Élargir le type local `QuickFilter` pour accepter une string de catégorie :
```tsx
type QuickFilter = 'all' | 'subsahariens' | 'promotions' | 'closest' | 'top_rated' | 'fastest' | 'open_now' | string
```

- [ ] **Step 4: Rendre les chips depuis l'état dynamique**

Remplacer le `.map` de rendu des chips par :
```tsx
          {quickFilters.map(f => {
            const Icon = iconForKey(f.iconKey)
            return (
              <Chip key={f.id} active={quickFilter === f.id} icon={Icon} onClick={() => setQuickFilter(f.id as QuickFilter)}>
                {f.label}
              </Chip>
            )
          })}
```

- [ ] **Step 5: Type-check**

Run: `cd "/mnt/c/Users/Issiaka traoré/Desktop/systalink/mysugu/mysugu-frontend" && npx tsc -b --noEmit; echo "TSC_EXIT:$?"`
Expected: `TSC_EXIT:0`

- [ ] **Step 6: Commit**

```bash
git add src/pages/RestaurantsPage.tsx
git commit -m "feat(filtres): barre de filtres dynamique sur RestaurantsPage (API + repli)"
```

---

### Task 9 : Brancher `AlimentairesPage` et `CosmetiquesPage` (app users)

**Files:**
- Modify: `mysugu-frontend/src/pages/AlimentairesPage.tsx`
- Modify: `mysugu-frontend/src/pages/CosmetiquesPage.tsx`

**Interfaces:**
- Consumes: `filtresApi.getByContexte`, `iconForKey` (Task 7).
- Produces: barres de filtres dynamiques pour ALIMENTAIRE et COSMETIQUE, avec repli.

Même approche que Task 8, contexte `ALIMENTAIRE` / `COSMETIQUE`. Ces pages ont leurs propres comportements de catégorie (`fruits_legumes`, `epicerie`, `boissons`) — ces filtres deviendront des filtres `CATEGORIE` ; tant qu'aucun n'est créé, le repli reproduit la barre actuelle.

- [ ] **Step 1: `AlimentairesPage.tsx` — ajouter mapping + chargement**

Après les imports, ajouter :
```tsx
import { filtresApi, type ApiFiltre } from '@/api/filtres.api'
import { iconForKey } from '@/lib/filterIcons'

const COMPORTEMENT_TO_KEY: Record<string, string> = {
  TOUS: 'all', PROMOTIONS: 'promotions', PLUS_PROCHES: 'closest', PLUS_RAPIDES: 'fastest',
}
const FALLBACK_FILTERS = [
  { id: 'all', label: 'Tous', iconKey: 'Apple' },
  { id: 'fruits_legumes', label: 'Fruits & légumes', iconKey: 'Carrot' },
  { id: 'epicerie', label: 'Épicerie', iconKey: 'Wheat' },
  { id: 'boissons', label: 'Boissons', iconKey: 'Milk' },
  { id: 'promotions', label: 'Promotions', iconKey: 'Tag' },
  { id: 'closest', label: 'Plus proches', iconKey: 'MapPin' },
  { id: 'fastest', label: 'Plus rapides', iconKey: 'Clock' },
]
```
Supprimer le `const QUICK_FILTERS = [...]` statique et ajouter dans le composant :
```tsx
  const [quickFilters, setQuickFilters] = useState<{ id: string; label: string; iconKey?: string; categorieId?: number | null }[]>(FALLBACK_FILTERS)
  useEffect(() => {
    filtresApi.getByContexte('ALIMENTAIRE')
      .then(res => {
        const mapped = res.data.map((f: ApiFiltre) => ({
          id: f.comportement === 'CATEGORIE' ? `cat:${f.categorieId}` : (COMPORTEMENT_TO_KEY[f.comportement] || 'all'),
          label: f.libelle, iconKey: f.icone, categorieId: f.categorieId,
        }))
        if (mapped.length) setQuickFilters(mapped)
      })
      .catch(() => {})
  }, [])
```

- [ ] **Step 2: `AlimentairesPage.tsx` — rendre les chips dynamiques**

Remplacer le `.map` des chips (autour de la ligne 73) par le même rendu qu'en Task 8 Step 4 (avec `iconForKey(f.iconKey)`), et élargir le type `QuickFilter` local pour accepter `string`. Ajouter la branche `cat:` dans la logique d'application si la page filtre par catégorie.

- [ ] **Step 3: `CosmetiquesPage.tsx` — répéter Steps 1-2 avec contexte `COSMETIQUE`**

Mapping identique ; `FALLBACK_FILTERS` reproduisant la barre cosmétique actuelle (lire le `QUICK_FILTERS` existant de `CosmetiquesPage.tsx` et le recopier en `{id,label,iconKey}`), contexte `'COSMETIQUE'`.

- [ ] **Step 4: Type-check**

Run: `cd "/mnt/c/Users/Issiaka traoré/Desktop/systalink/mysugu/mysugu-frontend" && npx tsc -b --noEmit; echo "TSC_EXIT:$?"`
Expected: `TSC_EXIT:0`

- [ ] **Step 5: Commit**

```bash
git add src/pages/AlimentairesPage.tsx src/pages/CosmetiquesPage.tsx
git commit -m "feat(filtres): barres dynamiques Alimentaire + Cosmetique (API + repli)"
```

---

## Notes d'exécution
- Les commits backend vont sur la branche backend courante (`wip/restaurateur-onboarding`) sauf si tu préfères une branche dédiée `feat/filtres`.
- Côté admin/app users, créer une branche dédiée `feat/filtres` AVANT de committer pour ne pas mélanger avec le WIP existant ; n'ajouter que les fichiers listés par tâche (jamais `git add -A`).
- Après la phase, vérifier de bout en bout : page dashboard Filtres (créer/éditer/supprimer/réordonner) + l'app users affiche la barre via l'API.

## Self-review (couverture spec)
- Entité/enum/API/seed/sécurité #5 : Tasks 1-4. ✓
- Dashboard CRUD + réordonnancement + route + sidebar : Tasks 5-6. ✓
- App users : API + mapping + 3 barres dynamiques + repli : Tasks 7-9. ✓
- Comportements prédéfinis + CATEGORIE créable, portée par verticale, seed anti-régression : couverts. ✓
