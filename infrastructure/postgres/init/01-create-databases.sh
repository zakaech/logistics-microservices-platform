#!/bin/sh
#
# Creates one database and one dedicated user per service.
#
# This runs ONLY on the first initialisation of the postgres data volume.
# All three databases are created now, even though Phase 1 uses auth_db alone,
# so that a later phase does not require destroying the volume.
#
# Credentials come from the environment (docker-compose reads them from .env);
# nothing is hard-coded here.
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
