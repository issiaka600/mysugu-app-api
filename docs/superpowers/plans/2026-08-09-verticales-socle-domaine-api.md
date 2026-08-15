# Verticales boutiques — Sous-projet 1 : socle domaine et API

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Rendre le backend capable de servir un parcours d'achat complet en boutique alimentaire et cosmétique, en réutilisant les entités `Restaurant` et `Plat`, sans qu'aucune app mobile non mise à jour ne change de comportement.

**Architecture:** Une boutique est une ligne `restaurants` avec `vertical = ALIMENTAIRE|COSMETIQUE` ; un produit est une ligne `plats`. On ajoute trois champs de domaine (stock produit, verticale sur les catégories d'établissement, verticale sur les tuiles d'accueil), on sort les rayons du code en dur vers la base, et on propage le paramètre `vertical` sur les cinq endpoints de lecture qui mélangent aujourd'hui les univers. Aucune table de commerce nouvelle, aucun endpoint de commande nouveau.

**Tech Stack:** Java 21, Spring Boot 3, Spring Data JPA, PostgreSQL, JUnit 5 + AssertJ, Lombok, Maven.

**Spec de référence:** `docs/superpowers/specs/2026-08-09-verticales-boutiques-design.md`

## Global Constraints

- **Convention du paramètre `vertical`, identique partout** : absent ⇒ `RESTAURANT` ; `ALL` ⇒ toutes verticales ; valeur inconnue ⇒ 400. La méthode de référence est `RestaurantServiceImpl.parseVertical` (ligne 75) — la réutiliser, ne pas la dupliquer.
- **Compatibilité descendante non négociable** : tout endpoint touché, appelé sans le nouveau paramètre, doit renvoyer exactement ce qu'il renvoie aujourd'hui. La tâche 13 en fait la preuve exécutable.
- **`quantiteStock == null` signifie « stock non géré »** : aucun verrou, aucun décrément, aucun blocage. C'est le cas de 100 % des plats existants.
- **`isAvailable` reste l'interrupteur maître du vendeur** : le stock ne l'écrase jamais en base. La pondération se fait à la lecture, dans les DTO.
- **Package racine** : `ma.mysuguclientapp`. Entités dans `entities`, DTO dans `dtos`, repos dans `repositories`, services dans `services.{interfaces,implementations}`, contrôleurs dans `controllers`, enums dans `enumerations`, seeds dans `config`.
- **Nommage** : le domaine est en français (`quantiteStock`, `categorieProduit`, `libelle`), comme le reste du code.
- **Schéma** : `spring.jpa.hibernate.ddl-auto` gère les colonnes ; aucun script de migration à écrire. Tous les nouveaux champs sont nullables ou ont une valeur par défaut, donc les lignes existantes restent valides.
- **Pré-requis de démarrage** : vérifier que `feat/backend-shims` est fusionnée dans `dev` (`git log --oneline dev | grep -i seller`). Le dépôt local était à `9accf13`, en retard sur `origin`.
- **Avant tout `mvn test`** : `rm -rf target` (des `.class` périmés provoquent un « Ambiguous mapping » au boot du contexte web, faux échec).
- **Commande de test** :
  ```bash
  rm -rf target && mvn -o test -Dtest=<NomDuTest> \
    -DargLine="-javaagent:$HOME/.m2/repository/org/mockito/mockito-core/5.20.0/mockito-core-5.20.0.jar -Xshare:off"
  ```
- **Un commit par tâche**, message en français, préfixe `feat:` / `test:` / `perf:`.

---

## Structure des fichiers

**Créés :**
- `entities/CategorieProduit.java` — un rayon (code + libellé) rattaché à une verticale
- `repositories/CategorieProduitRepository.java`
- `services/interfaces/CategorieProduitService.java` + `services/implementations/CategorieProduitServiceImpl.java` — lecture publique et CRUD admin
- `dtos/CategorieProduitDTO.java` — forme d'écriture admin (le DTO de lecture publique reste `EnumOptionDTO`)
- `config/CategorieProduitInitializer.java` — seed idempotent des rayons aujourd'hui codés en dur
- `controllers/AdminCategorieProduitController.java` — CRUD admin des rayons
- `services/implementations/StockService.java` — décrément et restitution de stock, seul endroit qui touche `quantiteStock`
- Tests : `StockDecrementTest`, `StockConcurrenceTest`, `CategorieProduitTest`, `RayonsBoutiqueTest`, `NonRegressionVerticalTest`

**Modifiés :**
- `entities/Plat.java` — `quantiteStock`, `seuilAlerteStock`, `isEffectivementDisponible()`
- `entities/CategorieRestaurant.java` — `vertical`
- `entities/ServiceCategorie.java` — `vertical`
- `entities/Commande.java` — `stockRestitue`
- `dtos/PlatDTO.java`, `dtos/PlatCreateDTO.java` — champs de stock
- `dtos/ServiceCategorieDTO.java`, `dtos/CategorieRestaurantDTO.java` — `vertical`
- `dtos/RestaurantDTO.java` — `rayons`
- `repositories/PlatRepository.java` — verrou pessimiste, requête paginée filtrée, rayons distincts
- `repositories/CategorieRestaurantRepository.java` — filtre par verticale
- `services/implementations/PlatServiceImpl.java` — disponibilité effective, filtres verticale et rayon
- `services/implementations/RestaurantServiceImpl.java` — verticale sur search/top-rated/nearby, rayons au détail
- `services/implementations/CommandeServiceImpl.java` — appels au `StockService`
- `services/implementations/ServiceCategorieServiceImpl.java`, `CategorieRestaurantServiceImpl` — mapping de `vertical`
- `controllers/RestaurantController.java`, `controllers/PlatController.java`, `controllers/CategorieProduitController.java`, `controllers/CategorieController.java`
- Interfaces de service correspondantes (`PlatService`, `RestaurantService`)

**Non modifiés, et c'est le cœur du gain :** `Panier*`, `LigneCommande*`, `CaisseLivreur`, `GainsLivreur`, `FacturationRestaurant`, `ZoneDeploiement`, chat, avis, codes promo, fidélité, wallet, `legacy/deliveryman/*`, `legacy/seller/*`.

---

### Task 1: Entité rayon et seed des valeurs actuelles

Sort les rayons du code en dur (`CategorieProduitController`, lignes 18-32) vers la base, sans changer une virgule au contrat HTTP.

**Files:**
- Create: `src/main/java/ma/mysuguclientapp/entities/CategorieProduit.java`
- Create: `src/main/java/ma/mysuguclientapp/repositories/CategorieProduitRepository.java`
- Create: `src/main/java/ma/mysuguclientapp/config/CategorieProduitInitializer.java`
- Test: `src/test/java/ma/mysuguclientapp/CategorieProduitTest.java`

**Interfaces:**
- Consumes: `enumerations.Vertical` (existant)
- Produces: `CategorieProduit` (getters Lombok : `getVertical()`, `getCode()`, `getLibelle()`, `getOrdre()`, `getActif()`) ; `CategorieProduitRepository.findByVerticalAndActifTrueOrderByOrdreAsc(Vertical)` → `List<CategorieProduit>` ; `existsByVerticalAndCode(Vertical, String)` → `boolean`

- [ ] **Step 1: Write the failing test**

`src/test/java/ma/mysuguclientapp/CategorieProduitTest.java` :

```java
package ma.mysuguclientapp;

import ma.mysuguclientapp.enumerations.Vertical;
import ma.mysuguclientapp.repositories.CategorieProduitRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class CategorieProduitTest {

    @Autowired CategorieProduitRepository repository;

    @Test
    void seedContientLesRayonsAlimentaires() {
        var rayons = repository.findByVerticalAndActifTrueOrderByOrdreAsc(Vertical.ALIMENTAIRE);
        assertThat(rayons).extracting("code")
                .containsExactly("fruits_legumes", "epicerie", "boissons", "produits_frais");
        assertThat(rayons).extracting("libelle").first().isEqualTo("Fruits & légumes");
    }

    @Test
    void seedContientLesRayonsCosmetiques() {
        var rayons = repository.findByVerticalAndActifTrueOrderByOrdreAsc(Vertical.COSMETIQUE);
        assertThat(rayons).extracting("code")
                .containsExactly("soin_visage", "soin_corps", "parfums", "cheveux");
    }

    @Test
    void aucunRayonPourLaVerticaleRestaurant() {
        assertThat(repository.findByVerticalAndActifTrueOrderByOrdreAsc(Vertical.RESTAURANT)).isEmpty();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run : `rm -rf target && mvn -o test -Dtest=CategorieProduitTest -DargLine="-javaagent:$HOME/.m2/repository/org/mockito/mockito-core/5.20.0/mockito-core-5.20.0.jar -Xshare:off"`
Expected : ÉCHEC à la compilation — `CategorieProduitRepository` n'existe pas.

- [ ] **Step 3: Créer l'entité**

`entities/CategorieProduit.java` :

```java
package ma.mysuguclientapp.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import ma.mysuguclientapp.enumerations.Vertical;

/** Rayon d'une boutique (verticale non-restaurant). Le code est stable, le libellé est affiché. */
@Entity
@Table(name = "categories_produit",
        uniqueConstraints = @UniqueConstraint(columnNames = {"vertical", "code"}))
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CategorieProduit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Vertical vertical;

    /** Code technique stable, repris tel quel dans Plat.categorieProduit (ex : "fruits_legumes"). */
    @Column(nullable = false)
    private String code;

    @Column(nullable = false)
    private String libelle;

    @Column(nullable = false)
    private Integer ordre = 0;

    @Column(nullable = false)
    private Boolean actif = true;
}
```

- [ ] **Step 4: Créer le repository**

`repositories/CategorieProduitRepository.java` :

```java
package ma.mysuguclientapp.repositories;

import ma.mysuguclientapp.entities.CategorieProduit;
import ma.mysuguclientapp.enumerations.Vertical;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CategorieProduitRepository extends JpaRepository<CategorieProduit, Long> {
    List<CategorieProduit> findByVerticalAndActifTrueOrderByOrdreAsc(Vertical vertical);
    List<CategorieProduit> findByVerticalOrderByOrdreAsc(Vertical vertical);
    boolean existsByVerticalAndCode(Vertical vertical, String code);
    Optional<CategorieProduit> findByVerticalAndCode(Vertical vertical, String code);
}
```

- [ ] **Step 5: Créer le seed idempotent**

`config/CategorieProduitInitializer.java` — même patron que `FiltreInitializer` (`@Order(20)`), donc `@Order(21)` :

```java
package ma.mysuguclientapp.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.mysuguclientapp.entities.CategorieProduit;
import ma.mysuguclientapp.enumerations.Vertical;
import ma.mysuguclientapp.repositories.CategorieProduitRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/** Sème les rayons par défaut au premier démarrage. Idempotent : ne fait rien si la table est non vide. */
@Component
@RequiredArgsConstructor
@Slf4j
@Order(21)
public class CategorieProduitInitializer implements CommandLineRunner {

    private final CategorieProduitRepository repository;

    @Override
    @Transactional
    public void run(String... args) {
        if (repository.count() > 0) {
            return;
        }
        List<CategorieProduit> defauts = new ArrayList<>();
        defauts.add(c(Vertical.ALIMENTAIRE, "fruits_legumes", "Fruits & légumes", 0));
        defauts.add(c(Vertical.ALIMENTAIRE, "epicerie", "Épicerie", 1));
        defauts.add(c(Vertical.ALIMENTAIRE, "boissons", "Boissons", 2));
        defauts.add(c(Vertical.ALIMENTAIRE, "produits_frais", "Produits frais", 3));
        defauts.add(c(Vertical.COSMETIQUE, "soin_visage", "Soin visage", 0));
        defauts.add(c(Vertical.COSMETIQUE, "soin_corps", "Soin corps", 1));
        defauts.add(c(Vertical.COSMETIQUE, "parfums", "Parfums", 2));
        defauts.add(c(Vertical.COSMETIQUE, "cheveux", "Cheveux", 3));
        repository.saveAll(defauts);
        log.info("Rayons par défaut semés: {}", defauts.size());
    }

    private CategorieProduit c(Vertical vertical, String code, String libelle, int ordre) {
        CategorieProduit categorie = new CategorieProduit();
        categorie.setVertical(vertical);
        categorie.setCode(code);
        categorie.setLibelle(libelle);
        categorie.setOrdre(ordre);
        categorie.setActif(true);
        return categorie;
    }
}
```

- [ ] **Step 6: Run test to verify it passes**

Run : `rm -rf target && mvn -o test -Dtest=CategorieProduitTest -DargLine="-javaagent:$HOME/.m2/repository/org/mockito/mockito-core/5.20.0/mockito-core-5.20.0.jar -Xshare:off"`
Expected : PASS, 3 tests.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/ma/mysuguclientapp/entities/CategorieProduit.java \
        src/main/java/ma/mysuguclientapp/repositories/CategorieProduitRepository.java \
        src/main/java/ma/mysuguclientapp/config/CategorieProduitInitializer.java \
        src/test/java/ma/mysuguclientapp/CategorieProduitTest.java
git commit -m "feat(verticales): entite CategorieProduit + seed idempotent des rayons"
```

---

### Task 2: Servir les rayons depuis la base, contrat HTTP inchangé

**Files:**
- Create: `src/main/java/ma/mysuguclientapp/services/interfaces/CategorieProduitService.java`
- Create: `src/main/java/ma/mysuguclientapp/services/implementations/CategorieProduitServiceImpl.java`
- Modify: `src/main/java/ma/mysuguclientapp/controllers/CategorieProduitController.java` (remplace intégralement les listes en dur, lignes 18-32)
- Test: `src/test/java/ma/mysuguclientapp/CategorieProduitTest.java` (ajout)

**Interfaces:**
- Consumes: `CategorieProduitRepository` (tâche 1), `RestaurantServiceImpl.parseVertical` (comportement de référence)
- Produces: `CategorieProduitService.listerPublic(String vertical)` → `List<EnumOptionDTO>` (`value` = code, `label` = libellé)

- [ ] **Step 1: Write the failing test**

Ajouter à `CategorieProduitTest` :

```java
    @Autowired ma.mysuguclientapp.services.interfaces.CategorieProduitService service;

    @Test
    void listerPublicRenvoieValueEtLabel() {
        var options = service.listerPublic("ALIMENTAIRE");
        assertThat(options).extracting("value")
                .containsExactly("fruits_legumes", "epicerie", "boissons", "produits_frais");
        assertThat(options.get(0).getLabel()).isEqualTo("Fruits & légumes");
    }

    @Test
    void listerPublicEstInsensibleALaCasse() {
        assertThat(service.listerPublic("alimentaire")).hasSize(4);
    }

    @Test
    void listerPublicRenvoieVideSiVerticaleInconnue() {
        assertThat(service.listerPublic("PHARMACIE")).isEmpty();
    }
```

Note : verticale inconnue ⇒ **liste vide**, pas 400. C'est le comportement actuel du contrôleur (`getOrDefault(..., List.of())`) et on le préserve. La règle « inconnue ⇒ 400 » de la spec s'applique aux endpoints de listing d'établissements et de produits, pas à celui-ci.

- [ ] **Step 2: Run test to verify it fails**

Run : `rm -rf target && mvn -o test -Dtest=CategorieProduitTest -DargLine="-javaagent:$HOME/.m2/repository/org/mockito/mockito-core/5.20.0/mockito-core-5.20.0.jar -Xshare:off"`
Expected : ÉCHEC à la compilation — `CategorieProduitService` n'existe pas.

- [ ] **Step 3: Créer l'interface de service**

`services/interfaces/CategorieProduitService.java` :

```java
package ma.mysuguclientapp.services.interfaces;

import ma.mysuguclientapp.dtos.CategorieProduitDTO;
import ma.mysuguclientapp.dtos.EnumOptionDTO;

import java.util.List;

public interface CategorieProduitService {

    /** Lecture publique : renvoie les rayons actifs d'une verticale, vide si verticale inconnue. */
    List<EnumOptionDTO> listerPublic(String vertical);

    /** Lecture admin : tous les rayons d'une verticale, actifs ou non. */
    List<CategorieProduitDTO> listerAdmin(String vertical);

    CategorieProduitDTO creer(CategorieProduitDTO dto);

    CategorieProduitDTO modifier(Long id, CategorieProduitDTO dto);

    void supprimer(Long id);
}
```

- [ ] **Step 4: Créer le DTO admin**

`dtos/CategorieProduitDTO.java` :

```java
package ma.mysuguclientapp.dtos;

import lombok.Data;

@Data
public class CategorieProduitDTO {
    private Long id;
    private String vertical;
    private String code;
    private String libelle;
    private Integer ordre;
    private Boolean actif;
}
```

- [ ] **Step 5: Créer l'implémentation**

`services/implementations/CategorieProduitServiceImpl.java` :

```java
package ma.mysuguclientapp.services.implementations;

import lombok.RequiredArgsConstructor;
import ma.mysuguclientapp.dtos.CategorieProduitDTO;
import ma.mysuguclientapp.dtos.EnumOptionDTO;
import ma.mysuguclientapp.entities.CategorieProduit;
import ma.mysuguclientapp.enumerations.Vertical;
import ma.mysuguclientapp.exceptions.BadRequestException;
import ma.mysuguclientapp.exceptions.ResourceNotFoundException;
import ma.mysuguclientapp.repositories.CategorieProduitRepository;
import ma.mysuguclientapp.services.interfaces.CategorieProduitService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class CategorieProduitServiceImpl implements CategorieProduitService {

    private final CategorieProduitRepository repository;

    @Override
    @Transactional(readOnly = true)
    public List<EnumOptionDTO> listerPublic(String vertical) {
        Vertical v = parseSilencieux(vertical);
        if (v == null) {
            return List.of();
        }
        return repository.findByVerticalAndActifTrueOrderByOrdreAsc(v).stream()
                .map(c -> new EnumOptionDTO(c.getCode(), c.getLibelle()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategorieProduitDTO> listerAdmin(String vertical) {
        Vertical v = parseStrict(vertical);
        return repository.findByVerticalOrderByOrdreAsc(v).stream().map(this::toDTO).toList();
    }

    @Override
    public CategorieProduitDTO creer(CategorieProduitDTO dto) {
        Vertical v = parseStrict(dto.getVertical());
        String code = exigerCode(dto.getCode());
        if (repository.existsByVerticalAndCode(v, code)) {
            throw new BadRequestException("Ce rayon existe déjà pour cette verticale: " + code);
        }
        CategorieProduit entite = new CategorieProduit();
        entite.setVertical(v);
        entite.setCode(code);
        entite.setLibelle(exigerLibelle(dto.getLibelle()));
        entite.setOrdre(dto.getOrdre() != null ? dto.getOrdre() : 0);
        entite.setActif(dto.getActif() != null ? dto.getActif() : true);
        return toDTO(repository.save(entite));
    }

    @Override
    public CategorieProduitDTO modifier(Long id, CategorieProduitDTO dto) {
        CategorieProduit entite = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Rayon non trouvé avec l'ID: " + id));
        if (dto.getLibelle() != null) {
            entite.setLibelle(exigerLibelle(dto.getLibelle()));
        }
        if (dto.getOrdre() != null) {
            entite.setOrdre(dto.getOrdre());
        }
        if (dto.getActif() != null) {
            entite.setActif(dto.getActif());
        }
        // Le code n'est pas modifiable : des lignes plats y font référence.
        return toDTO(repository.save(entite));
    }

    @Override
    public void supprimer(Long id) {
        CategorieProduit entite = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Rayon non trouvé avec l'ID: " + id));
        // Suppression logique : des produits peuvent référencer ce code.
        entite.setActif(false);
        repository.save(entite);
    }

    private Vertical parseSilencieux(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Vertical.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private Vertical parseStrict(String value) {
        Vertical v = parseSilencieux(value);
        if (v == null) {
            throw new BadRequestException("Vertical invalide: " + value);
        }
        return v;
    }

    private String exigerCode(String code) {
        if (code == null || code.isBlank()) {
            throw new BadRequestException("Le code du rayon est obligatoire");
        }
        return code.trim().toLowerCase();
    }

    private String exigerLibelle(String libelle) {
        if (libelle == null || libelle.isBlank()) {
            throw new BadRequestException("Le libellé du rayon est obligatoire");
        }
        return libelle.trim();
    }

    private CategorieProduitDTO toDTO(CategorieProduit entite) {
        CategorieProduitDTO dto = new CategorieProduitDTO();
        dto.setId(entite.getId());
        dto.setVertical(entite.getVertical().name());
        dto.setCode(entite.getCode());
        dto.setLibelle(entite.getLibelle());
        dto.setOrdre(entite.getOrdre());
        dto.setActif(entite.getActif());
        return dto;
    }
}
```

- [ ] **Step 6: Réécrire le contrôleur public**

`controllers/CategorieProduitController.java` — remplacer tout le contenu :

```java
package ma.mysuguclientapp.controllers;

import lombok.RequiredArgsConstructor;
import ma.mysuguclientapp.dtos.EnumOptionDTO;
import ma.mysuguclientapp.services.interfaces.CategorieProduitService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/plats")
@RequiredArgsConstructor
public class CategorieProduitController {

    private final CategorieProduitService categorieProduitService;

    @GetMapping("/categories-produit")
    public ResponseEntity<List<EnumOptionDTO>> getCategoriesProduit(@RequestParam String vertical) {
        return ResponseEntity.ok(categorieProduitService.listerPublic(vertical));
    }
}
```

- [ ] **Step 7: Run test to verify it passes**

Run : `rm -rf target && mvn -o test -Dtest=CategorieProduitTest -DargLine="-javaagent:$HOME/.m2/repository/org/mockito/mockito-core/5.20.0/mockito-core-5.20.0.jar -Xshare:off"`
Expected : PASS, 6 tests.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/ma/mysuguclientapp/services src/main/java/ma/mysuguclientapp/dtos/CategorieProduitDTO.java \
        src/main/java/ma/mysuguclientapp/controllers/CategorieProduitController.java \
        src/test/java/ma/mysuguclientapp/CategorieProduitTest.java
git commit -m "feat(verticales): rayons servis depuis la base, contrat /categories-produit inchange"
```

---

### Task 3: CRUD admin des rayons

Le panneau admin (sous-projet 2) sera du React pur : les endpoints doivent exister ici.

**Files:**
- Create: `src/main/java/ma/mysuguclientapp/controllers/AdminCategorieProduitController.java`
- Test: `src/test/java/ma/mysuguclientapp/CategorieProduitTest.java` (ajout)

**Interfaces:**
- Consumes: `CategorieProduitService` (tâche 2)
- Produces: `GET|POST /api/admin/categories-produit`, `PUT|DELETE /api/admin/categories-produit/{id}`

Aucune modification de `SecurityConfig` : la ligne 99 couvre déjà `/api/admin/**` en `hasRole("ADMIN")`.

- [ ] **Step 1: Write the failing test**

Ajouter à `CategorieProduitTest` :

```java
    @Test
    void creerPuisModifierPuisDesactiverUnRayon() {
        var creation = new ma.mysuguclientapp.dtos.CategorieProduitDTO();
        creation.setVertical("COSMETIQUE");
        creation.setCode("maquillage");
        creation.setLibelle("Maquillage");
        creation.setOrdre(9);
        var cree = service.creer(creation);
        assertThat(cree.getId()).isNotNull();
        assertThat(cree.getActif()).isTrue();

        var modification = new ma.mysuguclientapp.dtos.CategorieProduitDTO();
        modification.setLibelle("Maquillage & teint");
        assertThat(service.modifier(cree.getId(), modification).getLibelle())
                .isEqualTo("Maquillage & teint");

        service.supprimer(cree.getId());
        assertThat(service.listerPublic("COSMETIQUE")).extracting("value").doesNotContain("maquillage");
        assertThat(service.listerAdmin("COSMETIQUE")).extracting("code").contains("maquillage");
    }

    @Test
    void refuseUnCodeDejaPrisDansLaMemeVerticale() {
        var doublon = new ma.mysuguclientapp.dtos.CategorieProduitDTO();
        doublon.setVertical("ALIMENTAIRE");
        doublon.setCode("epicerie");
        doublon.setLibelle("Épicerie bis");
        org.junit.jupiter.api.Assertions.assertThrows(
                ma.mysuguclientapp.exceptions.BadRequestException.class,
                () -> service.creer(doublon));
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run : `rm -rf target && mvn -o test -Dtest=CategorieProduitTest -DargLine="-javaagent:$HOME/.m2/repository/org/mockito/mockito-core/5.20.0/mockito-core-5.20.0.jar -Xshare:off"`
Expected : les deux nouveaux tests passent déjà si la tâche 2 est faite (le service porte la logique) — dans ce cas, ils servent de garde-fou et on enchaîne. S'ils échouent, corriger `CategorieProduitServiceImpl` avant de continuer.

- [ ] **Step 3: Créer le contrôleur admin**

`controllers/AdminCategorieProduitController.java` :

```java
package ma.mysuguclientapp.controllers;

import lombok.RequiredArgsConstructor;
import ma.mysuguclientapp.dtos.CategorieProduitDTO;
import ma.mysuguclientapp.services.interfaces.CategorieProduitService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Gestion des rayons (catégories produit) par l'admin. Sécurisé par /api/admin/** → ADMIN. */
@RestController
@RequestMapping("/api/admin/categories-produit")
@RequiredArgsConstructor
public class AdminCategorieProduitController {

    private final CategorieProduitService categorieProduitService;

    @GetMapping
    public ResponseEntity<List<CategorieProduitDTO>> lister(@RequestParam String vertical) {
        return ResponseEntity.ok(categorieProduitService.listerAdmin(vertical));
    }

    @PostMapping
    public ResponseEntity<CategorieProduitDTO> creer(@RequestBody CategorieProduitDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(categorieProduitService.creer(dto));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CategorieProduitDTO> modifier(@PathVariable Long id,
                                                        @RequestBody CategorieProduitDTO dto) {
        return ResponseEntity.ok(categorieProduitService.modifier(id, dto));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> supprimer(@PathVariable Long id) {
        categorieProduitService.supprimer(id);
        return ResponseEntity.noContent().build();
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run : `rm -rf target && mvn -o test -Dtest=CategorieProduitTest -DargLine="-javaagent:$HOME/.m2/repository/org/mockito/mockito-core/5.20.0/mockito-core-5.20.0.jar -Xshare:off"`
Expected : PASS, 8 tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/ma/mysuguclientapp/controllers/AdminCategorieProduitController.java \
        src/test/java/ma/mysuguclientapp/CategorieProduitTest.java
git commit -m "feat(verticales): CRUD admin des rayons"
```

---

### Task 4: Verticale sur les catégories d'établissement

Sans ce champ, un boutiquier doit ranger sa parapharmacie sous « marocains », et le client voit des filtres de cuisine sur la barre boutique.

**Files:**
- Modify: `src/main/java/ma/mysuguclientapp/entities/CategorieRestaurant.java`
- Modify: `src/main/java/ma/mysuguclientapp/repositories/CategorieRestaurantRepository.java`
- Modify: `src/main/java/ma/mysuguclientapp/dtos/CategorieRestaurantDTO.java`
- Modify: `src/main/java/ma/mysuguclientapp/controllers/CategorieController.java`
- Modify: le service correspondant (`services/implementations/CategorieRestaurantServiceImpl.java` ou équivalent — repérer via `grep -rl "CategorieRestaurantDTO" src/main/java/ma/mysuguclientapp/services`)
- Test: `src/test/java/ma/mysuguclientapp/VerticalFilterTest.java` (ajout)

**Interfaces:**
- Consumes: `enumerations.Vertical`
- Produces: `CategorieRestaurant.getVertical()` → `Vertical` (null ⇒ RESTAURANT) ; `GET /api/categories?vertical=…`

- [ ] **Step 1: Write the failing test**

Ajouter à `VerticalFilterTest` (le fichier a déjà `@TestInstance(PER_CLASS)` et un `setup`/`cleanup`) :

```java
    @Autowired ma.mysuguclientapp.repositories.CategorieRestaurantRepository categorieRepository;

    @Test
    void categoriesFiltreesParVerticale() {
        var cosmetique = new ma.mysuguclientapp.entities.CategorieRestaurant();
        cosmetique.setNom("Parfumerie " + System.nanoTime());
        cosmetique.setVertical(Vertical.COSMETIQUE);
        var sauvee = categorieRepository.save(cosmetique);
        try {
            assertThat(categorieRepository.findByVerticalEffectif(Vertical.COSMETIQUE))
                    .extracting("id").contains(sauvee.getId());
            assertThat(categorieRepository.findByVerticalEffectif(Vertical.RESTAURANT))
                    .extracting("id").doesNotContain(sauvee.getId());
        } finally {
            categorieRepository.deleteById(sauvee.getId());
        }
    }

    @Test
    void categoriesSansVerticaleSontDesCategoriesRestaurant() {
        var cuisine = new ma.mysuguclientapp.entities.CategorieRestaurant();
        cuisine.setNom("Cuisine test " + System.nanoTime());
        // vertical volontairement null : donnée historique
        var sauvee = categorieRepository.save(cuisine);
        try {
            assertThat(categorieRepository.findByVerticalEffectif(Vertical.RESTAURANT))
                    .extracting("id").contains(sauvee.getId());
        } finally {
            categorieRepository.deleteById(sauvee.getId());
        }
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run : `rm -rf target && mvn -o test -Dtest=VerticalFilterTest -DargLine="-javaagent:$HOME/.m2/repository/org/mockito/mockito-core/5.20.0/mockito-core-5.20.0.jar -Xshare:off"`
Expected : ÉCHEC à la compilation — `setVertical` et `findByVerticalEffectif` n'existent pas.

- [ ] **Step 3: Ajouter le champ à l'entité**

Dans `entities/CategorieRestaurant.java`, après le champ `imageBannerUrl` :

```java
    /** Verticale à laquelle cette catégorie d'établissement s'applique. Null = RESTAURANT (données historiques). */
    @Enumerated(EnumType.STRING)
    @Column(name = "vertical")
    private Vertical vertical;
```

Ajouter les imports `ma.mysuguclientapp.enumerations.Vertical` (`jakarta.persistence.*` est déjà importé).

- [ ] **Step 4: Ajouter la requête au repository**

Dans `repositories/CategorieRestaurantRepository.java` :

```java
    /** Null est traité comme RESTAURANT : les catégories historiques restent des catégories de restaurant. */
    @org.springframework.data.jpa.repository.Query(
            "SELECT c FROM CategorieRestaurant c WHERE " +
            "(:vertical = ma.mysuguclientapp.enumerations.Vertical.RESTAURANT AND c.vertical IS NULL) " +
            "OR c.vertical = :vertical")
    java.util.List<ma.mysuguclientapp.entities.CategorieRestaurant> findByVerticalEffectif(
            @org.springframework.data.repository.query.Param("vertical") ma.mysuguclientapp.enumerations.Vertical vertical);
```

- [ ] **Step 5: Exposer et filtrer côté API**

Dans `dtos/CategorieRestaurantDTO.java`, ajouter :

```java
    private String vertical;
```

Dans le service qui construit ce DTO, renseigner `dto.setVertical(entite.getVertical() != null ? entite.getVertical().name() : "RESTAURANT")`, et ajouter une méthode de listing filtrée qui délègue à `findByVerticalEffectif`, en réutilisant `RestaurantServiceImpl.parseVertical` pour la convention (absent ⇒ RESTAURANT, `ALL` ⇒ tout, inconnue ⇒ 400).

Dans `controllers/CategorieController.java`, ajouter le paramètre optionnel au listing :

```java
    @GetMapping
    public ResponseEntity<List<CategorieRestaurantDTO>> getAllCategories(
            @RequestParam(required = false) String vertical) {
        return ResponseEntity.ok(categorieService.getAllCategories(vertical));
    }
```

- [ ] **Step 6: Run test to verify it passes**

Run : `rm -rf target && mvn -o test -Dtest=VerticalFilterTest -DargLine="-javaagent:$HOME/.m2/repository/org/mockito/mockito-core/5.20.0/mockito-core-5.20.0.jar -Xshare:off"`
Expected : PASS, 5 tests.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/ma/mysuguclientapp/entities/CategorieRestaurant.java \
        src/main/java/ma/mysuguclientapp/repositories/CategorieRestaurantRepository.java \
        src/main/java/ma/mysuguclientapp/dtos/CategorieRestaurantDTO.java \
        src/main/java/ma/mysuguclientapp/controllers/CategorieController.java \
        src/main/java/ma/mysuguclientapp/services src/test/java/ma/mysuguclientapp/VerticalFilterTest.java
git commit -m "feat(verticales): categories d'etablissement portees par une verticale"
```

---

### Task 5: Verticale sur les tuiles d'accueil

C'est ce qui permet à l'app cliente de router une tuile sans heuristique textuelle, et d'ajouter une verticale plus tard sans release mobile.

**Files:**
- Modify: `src/main/java/ma/mysuguclientapp/entities/ServiceCategorie.java`
- Modify: `src/main/java/ma/mysuguclientapp/dtos/ServiceCategorieDTO.java`
- Modify: `src/main/java/ma/mysuguclientapp/services/implementations/ServiceCategorieServiceImpl.java`
- Test: `src/test/java/ma/mysuguclientapp/VerticalFilterTest.java` (ajout)

**Interfaces:**
- Produces: `ServiceCategorieDTO.getVertical()` → `String` ou `null`. `null` signifie « cette tuile n'ouvre aucune liste d'établissements » (côté app : pas de navigation).

- [ ] **Step 1: Write the failing test**

Ajouter à `VerticalFilterTest` :

```java
    @Autowired ma.mysuguclientapp.repositories.ServiceCategorieRepository serviceCategorieRepository;
    @Autowired ma.mysuguclientapp.services.interfaces.ServiceCategorieService serviceCategorieService;

    @Test
    void laTuileDAccueilPorteSaVerticale() {
        var tuile = new ma.mysuguclientapp.entities.ServiceCategorie();
        tuile.setNom("Boutiques test " + System.nanoTime());
        tuile.setVertical(Vertical.ALIMENTAIRE);
        tuile.setIsActive(true);
        var sauvee = serviceCategorieRepository.save(tuile);
        try {
            assertThat(serviceCategorieService.getAllServices())
                    .filteredOn(s -> s.getId().equals(sauvee.getId()))
                    .allMatch(s -> "ALIMENTAIRE".equals(s.getVertical()));
        } finally {
            serviceCategorieRepository.deleteById(sauvee.getId());
        }
    }
```

Si la méthode de listing du service porte un autre nom, l'adapter (`grep -n "public .*List<ServiceCategorieDTO>" src/main/java/ma/mysuguclientapp/services/interfaces/ServiceCategorieService.java`).

- [ ] **Step 2: Run test to verify it fails**

Run : `rm -rf target && mvn -o test -Dtest=VerticalFilterTest -DargLine="-javaagent:$HOME/.m2/repository/org/mockito/mockito-core/5.20.0/mockito-core-5.20.0.jar -Xshare:off"`
Expected : ÉCHEC à la compilation — `setVertical` n'existe pas sur `ServiceCategorie`.

- [ ] **Step 3: Ajouter le champ**

Dans `entities/ServiceCategorie.java`, après le champ `type` :

```java
    /** Verticale ouverte par cette tuile. Null = la tuile n'ouvre aucune liste d'établissements. */
    @Enumerated(EnumType.STRING)
    @Column(name = "vertical")
    private ma.mysuguclientapp.enumerations.Vertical vertical;
```

Dans `dtos/ServiceCategorieDTO.java`, après `type` :

```java
    private String vertical;
```

Dans `ServiceCategorieServiceImpl`, dans la conversion vers DTO :

```java
        dto.setVertical(entite.getVertical() != null ? entite.getVertical().name() : null);
```

et dans la conversion depuis DTO (création/modification admin), accepter la valeur entrante en réutilisant la même tolérance de casse que `CategorieProduitServiceImpl.parseStrict`, avec `null` autorisé.

- [ ] **Step 4: Run test to verify it passes**

Run : `rm -rf target && mvn -o test -Dtest=VerticalFilterTest -DargLine="-javaagent:$HOME/.m2/repository/org/mockito/mockito-core/5.20.0/mockito-core-5.20.0.jar -Xshare:off"`
Expected : PASS, 6 tests.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/ma/mysuguclientapp/entities/ServiceCategorie.java \
        src/main/java/ma/mysuguclientapp/dtos/ServiceCategorieDTO.java \
        src/main/java/ma/mysuguclientapp/services/implementations/ServiceCategorieServiceImpl.java \
        src/test/java/ma/mysuguclientapp/VerticalFilterTest.java
git commit -m "feat(verticales): tuile d'accueil porteuse de sa verticale"
```

---

### Task 6: Champs de stock et disponibilité effective

**Files:**
- Modify: `src/main/java/ma/mysuguclientapp/entities/Plat.java`
- Modify: `src/main/java/ma/mysuguclientapp/dtos/PlatDTO.java`
- Modify: `src/main/java/ma/mysuguclientapp/dtos/PlatCreateDTO.java`
- Modify: `src/main/java/ma/mysuguclientapp/services/implementations/PlatServiceImpl.java` (`convertToDTO` ligne 280, `getPlatsByRestaurant` ligne 72, création et mise à jour)
- Test: `src/test/java/ma/mysuguclientapp/StockDecrementTest.java` (créé ici, complété tâches 7-8)

**Interfaces:**
- Produces: `Plat.getQuantiteStock()` / `setQuantiteStock(Integer)` ; `Plat.getSeuilAlerteStock()` ; `Plat.isEffectivementDisponible()` → `boolean` ; `PlatDTO.getQuantiteStock()`, `PlatDTO.getStockGere()` → `Boolean`, `PlatDTO.getAlerteStockBas()` → `Boolean`

- [ ] **Step 1: Write the failing test**

`src/test/java/ma/mysuguclientapp/StockDecrementTest.java` :

```java
package ma.mysuguclientapp;

import ma.mysuguclientapp.entities.Plat;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StockDecrementTest {

    private Plat plat(Boolean disponible, Integer stock) {
        Plat p = new Plat();
        p.setNom("Produit");
        p.setIsAvailable(disponible);
        p.setQuantiteStock(stock);
        return p;
    }

    @Test
    void platSansStockGereSuitLeFlagVendeur() {
        assertThat(plat(true, null).isEffectivementDisponible()).isTrue();
        assertThat(plat(false, null).isEffectivementDisponible()).isFalse();
    }

    @Test
    void stockEpuiseRendIndisponibleMemeSiFlagActif() {
        assertThat(plat(true, 0).isEffectivementDisponible()).isFalse();
    }

    @Test
    void stockPositifEtFlagActifDonneDisponible() {
        assertThat(plat(true, 3).isEffectivementDisponible()).isTrue();
    }

    @Test
    void flagVendeurDesactiveGagneSurLeStock() {
        assertThat(plat(false, 100).isEffectivementDisponible()).isFalse();
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run : `rm -rf target && mvn -o test -Dtest=StockDecrementTest -DargLine="-javaagent:$HOME/.m2/repository/org/mockito/mockito-core/5.20.0/mockito-core-5.20.0.jar -Xshare:off"`
Expected : ÉCHEC à la compilation — `setQuantiteStock` et `isEffectivementDisponible` n'existent pas.

- [ ] **Step 3: Ajouter les champs et la méthode à l'entité**

Dans `entities/Plat.java`, après `tempsPreparation` :

```java
    /** Quantité en stock. Null = stock non géré (cas de tous les plats de restaurant). */
    @Column(name = "quantite_stock")
    private Integer quantiteStock;

    /** Seuil d'alerte stock bas, affiché au commerçant. Null = pas d'alerte. */
    @Column(name = "seuil_alerte_stock")
    private Integer seuilAlerteStock;

    /**
     * Disponibilité réellement présentée au client : le flag vendeur pondéré par le stock.
     * Le flag {@code isAvailable} n'est jamais écrasé en base — un réapprovisionnement
     * rend le produit visible sans réintervention du commerçant.
     */
    @Transient
    public boolean isEffectivementDisponible() {
        return Boolean.TRUE.equals(isAvailable)
                && (quantiteStock == null || quantiteStock > 0);
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run : `rm -rf target && mvn -o test -Dtest=StockDecrementTest -DargLine="-javaagent:$HOME/.m2/repository/org/mockito/mockito-core/5.20.0/mockito-core-5.20.0.jar -Xshare:off"`
Expected : PASS, 4 tests.

- [ ] **Step 5: Exposer le stock dans les DTO**

Dans `dtos/PlatDTO.java`, après `tempsPreparation` :

```java
    private Integer quantiteStock;
    private Boolean stockGere;
    private Boolean alerteStockBas;
```

Dans `dtos/PlatCreateDTO.java`, après `tempsPreparation` :

```java
    private Integer quantiteStock;
    private Integer seuilAlerteStock;
```

- [ ] **Step 6: Câbler la conversion**

Dans `PlatServiceImpl.convertToDTO` (ligne 289), remplacer :

```java
        dto.setIsAvailable(plat.getIsAvailable());
```

par :

```java
        // Disponibilité effective : le client ne doit jamais se voir proposer un produit en rupture.
        dto.setIsAvailable(plat.isEffectivementDisponible());
        dto.setQuantiteStock(plat.getQuantiteStock());
        dto.setStockGere(plat.getQuantiteStock() != null);
        dto.setAlerteStockBas(plat.getQuantiteStock() != null
                && plat.getSeuilAlerteStock() != null
                && plat.getQuantiteStock() <= plat.getSeuilAlerteStock());
```

Dans `getPlatsByRestaurant` (ligne 75), remplacer `.filter(Plat::getIsAvailable)` par `.filter(Plat::isEffectivementDisponible)`.

Dans `getAllPlats` (ligne 55), remplacer `.filter(plat -> available == null || plat.getIsAvailable().equals(available))` par `.filter(plat -> available == null || plat.isEffectivementDisponible() == available)`.

Dans les méthodes de création et de mise à jour de plat, recopier `quantiteStock` et `seuilAlerteStock` depuis `PlatCreateDTO` vers l'entité, en ne les écrasant que si la valeur entrante est non nulle sur la mise à jour (sinon un formulaire partiel effacerait le stock).

- [ ] **Step 7: Run test to verify it passes**

Run : `rm -rf target && mvn -o test -Dtest=StockDecrementTest -DargLine="-javaagent:$HOME/.m2/repository/org/mockito/mockito-core/5.20.0/mockito-core-5.20.0.jar -Xshare:off"`
Expected : PASS, 4 tests. La compilation de tout le module doit passer.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/ma/mysuguclientapp/entities/Plat.java \
        src/main/java/ma/mysuguclientapp/dtos/PlatDTO.java \
        src/main/java/ma/mysuguclientapp/dtos/PlatCreateDTO.java \
        src/main/java/ma/mysuguclientapp/services/implementations/PlatServiceImpl.java \
        src/test/java/ma/mysuguclientapp/StockDecrementTest.java
git commit -m "feat(stock): champs quantiteStock/seuilAlerteStock + disponibilite effective"
```

---

### Task 7: Décrément du stock à la création de commande

**Files:**
- Create: `src/main/java/ma/mysuguclientapp/services/implementations/StockService.java`
- Modify: `src/main/java/ma/mysuguclientapp/repositories/PlatRepository.java`
- Modify: `src/main/java/ma/mysuguclientapp/services/implementations/CommandeServiceImpl.java` (boucle de création, lignes 193-227)
- Test: `src/test/java/ma/mysuguclientapp/StockDecrementTest.java` (ajout)

**Interfaces:**
- Consumes: `Plat.getQuantiteStock()`, `PlatRepository`
- Produces: `StockService.reserver(Long platId, int quantite)` → `void`, lève `BadRequestException` si stock insuffisant ; `StockService.restituer(Commande)` → `void` (implémenté tâche 8) ; `PlatRepository.findByIdForUpdate(Long)` → `Optional<Plat>`

**Deux pièges traités ici :**
1. **Deadlock** : deux commandes concurrentes verrouillant A puis B et B puis A se bloquent mutuellement. On trie les lignes par `platId` croissant avant la boucle, ce qui impose un ordre de verrouillage global.
2. **Même produit sur plusieurs lignes** (options différentes) : le décrément est cumulatif dans la même transaction, l'instance managée porte déjà la valeur décrémentée.

- [ ] **Step 1: Write the failing test**

Ajouter à `StockDecrementTest` — ce test devient un test Spring, donc annoter la classe `@SpringBootTest` et ajouter les imports nécessaires. Les tests unitaires purs des étapes précédentes continuent de fonctionner sous `@SpringBootTest`.

```java
    @org.springframework.beans.factory.annotation.Autowired
    ma.mysuguclientapp.repositories.PlatRepository platRepository;
    @org.springframework.beans.factory.annotation.Autowired
    ma.mysuguclientapp.repositories.RestaurantRepository restaurantRepository;
    @org.springframework.beans.factory.annotation.Autowired
    ma.mysuguclientapp.services.implementations.StockService stockService;

    private Plat creerProduitEnStock(int stock) {
        var boutique = new ma.mysuguclientapp.entities.Restaurant();
        boutique.setNom("Boutique stock " + System.nanoTime());
        boutique.setIsActive(true);
        boutique.setVertical(ma.mysuguclientapp.enumerations.Vertical.ALIMENTAIRE);
        boutique = restaurantRepository.save(boutique);

        Plat produit = new Plat();
        produit.setNom("Riz 5kg");
        produit.setPrix(new java.math.BigDecimal("50.00"));
        produit.setRestaurant(boutique);
        produit.setIsAvailable(true);
        produit.setQuantiteStock(stock);
        return platRepository.save(produit);
    }

    @Test
    void reserverDecrementeLeStock() {
        Plat produit = creerProduitEnStock(10);
        stockService.reserver(produit.getId(), 3);
        assertThat(platRepository.findById(produit.getId()).orElseThrow().getQuantiteStock()).isEqualTo(7);
    }

    @Test
    void reserverRefuseSiStockInsuffisant() {
        Plat produit = creerProduitEnStock(2);
        org.junit.jupiter.api.Assertions.assertThrows(
                ma.mysuguclientapp.exceptions.BadRequestException.class,
                () -> stockService.reserver(produit.getId(), 5));
        assertThat(platRepository.findById(produit.getId()).orElseThrow().getQuantiteStock()).isEqualTo(2);
    }

    @Test
    void reserverIgnoreLesPlatsSansStockGere() {
        Plat plat = creerProduitEnStock(5);
        plat.setQuantiteStock(null);
        platRepository.save(plat);
        stockService.reserver(plat.getId(), 999);
        assertThat(platRepository.findById(plat.getId()).orElseThrow().getQuantiteStock()).isNull();
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run : `rm -rf target && mvn -o test -Dtest=StockDecrementTest -DargLine="-javaagent:$HOME/.m2/repository/org/mockito/mockito-core/5.20.0/mockito-core-5.20.0.jar -Xshare:off"`
Expected : ÉCHEC à la compilation — `StockService` n'existe pas.

- [ ] **Step 3: Ajouter le verrou pessimiste au repository**

Dans `repositories/PlatRepository.java` :

```java
    /**
     * Charge un plat en verrouillant sa ligne jusqu'à la fin de la transaction.
     * Utilisé uniquement pour le décrément de stock, afin d'empêcher la survente concurrente.
     */
    @jakarta.persistence.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Plat p WHERE p.id = :id")
    java.util.Optional<Plat> findByIdForUpdate(@Param("id") Long id);
```

- [ ] **Step 4: Créer le StockService**

`services/implementations/StockService.java` :

```java
package ma.mysuguclientapp.services.implementations;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import ma.mysuguclientapp.entities.Plat;
import ma.mysuguclientapp.exceptions.BadRequestException;
import ma.mysuguclientapp.exceptions.ResourceNotFoundException;
import ma.mysuguclientapp.repositories.PlatRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seul point du code qui écrit {@code Plat.quantiteStock}.
 *
 * <p>Un plat dont {@code quantiteStock} est null n'est pas géré en stock : ni verrou,
 * ni décrément, ni blocage. C'est le cas de tous les plats de restaurant.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StockService {

    private final PlatRepository platRepository;

    /**
     * Réserve {@code quantite} unités du produit, sous verrou pessimiste.
     * À appeler dans la transaction qui écrit la commande.
     *
     * @throws BadRequestException si le stock disponible est insuffisant
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void reserver(Long platId, int quantite) {
        Plat plat = platRepository.findByIdForUpdate(platId)
                .orElseThrow(() -> new ResourceNotFoundException("Plat non trouve: " + platId));

        if (plat.getQuantiteStock() == null) {
            return; // stock non géré
        }
        if (plat.getQuantiteStock() < quantite) {
            throw new BadRequestException("Stock insuffisant pour " + plat.getNom()
                    + " : il reste " + plat.getQuantiteStock() + " unité(s)");
        }
        plat.setQuantiteStock(plat.getQuantiteStock() - quantite);
        platRepository.save(plat);
    }
}
```

`Propagation.MANDATORY` est délibéré : réserver du stock hors d'une transaction existante serait un bug, et on veut qu'il explose au premier appel plutôt que de créer une transaction isolée.

- [ ] **Step 5: Brancher sur la création de commande**

Dans `CommandeServiceImpl`, injecter `private final StockService stockService;`.

Juste avant la boucle `for (LigneCommandeCreateDTO ligneDTO : commandeDTO.getLignes())` (ligne 193), trier pour imposer un ordre de verrouillage global :

```java
        // Ordre de verrouillage stable : évite les interblocages entre commandes concurrentes
        // portant les mêmes produits dans un ordre différent.
        List<LigneCommandeCreateDTO> lignesTriees = commandeDTO.getLignes().stream()
                .sorted(java.util.Comparator.comparing(LigneCommandeCreateDTO::getPlatId))
                .toList();
```

puis itérer sur `lignesTriees` au lieu de `commandeDTO.getLignes()`.

Juste après le contrôle de disponibilité existant (ligne 201-203), ajouter :

```java
            stockService.reserver(plat.getId(), ligneDTO.getQuantite());
```

Le contrôle `!Boolean.TRUE.equals(plat.getIsAvailable())` qui le précède reste tel quel : il porte sur le flag vendeur, le stock est un contrôle distinct avec son propre message.

- [ ] **Step 6: Run test to verify it passes**

Run : `rm -rf target && mvn -o test -Dtest=StockDecrementTest -DargLine="-javaagent:$HOME/.m2/repository/org/mockito/mockito-core/5.20.0/mockito-core-5.20.0.jar -Xshare:off"`
Expected : PASS, 7 tests.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/ma/mysuguclientapp/services/implementations/StockService.java \
        src/main/java/ma/mysuguclientapp/repositories/PlatRepository.java \
        src/main/java/ma/mysuguclientapp/services/implementations/CommandeServiceImpl.java \
        src/test/java/ma/mysuguclientapp/StockDecrementTest.java
git commit -m "feat(stock): decrement sous verrou pessimiste a la creation de commande"
```

---

### Task 8: Restitution du stock à l'annulation, idempotente

Il existe **deux** chemins d'annulation, et ils doivent tous deux restituer, sans jamais restituer deux fois.

**Files:**
- Modify: `src/main/java/ma/mysuguclientapp/entities/Commande.java`
- Modify: `src/main/java/ma/mysuguclientapp/services/implementations/StockService.java`
- Modify: `src/main/java/ma/mysuguclientapp/services/implementations/CommandeServiceImpl.java` (`cancelCommande` ligne 587, `updateCommandeStatus` ligne 357)
- Test: `src/test/java/ma/mysuguclientapp/StockDecrementTest.java` (ajout)

**Interfaces:**
- Consumes: `StockService.reserver` (tâche 7)
- Produces: `StockService.restituer(Commande)` → `void`, idempotent ; `Commande.getStockRestitue()` → `Boolean`

- [ ] **Step 1: Write the failing test**

Ajouter à `StockDecrementTest` :

```java
    @Test
    void restituerRendLeStockUneSeuleFois() {
        Plat produit = creerProduitEnStock(10);
        stockService.reserver(produit.getId(), 4);

        var commande = new ma.mysuguclientapp.entities.Commande();
        var ligne = new ma.mysuguclientapp.entities.LigneCommande();
        ligne.setPlat(platRepository.findById(produit.getId()).orElseThrow());
        ligne.setQuantite(4);
        ligne.setCommande(commande);
        commande.setLignesCommande(new java.util.ArrayList<>(java.util.List.of(ligne)));

        stockService.restituer(commande);
        assertThat(platRepository.findById(produit.getId()).orElseThrow().getQuantiteStock()).isEqualTo(10);
        assertThat(commande.getStockRestitue()).isTrue();

        stockService.restituer(commande); // second appel : ne doit rien faire
        assertThat(platRepository.findById(produit.getId()).orElseThrow().getQuantiteStock()).isEqualTo(10);
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run : `rm -rf target && mvn -o test -Dtest=StockDecrementTest -DargLine="-javaagent:$HOME/.m2/repository/org/mockito/mockito-core/5.20.0/mockito-core-5.20.0.jar -Xshare:off"`
Expected : ÉCHEC à la compilation — `restituer` et `getStockRestitue` n'existent pas.

- [ ] **Step 3: Ajouter le marqueur d'idempotence**

Dans `entities/Commande.java`, après `raisonAnnulation` :

```java
    /** Marque que le stock des lignes a déjà été re-crédité. Empêche une double restitution. */
    @Column(name = "stock_restitue")
    private Boolean stockRestitue = false;
```

- [ ] **Step 4: Implémenter la restitution**

Dans `StockService` :

```java
    /**
     * Re-crédite le stock des lignes d'une commande annulée. Idempotent : une commande
     * déjà restituée n'est jamais re-créditée une seconde fois.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void restituer(ma.mysuguclientapp.entities.Commande commande) {
        if (Boolean.TRUE.equals(commande.getStockRestitue())) {
            return;
        }
        if (commande.getLignesCommande() != null) {
            for (ma.mysuguclientapp.entities.LigneCommande ligne : commande.getLignesCommande()) {
                if (ligne.getPlat() == null) {
                    continue;
                }
                Plat plat = platRepository.findByIdForUpdate(ligne.getPlat().getId()).orElse(null);
                if (plat == null || plat.getQuantiteStock() == null) {
                    continue; // produit supprimé, ou stock non géré
                }
                plat.setQuantiteStock(plat.getQuantiteStock() + ligne.getQuantite());
                platRepository.save(plat);
            }
        }
        commande.setStockRestitue(true);
        log.info("Stock restitue pour la commande {}", commande.getNumeroCommande());
    }
```

- [ ] **Step 5: Brancher les deux chemins d'annulation**

Dans `CommandeServiceImpl.cancelCommande`, juste après `commande.setStatut(StatutCommande.ANNULEE);` (ligne 609) :

```java
        stockService.restituer(commande);
```

Dans `CommandeServiceImpl.updateCommandeStatus`, dans le bloc `if (nouveauStatut == StatutCommande.ANNULEE)` (ligne 357), juste après `commande.setRaisonAnnulation(statusDTO.getRaisonAnnulation());` :

```java
            stockService.restituer(commande);
```

- [ ] **Step 6: Run test to verify it passes**

Run : `rm -rf target && mvn -o test -Dtest=StockDecrementTest -DargLine="-javaagent:$HOME/.m2/repository/org/mockito/mockito-core/5.20.0/mockito-core-5.20.0.jar -Xshare:off"`
Expected : PASS, 8 tests.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/ma/mysuguclientapp/entities/Commande.java \
        src/main/java/ma/mysuguclientapp/services/implementations/StockService.java \
        src/main/java/ma/mysuguclientapp/services/implementations/CommandeServiceImpl.java \
        src/test/java/ma/mysuguclientapp/StockDecrementTest.java
git commit -m "feat(stock): restitution idempotente sur les deux chemins d'annulation"
```

---

### Task 9: Preuve d'absence de survente sous concurrence

Exigence explicite de la spec. Les surventes ne se voient jamais en test manuel.

**Files:**
- Create: `src/test/java/ma/mysuguclientapp/StockConcurrenceTest.java`

**Interfaces:**
- Consumes: `StockService.reserver` (tâche 7), `TransactionTemplate`

- [ ] **Step 1: Write the failing test**

```java
package ma.mysuguclientapp;

import ma.mysuguclientapp.entities.Plat;
import ma.mysuguclientapp.entities.Restaurant;
import ma.mysuguclientapp.enumerations.Vertical;
import ma.mysuguclientapp.repositories.PlatRepository;
import ma.mysuguclientapp.repositories.RestaurantRepository;
import ma.mysuguclientapp.services.implementations.StockService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class StockConcurrenceTest {

    @Autowired StockService stockService;
    @Autowired PlatRepository platRepository;
    @Autowired RestaurantRepository restaurantRepository;
    @Autowired TransactionTemplate tx;

    @Test
    void deuxCommandesSimultaneesSurLeDernierArticleUneSeulePasse() throws Exception {
        Restaurant boutique = new Restaurant();
        boutique.setNom("Boutique concurrence " + System.nanoTime());
        boutique.setIsActive(true);
        boutique.setVertical(Vertical.ALIMENTAIRE);
        boutique = restaurantRepository.save(boutique);

        Plat produit = new Plat();
        produit.setNom("Dernier pot");
        produit.setPrix(new BigDecimal("30.00"));
        produit.setRestaurant(boutique);
        produit.setIsAvailable(true);
        produit.setQuantiteStock(1);
        Long produitId = platRepository.save(produit).getId();

        int concurrents = 8;
        var depart = new CountDownLatch(1);
        var succes = new AtomicInteger();
        var echecs = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(concurrents);

        for (int i = 0; i < concurrents; i++) {
            pool.submit(() -> {
                try {
                    depart.await();
                    tx.executeWithoutResult(s -> stockService.reserver(produitId, 1));
                    succes.incrementAndGet();
                } catch (Exception e) {
                    echecs.incrementAndGet();
                }
            });
        }
        depart.countDown();
        pool.shutdown();
        assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        assertThat(succes.get()).isEqualTo(1);
        assertThat(echecs.get()).isEqualTo(concurrents - 1);
        assertThat(platRepository.findById(produitId).orElseThrow().getQuantiteStock()).isZero();
    }
}
```

- [ ] **Step 2: Run test to verify it passes**

Run : `rm -rf target && mvn -o test -Dtest=StockConcurrenceTest -DargLine="-javaagent:$HOME/.m2/repository/org/mockito/mockito-core/5.20.0/mockito-core-5.20.0.jar -Xshare:off"`
Expected : PASS. Si le test échoue avec `succes > 1`, le verrou pessimiste de la tâche 7 n'est pas actif : vérifier que `findByIdForUpdate` est bien annotée `@Lock(PESSIMISTIC_WRITE)` et que l'appel se fait dans une transaction.

Ce test nécessite une vraie base PostgreSQL (le verrou pessimiste n'a pas de sens sur une base en mémoire). Utiliser la PG jetable du projet :
```bash
docker run --rm --network host -v $PWD:/app -v $HOME/.m2:/root/.m2 maven:3.9-eclipse-temurin-21 \
  mvn -o test -Dtest=StockConcurrenceTest \
  -Dspring.datasource.url=jdbc:postgresql://127.0.0.1:5435/mysugu_test
```

- [ ] **Step 3: Commit**

```bash
git add src/test/java/ma/mysuguclientapp/StockConcurrenceTest.java
git commit -m "test(stock): preuve d'absence de survente sous concurrence"
```

---

### Task 10: Verticale sur search, top-rated et nearby des établissements

Sans ça, dès qu'une boutique existe en base, une recherche depuis l'écran restaurant ramène des boutiques.

**Files:**
- Modify: `src/main/java/ma/mysuguclientapp/repositories/RestaurantRepository.java`
- Modify: `src/main/java/ma/mysuguclientapp/services/interfaces/RestaurantService.java`
- Modify: `src/main/java/ma/mysuguclientapp/services/implementations/RestaurantServiceImpl.java` (`searchRestaurants` ligne 96, `getTopRatedRestaurants`, `getNearbyRestaurants`)
- Modify: `src/main/java/ma/mysuguclientapp/controllers/RestaurantController.java` (lignes 59-87)
- Test: `src/test/java/ma/mysuguclientapp/NonRegressionVerticalTest.java` (créé ici, complété tâche 13)

**Interfaces:**
- Consumes: `RestaurantServiceImpl.parseVertical` (existant, ligne 75)
- Produces: `searchRestaurants(String keyword, String vertical)`, `getTopRatedRestaurants(int limit, String vertical)`, `getNearbyRestaurants(Double lat, Double lon, Double radiusKm, String vertical)`

**Point d'attention :** les signatures publiques changent. Repérer tous les appelants avant de modifier (`grep -rn "searchRestaurants\|getTopRatedRestaurants\|getNearbyRestaurants" src/main src/test`) — le shim vendeur et le shim livreur peuvent en dépendre. En cas d'appelant existant, lui passer explicitement `"ALL"` s'il doit conserver son comportement actuel non filtré, sinon `null`.

- [ ] **Step 1: Write the failing test**

`src/test/java/ma/mysuguclientapp/NonRegressionVerticalTest.java` :

```java
package ma.mysuguclientapp;

import ma.mysuguclientapp.entities.Restaurant;
import ma.mysuguclientapp.enumerations.Vertical;
import ma.mysuguclientapp.repositories.RestaurantRepository;
import ma.mysuguclientapp.services.interfaces.RestaurantService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NonRegressionVerticalTest {

    @Autowired RestaurantService restaurantService;
    @Autowired RestaurantRepository restaurantRepository;
    @Autowired TransactionTemplate tx;

    private static final String MARQUEUR = "ZzMarqueurNonRegression";
    private Long boutiqueId;
    private Long restoId;

    @BeforeAll
    void setup() {
        Restaurant boutique = new Restaurant();
        boutique.setNom(MARQUEUR + " Epicerie");
        boutique.setIsActive(true);
        boutique.setVertical(Vertical.ALIMENTAIRE);
        boutique.setAppreciation(5.0);
        boutiqueId = restaurantRepository.save(boutique).getId();

        Restaurant resto = new Restaurant();
        resto.setNom(MARQUEUR + " Resto");
        resto.setIsActive(true);
        resto.setVertical(Vertical.RESTAURANT);
        resto.setAppreciation(5.0);
        restoId = restaurantRepository.save(resto).getId();
    }

    @AfterAll
    void cleanup() {
        tx.executeWithoutResult(s -> {
            restaurantRepository.deleteById(boutiqueId);
            restaurantRepository.deleteById(restoId);
        });
    }

    @Test
    void searchSansVerticalNeVoitQueLesRestaurants() {
        var resultats = restaurantService.searchRestaurants(MARQUEUR, null);
        assertThat(resultats).extracting("id").contains(restoId).doesNotContain(boutiqueId);
    }

    @Test
    void searchAvecVerticalAlimentaireNeVoitQueLesBoutiques() {
        var resultats = restaurantService.searchRestaurants(MARQUEUR, "ALIMENTAIRE");
        assertThat(resultats).extracting("id").contains(boutiqueId).doesNotContain(restoId);
    }

    @Test
    void searchAvecAllVoitLesDeux() {
        var resultats = restaurantService.searchRestaurants(MARQUEUR, "ALL");
        assertThat(resultats).extracting("id").contains(boutiqueId, restoId);
    }

    @Test
    void topRatedSansVerticalNeVoitQueLesRestaurants() {
        var resultats = restaurantService.getTopRatedRestaurants(200, null);
        assertThat(resultats).extracting("id").doesNotContain(boutiqueId);
    }

    @Test
    void verticaleInconnueEstRejetee() {
        Assertions.assertThrows(ma.mysuguclientapp.exceptions.BadRequestException.class,
                () -> restaurantService.searchRestaurants(MARQUEUR, "PHARMACIE"));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run : `rm -rf target && mvn -o test -Dtest=NonRegressionVerticalTest -DargLine="-javaagent:$HOME/.m2/repository/org/mockito/mockito-core/5.20.0/mockito-core-5.20.0.jar -Xshare:off"`
Expected : ÉCHEC à la compilation — `searchRestaurants` ne prend qu'un argument.

- [ ] **Step 3: Ajouter les requêtes filtrées au repository**

Dans `repositories/RestaurantRepository.java` :

```java
    /** Recherche filtrée par verticale. Null en base est traité comme RESTAURANT. */
    @Query("SELECT r FROM Restaurant r WHERE r.isActive = true AND " +
            "(LOWER(r.nom) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(r.description) LIKE LOWER(CONCAT('%', :keyword, '%'))) AND " +
            "((:vertical = ma.mysuguclientapp.enumerations.Vertical.RESTAURANT AND r.vertical IS NULL) " +
            " OR r.vertical = :vertical)")
    List<Restaurant> searchByKeywordAndVertical(@Param("keyword") String keyword,
                                                @Param("vertical") Vertical vertical);

    @Query("SELECT r FROM Restaurant r WHERE r.isActive = true AND " +
            "((:vertical = ma.mysuguclientapp.enumerations.Vertical.RESTAURANT AND r.vertical IS NULL) " +
            " OR r.vertical = :vertical) ORDER BY r.appreciation DESC")
    List<Restaurant> findByVerticalOrderByAppreciationDesc(@Param("vertical") Vertical vertical);
```

- [ ] **Step 4: Propager dans le service**

Dans `RestaurantServiceImpl` :

- `searchRestaurants(String keyword, String vertical)` : si `"ALL"` (insensible à la casse) → `searchByKeyword(keyword)` inchangé ; sinon `searchByKeywordAndVertical(keyword, parseVertical(vertical))`.
- `getTopRatedRestaurants(int limit, String vertical)` : si `"ALL"` → `findByIsActiveOrderByAppreciationDesc(true)` inchangé ; sinon `findByVerticalOrderByAppreciationDesc(parseVertical(vertical))`. Puis `limit`.
- `getNearbyRestaurants(Double latitude, Double longitude, Double radiusKm, String vertical)` : appliquer le même aiguillage sur la source, avant le filtrage par distance existant.

Mettre à jour `services/interfaces/RestaurantService.java` en conséquence.

- [ ] **Step 5: Propager dans le contrôleur**

Dans `controllers/RestaurantController.java` :

```java
    @GetMapping("/search")
    public ResponseEntity<List<RestaurantDTO>> searchRestaurants(
            @RequestParam String keyword,
            @RequestParam(required = false) String vertical) {
        return ResponseEntity.ok(restaurantService.searchRestaurants(keyword, vertical));
    }

    @GetMapping("/top-rated")
    public ResponseEntity<List<RestaurantDTO>> getTopRatedRestaurants(
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) String vertical) {
        return ResponseEntity.ok(restaurantService.getTopRatedRestaurants(limit, vertical));
    }

    @GetMapping("/nearby")
    public ResponseEntity<List<RestaurantDTO>> getNearbyRestaurants(
            @RequestParam Double latitude,
            @RequestParam Double longitude,
            @RequestParam(defaultValue = "5.0") Double radiusKm,
            @RequestParam(required = false) String vertical) {
        return ResponseEntity.ok(
                restaurantService.getNearbyRestaurants(latitude, longitude, radiusKm, vertical));
    }
```

- [ ] **Step 6: Run test to verify it passes**

Run : `rm -rf target && mvn -o test -Dtest=NonRegressionVerticalTest -DargLine="-javaagent:$HOME/.m2/repository/org/mockito/mockito-core/5.20.0/mockito-core-5.20.0.jar -Xshare:off"`
Expected : PASS, 5 tests.

- [ ] **Step 7: Commit**

```bash
git add src/main/java/ma/mysuguclientapp/repositories/RestaurantRepository.java \
        src/main/java/ma/mysuguclientapp/services/interfaces/RestaurantService.java \
        src/main/java/ma/mysuguclientapp/services/implementations/RestaurantServiceImpl.java \
        src/main/java/ma/mysuguclientapp/controllers/RestaurantController.java \
        src/test/java/ma/mysuguclientapp/NonRegressionVerticalTest.java
git commit -m "feat(verticales): parametre vertical sur search, top-rated et nearby"
```

---

### Task 11: Verticale et rayon sur les produits, avec pagination en base

Traite à la fois la fuite de verticale et la dette de performance : `getAllPlats` charge aujourd'hui **toute** la table avant de filtrer et paginer en mémoire (`PlatServiceImpl` ligne 50).

**Files:**
- Modify: `src/main/java/ma/mysuguclientapp/repositories/PlatRepository.java`
- Modify: `src/main/java/ma/mysuguclientapp/services/interfaces/PlatService.java`
- Modify: `src/main/java/ma/mysuguclientapp/services/implementations/PlatServiceImpl.java` (`getAllPlats` lignes 39-60, `searchPlats` ligne 82)
- Modify: `src/main/java/ma/mysuguclientapp/controllers/PlatController.java` (lignes 28-52)
- Test: `src/test/java/ma/mysuguclientapp/NonRegressionVerticalTest.java` (ajout)

**Interfaces:**
- Produces: `getAllPlats(Long restaurantId, String categorie, String categorieProduit, Boolean available, String vertical, Pageable)` → `Page<PlatDTO>` ; `searchPlats(String keyword, String vertical)` → `List<PlatDTO>`

**Point d'attention :** l'ordre des paramètres de `getAllPlats` change. Repérer les appelants (`grep -rn "getAllPlats\|searchPlats" src/main src/test`) et les mettre à jour.

- [ ] **Step 1: Write the failing test**

Ajouter à `NonRegressionVerticalTest` :

```java
    @Autowired ma.mysuguclientapp.repositories.PlatRepository platRepository;
    @Autowired ma.mysuguclientapp.services.interfaces.PlatService platService;

    private Long produitBoutiqueId;
    private Long platRestoId;

    @BeforeAll
    void setupProduits() {
        var produit = new ma.mysuguclientapp.entities.Plat();
        produit.setNom(MARQUEUR + " Savon");
        produit.setPrix(new java.math.BigDecimal("20.00"));
        produit.setIsAvailable(true);
        produit.setCategorieProduit("epicerie");
        produit.setRestaurant(restaurantRepository.findById(boutiqueId).orElseThrow());
        produitBoutiqueId = platRepository.save(produit).getId();

        var plat = new ma.mysuguclientapp.entities.Plat();
        plat.setNom(MARQUEUR + " Tajine");
        plat.setPrix(new java.math.BigDecimal("80.00"));
        plat.setIsAvailable(true);
        plat.setRestaurant(restaurantRepository.findById(restoId).orElseThrow());
        platRestoId = platRepository.save(plat).getId();
    }

    @Test
    void platsSansVerticalNeVoientQueLesPlatsDeRestaurant() {
        var page = platService.getAllPlats(null, null, null, null, null,
                org.springframework.data.domain.PageRequest.of(0, 500));
        assertThat(page.getContent()).extracting("id")
                .contains(platRestoId).doesNotContain(produitBoutiqueId);
    }

    @Test
    void platsFiltresParRayon() {
        var page = platService.getAllPlats(null, null, "epicerie", null, "ALIMENTAIRE",
                org.springframework.data.domain.PageRequest.of(0, 500));
        assertThat(page.getContent()).extracting("id").contains(produitBoutiqueId);
    }

    @Test
    void platsRayonInexistantRenvoieVide() {
        var page = platService.getAllPlats(null, null, "rayon_inexistant", null, "ALIMENTAIRE",
                org.springframework.data.domain.PageRequest.of(0, 500));
        assertThat(page.getContent()).isEmpty();
    }

    @Test
    void searchPlatsSansVerticalNeVoitQueLeRestaurant() {
        assertThat(platService.searchPlats(MARQUEUR, null)).extracting("id")
                .contains(platRestoId).doesNotContain(produitBoutiqueId);
    }
```

Ajouter au `cleanup` existant, **avant** la suppression des établissements :
```java
            platRepository.deleteById(produitBoutiqueId);
            platRepository.deleteById(platRestoId);
```

- [ ] **Step 2: Run test to verify it fails**

Run : `rm -rf target && mvn -o test -Dtest=NonRegressionVerticalTest -DargLine="-javaagent:$HOME/.m2/repository/org/mockito/mockito-core/5.20.0/mockito-core-5.20.0.jar -Xshare:off"`
Expected : ÉCHEC à la compilation — `getAllPlats` n'a pas cette signature.

- [ ] **Step 3: Requête paginée unique en base**

Dans `repositories/PlatRepository.java` :

```java
    /**
     * Listing paginé filtré en base. Tous les critères sont optionnels (null = pas de filtre),
     * sauf la verticale : null y signifie « toutes verticales », l'aiguillage RESTAURANT-par-défaut
     * étant fait par le service.
     */
    @Query("SELECT p FROM Plat p WHERE " +
            "(:restaurantId IS NULL OR p.restaurant.id = :restaurantId) AND " +
            "(:categoriePlat IS NULL OR p.categoriePlat = :categoriePlat) AND " +
            "(:categorieProduit IS NULL OR p.categorieProduit = :categorieProduit) AND " +
            "(:vertical IS NULL OR " +
            " (:vertical = ma.mysuguclientapp.enumerations.Vertical.RESTAURANT AND p.restaurant.vertical IS NULL) " +
            " OR p.restaurant.vertical = :vertical)")
    Page<Plat> rechercheFiltree(@Param("restaurantId") Long restaurantId,
                                @Param("categoriePlat") CategoriePlat categoriePlat,
                                @Param("categorieProduit") String categorieProduit,
                                @Param("vertical") ma.mysuguclientapp.enumerations.Vertical vertical,
                                Pageable pageable);

    @Query("SELECT p FROM Plat p WHERE " +
            "(LOWER(p.nom) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
            "LOWER(p.description) LIKE LOWER(CONCAT('%', :keyword, '%'))) AND " +
            "(:vertical IS NULL OR " +
            " (:vertical = ma.mysuguclientapp.enumerations.Vertical.RESTAURANT AND p.restaurant.vertical IS NULL) " +
            " OR p.restaurant.vertical = :vertical)")
    List<Plat> searchByKeywordAndVertical(@Param("keyword") String keyword,
                                          @Param("vertical") ma.mysuguclientapp.enumerations.Vertical vertical);
```

Le filtre `available` reste appliqué après conversion, car il porte sur la disponibilité **effective** (flag + stock + expiration de l'indisponibilité temporaire), qui n'est pas exprimable en SQL seul.

- [ ] **Step 4: Réécrire getAllPlats et searchPlats**

Dans `PlatServiceImpl`, remplacer le corps de `getAllPlats` :

```java
    @Override
    @Transactional(readOnly = true)
    public Page<PlatDTO> getAllPlats(Long restaurantId, String categorie, String categorieProduit,
                                     Boolean available, String vertical, Pageable pageable) {
        CategoriePlat categoriePlat = parseCategorie(categorie);
        Vertical v = "ALL".equalsIgnoreCase(vertical) ? null : parseVertical(vertical);

        Page<Plat> page = platRepository.rechercheFiltree(
                restaurantId, categoriePlat, categorieProduit, v, pageable);

        List<PlatDTO> contenu = page.getContent().stream()
                .map(this::refreshAvailabilityIfNeeded)
                .filter(plat -> available == null || plat.isEffectivementDisponible() == available)
                .map(this::convertToDTO)
                .toList();

        return new PageImpl<>(contenu, pageable, page.getTotalElements());
    }
```

Ajouter dans `PlatServiceImpl` la même méthode `parseVertical` que `RestaurantServiceImpl` (absent ⇒ RESTAURANT, inconnue ⇒ `BadRequestException`), ou l'extraire dans une classe utilitaire partagée `ma.mysuguclientapp.services.implementations.VerticalParser` si les deux copies dérangent. **Une seule définition du comportement, quelle que soit l'option retenue.**

Remplacer `searchPlats` :

```java
    @Override
    @Transactional(readOnly = true)
    public List<PlatDTO> searchPlats(String keyword, String vertical) {
        Vertical v = "ALL".equalsIgnoreCase(vertical) ? null : parseVertical(vertical);
        return platRepository.searchByKeywordAndVertical(keyword, v).stream()
                .map(this::refreshAvailabilityIfNeeded)
                .map(this::convertToDTO)
                .toList();
    }
```

Mettre à jour `services/interfaces/PlatService.java`.

- [ ] **Step 5: Propager dans le contrôleur**

```java
    @GetMapping
    public ResponseEntity<Page<PlatDTO>> getAllPlats(
            @RequestParam(required = false) Long restaurantId,
            @RequestParam(required = false) String categorie,
            @RequestParam(required = false) String categorieProduit,
            @RequestParam(required = false) Boolean available,
            @RequestParam(required = false) String vertical,
            Pageable pageable) {
        return ResponseEntity.ok(
                platService.getAllPlats(restaurantId, categorie, categorieProduit, available, vertical, pageable));
    }

    @GetMapping("/search")
    public ResponseEntity<List<PlatDTO>> searchPlats(
            @RequestParam String keyword,
            @RequestParam(required = false) String vertical) {
        return ResponseEntity.ok(platService.searchPlats(keyword, vertical));
    }
```

- [ ] **Step 6: Ajouter les index**

Dans `entities/Plat.java`, remplacer `@Table(name = "plats")` par :

```java
@Table(name = "plats", indexes = {
        @Index(name = "idx_plats_restaurant_rayon", columnList = "restaurant_id, categorie_produit")
})
```

Dans `entities/Restaurant.java`, remplacer `@Table(name = "restaurants")` par :

```java
@Table(name = "restaurants", indexes = {
        @Index(name = "idx_restaurants_vertical_actif", columnList = "vertical, is_active")
})
```

- [ ] **Step 7: Run test to verify it passes**

Run : `rm -rf target && mvn -o test -Dtest=NonRegressionVerticalTest -DargLine="-javaagent:$HOME/.m2/repository/org/mockito/mockito-core/5.20.0/mockito-core-5.20.0.jar -Xshare:off"`
Expected : PASS, 9 tests.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/ma/mysuguclientapp/repositories/PlatRepository.java \
        src/main/java/ma/mysuguclientapp/services/interfaces/PlatService.java \
        src/main/java/ma/mysuguclientapp/services/implementations/PlatServiceImpl.java \
        src/main/java/ma/mysuguclientapp/controllers/PlatController.java \
        src/main/java/ma/mysuguclientapp/entities/Plat.java \
        src/main/java/ma/mysuguclientapp/entities/Restaurant.java \
        src/test/java/ma/mysuguclientapp/NonRegressionVerticalTest.java
git commit -m "perf+feat(verticales): listing produits filtre et pagine en base, verticale et rayon"
```

---

### Task 12: Rayons de la boutique exposés au détail de l'établissement

Permet à l'app d'afficher des onglets par rayon sans rien connaître du métier.

**Files:**
- Modify: `src/main/java/ma/mysuguclientapp/dtos/RestaurantDTO.java`
- Modify: `src/main/java/ma/mysuguclientapp/repositories/PlatRepository.java`
- Modify: `src/main/java/ma/mysuguclientapp/services/implementations/RestaurantServiceImpl.java` (`getRestaurantById` ligne 88)
- Modify: `src/main/java/ma/mysuguclientapp/controllers/RestaurantController.java` (ligne 146, `/{id}/plats`)
- Test: `src/test/java/ma/mysuguclientapp/RayonsBoutiqueTest.java`

**Interfaces:**
- Consumes: `CategorieProduitRepository` (tâche 1)
- Produces: `RestaurantDTO.getRayons()` → `List<EnumOptionDTO>` (vide pour un restaurant) ; `GET /api/restaurants/{id}/plats?categorieProduit=…`

**Décision de conception :** les rayons ne sont calculés **que** dans `getRestaurantById` (écran détail), jamais dans le listing — sinon on déclenche une requête par établissement sur chaque page de résultats.

- [ ] **Step 1: Write the failing test**

`src/test/java/ma/mysuguclientapp/RayonsBoutiqueTest.java` :

```java
package ma.mysuguclientapp;

import ma.mysuguclientapp.entities.Plat;
import ma.mysuguclientapp.entities.Restaurant;
import ma.mysuguclientapp.enumerations.Vertical;
import ma.mysuguclientapp.repositories.PlatRepository;
import ma.mysuguclientapp.repositories.RestaurantRepository;
import ma.mysuguclientapp.services.interfaces.RestaurantService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RayonsBoutiqueTest {

    @Autowired RestaurantService restaurantService;
    @Autowired RestaurantRepository restaurantRepository;
    @Autowired PlatRepository platRepository;
    @Autowired TransactionTemplate tx;

    private Long boutiqueId;
    private Long restoId;

    @BeforeAll
    void setup() {
        Restaurant boutique = new Restaurant();
        boutique.setNom("Superette " + System.nanoTime());
        boutique.setIsActive(true);
        boutique.setVertical(Vertical.ALIMENTAIRE);
        boutique = restaurantRepository.save(boutique);
        boutiqueId = boutique.getId();

        platRepository.save(produit(boutique, "Pommes", "fruits_legumes"));
        platRepository.save(produit(boutique, "Riz", "epicerie"));
        platRepository.save(produit(boutique, "Bananes", "fruits_legumes"));

        Restaurant resto = new Restaurant();
        resto.setNom("Resto " + System.nanoTime());
        resto.setIsActive(true);
        resto.setVertical(Vertical.RESTAURANT);
        resto = restaurantRepository.save(resto);
        restoId = resto.getId();
        platRepository.save(produit(resto, "Tajine", null));
    }

    private Plat produit(Restaurant commerce, String nom, String rayon) {
        Plat p = new Plat();
        p.setNom(nom);
        p.setPrix(new BigDecimal("15.00"));
        p.setIsAvailable(true);
        p.setRestaurant(commerce);
        p.setCategorieProduit(rayon);
        return p;
    }

    @AfterAll
    void cleanup() {
        tx.executeWithoutResult(s -> {
            platRepository.deleteAll(platRepository.findByRestaurantId(boutiqueId));
            platRepository.deleteAll(platRepository.findByRestaurantId(restoId));
            restaurantRepository.deleteById(boutiqueId);
            restaurantRepository.deleteById(restoId);
        });
    }

    @Test
    void laBoutiqueExposeSesRayonsNonVidesAvecLeurLibelle() {
        var rayons = restaurantService.getRestaurantById(boutiqueId).getRayons();
        assertThat(rayons).extracting("value").containsExactly("fruits_legumes", "epicerie");
        assertThat(rayons).extracting("label").containsExactly("Fruits & légumes", "Épicerie");
    }

    @Test
    void unRestaurantNExposeAucunRayon() {
        assertThat(restaurantService.getRestaurantById(restoId).getRayons()).isEmpty();
    }
}
```

L'ordre attendu (`fruits_legumes` puis `epicerie`) est celui du seed (`ordre` 0 puis 1), pas l'ordre alphabétique ni l'ordre d'insertion des produits.

- [ ] **Step 2: Run test to verify it fails**

Run : `rm -rf target && mvn -o test -Dtest=RayonsBoutiqueTest -DargLine="-javaagent:$HOME/.m2/repository/org/mockito/mockito-core/5.20.0/mockito-core-5.20.0.jar -Xshare:off"`
Expected : ÉCHEC à la compilation — `getRayons` n'existe pas.

- [ ] **Step 3: Ajouter le champ au DTO**

Dans `dtos/RestaurantDTO.java`, après `vertical` :

```java
    /** Rayons non vides de cet établissement, ordonnés. Vide pour un restaurant. */
    private java.util.List<EnumOptionDTO> rayons = java.util.List.of();
```

- [ ] **Step 4: Requête des rayons distincts**

Dans `repositories/PlatRepository.java` :

```java
    @Query("SELECT DISTINCT p.categorieProduit FROM Plat p " +
            "WHERE p.restaurant.id = :restaurantId AND p.categorieProduit IS NOT NULL")
    List<String> findRayonsUtilises(@Param("restaurantId") Long restaurantId);
```

- [ ] **Step 5: Renseigner les rayons au détail**

Dans `RestaurantServiceImpl`, injecter `platRepository` et `categorieProduitRepository`, puis dans `getRestaurantById` (ligne 88), après `convertToDTO` :

```java
        RestaurantDTO dto = convertToDTO(restaurant, null, null);
        Vertical v = restaurant.getVertical() != null ? restaurant.getVertical() : Vertical.RESTAURANT;
        if (v != Vertical.RESTAURANT) {
            java.util.Set<String> utilises =
                    new java.util.HashSet<>(platRepository.findRayonsUtilises(restaurant.getId()));
            dto.setRayons(categorieProduitRepository.findByVerticalAndActifTrueOrderByOrdreAsc(v).stream()
                    .filter(c -> utilises.contains(c.getCode()))
                    .map(c -> new EnumOptionDTO(c.getCode(), c.getLibelle()))
                    .toList());
        }
        return dto;
```

L'ordre vient du catalogue de rayons, pas des produits : un rayon renommé ou réordonné en admin se reflète immédiatement.

- [ ] **Step 6: Filtrer les produits par rayon sur l'endpoint de l'établissement**

Dans `controllers/RestaurantController.java` (ligne 146) :

```java
    @GetMapping("/{id}/plats")
    public ResponseEntity<List<PlatDTO>> getRestaurantPlats(
            @PathVariable Long id,
            @RequestParam(required = false) String categorieProduit) {
        return ResponseEntity.ok(restaurantService.getRestaurantPlats(id, categorieProduit));
    }
```

Côté service, déléguer à `platService.getAllPlats(id, null, categorieProduit, true, "ALL", Pageable.unpaged())` et renvoyer le contenu — `"ALL"` parce que la verticale est déjà déterminée par l'établissement demandé. Adapter la signature dans `RestaurantService`.

- [ ] **Step 7: Run test to verify it passes**

Run : `rm -rf target && mvn -o test -Dtest=RayonsBoutiqueTest -DargLine="-javaagent:$HOME/.m2/repository/org/mockito/mockito-core/5.20.0/mockito-core-5.20.0.jar -Xshare:off"`
Expected : PASS, 2 tests.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/ma/mysuguclientapp/dtos/RestaurantDTO.java \
        src/main/java/ma/mysuguclientapp/repositories/PlatRepository.java \
        src/main/java/ma/mysuguclientapp/services \
        src/main/java/ma/mysuguclientapp/controllers/RestaurantController.java \
        src/test/java/ma/mysuguclientapp/RayonsBoutiqueTest.java
git commit -m "feat(verticales): rayons non vides au detail boutique + filtre par rayon"
```

---

### Task 13: Parcours boutique de bout en bout et suite complète

Le livrable du sous-projet doit être démontrable sans aucune app : une boutique, un catalogue, une commande, une livraison.

**Files:**
- Test: `src/test/java/ma/mysuguclientapp/ParcoursBoutiqueE2ETest.java`

**Interfaces:**
- Consumes: tout ce qui précède, plus `CommandeService` et `PanierService` existants

- [ ] **Step 1: Write the test**

```java
package ma.mysuguclientapp;

import ma.mysuguclientapp.entities.*;
import ma.mysuguclientapp.enumerations.*;
import ma.mysuguclientapp.repositories.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prouve qu'une commande en boutique traverse la même machine à états qu'une commande
 * restaurant, et que le stock est décrémenté puis restitué au bon moment.
 */
@SpringBootTest
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ParcoursBoutiqueE2ETest {

    @Autowired RestaurantRepository restaurantRepository;
    @Autowired PlatRepository platRepository;
    @Autowired UserRepository userRepository;
    @Autowired ma.mysuguclientapp.services.interfaces.CommandeService commandeService;

    private Long boutiqueId;
    private Long produitId;
    private Long clientId;

    @BeforeAll
    void setup() {
        Restaurant boutique = new Restaurant();
        boutique.setNom("Boutique E2E " + System.nanoTime());
        boutique.setIsActive(true);
        // autoCloseEnabled reste false : isRestaurantOpenNow renvoie true sans horaires.
        boutique.setVertical(Vertical.ALIMENTAIRE);
        boutiqueId = restaurantRepository.save(boutique).getId();

        Plat produit = new Plat();
        produit.setNom("Huile d'argan");
        produit.setPrix(new BigDecimal("120.00"));
        produit.setIsAvailable(true);
        produit.setQuantiteStock(5);
        produit.setCategorieProduit("epicerie");
        produit.setRestaurant(restaurantRepository.findById(boutiqueId).orElseThrow());
        produitId = platRepository.save(produit).getId();

        User client = new User();
        client.setEmail("client.e2e." + System.nanoTime() + "@mysugu.test");
        client.setPassword("motdepasse");
        client.setNom("Test");
        client.setPrenom("Client");
        client.setRole(UserRole.CLIENT);
        clientId = userRepository.save(client).getId();
    }

    @AfterAll
    void cleanup() {
        // Les commandes référencent produit et client : on laisse les lignes en base,
        // les identifiants étant uniques par exécution (System.nanoTime).
    }

    @Test
    void commanderEnBoutiqueDecrementeLeStockEtAnnulerLeRestitue() {
        var ligne = new ma.mysuguclientapp.dtos.LigneCommandeCreateDTO();
        ligne.setPlatId(produitId);
        ligne.setQuantite(2);

        var creation = new ma.mysuguclientapp.dtos.CommandeCreateDTO();
        creation.setClientId(clientId);
        creation.setRestaurantId(boutiqueId);
        creation.setLignes(java.util.List.of(ligne));
        // RETRAIT_SUR_PLACE : évite d'avoir à créer une zone de déploiement pour ce test.
        creation.setModeReception(ModeReceptionCommande.RETRAIT_SUR_PLACE.name());
        creation.setMethodePaiement("ESPECES");

        var commande = commandeService.createCommande(creation);
        assertThat(commande.getId()).isNotNull();
        assertThat(platRepository.findById(produitId).orElseThrow().getQuantiteStock()).isEqualTo(3);

        var annulee = commandeService.cancelCommande(commande.getId());
        assertThat(annulee.getStatut()).isEqualTo(StatutCommande.ANNULEE.name());
        assertThat(platRepository.findById(produitId).orElseThrow().getQuantiteStock()).isEqualTo(5);
    }

    @Test
    void commanderPlusQueLeStockEstRefuse() {
        var ligne = new ma.mysuguclientapp.dtos.LigneCommandeCreateDTO();
        ligne.setPlatId(produitId);
        ligne.setQuantite(999);

        var creation = new ma.mysuguclientapp.dtos.CommandeCreateDTO();
        creation.setClientId(clientId);
        creation.setRestaurantId(boutiqueId);
        creation.setLignes(java.util.List.of(ligne));
        creation.setModeReception(ModeReceptionCommande.RETRAIT_SUR_PLACE.name());
        creation.setMethodePaiement("ESPECES");

        Assertions.assertThrows(ma.mysuguclientapp.exceptions.BadRequestException.class,
                () -> commandeService.createCommande(creation));
    }
}
```

Deux points vérifiés dans le code avant d'écrire ce test, à ne pas redécouvrir :
- La méthode s'appelle `createCommande` (`CommandeServiceImpl` ligne 163), pas `creerCommande`.
- `isRestaurantOpenNow` (ligne 1048) renvoie `true` dès que `autoCloseEnabled` est absent ou faux : une boutique sans horaires est donc ouverte, aucun horaire à poser dans le test.
- `getStatut()` du `CommandeDTO` est une chaîne — comparer à `StatutCommande.ANNULEE.name()`. Si le DTO expose l'enum, retirer le `.name()`.

- [ ] **Step 2: Run the test**

Run : `rm -rf target && mvn -o test -Dtest=ParcoursBoutiqueE2ETest -DargLine="-javaagent:$HOME/.m2/repository/org/mockito/mockito-core/5.20.0/mockito-core-5.20.0.jar -Xshare:off"`
Expected : PASS.

- [ ] **Step 3: Lancer la suite complète**

Run : `rm -rf target && mvn -o test -DargLine="-javaagent:$HOME/.m2/repository/org/mockito/mockito-core/5.20.0/mockito-core-5.20.0.jar -Xshare:off"`
Expected : verte, **sauf `PushNotificationTest`** (4 échecs + 3 erreurs) qui est cassé à la base depuis juin et sans rapport avec ce travail. Tout autre échec est une régression introduite ici et doit être corrigé avant le commit.

- [ ] **Step 4: Commit**

```bash
git add src/test/java/ma/mysuguclientapp/ParcoursBoutiqueE2ETest.java
git commit -m "test(verticales): parcours d'achat boutique de bout en bout"
```

---

## Definition of done du sous-projet

- [ ] Une boutique créée en base est listable via `GET /api/restaurants?vertical=ALIMENTAIRE` et invisible sans le paramètre
- [ ] Son catalogue est navigable par rayon, et les rayons sont administrables sans déploiement
- [ ] Une commande boutique traverse la machine à états jusqu'à `LIVREE`, frais de livraison et commission calculés par le code existant, non modifié
- [ ] Le stock est décrémenté à la commande, restitué à l'annulation une seule fois, et deux commandes concurrentes sur le dernier article n'en laissent passer qu'une
- [ ] Aucun appel sans paramètre `vertical` ne change de réponse — prouvé par `NonRegressionVerticalTest`
- [ ] Suite complète verte hors `PushNotificationTest`
