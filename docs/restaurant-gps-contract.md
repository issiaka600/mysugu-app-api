> Le flux vendeur et le calcul des frais décrits ci-dessous sont remplacés par le
> [contrat adresse restaurant et Google Routes](restaurant-address-google-contract.md).
> Les routes et validations des adresses client restent conservées.
> Les constatations datées ci-dessous décrivent l'état antérieur à cette modification.

# Localisation des restaurants et navigation livreur

Le back-office envoie un PUT multipart sur /api/restaurants/{id}.
Les champs localisation.latitude et localisation.longitude sont acceptés,
ainsi que les alias latitude et longitude. Les champs imbriqués ont priorité.

Une paire GPS doit être complète, finie et dans les bornes terrestres.
La modification d'une adresse, ville, code postal ou pays existants exige
une paire GPS fournie explicitement. Sinon le serveur renvoie HTTP 400 et
la transaction laisse la fiche existante intacte. Une mise à jour sans
modification de l'adresse ni du GPS conserve la localisation existante.
Une paire explicitement fournie peut rester identique : un changement de libellé
à la même entrée est légitime. Le backend ne géocode pas le texte de l'adresse.

Le GPS enregistré est la source des réponses :
- GET /api/restaurants/{id} : localisation.latitude et localisation.longitude.
- Chat livreur : seller_info.shop.latitude / longitude / address
  (shops conserve le même objet pour la compatibilité existante).
- GET /api/v2/delivery-man/seller-location?order_id={id} : latitude et longitude
  sous forme de chaînes. La commande doit être assignée au livreur connecté.
  Toujours fournir order_id ; seller_id seul ne résout pas la commande.
- Commandes livreur : seller.shop.latitude / longitude / address.

RestaurantLocationContractTest exerce le vrai HTTP multipart du back-office,
la persistance et les deux réponses livreur après modification d'une fiche,
y compris pour une commande et une conversation préexistantes. Il vérifie
le rollback en cas d'adresse sans GPS, les bornes et l'accès du livreur.

Restaurant réel 16 (Saveurs d’Afrique), constat du 2026-10-08 :
adresse « Akwaba Café O.B.V, 40090, Marrakech 40090 » ;
GPS enregistré 31.6540716, -8.0095317, signalé erroné par le frontend.
La position de l'entrée réelle n'est pas confirmée. Aucune coordonnée
de remplacement ne doit être déduite de l'adresse ou des valeurs de test.
La correction de cette fiche et la vérification de l'itinéraire nécessitent
une épingle GPS confirmée à l'entrée, puis un contrôle dans l'application livreur.

## Confirmation depuis l’application vendeur

POST /api/v3/seller/shop-update (multipart authentifié vendeur) accepte
address, latitude, longitude et location_confirmed=true. Envoyer ces quatre
champs ensemble après confirmation explicite de l’entrée sur la carte.
Une adresse ou un point modifiés sans confirmation sont refusés (HTTP 400).
Une confirmation sans adresse et paire GPS complète est également refusée.
Les coordonnées sont validées avant toute écriture. L’adresse, le point et
la date de confirmation sont enregistrés dans la même transaction.

L’application vendeur doit invalider sa confirmation dès que le texte de
l’adresse ou le marqueur change, puis demander une nouvelle confirmation.
Cette carte reste à implémenter et vérifier côté frontend ; le backend ne
peut pas déterminer si une paire valide correspond à l’entrée réelle.
Utiliser shop-update pour le restaurant : seller-update modifie le profil
personnel du vendeur.

GET /api/v3/seller/shop-info renvoie latitude, longitude, location_confirmed
et location_confirmed_at. Les réponses seller_info.shop du chat livreur et
seller-location exposent le même point enregistré, son adresse et ces deux
champs de confirmation. Les restaurants historiques sont non confirmés
jusqu’à confirmation explicite. Une modification administrative de la
localisation sans confirmation invalide la confirmation précédente.
GET /api/restaurants/{id} expose locationConfirmed et locationConfirmedAt.

seller-location sans order_id renvoie 400 ; une localisation absente ou
incomplète renvoie 409, au lieu d’un point artificiel 0,0.

Validation antérieure : RestaurantLocationContractTest et ShopUpdateTest couvrent les écritures vendeur et administrateur, la conservation lors d’un changement de nom, les droits du livreur et la cohérence des réponses de localisation.

## Adresses client, devis et commande

POST /api/users/adresses exige adresse, latitude, longitude et
locationConfirmed=true, puis enregistre le point et sa date de confirmation
ensemble. PUT /api/users/adresses/{id} exige le même jeu de champs si l’adresse
textuelle ou ses coordonnées changent. Les coordonnées doivent être finies et
respecter les bornes terrestres. La réponse inclut locationConfirmed et
locationConfirmedAt. Les adresses historiques sans date doivent être
reconfirmées par le client.

Pour une livraison, GET /api/commandes/devis exige une session client et
restaurantId + adresseLivraisonId. POST /api/commandes/devis et POST
/api/commandes exigent adresseLivraisonId dans le panier/commande et le client
connecté doit correspondre au clientId. Le serveur relit l’adresse enregistrée
appartenant à ce client et utilise son point confirmé comme destination ; il
ignore les coordonnées brutes de la requête. Il refuse le devis et la commande
si le restaurant ou l’adresse client n’a pas de confirmation sauvegardée ou si
les coordonnées sont invalides. Le mode retrait n’exige pas de point client.

Le devis renvoie distanceKm, distanceMethod et detailFrais en plus des frais.
À ce jour distanceMethod vaut HAVERSINE_STRAIGHT_LINE : le calcul reste à vol
d’oiseau. Aucun fournisseur routier ni aucune clé d’itinéraire n’est configuré
sur le serveur. Ne pas interpréter cette réponse comme une distance routière ;
le fournisseur routier doit être choisi/configuré avant d’annoncer une distance
comparable à Maps.

Les quatre restaurants actifs et les neuf adresses client en production étaient
encore non confirmés lors de la vérification du 2026-10-11. Aucun point ancien
n’a été marqué confirmé automatiquement. Après déploiement des nouvelles
règles, leurs propriétaires devront confirmer les points sur la carte avant
la remise en service des devis et commandes de livraison.

Vérification d’intégration : 13 RestaurantLocationContractTest et 6
CreationEtablissementBackOfficeTest passent. Les cas testés incluent refus
sans confirmation, création et modification d’une adresse client, coordonnées
invalides, devis avec deux restaurants et deux adresses, distance/méthode,
valeurs tarifaires exposées, et mise à jour restaurant sans point confirmé.
