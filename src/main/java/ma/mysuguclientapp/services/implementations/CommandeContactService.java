package ma.mysuguclientapp.services.implementations;

import lombok.RequiredArgsConstructor;
import ma.mysuguclientapp.dtos.CommandeContactDTO;
import ma.mysuguclientapp.dtos.CommandeContactsDTO;
import ma.mysuguclientapp.entities.Commande;
import ma.mysuguclientapp.entities.Restaurant;
import ma.mysuguclientapp.entities.User;
import ma.mysuguclientapp.enumerations.StatutOffreLivraison;
import ma.mysuguclientapp.enumerations.UserRole;
import ma.mysuguclientapp.exceptions.ResourceNotFoundException;
import ma.mysuguclientapp.repositories.CommandeRepository;
import ma.mysuguclientapp.repositories.OffreLivraisonRepository;
import ma.mysuguclientapp.repositories.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Expose les coordonnées de commande à un participant, jamais à partir d'un identifiant fourni
 * par le mobile. Cela évite qu'un utilisateur consulte les coordonnées d'une autre commande.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommandeContactService {

    private final CommandeRepository commandeRepository;
    private final UserRepository userRepository;
    private final OffreLivraisonRepository offreLivraisonRepository;

    public CommandeContactsDTO getContacts(Long commandeId, String email) {
        User viewer = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Utilisateur non authentifié"));
        Commande commande = commandeRepository.findById(commandeId)
                .orElseThrow(() -> new ResourceNotFoundException("Commande non trouvée: " + commandeId));

        assertCanReadContacts(viewer, commande);

        CommandeContactsDTO response = new CommandeContactsDTO();
        response.setOrderId(commande.getId());

        switch (viewer.getRole()) {
            case CLIENT -> {
                response.setSeller(toSellerContact(commande.getRestaurant()));
                response.setDeliveryMan(toUserContact(commande.getLivreur(), "delivery_man"));
            }
            case LIVREUR -> {
                response.setCustomer(toUserContact(commande.getClient(), "customer"));
                response.setSeller(toSellerContact(commande.getRestaurant()));
            }
            case RESTAURANT_OWNER, RESTAURANT_STAFF -> {
                response.setCustomer(toUserContact(commande.getClient(), "customer"));
                response.setDeliveryMan(toUserContact(commande.getLivreur(), "delivery_man"));
            }
            case ADMIN -> {
                response.setCustomer(toUserContact(commande.getClient(), "customer"));
                response.setSeller(toSellerContact(commande.getRestaurant()));
                response.setDeliveryMan(toUserContact(commande.getLivreur(), "delivery_man"));
            }
        }
        return response;
    }

    private void assertCanReadContacts(User viewer, Commande commande) {
        UserRole role = viewer.getRole();
        if (role == UserRole.ADMIN) return;
        if (role == UserRole.CLIENT && sameUser(viewer, commande.getClient())) return;
        if (role == UserRole.LIVREUR && (sameUser(viewer, commande.getLivreur())
                || offreLivraisonRepository.findByCommandeIdAndLivreurIdAndStatut(
                        commande.getId(), viewer.getId(), StatutOffreLivraison.PROPOSEE).isPresent())) return;
        if ((role == UserRole.RESTAURANT_OWNER || role == UserRole.RESTAURANT_STAFF)
                && commande.getRestaurant() != null && sameUser(viewer, commande.getRestaurant().getOwner())) return;
        throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                "Vous n'êtes pas autorisé à consulter les contacts de cette commande");
    }

    private boolean sameUser(User left, User right) {
        return left != null && right != null && left.getId() != null && left.getId().equals(right.getId());
    }

    private CommandeContactDTO toUserContact(User user, String participantType) {
        if (user == null) return null;
        CommandeContactDTO contact = new CommandeContactDTO();
        contact.setId(user.getId());
        contact.setChatUserId(user.getId());
        contact.setParticipantType(participantType);
        contact.setName(fullName(user));
        contact.setPhone(user.getTelephone());
        contact.setAvatar(user.getAvatar());
        return contact;
    }

    private CommandeContactDTO toSellerContact(Restaurant restaurant) {
        if (restaurant == null) return null;
        CommandeContactDTO contact = new CommandeContactDTO();
        contact.setId(restaurant.getId());
        contact.setChatUserId(restaurant.getId());
        contact.setParticipantType("restaurant");
        contact.setName(restaurant.getNom());
        contact.setAvatar(restaurant.getLogoUrl());
        contact.setRestaurantId(restaurant.getId());
        if (restaurant.getOwner() != null) {
            contact.setOwnerUserId(restaurant.getOwner().getId());
            contact.setPhone(restaurant.getOwner().getTelephone());
        }
        return contact;
    }

    private String fullName(User user) {
        return ((user.getPrenom() == null ? "" : user.getPrenom()) + " "
                + (user.getNom() == null ? "" : user.getNom())).trim();
    }
}
