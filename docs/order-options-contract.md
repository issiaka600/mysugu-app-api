# Choix de plats : panier et commande vendeur

Le client transmet les identifiants via `optionItemIds` sur chaque ligne.
Le serveur relit les options du plat, contrôle disponibilité et limites, et calcule
le prix unitaire à partir du prix du plat et de tous les suppléments sélectionnés.

Les groupes nommés « Accompagnement » ou « Accompagnements » (casse et accents
ignorés) requièrent au moins un choix, même pour les configurations historiques
avec `obligatoire=false`. Les autres groupes respectent leur configuration
`obligatoire`, `minSelections`, `maxSelections` et `selectionMode`.
Le catalogue expose la même obligation. Toute sélection invalide déclenche une
BadRequestException (HTTP 400) au panier, au devis par identifiants et à la commande.

GET /api/v3/seller/orders/{id} renvoie un tableau de lignes. Chaque ligne contient
`options`, toujours un tableau (vide si aucune option), par exemple :

```json
{
  "qty": 2,
  "price": 125.00,
  "options": [
    {"optionItemId": 1, "optionGroupNom": "Accompagnement", "optionNom": "Frites", "prixSupplement": 0.00},
    {"optionItemId": 2, "optionGroupNom": "Suppléments", "optionNom": "Fromage", "prixSupplement": 10.00},
    {"optionItemId": 3, "optionGroupNom": "Boissons", "optionNom": "Jus", "prixSupplement": 15.00}
  ]
}
```

Dans cet exemple le plat vaut 100.00 : prix unitaire = 125.00 et sous-total
pour deux articles = 250.00 avant livraison, remises et règles de commission.
`price` inclut déjà les suppléments : le frontend ne doit pas les additionner à nouveau.
Les noms de groupes, noms d'options et suppléments de la commande sont des
instantanés persistés, indépendants des modifications ultérieures du catalogue.

Validation : OrderOptionsContractTest (base PostgreSQL de test isolée) vérifie
absence d'accompagnement, identifiant étranger, option indisponible, limites
SINGLE/MULTIPLE, total du panier, persistance des trois choix, puis réponse HTTP
vendeur après modification des noms et prix dans le catalogue.
OrderDetailsTest vérifie aussi les options vides et l'isolation entre vendeurs.
