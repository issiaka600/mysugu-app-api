# Options de plats (#2) — Phase 3 (Panier/Commande) — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Permettre au client de choisir les options d'un plat (sauces, suppléments…), valider la sélection, répercuter les suppléments sur le prix, et conserver un snapshot des options choisies dans le panier puis la commande.

**Architecture:** Le panier et la commande sont découplés (la commande est construite à partir des lignes envoyées par le client, pas de l'entité Panier). Les `optionItemIds` transitent donc dans `AjouterItemDTO` (panier) ET `LigneCommandeCreateDTO` (commande). Une logique partagée `OptionSelectionService` valide la sélection contre le catalogue du plat et calcule le supplément ; chaque flux fige `prixUnitaire = prix plat + Σ suppléments` et écrit un snapshot (`PanierItemOption` / `LigneCommandeOption`).

**Tech Stack:** Spring Boot 4 / JPA / Postgres ; React + Vite + TypeScript.

## Global Constraints

- Backend Java 21 ; build via `cmd.exe /c "C:\Users\Issiaka traoré\Desktop\systalink\mysugu\_build_local.bat" > /tmp/winbuild.log 2>&1; tail -5 /tmp/winbuild.log` → succès = `BUILD_EXIT=0`. Jamais `mvn`/`mvnw` direct.
- Backend local : `_run_local.bat` (postgres :5433, minio :9100, `TEST_DATA_ENABLED=true`) ; joignable depuis WSL via `http://$(ip route|grep default|awk '{print $3}'):8083` (PAS localhost). Comptes seedés : client `client.demo@mysuku.ma`/`demo1234`, owner `owner.demo@mysuku.ma`/`demo1234`, admin `admin.demo@mysuku.ma`/`demo1234`.
- Front : `npx tsc -b --noEmit` → `TSC_EXIT:0`.
- Règles de sélection (catalogue existant `OptionGroup`/`OptionItem`) : items choisis doivent appartenir au plat et être `disponible` ; par groupe `obligatoire` ⇒ au moins `max(minSelections,1)` choix ; `SINGLE` ⇒ au plus 1 choix ; `MULTIPLE` ⇒ entre `minSelections` et `maxSelections` (si défini) ; sinon 400.
- `prixUnitaire` de ligne (panier et commande) = `plat.getPrix() + Σ prixSupplement des items choisis`. Les commissions/totaux commande existants se basent déjà sur `montantTotal` de ligne → corrects automatiquement.
- Snapshot = copie figée (`optionItemId`, `optionGroupNom`, `optionNom`, `prixSupplement`) indépendante du catalogue.
- Branche dédiée `feat/plat-options-cart` : backend depuis `feat/plat-options`, app users depuis `feat/plat-options`. Créer la branche AVANT le premier commit. Jamais `git add -A` ; n'ajouter que les fichiers listés par tâche.
- Conventions : commentaires français ; `@Data` Lombok ; services `@RequiredArgsConstructor`.

---

### Task 1 : Entités snapshot + collections (backend)

**Files:**
- Create: `MySuguClientApp/src/main/java/ma/mysuguclientapp/entities/PanierItemOption.java`
- Create: `MySuguClientApp/src/main/java/ma/mysuguclientapp/entities/LigneCommandeOption.java`
- Modify: `MySuguClientApp/src/main/java/ma/mysuguclientapp/entities/PanierItem.java` (collection `options`)
- Modify: `MySuguClientApp/src/main/java/ma/mysuguclientapp/entities/LigneCommande.java` (collection `options`)

**Interfaces:**
- Produces: `PanierItemOption{id,panierItem,optionItemId,optionGroupNom,optionNom,prixSupplement}`, `LigneCommandeOption{id,ligneCommande,optionItemId,optionGroupNom,optionNom,prixSupplement}`, `PanierItem.getOptions()`, `LigneCommande.getOptions()`.

- [ ] **Step 1: Créer `PanierItemOption.java`**

```java
package ma.mysuguclientapp.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Snapshot figé d'une option choisie sur une ligne de panier. */
@Entity
@Table(name = "panier_item_options")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PanierItemOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "panier_item_id", nullable = false)
    private PanierItem panierItem;

    @Column(name = "option_item_id")
    private Long optionItemId;

    @Column(name = "option_group_nom")
    private String optionGroupNom;

    @Column(name = "option_nom")
    private String optionNom;

    @Column(name = "prix_supplement", precision = 10, scale = 2)
    private BigDecimal prixSupplement;
}
```

- [ ] **Step 2: Créer `LigneCommandeOption.java`**

```java
package ma.mysuguclientapp.entities;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/** Snapshot figé d'une option choisie sur une ligne de commande. */
@Entity
@Table(name = "ligne_commande_options")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LigneCommandeOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ligne_commande_id", nullable = false)
    private LigneCommande ligneCommande;

    @Column(name = "option_item_id")
    private Long optionItemId;

    @Column(name = "option_group_nom")
    private String optionGroupNom;

    @Column(name = "option_nom")
    private String optionNom;

    @Column(name = "prix_supplement", precision = 10, scale = 2)
    private BigDecimal prixSupplement;
}
```

- [ ] **Step 3: Ajouter la collection à `PanierItem.java`**

Vérifier/ajouter les imports `import java.util.ArrayList;`, `import java.util.List;`, `jakarta.persistence.*` (présents si nécessaire), puis ajouter après le champ `remarque` :
```java

    @OneToMany(mappedBy = "panierItem", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PanierItemOption> options = new ArrayList<>();
```

- [ ] **Step 4: Ajouter la collection à `LigneCommande.java`**

Vérifier/ajouter les imports `java.util.ArrayList`/`java.util.List`, puis ajouter après le champ `montantCommission` :
```java

    @OneToMany(mappedBy = "ligneCommande", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<LigneCommandeOption> options = new ArrayList<>();
```

- [ ] **Step 5: Build**

Run: `cmd.exe /c "C:\Users\Issiaka traoré\Desktop\systalink\mysugu\_build_local.bat" > /tmp/winbuild.log 2>&1; tail -3 /tmp/winbuild.log`
Expected: `BUILD_EXIT=0` (si `PanierItem`/`LigneCommande` utilisent `@Builder`, l'init `= new ArrayList<>()` peut nécessiter `@Builder.Default` — l'ajouter si le build le signale).

- [ ] **Step 6: Commit**

```bash
cd "/mnt/c/Users/Issiaka traoré/Desktop/systalink/mysugu/MySuguClientApp"
git add src/main/java/ma/mysuguclientapp/entities/PanierItemOption.java \
        src/main/java/ma/mysuguclientapp/entities/LigneCommandeOption.java \
        src/main/java/ma/mysuguclientapp/entities/PanierItem.java \
        src/main/java/ma/mysuguclientapp/entities/LigneCommande.java
git commit -m "feat(plat-options-cart): entites snapshot PanierItemOption/LigneCommandeOption + collections"
```

---

### Task 2 : Service partagé de sélection/validation/prix (backend)

**Files:**
- Create: `MySuguClientApp/src/main/java/ma/mysuguclientapp/services/interfaces/OptionSelectionService.java`
- Create: `MySuguClientApp/src/main/java/ma/mysuguclientapp/services/implementations/OptionSelectionServiceImpl.java`

**Interfaces:**
- Consumes: entité `Plat` (avec `getOptionGroups()` → `OptionGroup` → `OptionItem`), `OptionSelectionMode`, `BadRequestException`.
- Produces: `OptionSelectionService.resolve(Plat plat, List<Long> optionItemIds) → OptionSelectionService.Selection` où `Selection` expose `List<OptionItem> getItems()` et `BigDecimal getSupplementTotal()`.

- [ ] **Step 1: Créer l'interface `OptionSelectionService.java`**

```java
package ma.mysuguclientapp.services.interfaces;

import ma.mysuguclientapp.entities.OptionItem;
import ma.mysuguclientapp.entities.Plat;

import java.math.BigDecimal;
import java.util.List;

public interface OptionSelectionService {

    /** Résultat validé d'une sélection d'options. */
    class Selection {
        private final List<OptionItem> items;
        private final BigDecimal supplementTotal;
        public Selection(List<OptionItem> items, BigDecimal supplementTotal) {
            this.items = items; this.supplementTotal = supplementTotal;
        }
        public List<OptionItem> getItems() { return items; }
        public BigDecimal getSupplementTotal() { return supplementTotal; }
    }

    /**
     * Valide les identifiants d'options choisis pour un plat (appartenance, disponibilité,
     * contraintes par groupe : obligatoire/min/max/SINGLE) et calcule le supplément total.
     * @throws ma.mysuguclientapp.exceptions.BadRequestException si la sélection est invalide.
     */
    Selection resolve(Plat plat, List<Long> optionItemIds);
}
```

- [ ] **Step 2: Créer `OptionSelectionServiceImpl.java`**

```java
package ma.mysuguclientapp.services.implementations;

import ma.mysuguclientapp.entities.OptionGroup;
import ma.mysuguclientapp.entities.OptionItem;
import ma.mysuguclientapp.entities.Plat;
import ma.mysuguclientapp.enumerations.OptionSelectionMode;
import ma.mysuguclientapp.exceptions.BadRequestException;
import ma.mysuguclientapp.services.interfaces.OptionSelectionService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class OptionSelectionServiceImpl implements OptionSelectionService {

    @Override
    public Selection resolve(Plat plat, List<Long> optionItemIds) {
        Set<Long> selectedIds = new HashSet<>();
        if (optionItemIds != null) {
            selectedIds.addAll(optionItemIds);
        }

        List<OptionItem> chosen = new ArrayList<>();
        BigDecimal supplementTotal = BigDecimal.ZERO;
        Set<Long> matched = new HashSet<>();

        List<OptionGroup> groups = plat.getOptionGroups() != null ? plat.getOptionGroups() : List.of();
        for (OptionGroup g : groups) {
            int countInGroup = 0;
            for (OptionItem it : g.getItems()) {
                if (selectedIds.contains(it.getId())) {
                    if (Boolean.FALSE.equals(it.getDisponible())) {
                        throw new BadRequestException("Option indisponible: " + it.getNom());
                    }
                    chosen.add(it);
                    matched.add(it.getId());
                    supplementTotal = supplementTotal.add(
                            it.getPrixSupplement() != null ? it.getPrixSupplement() : BigDecimal.ZERO);
                    countInGroup++;
                }
            }
            valider(g, countInGroup);
        }

        // Tout id sélectionné non rattaché à un groupe du plat => invalide
        for (Long id : selectedIds) {
            if (!matched.contains(id)) {
                throw new BadRequestException("Option invalide pour ce plat: " + id);
            }
        }
        return new Selection(chosen, supplementTotal);
    }

    private void valider(OptionGroup g, int count) {
        int min = g.getMinSelections() != null ? g.getMinSelections() : 0;
        if (Boolean.TRUE.equals(g.getObligatoire()) && min < 1) {
            min = 1;
        }
        if (g.getSelectionMode() == OptionSelectionMode.SINGLE) {
            if (count > 1) {
                throw new BadRequestException("La section \"" + g.getNom() + "\" n'autorise qu'un seul choix");
            }
            if (min >= 1 && count < 1) {
                throw new BadRequestException("La section \"" + g.getNom() + "\" est obligatoire");
            }
            return;
        }
        // MULTIPLE
        if (count < min) {
            throw new BadRequestException("La section \"" + g.getNom() + "\" requiert au moins " + min + " choix");
        }
        if (g.getMaxSelections() != null && count > g.getMaxSelections()) {
            throw new BadRequestException("La section \"" + g.getNom() + "\" autorise au plus " + g.getMaxSelections() + " choix");
        }
    }
}
```

- [ ] **Step 3: Build**

Run: `cmd.exe /c "C:\Users\Issiaka traoré\Desktop\systalink\mysugu\_build_local.bat" > /tmp/winbuild.log 2>&1; tail -3 /tmp/winbuild.log`
Expected: `BUILD_EXIT=0`

- [ ] **Step 4: Commit**

```bash
git add src/main/java/ma/mysuguclientapp/services/interfaces/OptionSelectionService.java \
        src/main/java/ma/mysuguclientapp/services/implementations/OptionSelectionServiceImpl.java
git commit -m "feat(plat-options-cart): service partage de validation/calcul des options choisies"
```

---

### Task 3 : DTOs entrée/sortie options (backend)

**Files:**
- Create: `MySuguClientApp/src/main/java/ma/mysuguclientapp/dtos/OptionChoisieDTO.java`
- Modify: `MySuguClientApp/src/main/java/ma/mysuguclientapp/dtos/cart/AjouterItemDTO.java`
- Modify: `MySuguClientApp/src/main/java/ma/mysuguclientapp/dtos/LigneCommandeCreateDTO.java`
- Modify: `MySuguClientApp/src/main/java/ma/mysuguclientapp/dtos/LigneCommandeDTO.java`

**Interfaces:**
- Produces: `OptionChoisieDTO{optionItemId,optionGroupNom,optionNom,prixSupplement}` ; `AjouterItemDTO.optionItemIds:List<Long>` ; `LigneCommandeCreateDTO.optionItemIds:List<Long>` ; `LigneCommandeDTO.options:List<OptionChoisieDTO>`.

- [ ] **Step 1: Créer `OptionChoisieDTO.java`**

```java
package ma.mysuguclientapp.dtos;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class OptionChoisieDTO {
    private Long optionItemId;
    private String optionGroupNom;
    private String optionNom;
    private BigDecimal prixSupplement;
}
```

- [ ] **Step 2: Ajouter `optionItemIds` à `AjouterItemDTO.java`**

Ajouter le champ (et `import java.util.List;`) :
```java
    private java.util.List<Long> optionItemIds;
```

- [ ] **Step 3: Ajouter `optionItemIds` à `LigneCommandeCreateDTO.java`**

Ajouter :
```java
    private java.util.List<Long> optionItemIds;
```

- [ ] **Step 4: Ajouter `options` à `LigneCommandeDTO.java`**

Ajouter :
```java
    private java.util.List<OptionChoisieDTO> options;
```

- [ ] **Step 5: Build**

Run: `cmd.exe /c "C:\Users\Issiaka traoré\Desktop\systalink\mysugu\_build_local.bat" > /tmp/winbuild.log 2>&1; tail -3 /tmp/winbuild.log`
Expected: `BUILD_EXIT=0`

- [ ] **Step 6: Commit**

```bash
git add src/main/java/ma/mysuguclientapp/dtos/OptionChoisieDTO.java \
        src/main/java/ma/mysuguclientapp/dtos/cart/AjouterItemDTO.java \
        src/main/java/ma/mysuguclientapp/dtos/LigneCommandeCreateDTO.java \
        src/main/java/ma/mysuguclientapp/dtos/LigneCommandeDTO.java
git commit -m "feat(plat-options-cart): champs optionItemIds (entree) + options (sortie commande)"
```

---

### Task 4 : Intégration PANIER (backend)

**Files:**
- Modify: `MySuguClientApp/src/main/java/ma/mysuguclientapp/services/implementations/PanierServiceImpl.java`

**Interfaces:**
- Consumes: `OptionSelectionService.resolve(...)` (Task 2), `AjouterItemDTO.optionItemIds` (Task 3), entités snapshot (Task 1).
- Produces: ligne de panier avec `prixUnitaire = plat.prix + supplément`, snapshot `PanierItemOption`, et options exposées dans la sortie.

- [ ] **Step 1: Injecter `OptionSelectionService`**

Dans `PanierServiceImpl`, ajouter au constructeur (champs `@RequiredArgsConstructor`) :
```java
    private final ma.mysuguclientapp.services.interfaces.OptionSelectionService optionSelectionService;
```

- [ ] **Step 2: Utiliser la sélection dans `ajouterItem`**

Dans `ajouterItem(Long userId, AjouterItemDTO dto)`, APRÈS avoir récupéré `plat` et vérifié sa disponibilité, AVANT la gestion des doublons, calculer la sélection :
```java
        ma.mysuguclientapp.services.interfaces.OptionSelectionService.Selection selection =
                optionSelectionService.resolve(plat, dto.getOptionItemIds());
        java.math.BigDecimal prixUnitaireLigne = plat.getPrix().add(selection.getSupplementTotal());
        java.util.List<Long> idsChoisis = selection.getItems().stream()
                .map(ma.mysuguclientapp.entities.OptionItem::getId)
                .sorted().collect(java.util.stream.Collectors.toList());
```

- [ ] **Step 3: Remplacer la déduplication par une correspondance plat + mêmes options**

Remplacer la recherche de doublon existante (par `plat.id` seul) par une correspondance « même plat ET même ensemble d'options » :
```java
        PanierItem existant = panier.getItems().stream()
                .filter(it -> it.getPlat().getId().equals(plat.getId()))
                .filter(it -> memeSelection(it, idsChoisis))
                .findFirst().orElse(null);

        if (existant != null) {
            existant.setQuantite(existant.getQuantite() + (dto.getQuantite() != null ? dto.getQuantite() : 1));
            if (dto.getRemarque() != null) existant.setRemarque(dto.getRemarque());
        } else {
            PanierItem item = new PanierItem();
            item.setPanier(panier);
            item.setPlat(plat);
            item.setQuantite(dto.getQuantite() != null ? dto.getQuantite() : 1);
            item.setPrixUnitaire(prixUnitaireLigne);
            item.setRemarque(dto.getRemarque());
            for (ma.mysuguclientapp.entities.OptionItem oi : selection.getItems()) {
                ma.mysuguclientapp.entities.PanierItemOption snap = new ma.mysuguclientapp.entities.PanierItemOption();
                snap.setPanierItem(item);
                snap.setOptionItemId(oi.getId());
                snap.setOptionGroupNom(oi.getGroup() != null ? oi.getGroup().getNom() : null);
                snap.setOptionNom(oi.getNom());
                snap.setPrixSupplement(oi.getPrixSupplement());
                item.getOptions().add(snap);
            }
            panier.getItems().add(item);
        }
```
(Adapter aux variables/locales réelles de la méthode — `panier`, `plat`, `dto`. Conserver la logique existante de changement de restaurant et l'appel `recalculerTotal(panier)` + `save`.)

- [ ] **Step 4: Ajouter le helper `memeSelection`**

Ajouter une méthode privée :
```java
    private boolean memeSelection(PanierItem item, java.util.List<Long> idsChoisis) {
        java.util.List<Long> existants = item.getOptions().stream()
                .map(ma.mysuguclientapp.entities.PanierItemOption::getOptionItemId)
                .sorted().collect(java.util.stream.Collectors.toList());
        return existants.equals(idsChoisis);
    }
```

- [ ] **Step 5: Exposer les options dans la sortie `toItemDTO`**

Dans `toItemDTO` (qui construit la `Map<String,Object>` de la ligne), ajouter une clé `options` :
```java
        map.put("options", item.getOptions().stream().map(o -> {
            java.util.Map<String,Object> m = new java.util.HashMap<>();
            m.put("optionItemId", o.getOptionItemId());
            m.put("optionGroupNom", o.getOptionGroupNom());
            m.put("optionNom", o.getOptionNom());
            m.put("prixSupplement", o.getPrixSupplement());
            return m;
        }).collect(java.util.stream.Collectors.toList()));
```
(Adapter le nom de la variable `Map` locale de `toItemDTO`. `prixUnitaire` et `sousTotal` existants reflètent déjà les suppléments puisque `prixUnitaire` les inclut.)

- [ ] **Step 6: Build**

Run: `cmd.exe /c "C:\Users\Issiaka traoré\Desktop\systalink\mysugu\_build_local.bat" > /tmp/winbuild.log 2>&1; tail -3 /tmp/winbuild.log`
Expected: `BUILD_EXIT=0`

- [ ] **Step 7: Vérifier en runtime**

```bash
cmd.exe /c "taskkill /F /IM java.exe" 2>/dev/null
cmd.exe /c "C:\Users\Issiaka traoré\Desktop\systalink\mysugu\_run_local.bat" > /tmp/apprun.log 2>&1 &
until grep -qE "Started MySuguClientApp|APPLICATION FAILED TO START" /tmp/apprun.log; do sleep 3; done
BASE=http://$(ip route | grep default | awk '{print $3}'):8083
# (Pré-requis) s'assurer qu'un plat a des options (sinon PUT via owner.demo comme en Phase 2).
CLI=$(curl -s -X POST $BASE/auth/login -H "Content-Type: application/json" -d '{"email":"client.demo@mysuku.ma","password":"demo1234"}' | python3 -c "import sys,json;print(json.load(sys.stdin)['token'])")
# Récupérer un plat + un optionItemId payant
PLAT_JSON=$(curl -s "$BASE/api/plats?size=1")
PLAT=$(echo "$PLAT_JSON" | python3 -c "import sys,json;d=json.load(sys.stdin);print((d.get('content') or [{}])[0].get('id'))")
OPT=$(curl -s "$BASE/api/plats/$PLAT/options" | python3 -c "import sys,json;d=json.load(sys.stdin);print(next((it['id'] for g in d for it in g['items']), ''))")
echo "plat=$PLAT option=$OPT"
curl -s -X POST $BASE/api/panier/items -H "Authorization: Bearer $CLI" -H "Content-Type: application/json" \
  -d "{\"platId\":$PLAT,\"quantite\":1,\"optionItemIds\":[$OPT]}" | python3 -m json.tool | head -40
curl -s $BASE/api/panier -H "Authorization: Bearer $CLI" | python3 -m json.tool | head -50
```
Expected : la ligne de panier renvoyée a un `prixUnitaire` = prix plat + supplément de l'option choisie, et une liste `options` non vide. Tester aussi un `optionItemIds` violant une contrainte (ex. groupe obligatoire vide) → 400.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/ma/mysuguclientapp/services/implementations/PanierServiceImpl.java
git commit -m "feat(plat-options-cart): panier - selection options, prix + supplements, snapshot"
```

---

### Task 5 : Intégration COMMANDE (backend)

**Files:**
- Modify: `MySuguClientApp/src/main/java/ma/mysuguclientapp/services/implementations/CommandeServiceImpl.java`

**Interfaces:**
- Consumes: `OptionSelectionService.resolve(...)`, `LigneCommandeCreateDTO.optionItemIds`, entité `LigneCommandeOption`, `OptionChoisieDTO`.
- Produces: lignes de commande avec `prixUnitaire`/`montantTotal` incluant les suppléments, snapshot `LigneCommandeOption`, options exposées dans `LigneCommandeDTO`.

- [ ] **Step 1: Injecter `OptionSelectionService`**

Ajouter au constructeur (`@RequiredArgsConstructor`) de `CommandeServiceImpl` :
```java
    private final ma.mysuguclientapp.services.interfaces.OptionSelectionService optionSelectionService;
```

- [ ] **Step 2: Appliquer la sélection dans la boucle de conversion des lignes**

Dans `createCommande`, dans la boucle `for (LigneCommandeCreateDTO ligneDTO : commandeDTO.getLignes())`, APRÈS récupération du `plat` et ses vérifications, remplacer le figeage du prix par la version avec suppléments + snapshot :
```java
            ma.mysuguclientapp.services.interfaces.OptionSelectionService.Selection sel =
                    optionSelectionService.resolve(plat, ligneDTO.getOptionItemIds());
            java.math.BigDecimal prixUnitaireLigne = plat.getPrix().add(sel.getSupplementTotal());

            LigneCommande ligne = new LigneCommande();
            ligne.setPlat(plat);
            ligne.setQuantite(ligneDTO.getQuantite());
            ligne.setPrixUnitaire(prixUnitaireLigne);
            ligne.setMontantTotal(prixUnitaireLigne.multiply(java.math.BigDecimal.valueOf(ligneDTO.getQuantite())));
            ligne.setRemarque(ligneDTO.getRemarque());
            ligne.setCommande(commande);
            for (ma.mysuguclientapp.entities.OptionItem oi : sel.getItems()) {
                ma.mysuguclientapp.entities.LigneCommandeOption snap = new ma.mysuguclientapp.entities.LigneCommandeOption();
                snap.setLigneCommande(ligne);
                snap.setOptionItemId(oi.getId());
                snap.setOptionGroupNom(oi.getGroup() != null ? oi.getGroup().getNom() : null);
                snap.setOptionNom(oi.getNom());
                snap.setPrixSupplement(oi.getPrixSupplement());
                ligne.getOptions().add(snap);
            }
```
(Conserver le reste de la boucle : `lignes.add(ligne)`, `montantTotal = montantTotal.add(ligne.getMontantTotal())`. Les blocs commission/frais/remises en aval restent inchangés — ils consomment `ligne.getMontantTotal()`/`prixUnitaire`.)

- [ ] **Step 3: Exposer les options dans `convertLigneToDTO`**

Dans `convertLigneToDTO(LigneCommande ligne)`, avant le `return`, peupler `options` :
```java
        if (ligne.getOptions() != null) {
            dto.setOptions(ligne.getOptions().stream().map(o -> {
                ma.mysuguclientapp.dtos.OptionChoisieDTO od = new ma.mysuguclientapp.dtos.OptionChoisieDTO();
                od.setOptionItemId(o.getOptionItemId());
                od.setOptionGroupNom(o.getOptionGroupNom());
                od.setOptionNom(o.getOptionNom());
                od.setPrixSupplement(o.getPrixSupplement());
                return od;
            }).collect(java.util.stream.Collectors.toList()));
        }
```
(Adapter le nom de la variable DTO locale de `convertLigneToDTO`.)

- [ ] **Step 4: Build**

Run: `cmd.exe /c "C:\Users\Issiaka traoré\Desktop\systalink\mysugu\_build_local.bat" > /tmp/winbuild.log 2>&1; tail -3 /tmp/winbuild.log`
Expected: `BUILD_EXIT=0`

- [ ] **Step 5: Vérifier en runtime**

Redémarrer le backend (comme Task 4 Step 7). Créer une commande avec une ligne portant `optionItemIds`, via le client `client.demo` (adapter le body `CommandeCreateDTO` au format attendu — au minimum `restaurantId`, `lignes:[{platId,quantite,optionItemIds}]`, `modeReception`, `adresseLivraison` si requis). Vérifier que la commande renvoyée a, sur la ligne, `prixUnitaire` incluant le supplément et une liste `options` non vide.
```bash
BASE=http://$(ip route | grep default | awk '{print $3}'):8083
CLI=$(curl -s -X POST $BASE/auth/login -H "Content-Type: application/json" -d '{"email":"client.demo@mysuku.ma","password":"demo1234"}' | python3 -c "import sys,json;print(json.load(sys.stdin)['token'])")
# Réutiliser PLAT/OPT/RESTO du test panier ; ajuster le payload selon CommandeCreateDTO réel.
echo "Créer une commande avec optionItemIds et vérifier la ligne (prixUnitaire + options)."
```
Expected : ligne de commande avec supplément répercuté + snapshot options présent. (Si le payload commande exige des champs supplémentaires, les renseigner ; l'essentiel à vérifier = prix + options sur la ligne.)

- [ ] **Step 6: Commit**

```bash
git add src/main/java/ma/mysuguclientapp/services/implementations/CommandeServiceImpl.java
git commit -m "feat(plat-options-cart): commande - options par ligne, prix + supplements, snapshot"
```

---

### Task 6 : Sélection des options + ajout panier (app users)

**Files:**
- Modify: `mysugu-frontend/src/api/cart.api.ts` (ou le module d'ajout panier — à localiser)
- Modify: `mysugu-frontend/src/components/DishCard.tsx` (sélection + prix en direct + envoi `optionItemIds`)

**Interfaces:**
- Consumes: `dish.optionGroups` (déjà mappé en Phase 2) ; endpoint `POST /api/panier/items` (ajout body `optionItemIds`).
- Produces: sélection cliquable des options sur la carte plat, prix recalculé en direct, ajout panier avec `optionItemIds`.

- [ ] **Step 1: Localiser et étendre l'appel d'ajout au panier**

Repérer la fonction qui appelle `POST /api/panier/items` (chercher `'/api/panier/items'` dans `src/api/`). Ajouter un paramètre `optionItemIds?: number[]` au payload envoyé. Exemple (adapter au module réel) :
```ts
export const addToCart = (platId: number, quantite: number, optionItemIds: number[] = [], remarque?: string) =>
  apiClient.post('/api/panier/items', { platId, quantite, optionItemIds, remarque })
```

- [ ] **Step 2: État de sélection + prix en direct dans `DishCard.tsx`**

`DishCard` a déjà `dish.optionGroups` (Phase 2, lecture seule). Le rendre interactif :
- État : `const [selected, setSelected] = useState<Record<number, number[]>>({})` (clé = id du groupe, valeur = ids d'items choisis).
- Pour un groupe `SINGLE` : un radio (un seul id) ; pour `MULTIPLE` : cases à cocher (respecter `maxSelections` si défini : ignorer un clic au-delà du max).
- Supplément en direct :
```tsx
const allSelectedIds = Object.values(selected).flat()
const supplement = dish.optionGroups
  .flatMap(g => g.items)
  .filter(it => allSelectedIds.includes(it.id))
  .reduce((s, it) => s + (it.prixSupplement || 0), 0)
const prixAffiche = dish.price + supplement
```
- Validation avant ajout : pour chaque groupe `obligatoire`, exiger au moins `max(minSelections,1)` ; sinon afficher un toast et bloquer. (La même règle est revérifiée côté backend.)
- Bouton « Ajouter » : appelle `addToCart(dish.id, 1, allSelectedIds)` ; afficher le `prixAffiche`.

- [ ] **Step 3: Type-check**

Run: `cd "/mnt/c/Users/Issiaka traoré/Desktop/systalink/mysugu/mysugu-frontend" && npx tsc -b --noEmit; echo "TSC_EXIT:$?"`
Expected: `TSC_EXIT:0`

- [ ] **Step 4: Commit**

```bash
cd "/mnt/c/Users/Issiaka traoré/Desktop/systalink/mysugu/mysugu-frontend"
git add src/api/cart.api.ts src/components/DishCard.tsx
git commit -m "feat(plat-options-cart): selection des options + prix en direct + ajout panier (app users)"
```

---

### Task 7 : Transmettre les options à la commande + affichage panier/commande (app users)

**Files:**
- Modify: le module/page de création de commande (ex. `src/api/commandes.api.ts` + page caisse/checkout — à localiser)
- Modify: l'affichage du panier (CartSidebar) et/ou de la commande pour montrer les options choisies

**Interfaces:**
- Consumes: les lignes de panier (qui exposent désormais `options` + `prixUnitaire`), endpoint de création de commande (lignes avec `optionItemIds`).
- Produces: la commande créée porte les options ; le panier et la commande affichent les options choisies + prix.

- [ ] **Step 1: Inclure `optionItemIds` dans les lignes de commande envoyées**

Repérer la construction du `CommandeCreateDTO` (chercher `lignes` / `platId` dans `src/api/` et la page caisse). Pour chaque ligne construite à partir du panier, inclure `optionItemIds` = les `optionItemId` des `options` de la ligne de panier :
```ts
lignes: cart.items.map(it => ({
  platId: it.platId,
  quantite: it.quantite,
  optionItemIds: (it.options || []).map(o => o.optionItemId),
  remarque: it.remarque,
}))
```
(Adapter aux noms réels des champs du panier côté front ; les lignes de panier exposent `options` depuis le backend Task 4.)

- [ ] **Step 2: Afficher les options choisies dans le panier (CartSidebar) et la commande**

Là où chaque ligne de panier/commande est affichée (ex. `src/components/CartSidebar.tsx` et la page de suivi/commande), afficher sous le nom du plat la liste des options :
```tsx
{(item.options || []).map((o, i) => (
  <div key={i} className="text-xs text-warm-500 flex justify-between">
    <span>{o.optionGroupNom ? `${o.optionGroupNom}: ` : ''}{o.optionNom}</span>
    {o.prixSupplement > 0 && <span>+{o.prixSupplement} DH</span>}
  </div>
))}
```
S'assurer que le type front de la ligne de panier/commande inclut `options` (l'ajouter au type si nécessaire ; le backend renvoie `options` + `prixUnitaire` à jour).

- [ ] **Step 3: Type-check**

Run: `cd "/mnt/c/Users/Issiaka traoré/Desktop/systalink/mysugu/mysugu-frontend" && npx tsc -b --noEmit; echo "TSC_EXIT:$?"`
Expected: `TSC_EXIT:0`

- [ ] **Step 4: Commit**

```bash
git add -p   # NON : ajouter explicitement les fichiers modifiés listés
# Exemple :
git add src/api/commandes.api.ts src/components/CartSidebar.tsx
git commit -m "feat(plat-options-cart): transmettre options a la commande + affichage panier/commande"
```
(Remplacer la liste par les fichiers réellement modifiés ; ne jamais utiliser `git add -A`.)

---

## Notes d'exécution
- Branche dédiée `feat/plat-options-cart` (backend depuis `feat/plat-options`, app users depuis `feat/plat-options`) — créer AVANT le premier commit de chaque repo.
- Les tâches front (6-7) demandent de localiser les modules réels (ajout panier, création commande, affichage panier) : LIRE les fichiers concernés avant d'éditer (les noms exacts peuvent différer).
- Risque : modifie le cœur du flux de commande. Les vérifications runtime (Tasks 4-5) sont obligatoires avant de considérer la tâche faite.

## Self-review (couverture spec, section « Intégration Panier & Commande »)
- `optionItemIds` accepté à l'ajout panier : Task 3 + Task 4. ✓
- Validation (obligatoire/min/max/SINGLE/appartenance/disponibilité) : Task 2 (partagée), appliquée Tasks 4-5. ✓
- Prix unitaire = prix plat + Σ suppléments : Tasks 4-5. ✓
- Snapshot options ligne panier puis ligne commande : Tasks 1, 4, 5. ✓
- DTOs panier/commande exposant options + prix de ligne : Tasks 3, 4 (Map panier), 5 (LigneCommandeDTO). ✓
- App users : sélection + prix direct + envoi (Task 6) ; transmission commande + affichage (Task 7). ✓
- DRY : logique de validation/prix centralisée dans `OptionSelectionService` (Task 2), réutilisée panier & commande. ✓
