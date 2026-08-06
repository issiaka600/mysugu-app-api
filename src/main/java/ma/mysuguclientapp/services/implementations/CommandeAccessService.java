package ma.mysuguclientapp.services.implementations;

import lombok.RequiredArgsConstructor;
import ma.mysuguclientapp.entities.Commande;
import ma.mysuguclientapp.entities.User;
import ma.mysuguclientapp.entities.chat.ParticipantRef;
import ma.mysuguclientapp.enumerations.ParticipantType;
import ma.mysuguclientapp.enumerations.StatutOffreLivraison;
import ma.mysuguclientapp.enumerations.UserRole;
import ma.mysuguclientapp.exceptions.ResourceNotFoundException;
import ma.mysuguclientapp.repositories.CommandeRepository;
import ma.mysuguclientapp.repositories.OffreLivraisonRepository;
import ma.mysuguclientapp.repositories.RestaurantRepository;
import ma.mysuguclientapp.repositories.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/** Règles d'accès partagées pour commandes, suivi GPS et conversations. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommandeAccessService {

    private final CommandeRepository commandeRepository;
    private final UserRepository userRepository;
    private final RestaurantRepository restaurantRepository;
    private final OffreLivraisonRepository offreLivraisonRepository;

    public void requireOrderAccess(String email, Long commandeId) {
        requireOrderAccess(currentUser(email), requireCommande(commandeId));
    }

    public void requireOrderAccessByNumero(String email, String numeroCommande) {
        Commande commande = commandeRepository.findByNumeroCommande(numeroCommande)
                .orElseThrow(() -> new ResourceNotFoundException("Commande non trouvée: " + numeroCommande));
        requireOrderAccess(currentUser(email), commande);
    }

    public void requireClientListAccess(String email, Long clientId) {
        User user = currentUser(email);
        if (user.getRole() != UserRole.ADMIN && (user.getRole() != UserRole.CLIENT || !user.getId().equals(clientId))) {
            deny();
        }
    }

    public void requireLivreurListAccess(String email, Long livreurId) {
        User user = currentUser(email);
        if (user.getRole() != UserRole.ADMIN && (user.getRole() != UserRole.LIVREUR || !user.getId().equals(livreurId))) {
            deny();
        }
    }

    public void requireRestaurantListAccess(String email, Long restaurantId) {
        User user = currentUser(email);
        if (user.getRole() == UserRole.ADMIN) return;
        if (user.getRole() != UserRole.RESTAURANT_OWNER
                || restaurantRepository.findByOwnerId(user.getId()).map(r -> r.getId().equals(restaurantId)).orElse(false) == false) {
            deny();
        }
    }

    /** Un message entre deux parties n'est possible que si elles partagent au moins une commande. */
    public void requireConversationAccess(ParticipantRef first, ParticipantRef second) {
        if (!canAccessConversation(first, second)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Ces participants ne sont pas autorisés à échanger des messages");
        }
    }

    public boolean canAccessConversation(ParticipantRef first, ParticipantRef second) {
        if (first == null || second == null || first.id() == null || second.id() == null) return false;
        if (first.type() == ParticipantType.ADMIN || second.type() == ParticipantType.ADMIN) return true;
        if (first.type() == ParticipantType.CUSTOMER && second.type() == ParticipantType.RESTAURANT) {
            return commandeRepository.existsByClientIdAndRestaurantId(first.id(), second.id());
        }
        if (first.type() == ParticipantType.RESTAURANT && second.type() == ParticipantType.CUSTOMER) {
            return commandeRepository.existsByClientIdAndRestaurantId(second.id(), first.id());
        }
        if (first.type() == ParticipantType.CUSTOMER && second.type() == ParticipantType.LIVREUR) {
            return commandeRepository.existsByClientIdAndLivreurId(first.id(), second.id());
        }
        if (first.type() == ParticipantType.LIVREUR && second.type() == ParticipantType.CUSTOMER) {
            return commandeRepository.existsByClientIdAndLivreurId(second.id(), first.id());
        }
        if (first.type() == ParticipantType.RESTAURANT && second.type() == ParticipantType.LIVREUR) {
            return commandeRepository.existsByRestaurantIdAndLivreurId(first.id(), second.id());
        }
        if (first.type() == ParticipantType.LIVREUR && second.type() == ParticipantType.RESTAURANT) {
            return commandeRepository.existsByRestaurantIdAndLivreurId(second.id(), first.id());
        }
        return false;
    }

    /** Dernière commande commune, utilisée lorsque les anciennes façades chat ne transmettent pas order_id. */
    public Long resolveSharedCommandeId(ParticipantRef first, ParticipantRef second) {
        requireConversationAccess(first, second);
        if (first.type() == ParticipantType.ADMIN || second.type() == ParticipantType.ADMIN) return null;
        if (first.type() == ParticipantType.CUSTOMER && second.type() == ParticipantType.RESTAURANT) {
            return commandeRepository.findFirstByClientIdAndRestaurantIdOrderByCreatedAtDesc(first.id(), second.id())
                    .map(Commande::getId).orElse(null);
        }
        if (first.type() == ParticipantType.RESTAURANT && second.type() == ParticipantType.CUSTOMER) {
            return commandeRepository.findFirstByClientIdAndRestaurantIdOrderByCreatedAtDesc(second.id(), first.id())
                    .map(Commande::getId).orElse(null);
        }
        if (first.type() == ParticipantType.CUSTOMER && second.type() == ParticipantType.LIVREUR) {
            return commandeRepository.findFirstByClientIdAndLivreurIdOrderByCreatedAtDesc(first.id(), second.id())
                    .map(Commande::getId).orElse(null);
        }
        if (first.type() == ParticipantType.LIVREUR && second.type() == ParticipantType.CUSTOMER) {
            return commandeRepository.findFirstByClientIdAndLivreurIdOrderByCreatedAtDesc(second.id(), first.id())
                    .map(Commande::getId).orElse(null);
        }
        if (first.type() == ParticipantType.RESTAURANT && second.type() == ParticipantType.LIVREUR) {
            return commandeRepository.findFirstByRestaurantIdAndLivreurIdOrderByCreatedAtDesc(first.id(), second.id())
                    .map(Commande::getId).orElse(null);
        }
        if (first.type() == ParticipantType.LIVREUR && second.type() == ParticipantType.RESTAURANT) {
            return commandeRepository.findFirstByRestaurantIdAndLivreurIdOrderByCreatedAtDesc(second.id(), first.id())
                    .map(Commande::getId).orElse(null);
        }
        return null;
    }

    public void requireConversationAccessForCommande(ParticipantRef first, ParticipantRef second, Long commandeId) {
        if (commandeId == null) {
            requireConversationAccess(first, second);
            return;
        }
        Commande commande = requireCommande(commandeId);
        boolean customerRestaurant = (first.type() == ParticipantType.CUSTOMER && first.id().equals(commande.getClient().getId())
                && second.type() == ParticipantType.RESTAURANT && second.id().equals(commande.getRestaurant().getId()))
                || (second.type() == ParticipantType.CUSTOMER && second.id().equals(commande.getClient().getId())
                && first.type() == ParticipantType.RESTAURANT && first.id().equals(commande.getRestaurant().getId()));
        boolean withLivreur = commande.getLivreur() != null && (
                (first.type() == ParticipantType.CUSTOMER && first.id().equals(commande.getClient().getId())
                        && second.type() == ParticipantType.LIVREUR && second.id().equals(commande.getLivreur().getId()))
                || (second.type() == ParticipantType.CUSTOMER && second.id().equals(commande.getClient().getId())
                        && first.type() == ParticipantType.LIVREUR && first.id().equals(commande.getLivreur().getId()))
                || (first.type() == ParticipantType.RESTAURANT && first.id().equals(commande.getRestaurant().getId())
                        && second.type() == ParticipantType.LIVREUR && second.id().equals(commande.getLivreur().getId()))
                || (second.type() == ParticipantType.RESTAURANT && second.id().equals(commande.getRestaurant().getId())
                        && first.type() == ParticipantType.LIVREUR && first.id().equals(commande.getLivreur().getId())));
        if (!customerRestaurant && !withLivreur) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Cette commande ne relie pas les participants de la conversation");
        }
    }

    private void requireOrderAccess(User user, Commande commande) {
        if (user.getRole() == UserRole.ADMIN) return;
        if (user.getRole() == UserRole.CLIENT && sameUser(user, commande.getClient())) return;
        if (user.getRole() == UserRole.LIVREUR && (sameUser(user, commande.getLivreur())
                || offreLivraisonRepository.findByCommandeIdAndLivreurIdAndStatut(
                        commande.getId(), user.getId(), StatutOffreLivraison.PROPOSEE).isPresent())) return;
        if (user.getRole() == UserRole.RESTAURANT_OWNER && commande.getRestaurant() != null
                && sameUser(user, commande.getRestaurant().getOwner())) return;
        deny();
    }

    private User currentUser(String email) {
        if (email == null || email.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Utilisateur non authentifié");
        }
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Utilisateur non authentifié"));
    }

    private Commande requireCommande(Long id) {
        return commandeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Commande non trouvée: " + id));
    }

    private boolean sameUser(User first, User second) {
        return first != null && second != null && first.getId() != null && first.getId().equals(second.getId());
    }

    private void deny() {
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Accès à cette commande refusé");
    }
}
