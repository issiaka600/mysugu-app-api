# Donnees de test reels

Le backend Spring peut se peupler avec un jeu de donnees de test reel en activant :

```bash
TEST_DATA_ENABLED=true
```

Au demarrage, cela cree si besoin :

- `admin.demo@mysuku.ma` / `demo1234`
- `owner.demo@mysuku.ma` / `demo1234`
- `client.demo@mysuku.ma` / `demo1234`
- `livreur.demo@mysuku.ma` / `demo1234`

Et il ajoute :

- une zone `Marrakech`
- une categorie `Africain`
- un restaurant `Mama Afrika Demo`
- des plats de test
- deux commandes de test (`MSK-TEST-0001`, `MSK-TEST-0002`)

Pour TikTak / Laravel, il existe deja le refresh demo :

```bash
php artisan database:refresh
```

Ne pas activer ces donnees sur un environnement de production.
