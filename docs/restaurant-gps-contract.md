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
