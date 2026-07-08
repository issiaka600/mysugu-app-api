package ma.mysuguclientapp.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import ma.mysuguclientapp.dtos.ConversationDTO;
import ma.mysuguclientapp.dtos.MessageChatCreateDTO;
import ma.mysuguclientapp.dtos.MessageChatDTO;
import ma.mysuguclientapp.services.interfaces.MessagerieService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Module MESSAGERIE / CHAT (acheteur <-> vendeur). Correspond au ticket T9 du techspec
 * migration TikTak livreur ("Chat + counterpart" — le counterpart client/vendeur manquant).
 * Toutes les routes nécessitent un utilisateur authentifié (CLIENT ou RESTAURANT_OWNER) ;
 * l'appartenance à la conversation est vérifiée dans le service.
 */
@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class MessagerieController {

    private final MessagerieService messagerieService;

    /**
     * GET /api/messages/conversations - Liste des conversations de l'utilisateur courant
     * (vue client : ses fils avec des restaurants ; vue restaurateur : les fils de son restaurant)
     */
    @GetMapping("/conversations")
    public ResponseEntity<List<ConversationDTO>> listerConversations(
            @RequestHeader("Authorization") String token) {
        return ResponseEntity.ok(messagerieService.getConversations(token));
    }

    /**
     * GET /api/messages/conversations/search?q=... - Recherche par mot-clé dans le contenu
     * des messages de mes conversations
     */
    @GetMapping("/conversations/search")
    public ResponseEntity<List<ConversationDTO>> rechercherConversations(
            @RequestHeader("Authorization") String token,
            @RequestParam("q") String motCle) {
        return ResponseEntity.ok(messagerieService.rechercherConversations(token, motCle));
    }

    /**
     * GET /api/messages/conversations/{id} - Messages d'une conversation (marque comme lus)
     */
    @GetMapping("/conversations/{id}")
    public ResponseEntity<List<MessageChatDTO>> getMessages(
            @RequestHeader("Authorization") String token,
            @PathVariable("id") Long conversationId) {
        return ResponseEntity.ok(messagerieService.getMessages(token, conversationId));
    }

    /**
     * POST /api/messages - Envoyer un message.
     * Fournir soit conversationId (répondre), soit restaurantId (démarrer un nouveau fil, client uniquement).
     */
    @PostMapping
    public ResponseEntity<MessageChatDTO> envoyerMessage(
            @RequestHeader("Authorization") String token,
            @Valid @RequestBody MessageChatCreateDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(messagerieService.envoyerMessage(token, dto));
    }
}