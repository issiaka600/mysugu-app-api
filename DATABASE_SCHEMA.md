# 🗄️ Schéma de Base de Données PostgreSQL

## Tables et Relations

```
┌─────────────────┐
│     USERS       │
├─────────────────┤
│ id (PK)         │
│ email           │
│ password        │
│ nom             │
│ prenom          │
│ telephone       │
│ role            │◄────────┐
│ avatar          │         │
│ latitude        │         │
│ longitude       │         │
│ adresse         │         │
│ ville           │         │
│ code_postal     │         │
│ pays            │         │
│ is_active       │         │
│ created_at      │         │
│ updated_at      │         │
└─────────────────┘         │
         ▲                  │
         │                  │
         │                  │
         │                  │
┌────────┴────────┐         │
│   RESTAURANTS   │         │
├─────────────────┤         │
│ id (PK)         │         │
│ nom             │         │
│ description     │         │
│ logo_url        │         │
│ appreciation    │         │
│ nombre_avis     │         │
│ temps_livraison │         │
│ latitude        │         │
│ longitude       │         │
│ adresse         │         │
│ ville           │         │
│ categorie_id (FK)─────┐   │
│ owner_id (FK)   ├─────────┘
│ promotion_id (FK)─┐   │
│ is_active       │ │   │
│ horaires        │ │   │
│ created_at      │ │   │
└─────────────────┘ │   │
         ▲          │   │
         │          │   │
         │          │   │
         │          │   ┌────────────────────┐
         │          │   │ CATEGORIES_RESTO   │
         │          │   ├────────────────────┤
         │          │   │ id (PK)            │
         │          │   │ nom                │
         │          │   │ description        │
         │          │   │ image_url          │
         │          │   └────────────────────┘
         │          │
         │          │
         │          │   ┌─────────────────┐
         │          └──►│   PROMOTIONS    │
         │              ├─────────────────┤
         │              │ id (PK)         │
┌────────┴────────┐     │ pourcentage     │
│     PLATS       │     │ date_debut      │
├─────────────────┤     │ date_fin        │
│ id (PK)         │     │ description     │
│ nom             │     │ is_active       │
│ description     │     └─────────────────┘
│ prix            │
│ image_url       │
│ restaurant_id(FK)
│ categorie_plat  │
│ is_available    │
│ temps_prep      │
└─────────────────┘
         ▲
         │
         │
         │
┌────────┴──────────┐
│ LIGNES_COMMANDE   │
├───────────────────┤
│ id (PK)           │
│ commande_id (FK)  ├───────┐
│ plat_id (FK)      │       │
│ quantite          │       │
│ prix_unitaire     │       │
│ montant_total     │       │
│ remarque          │       │
└───────────────────┘       │
                            │
                            ▼
                   ┌─────────────────────┐
                   │     COMMANDES       │
                   ├─────────────────────┤
                   │ id (PK)             │
                   │ numero_commande     │
                   │ client_id (FK)      ├────► USERS (CLIENT)
                   │ restaurant_id (FK)  ├────► RESTAURANTS
                   │ livreur_id (FK)     ├────► USERS (LIVREUR)
                   │ statut              │
                   │ livraison_latitude  │
                   │ livraison_longitude │
                   │ livraison_adresse   │
                   │ livraison_ville     │
                   │ montant_total       │
                   │ frais_livraison     │
                   │ temps_livraison_est │
                   │ commentaire         │
                   │ methode_paiement    │
                   │ statut_paiement     │
                   │ created_at          │
                   │ updated_at          │
                   │ livree_at           │
                   └─────────────────────┘

┌───────────────────────┐
│  PLAT_INGREDIENTS     │
├───────────────────────┤
│ plat_id (FK)          │
│ ingredient            │
└───────────────────────┘
```

## Script SQL de Création

```sql
-- Créer la base de données
CREATE DATABASE food_delivery;

-- Extensions PostgreSQL utiles
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";
CREATE EXTENSION IF NOT EXISTS "postgis"; -- Pour les fonctions géospatiales

-- Table USERS
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    nom VARCHAR(100) NOT NULL,
    prenom VARCHAR(100) NOT NULL,
    telephone VARCHAR(20),
    role VARCHAR(20) NOT NULL,
    avatar VARCHAR(500),
    latitude DECIMAL(10, 8),
    longitude DECIMAL(11, 8),
    adresse VARCHAR(500),
    ville VARCHAR(100),
    code_postal VARCHAR(20),
    pays VARCHAR(100),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Index sur email pour les recherches
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_role ON users(role);

-- Table CATEGORIES_RESTAURANT
CREATE TABLE categories_restaurant (
    id BIGSERIAL PRIMARY KEY,
    nom VARCHAR(100) UNIQUE NOT NULL,
    description TEXT,
    image_url VARCHAR(500)
);

-- Table PROMOTIONS
CREATE TABLE promotions (
    id BIGSERIAL PRIMARY KEY,
    pourcentage INTEGER NOT NULL,
    date_debut TIMESTAMP NOT NULL,
    date_fin TIMESTAMP NOT NULL,
    description TEXT,
    is_active BOOLEAN DEFAULT TRUE
);

-- Table RESTAURANTS
CREATE TABLE restaurants (
    id BIGSERIAL PRIMARY KEY,
    nom VARCHAR(200) NOT NULL,
    description TEXT,
    logo_url VARCHAR(500),
    appreciation DECIMAL(2, 1) DEFAULT 0.0,
    nombre_avis INTEGER DEFAULT 0,
    temps_livraison_moyen INTEGER,
    latitude DECIMAL(10, 8),
    longitude DECIMAL(11, 8),
    adresse VARCHAR(500),
    ville VARCHAR(100),
    code_postal VARCHAR(20),
    pays VARCHAR(100),
    categorie_id BIGINT REFERENCES categories_restaurant(id) ON DELETE SET NULL,
    owner_id BIGINT REFERENCES users(id) ON DELETE CASCADE,
    promotion_id BIGINT REFERENCES promotions(id) ON DELETE SET NULL,
    is_active BOOLEAN DEFAULT TRUE,
    horaires_ouverture VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Index pour les recherches géospatiales
CREATE INDEX idx_restaurants_location ON restaurants(latitude, longitude);
CREATE INDEX idx_restaurants_categorie ON restaurants(categorie_id);
CREATE INDEX idx_restaurants_appreciation ON restaurants(appreciation DESC);

-- Table PLATS
CREATE TABLE plats (
    id BIGSERIAL PRIMARY KEY,
    nom VARCHAR(200) NOT NULL,
    description TEXT,
    prix DECIMAL(10, 2) NOT NULL,
    image_url VARCHAR(500),
    restaurant_id BIGINT NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    categorie_plat VARCHAR(50),
    is_available BOOLEAN DEFAULT TRUE,
    temps_preparation INTEGER
);

CREATE INDEX idx_plats_restaurant ON plats(restaurant_id);
CREATE INDEX idx_plats_categorie ON plats(categorie_plat);

-- Table PLAT_INGREDIENTS (relation many-to-many)
CREATE TABLE plat_ingredients (
    plat_id BIGINT REFERENCES plats(id) ON DELETE CASCADE,
    ingredient VARCHAR(100),
    PRIMARY KEY (plat_id, ingredient)
);

-- Table COMMANDES
CREATE TABLE commandes (
    id BIGSERIAL PRIMARY KEY,
    numero_commande VARCHAR(50) UNIQUE NOT NULL,
    client_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    restaurant_id BIGINT NOT NULL REFERENCES restaurants(id) ON DELETE CASCADE,
    livreur_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    statut VARCHAR(20) NOT NULL,
    livraison_latitude DECIMAL(10, 8),
    livraison_longitude DECIMAL(11, 8),
    livraison_adresse VARCHAR(500),
    livraison_ville VARCHAR(100),
    livraison_code_postal VARCHAR(20),
    livraison_pays VARCHAR(100),
    montant_total DECIMAL(10, 2) NOT NULL,
    frais_livraison DECIMAL(10, 2),
    temps_livraison_estime INTEGER,
    commentaire TEXT,
    methode_paiement VARCHAR(20),
    statut_paiement VARCHAR(20),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    livree_at TIMESTAMP
);

CREATE INDEX idx_commandes_client ON commandes(client_id);
CREATE INDEX idx_commandes_restaurant ON commandes(restaurant_id);
CREATE INDEX idx_commandes_livreur ON commandes(livreur_id);
CREATE INDEX idx_commandes_statut ON commandes(statut);
CREATE INDEX idx_commandes_numero ON commandes(numero_commande);

-- Table LIGNES_COMMANDE
CREATE TABLE lignes_commande (
    id BIGSERIAL PRIMARY KEY,
    commande_id BIGINT NOT NULL REFERENCES commandes(id) ON DELETE CASCADE,
    plat_id BIGINT NOT NULL REFERENCES plats(id) ON DELETE CASCADE,
    quantite INTEGER NOT NULL,
    prix_unitaire DECIMAL(10, 2) NOT NULL,
    montant_total DECIMAL(10, 2) NOT NULL,
    remarque TEXT
);

CREATE INDEX idx_lignes_commande ON lignes_commande(commande_id);

-- Trigger pour mettre à jour updated_at automatiquement
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER update_users_updated_at BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER update_commandes_updated_at BEFORE UPDATE ON commandes
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

-- Données initiales de test
INSERT INTO categories_restaurant (nom, description) VALUES
('Subsahariens', 'Cuisine d''Afrique subsaharienne'),
('Marocains', 'Cuisine marocaine traditionnelle'),
('Maghrébins', 'Cuisine du Maghreb'),
('Orientaux', 'Cuisine du Moyen-Orient'),
('Européens', 'Cuisine européenne'),
('Asiatiques', 'Cuisine asiatique');

-- Fonction pour calculer la distance entre deux points (formule Haversine)
CREATE OR REPLACE FUNCTION calculate_distance(
    lat1 DECIMAL, lon1 DECIMAL,
    lat2 DECIMAL, lon2 DECIMAL
) RETURNS DECIMAL AS $$
DECLARE
    R DECIMAL := 6371; -- Rayon de la Terre en km
    dLat DECIMAL;
    dLon DECIMAL;
    a DECIMAL;
    c DECIMAL;
BEGIN
    dLat := RADIANS(lat2 - lat1);
    dLon := RADIANS(lon2 - lon1);
    
    a := SIN(dLat/2) * SIN(dLat/2) +
         COS(RADIANS(lat1)) * COS(RADIANS(lat2)) *
         SIN(dLon/2) * SIN(dLon/2);
    
    c := 2 * ATAN2(SQRT(a), SQRT(1-a));
    
    RETURN R * c;
END;
$$ LANGUAGE plpgsql IMMUTABLE;
```

## Contraintes et Relations

### Contraintes d'intégrité:
- **users.email**: UNIQUE
- **commandes.numero_commande**: UNIQUE
- **categories_restaurant.nom**: UNIQUE

### Clés étrangères:
- restaurants.categorie_id → categories_restaurant.id
- restaurants.owner_id → users.id
- restaurants.promotion_id → promotions.id
- plats.restaurant_id → restaurants.id
- commandes.client_id → users.id
- commandes.restaurant_id → restaurants.id
- commandes.livreur_id → users.id
- lignes_commande.commande_id → commandes.id
- lignes_commande.plat_id → plats.id

### Cascades:
- Suppression d'un restaurant → supprime tous ses plats
- Suppression d'un utilisateur propriétaire → supprime ses restaurants
- Suppression d'une commande → supprime toutes ses lignes
