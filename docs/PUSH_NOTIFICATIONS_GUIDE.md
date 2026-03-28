# Guide d'implémentation — Notifications Push MySugu

**Version** : 1.0
**Date** : Mars 2026
**Projet** : MySugu — Plateforme de livraison de repas
**Technologie push** : Firebase Cloud Messaging (FCM)

---

## Table des matières

1. [Architecture générale](#1-architecture-générale)
2. [Configuration Firebase (commun à toutes les plateformes)](#2-configuration-firebase)
3. [Référence API Backend](#3-référence-api-backend)
4. [Implémentation Web (React)](#4-implémentation-web-react)
5. [Implémentation Android](#5-implémentation-android)
6. [Implémentation iOS](#6-implémentation-ios)
7. [Cycle de vie du token FCM](#7-cycle-de-vie-du-token-fcm)
8. [Types de notifications](#8-types-de-notifications)
9. [Checklist de validation](#9-checklist-de-validation)

---

## 1. Architecture générale

```
┌──────────────────────────────────────────────────────────────────────┐
│  APPLICATION CLIENTE  (Web / Android / iOS)                          │
│                                                                      │
│  1. Initialise Firebase SDK                                          │
│  2. Obtient un token FCM unique au device                            │
│  3. Enregistre ce token auprès du backend MySugu                     │
│  4. Reçoit les notifications push envoyées par le backend            │
└───────────────────────────────┬──────────────────────────────────────┘
                                │  POST /api/device-tokens/register
                                ▼
┌──────────────────────────────────────────────────────────────────────┐
│  BACKEND MySugu  (Spring Boot)                                       │
│                                                                      │
│  • Stocke les tokens FCM en base de données (table device_tokens)   │
│  • Envoie les notifications via Firebase Admin SDK                   │
│  • Déclencheurs automatiques :                                       │
│      - Confirmation de commande (client, restaurant, livreur)        │
│      - Assignation d'un livreur                                      │
│      - Changement de statut d'une commande                           │
│  • Campagnes manuelles : admin envoie à un segment d'utilisateurs    │
└───────────────────────────────┬──────────────────────────────────────┘
                                │  Firebase Admin SDK
                                ▼
┌──────────────────────────────────────────────────────────────────────┐
│  FIREBASE CLOUD MESSAGING (FCM)                                      │
│                                                                      │
│  Route les messages vers le bon canal selon la plateforme :          │
│  • Android  →  FCM HTTP v1                                           │
│  • iOS      →  APNs (Apple Push Notification service)                │
│  • Web      →  Web Push Protocol                                     │
└──────────────────────────────────────────────────────────────────────┘
```

> **Point clé** : le backend utilise un seul fichier `firebase-service-account.json` pour
> envoyer sur toutes les plateformes. Il n'y a rien à modifier côté serveur selon la plateforme.
> Chaque plateforme cliente doit en revanche intégrer le SDK Firebase correspondant.

---

## 2. Configuration Firebase

### 2.1 Projet Firebase existant

Le projet Firebase MySugu est déjà créé :

| Paramètre | Valeur |
|---|---|
| **Project ID** | `mysugu-app` |
| **Messaging Sender ID** | `673962321887` |

### 2.2 Créer les applications dans Firebase Console

Pour chaque plateforme, une application doit être enregistrée dans la Firebase Console
(**Paramètres du projet ⚙️ → Vos applications → Ajouter une application**).

| Plateforme | Statut | Fichier à récupérer |
|---|---|---|
| Web | ✅ Déjà créée | Config JS (apiKey, appId…) |
| Android | À créer | `google-services.json` |
| iOS | À créer | `GoogleService-Info.plist` |

### 2.3 Clé VAPID (Web uniquement)

La clé VAPID est requise pour le push Web. Elle se trouve dans :
**Firebase Console → Paramètres du projet → Cloud Messaging → Web Push certificates**

Si aucune clé n'existe, cliquer **Generate key pair**. Copier la **clé publique**.

### 2.4 Certificat APNs (iOS uniquement)

Requis pour que FCM puisse relayer les notifications vers les appareils Apple.

1. Apple Developer Console → **Certificates, Identifiers & Profiles** → **Keys**
2. Créer une clé avec **Apple Push Notifications service (APNs)** activé
3. Télécharger le fichier `.p8`
4. Firebase Console → **Paramètres du projet → Cloud Messaging → Apple app configuration**
5. Uploader le fichier `.p8` avec le **Key ID** et le **Team ID**

> Sans cette étape, les notifications iOS ne seront pas délivrées.

---

## 3. Référence API Backend

**URL de base** : `https://api.mysugu.ma` (production) / `http://localhost:8083` (dev)
**Authentification** : Bearer Token JWT dans le header `Authorization`

### 3.1 Enregistrer un token FCM

Associe un token FCM à un utilisateur. À appeler **à chaque démarrage de l'application**
et à chaque renouvellement du token FCM.

```
POST /api/device-tokens/register
Authorization: Bearer <jwt>
Content-Type: application/json
```

**Corps de la requête :**

```json
{
  "userId": 42,
  "token": "fcm_token_string...",
  "platform": "ANDROID"
}
```

| Champ | Type | Obligatoire | Description |
|---|---|---|---|
| `userId` | Long | Oui | ID de l'utilisateur connecté |
| `token` | String | Oui | Token FCM obtenu depuis le SDK Firebase |
| `platform` | String | Oui | `ANDROID`, `IOS` ou `WEB` |

**Réponse 200 :**
```json
{ "message": "Token FCM enregistré avec succès" }
```

---

### 3.2 Désactiver un token FCM

À appeler lors de la **déconnexion** de l'utilisateur pour cesser de recevoir des notifications.

```
DELETE /api/device-tokens/{token}
Authorization: Bearer <jwt>
```

**Réponse 200 :**
```json
{ "message": "Token FCM désactivé" }
```

---

### 3.3 Désactiver tous les tokens d'un utilisateur

```
DELETE /api/device-tokens/user/{userId}
Authorization: Bearer <jwt>
```

**Réponse 200 :**
```json
{ "message": "Tous les tokens FCM de l'utilisateur désactivés" }
```

---

### 3.4 Récupérer ses notifications in-app (paginées)

```
GET /api/notifications?page=0&size=20
Authorization: Bearer <jwt>
```

**Réponse 200 :**
```json
{
  "content": [
    {
      "id": 101,
      "destinataireId": 42,
      "titre": "Commande confirmée",
      "message": "Votre commande CMD-20260301-001 a été confirmée.",
      "type": "COMMANDE_CONFIRMEE",
      "lue": false,
      "lueAt": null,
      "entityId": 15,
      "entityType": "COMMANDE",
      "createdAt": "2026-03-28T14:30:00"
    }
  ],
  "totalElements": 47,
  "totalPages": 3,
  "number": 0
}
```

---

### 3.5 Récupérer les notifications non lues

```
GET /api/notifications/non-lues
Authorization: Bearer <jwt>
```

---

### 3.6 Compter les notifications non lues

Utile pour afficher un badge sur l'icône de notification.

```
GET /api/notifications/count
Authorization: Bearer <jwt>
```

**Réponse 200 :**
```json
{ "nonLues": 5 }
```

---

### 3.7 Marquer une notification comme lue

```
PATCH /api/notifications/{id}/lire
Authorization: Bearer <jwt>
```

---

### 3.8 Marquer toutes les notifications comme lues

```
POST /api/notifications/lire-toutes
Authorization: Bearer <jwt>
```

---

### 3.9 Envoyer une campagne (ADMIN uniquement)

```
POST /api/admin/notifications/campagne
Authorization: Bearer <jwt_admin>
Content-Type: application/json
```

**Corps de la requête :**

```json
{
  "titre": "Offre spéciale weekend !",
  "message": "Profitez de -20% sur toutes vos commandes. Code : WEEKEND20",
  "type": "PROMOTION",
  "cibleRole": "CLIENT",
  "entityId": 7,
  "entityType": "PROMOTION"
}
```

| Champ | Type | Obligatoire | Valeurs | Défaut |
|---|---|---|---|---|
| `titre` | String | Oui | max 200 caractères | — |
| `message` | String | Oui | max 1000 caractères | — |
| `type` | String | Non | `PROMOTION`, `SYSTEME` | `PROMOTION` |
| `cibleRole` | String | Non | `CLIENT`, `LIVREUR`, `RESTAURANT_OWNER`, `ADMIN`, `ALL` | `CLIENT` |
| `entityId` | Long | Non | ID de l'entité liée (ex. promotion) | null |
| `entityType` | String | Non | Ex. `"PROMOTION"` | null |

**Réponse 200 :**
```json
{
  "destinatairesCount": 1250,
  "notificationsCreees": 1250,
  "pushEnvoyees": 1250,
  "envoyeeAt": "2026-03-28T14:30:00"
}
```

---

## 4. Implémentation Web (React)

### 4.1 Dépendances

```bash
npm install firebase
```

### 4.2 Fichier Service Worker

Créer le fichier `public/firebase-messaging-sw.js` à la racine du dossier `public` :

```javascript
importScripts('https://www.gstatic.com/firebasejs/10.12.0/firebase-app-compat.js');
importScripts('https://www.gstatic.com/firebasejs/10.12.0/firebase-messaging-compat.js');

firebase.initializeApp({
  apiKey:            "AIzaSyCW_hCE-prQ8ZGLiPfJfsuM6OFNyFQQ1iU",
  authDomain:        "mysugu-app.firebaseapp.com",
  projectId:         "mysugu-app",
  storageBucket:     "mysugu-app.firebasestorage.app",
  messagingSenderId: "673962321887",
  appId:             "1:673962321887:web:23135fdf1f17901dba466a"
});

const messaging = firebase.messaging();

// Notifications reçues quand l'onglet est en arrière-plan
messaging.onBackgroundMessage(payload => {
  const { title, body } = payload.notification;
  self.registration.showNotification(title, {
    body,
    icon: '/logo192.png',
    data: payload.data
  });
});
```

> Ce fichier **doit être servi depuis la racine du domaine** (ex: `https://app.mysugu.ma/firebase-messaging-sw.js`).
> En React, placer dans `public/` suffit.

### 4.3 Service d'initialisation Firebase

Créer `src/services/firebaseService.js` :

```javascript
import { initializeApp } from 'firebase/app';
import { getMessaging, getToken, onMessage } from 'firebase/messaging';

const firebaseConfig = {
  apiKey:            "AIzaSyCW_hCE-prQ8ZGLiPfJfsuM6OFNyFQQ1iU",
  authDomain:        "mysugu-app.firebaseapp.com",
  projectId:         "mysugu-app",
  storageBucket:     "mysugu-app.firebasestorage.app",
  messagingSenderId: "673962321887",
  appId:             "1:673962321887:web:23135fdf1f17901dba466a"
};

// Clé VAPID — Firebase Console → Cloud Messaging → Web Push certificates
const VAPID_KEY = "VOTRE_CLE_VAPID_ICI";

const app       = initializeApp(firebaseConfig);
const messaging = getMessaging(app);

/**
 * Demande la permission de notification et retourne le token FCM.
 * Retourne null si la permission est refusée.
 */
export async function requestNotificationPermission() {
  try {
    const permission = await Notification.requestPermission();
    if (permission !== 'granted') return null;

    const registration = await navigator.serviceWorker.register('/firebase-messaging-sw.js');
    const token = await getToken(messaging, {
      vapidKey: VAPID_KEY,
      serviceWorkerRegistration: registration
    });
    return token;
  } catch (error) {
    console.error('Erreur FCM :', error);
    return null;
  }
}

/**
 * Écoute les notifications reçues quand l'onglet est actif.
 * @param {function} callback - Fonction appelée avec le payload de la notification.
 */
export function onForegroundMessage(callback) {
  return onMessage(messaging, callback);
}
```

### 4.4 Enregistrement du token au login

Appeler cette logique juste après la connexion de l'utilisateur :

```javascript
import { requestNotificationPermission } from '../services/firebaseService';
import api from '../services/api'; // votre instance axios

async function handleLoginSuccess(user, jwtToken) {
  // 1. Obtenir le token FCM
  const fcmToken = await requestNotificationPermission();

  // 2. Enregistrer auprès du backend si obtenu
  if (fcmToken) {
    await api.post('/api/device-tokens/register', {
      userId:   user.id,
      token:    fcmToken,
      platform: 'WEB'
    }, {
      headers: { Authorization: `Bearer ${jwtToken}` }
    });
  }
}
```

### 4.5 Désactivation du token à la déconnexion

```javascript
async function handleLogout(fcmToken, jwtToken) {
  if (fcmToken) {
    await api.delete(`/api/device-tokens/${fcmToken}`, {
      headers: { Authorization: `Bearer ${jwtToken}` }
    });
  }
  // ... reste de la logique de déconnexion
}
```

### 4.6 Affichage des notifications en temps réel

```javascript
import { onForegroundMessage } from '../services/firebaseService';

// Dans un composant de haut niveau (App.jsx ou layout principal)
useEffect(() => {
  const unsubscribe = onForegroundMessage(payload => {
    const { title, body } = payload.notification;
    // Afficher un toast, une alerte, ou une notification dans l'UI
    showToast({ title, body });
  });

  return () => unsubscribe();
}, []);
```

### 4.7 Badge de notifications non lues

```javascript
async function fetchUnreadCount(jwtToken) {
  const response = await api.get('/api/notifications/count', {
    headers: { Authorization: `Bearer ${jwtToken}` }
  });
  return response.data.nonLues; // afficher ce nombre sur l'icône cloche
}
```

---

## 5. Implémentation Android

### 5.1 Configuration Firebase

1. **Firebase Console → Paramètres du projet → Ajouter une application → Android**
2. Renseigner le **package name** de l'application (ex: `ma.mysugu.client`)
3. Télécharger `google-services.json`
4. Placer `google-services.json` dans le dossier `app/` du projet Android

### 5.2 Dépendances Gradle

`build.gradle` (niveau projet) :
```gradle
plugins {
    id 'com.google.gms.google-services' version '4.4.0' apply false
}
```

`build.gradle` (niveau app) :
```gradle
plugins {
    id 'com.google.gms.google-services'
}

dependencies {
    implementation platform('com.google.firebase:firebase-bom:32.7.0')
    implementation 'com.google.firebase:firebase-messaging-ktx'
}
```

### 5.3 Service de gestion des notifications

Créer `MysuguFirebaseMessagingService.kt` :

```kotlin
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

class MysuguFirebaseMessagingService : FirebaseMessagingService() {

    /**
     * Appelé quand un nouveau token FCM est généré (premier lancement ou renouvellement).
     * Enregistrer immédiatement le nouveau token auprès du backend.
     */
    override fun onNewToken(token: String) {
        super.onNewToken(token)
        val userId = SessionManager.getUserId() ?: return
        val jwt    = SessionManager.getJwt()    ?: return
        ApiClient.deviceTokenApi.registerToken(
            jwt      = "Bearer $jwt",
            body     = DeviceTokenRequest(
                userId   = userId,
                token    = token,
                platform = "ANDROID"
            )
        ).enqueue(/* callback */)
    }

    /**
     * Appelé quand une notification est reçue en premier plan (app ouverte).
     * En arrière-plan, Android affiche automatiquement la notification.
     */
    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        remoteMessage.notification?.let { notif ->
            showLocalNotification(
                title = notif.title ?: "MySugu",
                body  = notif.body  ?: "",
                data  = remoteMessage.data
            )
        }
    }

    private fun showLocalNotification(title: String, body: String, data: Map<String, String>) {
        val channelId = "mysugu_notifications"
        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)

        // Navigation vers l'écran concerné selon le type
        val type = data["type"]
        val entityId = data["entityId"]
        val intent = buildDeepLinkIntent(type, entityId)
        if (intent != null) {
            val pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            builder.setContentIntent(pendingIntent)
        }

        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannelIfNeeded(manager, channelId)
        manager.notify(System.currentTimeMillis().toInt(), builder.build())
    }

    private fun createNotificationChannelIfNeeded(manager: NotificationManager, channelId: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Notifications MySugu",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Commandes, promotions et alertes MySugu"
            }
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildDeepLinkIntent(type: String?, entityId: String?): Intent? {
        // Adapter selon la navigation de votre app
        return when (type) {
            "COMMANDE_CONFIRMEE",
            "COMMANDE_EN_PREPARATION",
            "COMMANDE_PRETE",
            "COMMANDE_EN_COURS",
            "COMMANDE_LIVREE",
            "COMMANDE_ANNULEE" -> Intent(this, CommandeDetailActivity::class.java)
                .putExtra("commandeId", entityId?.toLongOrNull())
            "PROMOTION"        -> Intent(this, PromotionsActivity::class.java)
            else               -> null
        }
    }
}
```

### 5.4 Déclaration dans AndroidManifest.xml

```xml
<application ...>

    <!-- Service FCM -->
    <service
        android:name=".MysuguFirebaseMessagingService"
        android:exported="false">
        <intent-filter>
            <action android:name="com.google.firebase.MESSAGING_EVENT" />
        </intent-filter>
    </service>

    <!-- Icône et couleur des notifications -->
    <meta-data
        android:name="com.google.firebase.messaging.default_notification_icon"
        android:resource="@drawable/ic_notification" />
    <meta-data
        android:name="com.google.firebase.messaging.default_notification_color"
        android:resource="@color/orange_primary" />
    <meta-data
        android:name="com.google.firebase.messaging.default_notification_channel_id"
        android:value="mysugu_notifications" />

</application>

<!-- Permission notification (Android 13+) -->
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

### 5.5 Enregistrement du token au login

```kotlin
// Dans LoginViewModel ou AuthRepository, après connexion réussie
fun onLoginSuccess(user: User, jwt: String) {
    FirebaseMessaging.getInstance().token.addOnSuccessListener { fcmToken ->
        apiService.registerDeviceToken(
            authorization = "Bearer $jwt",
            body = DeviceTokenRequest(
                userId   = user.id,
                token    = fcmToken,
                platform = "ANDROID"
            )
        ).enqueue(object : Callback<ApiResponse> {
            override fun onResponse(call: Call<ApiResponse>, response: Response<ApiResponse>) {
                // Token enregistré
            }
            override fun onFailure(call: Call<ApiResponse>, t: Throwable) {
                // Logger l'erreur — ne pas bloquer la connexion
            }
        })
    }
}
```

### 5.6 Permission notification (Android 13+)

```kotlin
// Dans MainActivity ou onboarding — demander la permission si non accordée
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    if (ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) != PackageManager.PERMISSION_GRANTED
    ) {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.POST_NOTIFICATIONS),
            REQUEST_CODE_NOTIFICATION
        )
    }
}
```

---

## 6. Implémentation iOS

### 6.1 Configuration Firebase

1. **Firebase Console → Paramètres du projet → Ajouter une application → Apple (iOS+)**
2. Renseigner le **Bundle ID** (ex: `ma.mysugu.client`)
3. Télécharger `GoogleService-Info.plist`
4. Dans Xcode : glisser `GoogleService-Info.plist` dans le projet (vérifier **Copy items if needed**)

### 6.2 Certificat APNs (obligatoire)

Sans cette étape, aucune notification ne sera reçue sur iOS.

1. **Apple Developer Console → Certificates, Identifiers & Profiles → Keys**
2. Créer une nouvelle clé, cocher **Apple Push Notifications service (APNs)**
3. Télécharger le fichier `.p8`, noter le **Key ID**
4. **Firebase Console → Paramètres du projet → Cloud Messaging → Apple app configuration**
5. Uploader le `.p8` + renseigner le **Key ID** + le **Team ID** (visible sur developer.apple.com)

### 6.3 Capabilities Xcode

Dans Xcode → sélectionner la target → onglet **Signing & Capabilities** :

- Ajouter **Push Notifications**
- Ajouter **Background Modes** → cocher **Remote notifications**

### 6.4 Dépendances (Swift Package Manager)

**Xcode → File → Add Package Dependencies** :
```
https://github.com/firebase/firebase-ios-sdk
```

Sélectionner **FirebaseMessaging**.

Ou via `Podfile` :
```ruby
pod 'Firebase/Messaging'
```

### 6.5 Initialisation dans AppDelegate

```swift
import UIKit
import Firebase
import FirebaseMessaging
import UserNotifications

@main
class AppDelegate: UIResponder, UIApplicationDelegate,
                   UNUserNotificationCenterDelegate,
                   MessagingDelegate {

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?
    ) -> Bool {
        // Initialiser Firebase
        FirebaseApp.configure()

        // Délégués
        Messaging.messaging().delegate = self
        UNUserNotificationCenter.current().delegate = self

        // Demander la permission de notification
        UNUserNotificationCenter.current().requestAuthorization(
            options: [.alert, .badge, .sound]
        ) { granted, _ in
            guard granted else { return }
            DispatchQueue.main.async {
                application.registerForRemoteNotifications()
            }
        }

        return true
    }

    // APNs token → transmettre à Firebase
    func application(_ application: UIApplication,
                     didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data) {
        Messaging.messaging().apnsToken = deviceToken
    }

    // Nouveau token FCM disponible → enregistrer auprès du backend
    func messaging(_ messaging: Messaging, didReceiveRegistrationToken fcmToken: String?) {
        guard let token = fcmToken,
              let userId = SessionManager.shared.userId,
              let jwt    = SessionManager.shared.jwt else { return }

        ApiService.shared.registerDeviceToken(
            userId:   userId,
            token:    token,
            platform: "IOS",
            jwt:      jwt
        )
    }

    // Notification reçue en premier plan (app ouverte)
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        // Afficher banner + son même si l'app est au premier plan
        completionHandler([.banner, .sound, .badge])
    }

    // Tap sur une notification → navigation vers l'écran concerné
    func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        let userInfo = response.notification.request.content.userInfo
        handleNotificationTap(userInfo: userInfo)
        completionHandler()
    }

    private func handleNotificationTap(userInfo: [AnyHashable: Any]) {
        guard let type     = userInfo["type"]     as? String,
              let entityId = userInfo["entityId"] as? String else { return }

        // Naviguer selon le type de notification
        switch type {
        case "COMMANDE_CONFIRMEE", "COMMANDE_EN_PREPARATION",
             "COMMANDE_PRETE", "COMMANDE_EN_COURS",
             "COMMANDE_LIVREE", "COMMANDE_ANNULEE":
            NavigationManager.shared.navigateToCommande(id: entityId)
        case "PROMOTION":
            NavigationManager.shared.navigateToPromotions()
        default:
            break
        }
    }
}
```

### 6.6 Appel API — Enregistrement du token

```swift
// ApiService.swift
func registerDeviceToken(userId: Int64, token: String, platform: String, jwt: String) {
    guard let url = URL(string: "\(baseURL)/api/device-tokens/register") else { return }

    var request = URLRequest(url: url)
    request.httpMethod = "POST"
    request.setValue("application/json",  forHTTPHeaderField: "Content-Type")
    request.setValue("Bearer \(jwt)",     forHTTPHeaderField: "Authorization")

    let body: [String: Any] = [
        "userId":   userId,
        "token":    token,
        "platform": platform
    ]
    request.httpBody = try? JSONSerialization.data(withJSONObject: body)

    URLSession.shared.dataTask(with: request) { _, _, error in
        if let error = error {
            print("Erreur enregistrement token FCM : \(error)")
        }
    }.resume()
}
```

### 6.7 Désactivation du token à la déconnexion

```swift
func logout() {
    if let fcmToken = Messaging.messaging().fcmToken,
       let jwt = SessionManager.shared.jwt {
        ApiService.shared.deactivateDeviceToken(token: fcmToken, jwt: jwt)
    }
    Messaging.messaging().deleteToken { _ in }
    // ... reste de la logique de déconnexion
}
```

---

## 7. Cycle de vie du token FCM

```
Démarrage app / Login
        │
        ▼
Initialiser Firebase SDK
        │
        ▼
Demander permission notification  ◄── Android 13+ et iOS : demande explicite
        │
        ├── Refusée → pas de push (fonctionnalités in-app toujours disponibles)
        │
        └── Accordée
                │
                ▼
        Obtenir le token FCM
                │
                ▼
        POST /api/device-tokens/register
                │
                ▼
        Recevoir les notifications push ✓

                │
         (déconnexion)
                │
                ▼
        DELETE /api/device-tokens/{token}
        (cesser de recevoir les push pour ce device)
```

> **Renouvellement automatique** : Firebase peut renouveler le token FCM. Le callback
> `onNewToken` (Android) / `didReceiveRegistrationToken` (iOS) / (pas de callback Web —
> appeler `getToken()` à chaque démarrage) est déclenché automatiquement. Il faut
> ré-appeler `/api/device-tokens/register` avec le nouveau token.

---

## 8. Types de notifications

Le champ `type` dans la notification indique l'origine et permet de naviguer vers le bon écran.

| Type | Déclencheur | Destinataire | Action recommandée |
|---|---|---|---|
| `COMMANDE_CONFIRMEE` | Commande confirmée par le restaurant | Client, Restaurant | Ouvrir le détail de la commande |
| `COMMANDE_EN_PREPARATION` | Restaurant commence à préparer | Client | Ouvrir le détail de la commande |
| `COMMANDE_PRETE` | Commande prête à être récupérée | Client | Ouvrir le détail de la commande |
| `COMMANDE_EN_COURS` | Livreur en route | Client | Ouvrir le tracking |
| `COMMANDE_LIVREE` | Commande livrée | Client | Inviter à laisser un avis |
| `COMMANDE_ANNULEE` | Commande annulée | Client | Ouvrir le détail de la commande |
| `LIVREUR_ASSIGNE` | Commande assignée à un livreur | Livreur | Ouvrir le détail de la livraison |
| `PROMOTION` | Campagne marketing admin | Clients (ou autre segment) | Ouvrir la page promotions |
| `SYSTEME` | Message système admin | Tous utilisateurs | Ouvrir les notifications |
| `AVIS_MODERE` | Avis modéré par l'admin | Client | Ouvrir l'avis |

### Données supplémentaires (`data` payload)

Chaque notification push contient également un champ `data` avec les informations suivantes :

```json
{
  "type":       "COMMANDE_CONFIRMEE",
  "entityId":   "15",
  "entityType": "COMMANDE"
}
```

Utiliser ces données dans les callbacks de notification pour construire la navigation deep-link.

---

## 9. Checklist de validation

### Backend
- [ ] Variable d'environnement `FIREBASE_ENABLED=true`
- [ ] Fichier `firebase-service-account.json` présent et chemin configuré dans `.env`
- [ ] Application démarrée sans erreur Firebase dans les logs

### Web
- [ ] `public/firebase-messaging-sw.js` créé avec la config Firebase correcte
- [ ] Clé VAPID récupérée dans Firebase Console et configurée
- [ ] Permission notification accordée dans le navigateur
- [ ] Appel à `/api/device-tokens/register` réussi après login
- [ ] Notification reçue après envoi d'une campagne admin

### Android
- [ ] `google-services.json` placé dans `app/`
- [ ] Plugin `com.google.gms.google-services` appliqué
- [ ] `MysuguFirebaseMessagingService` déclaré dans `AndroidManifest.xml`
- [ ] Permission `POST_NOTIFICATIONS` demandée (Android 13+)
- [ ] Appel à `/api/device-tokens/register` réussi après login
- [ ] Notification reçue en arrière-plan et en premier plan

### iOS
- [ ] `GoogleService-Info.plist` ajouté au projet Xcode
- [ ] Capability **Push Notifications** activée
- [ ] Capability **Background Modes → Remote notifications** activée
- [ ] Certificat APNs uploadé dans Firebase Console
- [ ] `Messaging.messaging().delegate = self` configuré dans `AppDelegate`
- [ ] Appel à `/api/device-tokens/register` réussi après login
- [ ] Notification reçue sur un vrai appareil physique (simulateur iOS ne supporte pas les push)
