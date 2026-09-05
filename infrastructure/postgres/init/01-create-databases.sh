#!/bin/sh
#
# Crée une base et un utilisateur dédié par service.
#
# Ce script ne s'exécute qu'à la PREMIÈRE initialisation du volume de données
# postgres. Les trois bases sont créées dès maintenant, même si la Phase 1
# n'utilise qu'auth_db, afin qu'une phase ultérieure n'oblige pas à détruire
# le volume.
#
# Les identifiants proviennent de l'environnement (docker-compose les lit depuis
# .env) ; rien n'est codé en dur ici.
set -e

create_database() {
    db_name="$1"
    db_user="$2"
    db_password="$3"

    if [ -z "${db_name}" ] || [ -z "${db_user}" ] || [ -z "${db_password}" ]; then
        echo "ERROR: missing name, user or password for a service database." >&2
        exit 1
    fi

    echo "Creating database '${db_name}' owned by '${db_user}'..."

    psql -v ON_ERROR_STOP=1 --username "${POSTGRES_USER}" --dbname "${POSTGRES_DB}" <<EOSQL
CREATE ROLE "${db_user}" WITH LOGIN PASSWORD '${db_password}';
CREATE DATABASE "${db_name}" OWNER "${db_user}";
REVOKE ALL ON DATABASE "${db_name}" FROM PUBLIC;
GRANT ALL PRIVILEGES ON DATABASE "${db_name}" TO "${db_user}";
EOSQL
}

create_database "${AUTH_DB_NAME}"      "${AUTH_DB_USER}"      "${AUTH_DB_PASSWORD}"
create_database "${INVENTORY_DB_NAME}" "${INVENTORY_DB_USER}" "${INVENTORY_DB_PASSWORD}"
create_database "${ORDER_DB_NAME}"     "${ORDER_DB_USER}"     "${ORDER_DB_PASSWORD}"

echo "Service databases created."
