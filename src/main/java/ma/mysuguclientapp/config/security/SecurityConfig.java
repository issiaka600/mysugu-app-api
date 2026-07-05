package ma.mysuguclientapp.config.security;


import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final CustomUserDetailsService userDetailsService;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    // Endpoints non protégés (login/register, docs, health, etc.) — Ant patterns
    private static final List<String> SECURITY_WHITELIST = List.of(
            "/api/auth/**",
            "/v3/api-docs",
            "/v3/api-docs/**",
            "/v3/api-docs.yaml",
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/actuator/health"
    );


    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> 
                    session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Routes publiques
//                        .requestMatchers("/api/users/register", "/api/users/login").permitAll()
                        .requestMatchers("/auth/**", "/v3/api-docs",
                                "/v3/api-docs/**",
                                "/v3/api-docs.yaml",
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/actuator/health",
                                "/notification-test.html",
                                "/firebase-messaging-sw.js",
                                "/api/integrations/tiktak/**",
                                // Legacy shim livreur (app Tiktak/moso) : auth publique + config au splash
                                "/api/v2/delivery-man/auth/**",
                                "/api/v1/config"
                        ).permitAll()

                        // WebSocket endpoint
                        .requestMatchers("/ws/**").permitAll()

                        // Stripe webhooks (sécurisé par vérification de signature Stripe, pas JWT)
                        .requestMatchers(HttpMethod.POST, "/api/stripe/webhook").permitAll()

                        // Formulaire de contact public — soumission ouverte, gestion admin
                        .requestMatchers(HttpMethod.POST, "/api/contact").permitAll()
                        .requestMatchers("/api/admin/contact-messages/**").hasRole("ADMIN")

                        // Routes publiques en lecture seule
                        .requestMatchers(HttpMethod.GET, "/api/categories/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/services/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/restaurants/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/plats/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/files/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/promotions/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/menus/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/zones-deploiement/actives").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/filtres").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/files/upload").authenticated()

                        // Auth enhanced (email verification, forgot password — public)
                        .requestMatchers("/api/auth/verify-email", "/api/auth/forgot-password",
                                "/api/auth/reset-password", "/api/auth/refresh").permitAll()

                        // Routes admin uniquement
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/users/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/statistiques/**").hasRole("ADMIN")
                        .requestMatchers("/api/codes-promo/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/promotions").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/promotions/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/promotions/**").hasRole("ADMIN")

                        // Onboarding restaurateur (app mobile) : inscription publique, reste authentifié
                        .requestMatchers(HttpMethod.POST, "/api/restaurateur/register").permitAll()
                        .requestMatchers("/api/restaurateur/**").hasAnyRole("RESTAURANT_OWNER", "ADMIN")

                        // Routes propriétaires de restaurant
                        .requestMatchers(HttpMethod.POST, "/api/restaurants").hasAnyRole("RESTAURANT_OWNER", "ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/restaurants/**").hasAnyRole("RESTAURANT_OWNER", "ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/restaurants/**").hasAnyRole("RESTAURANT_OWNER", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/restaurants/**").hasAnyRole("RESTAURANT_OWNER", "ADMIN")

                        .requestMatchers(HttpMethod.POST, "/api/plats").hasAnyRole("RESTAURANT_OWNER", "ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/plats/**").hasAnyRole("RESTAURANT_OWNER", "ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/plats/**").hasAnyRole("RESTAURANT_OWNER", "ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/plats/**").hasAnyRole("RESTAURANT_OWNER", "ADMIN")

                        .requestMatchers("/api/menus/**").hasAnyRole("RESTAURANT_OWNER", "ADMIN")
                        // Notification hors-zone — soumission publique (client sans compte)
                        .requestMatchers(HttpMethod.POST, "/api/zones-deploiement/notification").permitAll()
                        .requestMatchers("/api/zones-deploiement/**").hasRole("ADMIN")
                        .requestMatchers("/api/restaurant-dashboard/**").hasAnyRole("RESTAURANT_OWNER", "ADMIN")

                        // Routes catégories (admin)
                        .requestMatchers(HttpMethod.POST, "/api/categories").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/categories/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/categories/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/services").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.PUT, "/api/services/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.DELETE, "/api/services/**").hasRole("ADMIN")

                        // Routes commandes
                        .requestMatchers(HttpMethod.POST, "/api/commandes").hasAnyRole("CLIENT", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/commandes/client/**").hasAnyRole("CLIENT", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/commandes/restaurant/**").hasAnyRole("RESTAURANT_OWNER", "ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/commandes/livreur/**").hasAnyRole("LIVREUR", "ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/commandes/{commandeId}/status").hasAnyRole("RESTAURANT_OWNER", "LIVREUR", "ADMIN")
                        .requestMatchers(HttpMethod.PATCH, "/api/commandes/{commandeId}/assign-livreur/**").hasAnyRole("ADMIN", "RESTAURANT_OWNER")

                        // Panier (client)
                        .requestMatchers("/api/panier/**").hasRole("CLIENT")

                        // Wallet (client + admin)
                        .requestMatchers("/api/wallet/**").hasAnyRole("CLIENT", "ADMIN")

                        // Fidelité (client + admin)
                        .requestMatchers("/api/fidelite/**").hasAnyRole("CLIENT", "ADMIN")

                        // Avis (client soumettre, public lire)
                        .requestMatchers(HttpMethod.POST, "/api/avis").hasRole("CLIENT")
                        .requestMatchers(HttpMethod.GET, "/api/avis/**").permitAll()
                        .requestMatchers("/api/avis/*/moderer").hasRole("ADMIN")

                        // Favoris (client)
                        .requestMatchers("/api/favoris/**").hasRole("CLIENT")

                        // Notifications (authenticated)
                        .requestMatchers("/api/notifications/**").authenticated()

                        // Device tokens FCM (authenticated)
                        .requestMatchers("/api/device-tokens/**").authenticated()

                        // Disponibilité livreur (livreur uniquement)
                        .requestMatchers("/api/users/livreur/disponibilite").hasRole("LIVREUR")

                        // Livreur gains
                        .requestMatchers("/api/livreurs/gains/**").hasAnyRole("LIVREUR", "ADMIN")

                        // Codes promo validation (client)
                        .requestMatchers("/api/codes-promo/valider").hasAnyRole("CLIENT", "ADMIN")

                        // Caisse livreur
                        .requestMatchers("/api/caisse/ma-position").hasRole("LIVREUR")
                        .requestMatchers("/api/caisse/mon-historique").hasRole("LIVREUR")
                        .requestMatchers("/api/caisse/info-commande/**").hasRole("LIVREUR")
                        .requestMatchers(HttpMethod.POST, "/api/caisse/paiement-restaurant/**").hasRole("LIVREUR")
                        .requestMatchers("/api/caisse/bord-admin").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/caisse/avance/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/caisse/reconcilier").hasRole("ADMIN")
                        .requestMatchers("/api/caisse/parametres").hasRole("ADMIN")
                        .requestMatchers("/api/caisse/*/position").hasRole("ADMIN")
                        .requestMatchers("/api/caisse/*/historique").hasRole("ADMIN")
                        .requestMatchers("/api/caisse/*/plafond").hasRole("ADMIN")

                        // Legacy shim livreur (app Tiktak/moso) : tout le reste du namespace exige LIVREUR.
                        // (auth/** et /api/v1/config sont déjà en permitAll plus haut.)
                        .requestMatchers("/api/v2/delivery-man/**").hasRole("LIVREUR")

                        // Facturation restaurant
                        .requestMatchers("/api/facturation-restaurant/**").hasAnyRole("RESTAURANT_OWNER", "ADMIN")
                        .requestMatchers(HttpMethod.POST, "/api/facturation-restaurant/*/payer").hasRole("ADMIN")
                        .requestMatchers("/api/facturation-restaurant/*/dettes/**").hasRole("ADMIN")

                        // Toutes les autres routes nécessitent une authentification
                        .anyRequest().authenticated()
                )
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthenticationProvider authenticationProvider() {
        DaoAuthenticationProvider authProvider = new DaoAuthenticationProvider(userDetailsService);
        authProvider.setPasswordEncoder(passwordEncoder());
        return authProvider;
    }


    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) {
        return config.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("*"));
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("Authorization"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
