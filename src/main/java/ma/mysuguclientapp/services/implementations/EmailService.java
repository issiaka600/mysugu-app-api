package ma.mysuguclientapp.services.implementations;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String fromEmail;

    @Value("${app.frontend.url:http://localhost:3000}")
    private String frontendUrl;

    @Async
    public void envoyerVerificationEmail(String toEmail, String token) {
        String lien = frontendUrl + "/verify-email?token=" + token;
        String sujet = "MySugu - Vérification de votre adresse email";
        String message = "Bonjour,\n\n" +
                "Merci de vous être inscrit sur MySugu !\n\n" +
                "Veuillez vérifier votre adresse email en cliquant sur le lien ci-dessous :\n" +
                lien + "\n\n" +
                "Ce lien est valable pendant 24 heures.\n\n" +
                "Si vous n'avez pas créé de compte, vous pouvez ignorer cet email.\n\n" +
                "L'équipe MySugu";
        envoyerEmail(toEmail, sujet, message);
    }

    @Async
    public void envoyerReinitialisationMotDePasse(String toEmail, String token) {
        String lien = frontendUrl + "/reset-password?token=" + token;
        String sujet = "MySugu - Réinitialisation de votre mot de passe";
        String message = "Bonjour,\n\n" +
                "Vous avez demandé la réinitialisation de votre mot de passe.\n\n" +
                "Cliquez sur le lien ci-dessous pour créer un nouveau mot de passe :\n" +
                lien + "\n\n" +
                "Ce lien est valable pendant 1 heure.\n\n" +
                "Si vous n'avez pas fait cette demande, vous pouvez ignorer cet email.\n\n" +
                "L'équipe MySugu";
        envoyerEmail(toEmail, sujet, message);
    }

    @Async
    public void envoyerInvitationRestaurateur(String toEmail, String nom, String token) {
        String lien = frontendUrl + "/definir-mot-de-passe?token=" + token;
        String sujet = "MySugu - Activez votre compte restaurateur";
        String message = "Bonjour " + nom + ",\n\n" +
                "Un compte restaurateur a été créé pour vous sur MySugu.\n\n" +
                "Définissez votre mot de passe pour accéder à votre espace en cliquant sur le lien ci-dessous :\n" +
                lien + "\n\n" +
                "Ce lien est valable pendant 24 heures.\n\n" +
                "L'équipe MySugu";
        envoyerEmail(toEmail, sujet, message);
    }

    @Async
    public void envoyerConfirmationCommande(String toEmail, String numeroCommande, String restaurantNom) {
        String sujet = "MySugu - Commande #" + numeroCommande + " confirmée";
        String message = "Bonjour,\n\n" +
                "Votre commande #" + numeroCommande + " au restaurant " + restaurantNom + " a été confirmée.\n\n" +
                "Vous pouvez suivre votre commande en temps réel dans l'application MySugu.\n\n" +
                "Bon appétit !\nL'équipe MySugu";
        envoyerEmail(toEmail, sujet, message);
    }

    @Async
    public void envoyerNotificationLivraison(String toEmail, String numeroCommande) {
        String sujet = "MySugu - Votre commande #" + numeroCommande + " est livrée !";
        String message = "Bonjour,\n\n" +
                "Votre commande #" + numeroCommande + " vient d'être livrée.\n\n" +
                "Nous espérons que vous apprécierez votre repas !\n\n" +
                "N'oubliez pas de laisser un avis sur votre expérience.\n\n" +
                "L'équipe MySugu";
        envoyerEmail(toEmail, sujet, message);
    }

    @Async
    public void envoyerConfirmationSuppressionCompte(String toEmail, String nom) {
        String sujet = "MySugu - Suppression de votre compte";
        String message = "Bonjour " + nom + ",\n\n" +
                "Votre demande de suppression de compte a été traitée.\n\n" +
                "Vos données personnelles ont été anonymisées conformément au RGPD.\n\n" +
                "Nous sommes tristes de vous voir partir.\n\n" +
                "L'équipe MySugu";
        envoyerEmail(toEmail, sujet, message);
    }

    @Async
    public void envoyerAccuseReceptionContact(String toEmail, String nom, String sujet) {
        String objetEmail = "MySuku - Nous avons bien recu votre message";
        String message = "Bonjour " + (nom != null ? nom : "") + ",\n\n" +
                "Nous avons bien recu votre message concernant : \"" + sujet + "\".\n" +
                "Notre equipe reviendra vers vous dans les meilleurs delais.\n\n" +
                "A bientot,\nL'equipe MySuku";
        envoyerEmail(toEmail, objetEmail, message);
    }

    @Async
    public void envoyerReponseContact(String toEmail, String nom, String sujetInitial, String reponse) {
        String objetEmail = "MySuku - Reponse a votre message : " + sujetInitial;
        String message = "Bonjour " + (nom != null ? nom : "") + ",\n\n" +
                "Suite a votre message concernant \"" + sujetInitial + "\", voici notre reponse :\n\n" +
                reponse + "\n\n" +
                "Si vous avez d'autres questions, n'hesitez pas a nous recontacter.\n\n" +
                "Cordialement,\nL'equipe MySuku";
        envoyerEmail(toEmail, objetEmail, message);
    }

    @Async
    public void envoyerNotificationRevueRestaurant(String toEmail, String sujet, String message) {
        String objetEmail = "MySugu - " + sujet;
        String corps = "Bonjour,\n\n" + message + "\n\n" +
                "Vous pouvez consulter le statut de votre restaurant depuis votre application restaurateur.\n\n" +
                "Cordialement,\nL'equipe MySugu";
        envoyerEmail(toEmail, objetEmail, corps);
    }

    private void envoyerEmail(String to, String sujet, String message) {
        try {
            SimpleMailMessage email = new SimpleMailMessage();
            email.setFrom(fromEmail);
            email.setTo(to);
            email.setSubject(sujet);
            email.setText(message);
            mailSender.send(email);
            log.info("Email envoyé à {} - Sujet: {}", to, sujet);
        } catch (Exception e) {
            log.error("Erreur lors de l'envoi de l'email à {}: {}", to, e.getMessage());
        }
    }
}
