#!/usr/bin/env bash
#
# Charge un jeu de données de démonstration réaliste.
#
# Tout passe par l'API PUBLIQUE, jamais par SQL ni mongosh. C'est un choix délibéré :
# le script traverse la même validation, les mêmes contrôles de rôle et le même moteur
# d'affectation qu'un vrai client. Un contrat rompu échoue donc ici plutôt qu'en pleine
# démonstration. Il fait ainsi office de test de bout en bout de la plateforme.
#
# Usage :  ./scripts/seed-demo-data.sh [url-de-base]
#          url-de-base vaut par défaut la gateway sur http://localhost:8080
#
# Réexécutable sans risque : les entrepôts, catégories et produits déjà présents sont
# détectés et laissés intacts plutôt que dupliqués.
set -uo pipefail

BASE="${1:-http://localhost:8080}"
ENV_FILE="$(dirname "$0")/../.env"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

created=0; skipped=0; failed=0

info()  { printf '  %s\n' "$1"; }
step()  { printf '\n\033[1m%s\033[0m\n' "$1"; }
ok()    { created=$((created+1)); printf '  \033[32m+\033[0m %s\n' "$1"; }
same()  { skipped=$((skipped+1)); printf '  \033[33m=\033[0m %s (déjà présent)\n' "$1"; }
bad()   { failed=$((failed+1)); printf '  \033[31m!\033[0m %s -> %s\n' "$1" "$2"; }

jfield() { grep -o "\"$1\"[[:space:]]*:[[:space:]]*\"[^\"]*\"" | head -1 | sed 's/.*:[[:space:]]*"//; s/"$//'; }

# $1=méthode $2=chemin $3=corps $4=jeton -> renseigne CODE et BODY
api() {
  local method="$1" path="$2" body="$3" token="${4:-}"
  local args=(-s -o "$TMP/body" -w '%{http_code}' -X "$method" "$BASE$path")
  # Le corps transite par un fichier, jamais par la ligne de commande. Sous Windows,
  # MSYS réencode les arguments dans la page de codes ANSI : l'UTF-8 y est détruit,
  # « Dépôt » arrive illisible et Jackson rejette la requête en 400.
  if [ -n "$body" ]; then
    printf '%s' "$body" > "$TMP/request.json"
    args+=(-H 'Content-Type: application/json' --data-binary "@$TMP/request.json")
  fi
  [ -n "$token" ] && args+=(-H "Authorization: Bearer $token")
  CODE=$(curl "${args[@]}")
  BODY=$(cat "$TMP/body")
}

# --- vérifications préalables -----------------------------------------------

if [ ! -f "$ENV_FILE" ]; then
  echo "Aucun fichier .env à la racine du dépôt. Copiez d'abord .env.example vers .env." >&2
  exit 1
fi
set -a; . "$ENV_FILE"; set +a

step "Vérification que la plateforme répond"
api GET /actuator/health "" ""
if [ "$CODE" != "200" ]; then
  echo "  La gateway sur $BASE ne répond pas (HTTP $CODE)." >&2
  echo "  Démarrez la plateforme avec : docker compose up -d" >&2
  exit 1
fi
info "gateway joignable sur $BASE"

step "Connexion avec le compte administrateur initial"
api POST /api/v1/auth/login \
  "{\"email\":\"$BOOTSTRAP_ADMIN_EMAIL\",\"password\":\"$BOOTSTRAP_ADMIN_PASSWORD\"}" ""
if [ "$CODE" != "200" ]; then
  echo "  Connexion impossible avec $BOOTSTRAP_ADMIN_EMAIL (HTTP $CODE)." >&2
  echo "  Vérifiez BOOTSTRAP_ADMIN_* dans .env, et que auth-service a bien créé le compte." >&2
  exit 1
fi
ADMIN=$(printf '%s' "$BODY" | jfield accessToken)
info "authentifié en tant que $BOOTSTRAP_ADMIN_EMAIL"

# --- entrepôts --------------------------------------------------------------
#
# Quatre villes marocaines réelles, avec leurs coordonnées véritables. La géographie
# compte ici : le moteur d'affectation classe les entrepôts par distance orthodromique,
# une démonstration n'est donc convaincante que si les distances peuvent être vérifiées
# sur une carte.

declare -A WAREHOUSE_ID

seed_warehouse() {
  local code="$1" name="$2" line1="$3" city="$4" postal="$5" lat="$6" lon="$7"

  api GET "/api/v1/warehouses?page=0&size=100" "" "$ADMIN"
  local existing
  existing=$(printf '%s' "$BODY" | grep -o "\"id\":\"[^\"]*\",\"code\":\"$code\"" | head -1 \
             | sed 's/"id":"//; s/","code.*//')
  if [ -n "$existing" ]; then
    WAREHOUSE_ID["$code"]="$existing"
    same "entrepôt $code"
    return
  fi

  api POST /api/v1/warehouses "{\"code\":\"$code\",\"name\":\"$name\",\"address\":{\"line1\":\"$line1\",\"city\":\"$city\",\"postalCode\":\"$postal\",\"country\":\"MA\"},\"latitude\":$lat,\"longitude\":$lon}" "$ADMIN"
  if [ "$CODE" = "201" ]; then
    WAREHOUSE_ID["$code"]=$(printf '%s' "$BODY" | jfield id)
    ok "entrepôt $code — $city"
  else
    bad "entrepôt $code" "HTTP $CODE"
  fi
}

step "Entrepôts"
seed_warehouse WH-CASA-01 "Hub Casablanca"  "Zone industrielle Sidi Bernoussi" Casablanca 20600 33.573100  -7.589800
seed_warehouse WH-RABA-01 "Dépôt Rabat"     "Zone industrielle Takaddoum"      Rabat      10000 34.020900  -6.841600
seed_warehouse WH-TANG-01 "Port Tanger Med" "Zone franche Tanger Med"          Tanger     90000 35.759500  -5.833900
seed_warehouse WH-MARR-01 "Dépôt Marrakech" "Quartier industriel Sidi Ghanem"  Marrakech  40000 31.629800  -7.981000

# --- catégories du catalogue ------------------------------------------------
#
# Chaque catégorie déclare les attributs techniques que ses produits doivent porter.
# C'est tout l'intérêt d'héberger le catalogue dans MongoDB : un transpalette et un
# carton n'ont rien en commun au-delà d'un prix, et le schéma qui l'exprime est une
# donnée, pas du code.

declare -A CATEGORY_ID

seed_category() {
  local slug="$1" name="$2" parent="$3" schema="$4"

  api GET "/api/v1/categories" "" ""
  local existing
  existing=$(printf '%s' "$BODY" | grep -o "\"id\":\"[^\"]*\",\"name\":\"[^\"]*\",\"slug\":\"$slug\"" \
             | head -1 | sed 's/"id":"//; s/","name.*//')
  if [ -n "$existing" ]; then
    CATEGORY_ID["$slug"]="$existing"
    same "catégorie $slug"
    return
  fi

  local parent_json=""
  [ -n "$parent" ] && parent_json="\"parentId\":\"${CATEGORY_ID[$parent]}\","

  api POST /api/v1/categories "{\"name\":\"$name\",\"slug\":\"$slug\",${parent_json}\"attributeSchema\":$schema}" "$ADMIN"
  if [ "$CODE" = "201" ]; then
    CATEGORY_ID["$slug"]=$(printf '%s' "$BODY" | jfield id)
    ok "catégorie $slug"
  else
    bad "catégorie $slug" "HTTP $CODE"
  fi
}

step "Catégories et leurs schémas d'attributs"
seed_category handling "Matériel de manutention" "" '[]'
seed_category pallet-trucks "Transpalettes" handling '[
  {"key":"capacityKg","label":"Capacité (kg)","type":"NUMBER","required":true},
  {"key":"forkLengthMm","label":"Longueur de fourches (mm)","type":"NUMBER","required":true},
  {"key":"wheelMaterial","label":"Matériau des roues","type":"STRING","required":false},
  {"key":"electric","label":"Électrique","type":"BOOLEAN","required":false}]'
seed_category shelving "Rayonnages" handling '[
  {"key":"heightMm","label":"Hauteur (mm)","type":"NUMBER","required":true},
  {"key":"shelfCount","label":"Niveaux","type":"NUMBER","required":true},
  {"key":"loadPerShelfKg","label":"Charge par niveau (kg)","type":"NUMBER","required":false}]'
seed_category packaging "Emballage" "" '[]'
seed_category cartons "Cartons" packaging '[
  {"key":"flute","label":"Cannelure","type":"STRING","required":true},
  {"key":"burstStrengthKpa","label":"Résistance à l’éclatement (kPa)","type":"NUMBER","required":true},
  {"key":"doubleWall","label":"Double cannelure","type":"BOOLEAN","required":false}]'
seed_category stretch-film "Film étirable" packaging '[
  {"key":"widthMm","label":"Largeur (mm)","type":"NUMBER","required":true},
  {"key":"thicknessUm","label":"Épaisseur (um)","type":"NUMBER","required":true}]'

# --- produits ---------------------------------------------------------------

declare -A PRODUCT_ID

seed_product() {
  local sku="$1" name="$2" brand="$3" category="$4" amount="$5" attributes="$6" description="$7"

  # Listing complet plutôt que ?q=$sku : la recherche texte porte sur name/brand/description,
  # pas sur le SKU. Le catalogue de démonstration tient largement dans une page.
  api GET "/api/v1/products?page=0&size=200" "" ""
  local existing
  existing=$(printf '%s' "$BODY" | grep -o "\"id\":\"[^\"]*\",\"sku\":\"$sku\"" | head -1 \
             | sed 's/"id":"//; s/","sku.*//')
  if [ -n "$existing" ]; then
    PRODUCT_ID["$sku"]="$existing"
    same "produit $sku"
    return
  fi

  api POST /api/v1/products "{\"sku\":\"$sku\",\"name\":\"$name\",\"description\":\"$description\",\"brand\":\"$brand\",\"categoryId\":\"${CATEGORY_ID[$category]}\",\"price\":{\"amount\":$amount,\"currency\":\"EUR\"},\"attributes\":$attributes}" "$ADMIN"
  if [ "$CODE" = "201" ]; then
    PRODUCT_ID["$sku"]=$(printf '%s' "$BODY" | jfield id)
    ok "produit $sku — $name"
  else
    bad "produit $sku" "HTTP $CODE ${BODY:0:120}"
  fi
}

step "Produits"
seed_product PAL-TRK-2500 "Transpalette 2500 kg" Logimove pallet-trucks 349.90 \
  '{"capacityKg":"2500","forkLengthMm":"1150","wheelMaterial":"polyuréthane","electric":"false"}' \
  "Transpalette manuel à fourches renforcées, pour la manutention quotidienne en entrepôt."
seed_product PAL-TRK-3000E "Transpalette électrique 3000 kg" Logimove pallet-trucks 2890.00 \
  '{"capacityKg":"3000","forkLengthMm":"1150","wheelMaterial":"polyuréthane","electric":"true"}' \
  "Transpalette électrique à batterie lithium, pour les sites à forte cadence."
seed_product SHL-STD-2000 "Rayonnage 2000 mm" Rackline shelving 189.50 \
  '{"heightMm":"2000","shelfCount":"5","loadPerShelfKg":"150"}' \
  "Rayonnage acier sans boulons, cinq niveaux."
seed_product SHL-HD-2500 "Rayonnage lourd 2500 mm" Rackline shelving 329.00 \
  '{"heightMm":"2500","shelfCount":"6","loadPerShelfKg":"350"}' \
  "Rayonnage renforcé pour stockage lourd."
seed_product BOX-DW-600 "Carton double cannelure 600x400x400" Cartonis cartons 2.45 \
  '{"flute":"BC","burstStrengthKpa":"1400","doubleWall":"true"}' \
  "Carton d'expédition double cannelure pour marchandises lourdes ou fragiles."
seed_product BOX-SW-400 "Carton simple cannelure 400x300x300" Cartonis cartons 1.10 \
  '{"flute":"C","burstStrengthKpa":"800","doubleWall":"false"}' \
  "Carton simple cannelure standard pour les envois légers."
seed_product FLM-STR-500 "Film étirable 500 mm" Wrapex stretch-film 12.80 \
  '{"widthMm":"500","thicknessUm":"23"}' \
  "Film étirable manuel pour le filmage de palettes."
seed_product FLM-MCH-500 "Film étirable machine 500 mm" Wrapex stretch-film 34.00 \
  '{"widthMm":"500","thicknessUm":"17"}' \
  "Film préétiré pour banderoleuses automatiques."

# --- niveaux de stock -------------------------------------------------------
#
# La répartition est construite, pas aléatoire. Elle place le moteur d'affectation dans
# chacune des situations qui méritent d'être démontrées :
#
#   PAL-TRK-2500   aucun site ne couvre 10 unités : une commande de 10 doit être FRACTIONNÉE
#   PAL-TRK-3000E  seul Tanger en détient assez pour une commande entière : les deux
#                  stratégies divergent ici, nearest fractionne tandis que single-shipment
#                  expédie un seul colis depuis un site plus lointain
#   BOX-SW-400     abondant partout : le cas banal, toujours satisfiable
#   SHL-HD-2500    stocké sous son seuil de réapprovisionnement à Marrakech, pour que le
#                  tableau de bord entrepôt ait une véritable alerte de stock faible
#   FLM-MCH-500    présent sur un seul site : l'affectation n'a aucun choix à faire

set_stock() {
  local warehouse="$1" sku="$2" quantity="$3" threshold="$4"
  api PUT /api/v1/stock "{\"warehouseId\":\"${WAREHOUSE_ID[$warehouse]}\",\"productId\":\"${PRODUCT_ID[$sku]}\",\"quantityOnHand\":$quantity,\"reorderThreshold\":$threshold}" "$ADMIN"
  if [ "$CODE" = "200" ]; then
    ok "stock $warehouse / $sku = $quantity (seuil $threshold)"
  else
    bad "stock $warehouse / $sku" "HTTP $CODE"
  fi
}

step "Niveaux de stock"
set_stock WH-CASA-01 PAL-TRK-2500  6 10
set_stock WH-RABA-01 PAL-TRK-2500  4  5
set_stock WH-TANG-01 PAL-TRK-2500 12  5
set_stock WH-MARR-01 PAL-TRK-2500  3  5

set_stock WH-CASA-01 PAL-TRK-3000E 2  3
set_stock WH-RABA-01 PAL-TRK-3000E 1  3
set_stock WH-TANG-01 PAL-TRK-3000E 8  3

set_stock WH-CASA-01 SHL-STD-2000 40 15
set_stock WH-RABA-01 SHL-STD-2000 25 15
set_stock WH-MARR-01 SHL-STD-2000 18 15

set_stock WH-CASA-01 SHL-HD-2500  20 10
set_stock WH-MARR-01 SHL-HD-2500   4 12

set_stock WH-CASA-01 BOX-DW-600  500 100
set_stock WH-TANG-01 BOX-DW-600  320 100
set_stock WH-CASA-01 BOX-SW-400  900 200
set_stock WH-RABA-01 BOX-SW-400  640 200
set_stock WH-TANG-01 BOX-SW-400  480 200
set_stock WH-MARR-01 BOX-SW-400  310 200

set_stock WH-CASA-01 FLM-STR-500 260  80
set_stock WH-RABA-01 FLM-STR-500 140  80
set_stock WH-TANG-01 FLM-MCH-500  75  30

# --- un client de démonstration ---------------------------------------------

step "Client de démonstration"
DEMO_EMAIL="demo@logistics.local"
DEMO_PASSWORD="Demo-Pass-2026"

api POST /api/v1/auth/register \
  "{\"email\":\"$DEMO_EMAIL\",\"password\":\"$DEMO_PASSWORD\",\"firstName\":\"Client\",\"lastName\":\"Demo\"}" ""
case "$CODE" in
  201) ok "client $DEMO_EMAIL" ;;
  409) same "client $DEMO_EMAIL" ;;
  *)   bad "client $DEMO_EMAIL" "HTTP $CODE" ;;
esac

# --- récapitulatif ----------------------------------------------------------

step "Récapitulatif"
printf '  créés : %s   déjà présents : %s   en échec : %s\n' "$created" "$skipped" "$failed"

if [ "$failed" -gt 0 ]; then
  printf '\n  \033[31mCertains éléments ont échoué.\033[0m Consultez les journaux : docker compose logs\n'
  exit 1
fi

cat <<SUMMARY

  Connectez-vous sur http://localhost:4200

    administrateur   $BOOTSTRAP_ADMIN_EMAIL   (mot de passe dans .env)
    client           $DEMO_EMAIL / $DEMO_PASSWORD

  À essayer, car chacun montre le moteur d'affectation en train de décider :

    Commander 10 x « Transpalette 2500 kg »
      Aucun entrepôt n'en détient dix : la commande est FRACTIONNÉE. L'écran de
      suivi nomme chaque entrepôt et la distance qui a motivé le choix.

    Commander 3 x « Transpalette électrique 3000 kg »
      Seul Tanger peut la servir seul. Comparez les deux stratégies sur
      l'endpoint allocation-preview : « nearest » fractionne entre Casablanca et
      Rabat, « single-shipment » envoie un seul colis depuis Tanger, bien plus
      loin. Même stock, deux réponses défendables ; l'écran affiche la distance
      utilisée par chacune.

    Tableau de bord entrepôt, Marrakech
      « Rayonnage lourd 2500 mm » est sous son seuil de réapprovisionnement : une vraie
      alerte de stock faible, pas un badge décoratif.

    Commander 99 x n'importe quoi
      Refus en 409, avec un corps de réponse indiquant ce que le réseau détient
      réellement. La commande est conservée à l'état REJECTED avec son motif.

SUMMARY
