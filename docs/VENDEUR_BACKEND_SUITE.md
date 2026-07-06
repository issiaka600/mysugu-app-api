# Vendeur APIs

Ce document décrit les nouvelles APIs ajoutées, correspondant aux endpoints identifiés
comme manquants lors de l'analyse comparative TikTak (legacy) vs Mysugu.

---

# Partie 1 — Commandes

Les 4 APIs suivantes sont sous le contrôleur `CommandeController` (`/api/commandes`) et
nécessitent une authentification (`Authorization: Bearer <token>`).

---

## 1. Assigner un livreur tiers (hors plateforme)
Permet d'assigner un coursier externe (ex: Glovo, un indépendant...) à une commande,
plutôt qu'un livreur enregistré sur la plateforme.

**Requête**
```
PATCH /api/commandes/{id}/assign-third-party-delivery
Content-Type: application/json
```
**Body**
```json
{
  "nom": "Ahmed Coursier",
  "telephone": "0611223344",
  "entreprise": "Glovo"
}
```
**Comportement**
- Si un livreur interne était déjà assigné à la commande, il est automatiquement libéré
  (repasse disponible) et remplacé par le livreur tiers.
- Le statut de la commande passe à `ASSIGNEE_LIVREUR` si elle était `PRETE` ou `EN_PREPARATION`.
- Le client reçoit une notification.

**Réponse** : `CommandeDTO` avec les champs `livreurTiersNom`, `livreurTiersTelephone`,
`livreurTiersEntreprise` renseignés.

---

## 2. Mettre à jour le statut de paiement
**Requête**
```
PATCH /api/commandes/{id}/statut-paiement
Content-Type: application/json
```
**Body**
```json
{
  "statutPaiement": "PAYE"
}
```
**Valeurs possibles** : `EN_ATTENTE`, `PAYE`, `REMBOURSE`, `ECHOUE`

**Réponse** : `CommandeDTO` avec `statutPaiement` mis à jour.

---

## 3. Mettre à jour les frais et/ou la date de livraison
Permet de modifier les frais de livraison et/ou de reporter la date de livraison prévue
avec une cause (ex: client indisponible, retard restaurant...).

**Requête**
```
PATCH /api/commandes/{id}/livraison-frais-date
Content-Type: application/json
```
**Body** *(les deux blocs sont indépendants — seuls les champs fournis sont modifiés)*
```json
{
  "fraisLivraison": 15.0,
  "dateLivraisonPrevue": "2026-07-10T18:00:00",
  "causeReport": "Client indisponible, reporté à sa demande"
}
```
**Réponse** : `CommandeDTO` avec `fraisLivraison`, `dateLivraisonPrevue` et `causeReport`
mis à jour.

---

## 4. Déclarer les quantités réellement livrées
Version simplifiée de la fonctionnalité POS legacy "order-wise-product-upload".
Permet de déclarer, ligne par ligne, la quantité réellement livrée si elle diffère de
la quantité commandée (ex: rupture de stock partielle sur un plat).

**Requête**
```
POST /api/commandes/{id}/order-wise-product-upload
Content-Type: application/json
```
**Body**
```json
{
  "lignes": [
    { "ligneCommandeId": 12, "quantiteLivree": 2 },
    { "ligneCommandeId": 13, "quantiteLivree": 1 }
  ]
}
```
**Validations**
- Chaque `ligneCommandeId` doit exister et appartenir à la commande `{id}` (sinon erreur 400)
- `quantiteLivree` ne peut pas être négative

**Réponse** : `CommandeDTO` avec les lignes mises à jour (`quantiteLivree` visible dans
`lignesCommande[].quantiteLivree`).

---

## Résumé des champs ajoutés en base (Commandes)

| Entité | Champs ajoutés |
|---|---|
| `commandes` | `livreur_tiers_nom`, `livreur_tiers_telephone`, `livreur_tiers_entreprise` |
| `lignes_commande` | `quantite_livree` |

*(Créés automatiquement au démarrage via `spring.jpa.hibernate.ddl-auto=update`)*

---

# Partie 2 — Livreurs, retraits & contacts d'urgence

Les APIs suivantes sont toutes regroupées dans un seul contrôleur, `AdminLivreursController`
(`/api/admin/**`), et nécessitent une authentification avec un compte **ADMIN**
(`Authorization: Bearer <token>` d'un utilisateur ayant le rôle `ADMIN`).

---

## 5. Détails enrichis d'un livreur

**Requête**
```
GET /api/admin/livreurs/{id}
```

**Réponse**
```json
{
  "id": 5,
  "nom": "Doe",
  "prenom": "John",
  "email": "livreur@example.com",
  "telephone": "0611223344",
  "avatar": "https://...",
  "isActive": true,
  "livreurDisponible": true,
  "createdAt": "2026-06-01T10:00:00",
  "nombreLivraisons": 42,
  "commandesEnCours": 1,
  "noteMoyenne": 4.6,
  "soldeActuel": 850.00,
  "especeEnCaisse": 120.00,
  "montantEnAttenteRetrait": 200.00,
  "totalRetireApprouve": 1500.00
}
```
Combine l'identité du livreur avec ses statistiques (nombre de livraisons, note
moyenne) et son état financier (solde actuel, espèces en caisse, montants en
attente/déjà retirés) — plus riche que le `UserDTO` générique.

---

## 6. Supprimer un livreur

**Requête**
```
DELETE /api/admin/livreurs/{id}
```
**Comportement**
- Soft-delete (`isDeleted=true`, `deletedAt`), même convention que la suppression de
  compte self-service.
- **Refuse avec 400** si le livreur a des commandes en cours (`ASSIGNEE_LIVREUR` ou
  `EN_COURS`).

**Réponse** : `204 No Content` si succès.

---

## 7. Liste des demandes de retrait

**Requête**
```
GET /api/admin/livreurs/retraits?statut=EN_ATTENTE
```
Le paramètre `statut` est optionnel (`EN_ATTENTE`, `APPROUVE`, `REFUSE`). Sans lui,
retourne toutes les demandes.

---

## 8. Détails d'une demande de retrait

**Requête**
```
GET /api/admin/livreurs/retraits/{id}
```

---

## 9. Mettre à jour le statut d'une demande de retrait

Version générique des actions `/approuver` et `/refuser` déjà existantes — utile si
on préfère piloter le statut directement plutôt qu'appeler une action dédiée.

**Requête**
```
PATCH /api/admin/livreurs/retraits/{id}/statut
Content-Type: application/json
```
**Body**
```json
{
  "statut": "APPROUVE",
  "transactionRef": "VIR-2026-0012"
}
```
**Comportement**
- Refuse avec `409 Conflict` si la demande a déjà été traitée (statut différent de
  `EN_ATTENTE`).
- À l'approbation (`APPROUVE`), réduit automatiquement le `current_balance` du livreur
  du montant retiré.

---

## 10. Liste des contacts d'urgence

Un contact d'urgence est rattaché à un **restaurant** (ou global si non rattaché) —
c'est le numéro que le livreur appelle si le vendeur ne répond pas à une livraison.

**Requête**
```
GET /api/admin/contacts-urgence?restaurantId=5
```
Le paramètre `restaurantId` est optionnel. Sans lui, retourne tous les contacts.

---

## 11. Créer un contact d'urgence

**Requête**
```
POST /api/admin/contacts-urgence
Content-Type: application/json
```
**Body**
```json
{
  "restaurantId": 5,
  "nom": "Support Restaurant X",
  "telephone": "0522334455"
}
```
`restaurantId: null` crée un contact global, visible par tous les livreurs peu importe
le restaurant.

**Réponse** : `201 Created` avec le `ContactUrgenceDTO` créé.

---

## 12. Modifier un contact d'urgence

**Requête**
```
PUT /api/admin/contacts-urgence/{id}
Content-Type: application/json
```
**Body** *(mêmes champs que la création)*
```json
{
  "restaurantId": 5,
  "nom": "Nouveau nom",
  "telephone": "0611112222"
}
```

---

## 13. Activer/désactiver un contact d'urgence

**Requête**
```
PATCH /api/admin/contacts-urgence/{id}/statut
Content-Type: application/json
```
**Body**
```json
{
  "actif": false
}
```
Un contact désactivé (`actif: false`) disparaît de la liste consultée par les livreurs
(`GET /api/v2/delivery-man/emergency-contact-list`), sans être supprimé.

---

## 14. Supprimer un contact d'urgence

**Requête**
```
DELETE /api/admin/contacts-urgence/{id}
```
Suppression définitive (contrairement à la désactivation ci-dessus).

**Réponse** : `204 No Content` si succès.

---

## Résumé des endpoints (Partie 2)

| # | Méthode | Endpoint | Action |
|---|---|---|---|
| 5 | GET | `/api/admin/livreurs/{id}` | Détails enrichis d'un livreur |
| 6 | DELETE | `/api/admin/livreurs/{id}` | Supprimer un livreur (soft-delete, gardé) |
| 7 | GET | `/api/admin/livreurs/retraits` | Liste complète des retraits (filtrable) |
| 8 | GET | `/api/admin/livreurs/retraits/{id}` | Détails d'un retrait |
| 9 | PATCH | `/api/admin/livreurs/retraits/{id}/statut` | Changer le statut d'un retrait |
| 10 | GET | `/api/admin/contacts-urgence` | Liste des contacts d'urgence |
| 11 | POST | `/api/admin/contacts-urgence` | Créer un contact d'urgence |
| 12 | PUT | `/api/admin/contacts-urgence/{id}` | Modifier un contact d'urgence |
| 13 | PATCH | `/api/admin/contacts-urgence/{id}/statut` | Activer/désactiver un contact |
| 14 | DELETE | `/api/admin/contacts-urgence/{id}` | Supprimer un contact d'urgence |

*(Les routes `/api/admin/livreurs/retraits/{id}/approuver`, `/refuser` et
`/api/admin/livreurs/retraits/en-attente`, déjà présentes avant ce travail, restent
inchangées dans le même contrôleur `AdminLivreursController`.)*

---

# Partie 3 — Avis

## 15. Avis d'un restaurant filtrés par statut (vue vendeur/admin)

Complète `GET /api/avis/restaurant/{restaurantId}` (public), qui ne montre que les avis
`APPROUVE`. Ce nouvel endpoint permet au vendeur/admin de filtrer par n'importe quel
statut — utile pour voir les avis en attente de modération ou rejetés.

**Requête**
```
GET /api/avis/restaurant/{restaurantId}/status?statut=EN_ATTENTE
Authorization: Bearer <token>
```
Le paramètre `statut` est optionnel (`EN_ATTENTE`, `APPROUVE`, `REJETE`). Sans lui,
retombe sur `APPROUVE` (même comportement que l'endpoint public).

**Sécurité** : accès réservé à l'**ADMIN** ou au **propriétaire du restaurant** — sinon
`403 Forbidden`. Contrairement aux autres routes `GET /api/avis/**` qui sont publiques,
une règle Spring Security dédiée protège spécifiquement cette route.

**Réponse** : liste de `AvisDTO`, filtrée par le statut demandé.

```bash
curl "http://localhost:8083/api/avis/restaurant/5/status?statut=EN_ATTENTE" \
  -H "Authorization: Bearer TOKEN_PROPRIETAIRE_OU_ADMIN"
```

---

# Partie 4 — Messagerie / Chat (acheteur ↔ vendeur)

Nouveau module complet sous le contrôleur `MessagerieController` (`/api/messages`),
correspondant au module "MESSAGERIE / CHAT" identifié comme entièrement absent dans
l'analyse TikTak vs Mysugu (et au ticket **T9 "Chat + counterpart"** du techspec de
migration livreur, qui notait déjà l'absence de ce counterpart client/vendeur).

Toutes ces routes nécessitent une authentification (`Authorization: Bearer <token>`).
L'appartenance à la conversation est vérifiée côté serveur (sinon `401/403`).

---

## 16. Liste des conversations

**Requête**
```
GET /api/messages/conversations
```
**Comportement** : vue adaptative selon le rôle de l'appelant —
- **Client** : ses fils de discussion avec des restaurants
- **Restaurateur** (`RESTAURANT_OWNER`) : les fils de discussion de son restaurant

**Réponse** : liste de `ConversationDTO` (infos du client, du restaurant, dernier
message, date, nombre de messages non lus), triée par message le plus récent.

---

## 17. Recherche dans les conversations

**Requête**
```
GET /api/messages/conversations/search?q=livraison
```
Recherche par mot-clé dans le **contenu des messages** de mes conversations (pas
seulement le dernier message affiché en liste). Si `q` est vide, retourne la liste
complète (même résultat que #16).

---

## 18. Messages d'une conversation

**Requête**
```
GET /api/messages/conversations/{id}
```
**Comportement** : retourne tous les messages du fil, du plus ancien au plus récent.
Marque automatiquement comme lus tous les messages reçus (pas envoyés par
l'appelant) qui ne l'étaient pas encore.

**Réponse** : liste de `MessageChatDTO`, avec `envoyeParMoi` calculé par rapport à
l'appelant (pour l'affichage à gauche/droite côté app).

---

## 19. Envoyer un message

**Requête**
```
POST /api/messages
Content-Type: application/json
```
**Body — répondre dans un fil existant**
```json
{
  "conversationId": 42,
  "contenu": "Votre commande est en préparation !"
}
```
**Body — démarrer une nouvelle conversation (client uniquement)**
```json
{
  "restaurantId": 5,
  "commandeId": 123,
  "contenu": "Bonjour, ma commande arrive bientôt ?"
}
```
`commandeId` est optionnel (rattache la conversation à une commande précise pour le
contexte). `imageUrl` est aussi accepté pour un message avec pièce jointe (image déjà
uploadée sur MinIO).

**Règles**
- Seul un **client** peut démarrer une nouvelle conversation (`restaurantId` requis).
  Le vendeur doit fournir un `conversationId` pour répondre à un fil existant.
- Un seul fil par couple (client, restaurant) — envoyer un nouveau message au même
  restaurant réutilise automatiquement la conversation existante.
- Déclenche une notification push (`TypeNotification.MESSAGE`) à l'autre partie.

**Réponse** : `201 Created` avec le `MessageChatDTO` créé.

---

## Résumé des endpoints (Partie 4)

| # | Méthode | Endpoint | Action |
|---|---|---|---|
| 16 | GET | `/api/messages/conversations` | Liste des conversations |
| 17 | GET | `/api/messages/conversations/search?q=` | Recherche par mot-clé |
| 18 | GET | `/api/messages/conversations/{id}` | Messages d'une conversation |
| 19 | POST | `/api/messages` | Envoyer un message (répondre ou démarrer un fil) |

## Nouvelles tables en base (Messagerie)

| Table | Rôle |
|---|---|
| `conversations` | Un fil par couple (client, restaurant), avec aperçu du dernier message |
| `messages_chat` | Messages individuels (contenu, image optionnelle, statut lu) |

*(Créées automatiquement au démarrage via `spring.jpa.hibernate.ddl-auto=update`)*

---

## Fonctionnalités non reprises (voir analyse complète TikTak vs Mysugu)

- **Remboursements (refund)** — absent
- **OTP par SMS pour l'auth** — implémenté par email uniquement pour l'instant