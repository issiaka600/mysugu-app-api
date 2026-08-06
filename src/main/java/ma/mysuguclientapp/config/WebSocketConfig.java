package ma.mysuguclientapp.config;

import lombok.RequiredArgsConstructor;
import ma.mysuguclientapp.config.security.JwtTokenProvider;
import ma.mysuguclientapp.entities.Commande;
import ma.mysuguclientapp.entities.User;
import ma.mysuguclientapp.enumerations.UserRole;
import ma.mysuguclientapp.repositories.CommandeRepository;
import ma.mysuguclientapp.repositories.UserRepository;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.List;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserRepository userRepository;
    private final CommandeRepository commandeRepository;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic", "/queue");
        config.setApplicationDestinationPrefixes("/app");
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor == null || accessor.getCommand() == null) return message;

                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    authenticate(accessor);
                } else if (accessor.getUser() == null) {
                    throw new AccessDeniedException("Connexion WebSocket non authentifiée");
                }

                if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                    authorizeTrackingSubscription(accessor);
                }
                return message;
            }
        });
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader("Authorization");
        String token = header != null && header.startsWith("Bearer ") ? header.substring(7) : header;
        if (token == null || token.isBlank() || !jwtTokenProvider.validateToken(token)) {
            throw new AccessDeniedException("JWT WebSocket invalide ou absent");
        }
        String email = jwtTokenProvider.getEmailFromToken(token);
        User user = userRepository.findByEmail(email)
                .filter(u -> Boolean.TRUE.equals(u.getIsActive()))
                .orElseThrow(() -> new AccessDeniedException("Utilisateur WebSocket introuvable ou inactif"));
        accessor.setUser(new UsernamePasswordAuthenticationToken(
                user.getEmail(), null, List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole().name()))));
    }

    private void authorizeTrackingSubscription(StompHeaderAccessor accessor) {
        String destination = accessor.getDestination();
        if (destination == null || !destination.startsWith("/topic/tracking/")) return;

        Long commandeId;
        try {
            commandeId = Long.valueOf(destination.substring("/topic/tracking/".length()));
        } catch (NumberFormatException e) {
            throw new AccessDeniedException("Destination de suivi invalide");
        }
        User user = userRepository.findByEmail(accessor.getUser().getName())
                .orElseThrow(() -> new AccessDeniedException("Utilisateur WebSocket introuvable"));
        Commande commande = commandeRepository.findById(commandeId)
                .orElseThrow(() -> new AccessDeniedException("Commande introuvable"));
        boolean autorise = user.getRole() == UserRole.ADMIN
                || (commande.getClient() != null && commande.getClient().getId().equals(user.getId()))
                || (commande.getLivreur() != null && commande.getLivreur().getId().equals(user.getId()))
                || (commande.getRestaurant() != null && commande.getRestaurant().getOwner() != null
                        && commande.getRestaurant().getOwner().getId().equals(user.getId()));
        if (!autorise) {
            throw new AccessDeniedException("Accès au suivi de cette commande refusé");
        }
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }
}
