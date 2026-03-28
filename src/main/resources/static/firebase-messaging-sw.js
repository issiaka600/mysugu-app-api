// Service Worker FCM — requis pour recevoir les notifications push en arrière-plan
// Ce fichier DOIT être servi depuis la racine du domaine (ex: http://localhost:8083/firebase-messaging-sw.js)

importScripts('https://www.gstatic.com/firebasejs/10.12.0/firebase-app-compat.js');
importScripts('https://www.gstatic.com/firebasejs/10.12.0/firebase-messaging-compat.js');

// ⚠️ Remplacez ces valeurs par votre config Firebase Web
// (Firebase Console → Paramètres du projet → Général → Vos applications → Config SDK)
firebase.initializeApp({
    apiKey: "AIzaSyCW_hCE-prQ8ZGLiPfJfsuM6OFNyFQQ1iU",
    authDomain: "mysugu-app.firebaseapp.com",
    projectId: "mysugu-app",
    storageBucket: "mysugu-app.firebasestorage.app",
    messagingSenderId: "673962321887",
    appId: "1:673962321887:web:23135fdf1f17901dba466a",
    measurementId: "G-19PL7G7WSP"
});

const messaging = firebase.messaging();

// Gestion des notifications reçues en arrière-plan (onglet non actif)
messaging.onBackgroundMessage(payload => {
    console.log('[SW] Notification en arrière-plan reçue:', payload);
    const { title, body } = payload.notification;
    self.registration.showNotification(title, {
        body,
        icon: '/favicon.ico',
        badge: '/favicon.ico',
        data: payload.data
    });
});
