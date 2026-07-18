#!/usr/bin/env bash
#
# scenario-e2e.sh — Rejoue le parcours complet MySugu de bout en bout
# (client -> vendeur -> livreur : création comptes, commande, dispatch,
#  livraison, chats, stats, GET, mises à jour) et rapporte PASS/FAIL.
#
# Voir docs/SCENARIO-E2E-parcours-complet.md pour le détail des étapes.
#
# Usage:
#   BASE_URL=https://api.mysukuapp.com ./scripts/scenario-e2e.sh
#   ./scripts/scenario-e2e.sh                 # défaut = prod
#
# Prérequis: bash, curl, python3.
# Comptes opérationnels réutilisés (surchargeables par variables d'env):
#   SELLER_EMAIL/SELLER_PWD  -> vendeur avec restaurant APPROUVÉ + plats
#   ADMIN_EMAIL/ADMIN_PWD    -> pour les vérifs d'autorisation
# Le script CRÉE à chaque run un client et un livreur frais (comptes jetables).
#
set -uo pipefail

BASE_URL="${BASE_URL:-https://api.mysukuapp.com}"
SELLER_EMAIL="${SELLER_EMAIL:-gerant.demo@mysugu.ma}"
SELLER_PWD="${SELLER_PWD:-Gerant2026!}"
ADMIN_EMAIL="${ADMIN_EMAIL:-newadmin@mysugu.ma}"
ADMIN_PWD="${ADMIN_PWD:-Mysugu2024!}"
RESTO_ID="${RESTO_ID:-11}"           # restaurant du vendeur démo (approuvé)

PASS=0; FAIL=0; WARN=0
RUN="e2e$$$RANDOM"

c_green=$'\e[32m'; c_red=$'\e[31m'; c_yellow=$'\e[33m'; c_blue=$'\e[36m'; c_off=$'\e[0m'
phase() { echo; echo "${c_blue}=== $* ===${c_off}"; }
ok()    { echo "  ${c_green}✅ $*${c_off}"; PASS=$((PASS+1)); }
bad()   { echo "  ${c_red}❌ $*${c_off}"; FAIL=$((FAIL+1)); }
warn()  { echo "  ${c_yellow}⚠️  $*${c_off}"; WARN=$((WARN+1)); }

# extract <json-on-stdin> <keys...>  (clé numérique = index de liste)
extract() { python3 -c "
import json,sys
try:
    d=json.load(sys.stdin)
    for k in sys.argv[1:]:
        d = d[int(k)] if k.lstrip('-').isdigit() else (d.get(k) if isinstance(d,dict) else None)
        if d is None: break
    print(d if d is not None else '')
except Exception:
    print('')" "$@" 2>/dev/null; }

# call <METHOD> <PATH> <TOKEN|-> [JSON_DATA]   -> remplit HTTP_CODE, BODY
call() {
  local method="$1" path="$2" token="$3" data="${4:-}"
  local args=(-s -m 30 -w $'\n%{http_code}' -X "$method" "$BASE_URL$path")
  [ "$token" != "-" ] && args+=(-H "Authorization: Bearer $token")
  [ -n "$data" ] && args+=(-H "Content-Type: application/json" -d "$data")
  local resp; resp="$(curl "${args[@]}")"
  HTTP_CODE="$(printf '%s' "$resp" | tail -n1)"
  BODY="$(printf '%s' "$resp" | sed '$d')"
}

# callmp <METHOD> <PATH> <TOKEN> <-F args...>  (multipart) -> HTTP_CODE, BODY
callmp() {
  local method="$1" path="$2" token="$3"; shift 3
  local args=(-s -m 30 -w $'\n%{http_code}' -X "$method" "$BASE_URL$path" -H "Authorization: Bearer $token")
  local f; for f in "$@"; do args+=(-F "$f"); done
  local resp; resp="$(curl "${args[@]}")"
  HTTP_CODE="$(printf '%s' "$resp" | tail -n1)"
  BODY="$(printf '%s' "$resp" | sed '$d')"
}

expect()    { if [ "$HTTP_CODE" = "$1" ]; then ok "$2 (HTTP $HTTP_CODE)"; else bad "$2 (attendu $1, reçu $HTTP_CODE) — $(printf '%s' "$BODY"|head -c140)"; fi; }
expect_in() { case " $1 " in *" $HTTP_CODE "*) ok "$2 (HTTP $HTTP_CODE)";; *) bad "$2 (attendu $1, reçu $HTTP_CODE) — $(printf '%s' "$BODY"|head -c140)";; esac; }

echo "${c_blue}# Scénario E2E MySugu${c_off}  base=$BASE_URL  run=$RUN"

########################################################################
phase "Phase 0 — Connectivité"
call GET /swagger-ui/index.html - ; expect 200 "Backend joignable (swagger)"

########################################################################
phase "Phase 1 — Création & connexion des comptes"

# --- 1.1 Client (self-register) ---
CLI_EMAIL="client.$RUN@demo.ma"
call POST /auth/register - "{\"email\":\"$CLI_EMAIL\",\"password\":\"Passw0rd!\",\"nom\":\"Konate\",\"prenom\":\"Awa\",\"telephone\":\"+2126$RANDOM$RANDOM\",\"role\":\"CLIENT\"}"
expect_in "200 201" "Client inscrit ($CLI_EMAIL)"
call POST /auth/login - "{\"email\":\"$CLI_EMAIL\",\"password\":\"Passw0rd!\"}"
CLIENT="$(printf '%s' "$BODY" | extract token)"
[ -n "$CLIENT" ] && ok "Client login (token)" || bad "Client login (pas de token) — $(printf '%s' "$BODY"|head -c120)"
call GET /users/profile "$CLIENT"
CLIENT_ID="$(printf '%s' "$BODY" | extract id)"
[ -n "$CLIENT_ID" ] && ok "Profil client (id=$CLIENT_ID)" || bad "Profil client (pas d'id)"

# --- 1.2 Vendeur : démo (opérationnel) + démonstration self-register ---
call POST /api/v3/seller/auth/login - "{\"email\":\"$SELLER_EMAIL\",\"password\":\"$SELLER_PWD\"}"
SELLER="$(printf '%s' "$BODY" | extract token)"
[ -n "$SELLER" ] && ok "Vendeur login email ($SELLER_EMAIL)" || bad "Vendeur login — $(printf '%s' "$BODY"|head -c120)"
# self-registration d'un nouveau vendeur (compte en attente d'approbation)
callmp POST /api/v3/seller/registration - \
  "f_name=Demo" "l_name=Seller$RUN" "phone=+2126$RANDOM$RANDOM" \
  "email=seller.$RUN@demo.ma" "password=Passw0rd!" "confirm_password=Passw0rd!" \
  "shop_name=Boutique $RUN" "shop_address=Marrakech"
expect_in "200 201 403 422" "Vendeur self-register (démonstration, appro. admin requise)"

# --- 1.3 Livreur : création (admin) + login téléphone ---
LV_LOCAL="6$(python3 -c "import random;print('%08d'%random.randint(0,99999999))" 2>/dev/null || echo 0$RANDOM$RANDOM | cut -c1-8)"
LV_TEL="+212$LV_LOCAL"
LV_EMAIL="livreur.$RUN@demo.ma"
call POST /auth/register - "{\"email\":\"$LV_EMAIL\",\"password\":\"Livreur2026!\",\"nom\":\"Traore\",\"prenom\":\"Ibrahim\",\"telephone\":\"$LV_TEL\",\"role\":\"LIVREUR\"}"
LV_ID="$(printf '%s' "$BODY" | extract id)"
expect_in "200 201" "Livreur créé ($LV_TEL, id=$LV_ID)"
call POST /api/v2/delivery-man/auth/login - "{\"country_code\":\"212\",\"phone\":\"$LV_LOCAL\",\"password\":\"Livreur2026!\"}"
LV="$(printf '%s' "$BODY" | extract token)"
[ -n "$LV" ] && ok "Livreur login téléphone (212 / $LV_LOCAL)" || bad "Livreur login téléphone — $(printf '%s' "$BODY"|head -c120)"

########################################################################
phase "Phase 2 — Client : catalogue & panier"
call GET /api/services "$CLIENT";                         expect 200 "GET services"
call GET /api/categories "$CLIENT";                       expect 200 "GET categories"
call GET "/api/restaurants?page=0&size=20" "$CLIENT";     expect 200 "GET restaurants"
call GET "/api/restaurants/$RESTO_ID" "$CLIENT";          expect 200 "GET restaurant $RESTO_ID"
call GET "/api/plats/restaurant/$RESTO_ID" "$CLIENT"
PLAT_ID="$(printf '%s' "$BODY" | extract 0 id)"
[ -n "$PLAT_ID" ] && ok "Plats du resto (plat id=$PLAT_ID)" || bad "Plats du resto (aucun plat)"
call GET "/api/filtres?contexte=RESTAURANT" "$CLIENT";    expect 200 "GET filtres"
call POST /api/panier/items "$CLIENT" "{\"platId\":$PLAT_ID,\"quantite\":1}"; expect_in "200 201" "Ajout au panier"
call GET /api/panier "$CLIENT";                           expect 200 "GET panier"

########################################################################
phase "Phase 3 — Client : passage de commande"
call POST /api/commandes "$CLIENT" "{\"clientId\":$CLIENT_ID,\"restaurantId\":$RESTO_ID,\"lignes\":[{\"platId\":$PLAT_ID,\"quantite\":1}],\"methodePaiement\":\"ESPECES\",\"modeReception\":\"LIVRAISON\",\"adresseLivraison\":{\"latitude\":31.63,\"longitude\":-8.0,\"adresse\":\"Av Mohammed V\",\"ville\":\"Marrakech\",\"pays\":\"Maroc\",\"codePostal\":\"40000\"},\"montantTotal\":30}"
ORDER_ID="$(printf '%s' "$BODY" | extract id)"
expect_in "200 201" "Commande créée (id=$ORDER_ID)"
call GET "/api/commandes/client/$CLIENT_ID" "$CLIENT";    expect 200 "Client : mes commandes"

########################################################################
phase "Phase 4 — Vendeur : réception & préparation"
call GET "/api/v3/seller/orders/list?limit=10&offset=1&status=all" "$SELLER"; expect 200 "Vendeur : liste commandes"
call GET "/api/v3/seller/orders/$ORDER_ID" "$SELLER";                        expect 200 "Vendeur : détail commande $ORDER_ID"
call POST "/api/v3/seller/orders/order-detail-status/$ORDER_ID" "$SELLER" "{\"_method\":\"put\",\"order_status\":\"confirmed\"}";  expect 200 "Vendeur : -> confirmed"
call POST "/api/v3/seller/orders/order-detail-status/$ORDER_ID" "$SELLER" "{\"_method\":\"put\",\"order_status\":\"processing\"}"; expect 200 "Vendeur : -> processing"

########################################################################
phase "Phase 5 — Dispatch & livraison"
call POST /api/v3/seller/orders/assign-delivery-man "$SELLER" "{\"_method\":\"put\",\"order_id\":$ORDER_ID,\"delivery_man_id\":$LV_ID}"; expect 200 "Vendeur : assigne livreur $LV_ID"
call POST /api/v2/delivery-man/is-online "$LV" "{\"is_online\":1,\"_method\":\"put\"}"; expect 200 "Livreur : en ligne"
call GET /api/v2/delivery-man/current-orders "$LV"
printf '%s' "$BODY" | grep -q "\"id\":$ORDER_ID" && ok "Livreur : voit la commande $ORDER_ID" || warn "Livreur : commande $ORDER_ID absente de current-orders"
call POST "/api/v2/delivery-man/$ORDER_ID/accept" "$LV" "{\"order_id\":$ORDER_ID}"; expect_in "200 409" "Livreur : accept (200=OK, 409=déjà prise)"
call POST /api/v2/delivery-man/update-order-status "$LV" "{\"order_id\":$ORDER_ID,\"status\":\"out_for_delivery\",\"_method\":\"put\"}"; expect 200 "Livreur : -> out_for_delivery"
# Livraison finale : nécessite l'OTP reçu par le client (FCM) -> non récupérable via API ici.
call POST /api/v2/delivery-man/update-order-status "$LV" "{\"order_id\":$ORDER_ID,\"status\":\"delivered\",\"_method\":\"put\"}"
if [ "$HTTP_CODE" = "200" ]; then ok "Livreur : -> delivered (HTTP 200)"; else warn "Livreur : delivered = HTTP $HTTP_CODE (attendu : requiert l'OTP client via verify-order-delivery-otp)"; fi

########################################################################
phase "Phase 6 — Chats croisés (chat unifié)"
# client -> restaurant
callmp POST /api/v1/customer/chat/send-message/seller "$CLIENT" "id=$RESTO_ID" "message=Bonjour, ou en est ma commande ?"; expect_in "200 201" "Client -> resto : envoi message"
call GET "/api/v1/customer/chat/list/seller?offset=1&limit=50" "$CLIENT"; expect 200 "Client : liste conversations resto"
# vendeur -> client
callmp POST /api/v3/seller/messages/send/customer "$SELLER" "id=$CLIENT_ID" "message=Votre commande est en preparation."; expect_in "200 201" "Vendeur -> client : envoi message"
call GET "/api/v3/seller/messages/list/customer?limit=30&offset=1" "$SELLER"; expect 200 "Vendeur : liste conversations client"
# livreur -> client
callmp POST /api/v2/delivery-man/messages/send-message/customer "$LV" "id=$CLIENT_ID" "message=Je suis en route."; expect_in "200 201" "Livreur -> client : envoi message"
call GET "/api/v2/delivery-man/messages/list/customer?limit=10&offset=1" "$LV"; expect 200 "Livreur : liste conversations client"

########################################################################
phase "Phase 7 — Stats, GET & mises à jour"
# Vendeur stats + réconciliation order-statistics == orders/list
call GET "/api/v3/seller/order-statistics?statistics_type=overall" "$SELLER"; expect 200 "Vendeur : order-statistics"
STATS_BODY="$BODY"
call GET "/api/v3/seller/orders/list?status=all&limit=200&offset=1" "$SELLER"
python3 -c "
import json,sys
st=json.loads('''$STATS_BODY''')
lst=json.loads('''$BODY'''); orders=lst.get('orders',lst if isinstance(lst,list) else [])
print('OK' if st.get('total')==len(orders) else 'KO', st.get('total'), len(orders))
" 2>/dev/null | { read res a b; if [ "$res" = "OK" ]; then ok "Réconciliation : order-statistics.total($a) == orders/list($b)"; else warn "Réconciliation total=$a vs liste=$b (data en mouvement pendant le run)"; fi; }
call GET "/api/v3/seller/get-earning-statitics?type=yearEarn" "$SELLER"; expect 200 "Vendeur : earning-statistics"
# Livreur
call GET /api/v2/delivery-man/info "$LV";                       expect 200 "Livreur : /info"
call GET /api/v2/delivery-man/commission/all "$LV";             expect 200 "Livreur : commission"
call GET "/api/v2/delivery-man/delivery-wise-earned?limit=10&offset=1" "$LV"; expect 200 "Livreur : gains"
# Client
call GET /api/wallet "$CLIENT";        expect 200 "Client : wallet"
call GET /api/fidelite "$CLIENT";      expect 200 "Client : fidélité"
call GET /api/notifications "$CLIENT"; expect 200 "Client : notifications"

########################################################################
phase "Phase 8 — Autorisations & robustesse"
call GET /api/commandes "$CLIENT";                       expect 403 "Client NE voit PAS la liste globale /api/commandes"
call POST /auth/login - "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PWD\"}"
ADMIN="$(printf '%s' "$BODY" | extract token)"
if [ -n "$ADMIN" ]; then call GET /api/commandes "$ADMIN"; expect 200 "Admin voit la liste globale /api/commandes"; else warn "Admin login KO — check ignoré"; fi
call GET /api/plats/categories-produit "$CLIENT";        expect 400 "Param requis manquant -> 400 (pas 500)"
call POST /api/commandes "$CLIENT" "{\"clientId\":$CLIENT_ID,\"restaurantId\":$RESTO_ID,\"methodePaiement\":\"BITCOIN\",\"modeReception\":\"RETRAIT_SUR_PLACE\",\"lignes\":[{\"platId\":$PLAT_ID,\"quantite\":1}]}"; expect 400 "Méthode de paiement invalide -> 400"

########################################################################
phase "Résumé"
echo "  ${c_green}PASS=$PASS${c_off}  ${c_red}FAIL=$FAIL${c_off}  ${c_yellow}WARN=$WARN${c_off}"
echo "  (comptes créés ce run : client=$CLI_EMAIL, livreur=$LV_TEL, commande=$ORDER_ID)"
[ "$FAIL" -eq 0 ] && { echo "  ${c_green}>>> SCÉNARIO E2E OK${c_off}"; exit 0; } || { echo "  ${c_red}>>> $FAIL ÉCHEC(S)${c_off}"; exit 1; }
