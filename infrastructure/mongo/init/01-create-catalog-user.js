/*
 * Creates the catalog-service application user with readWrite on catalog_db only.
 * The root account stays reserved for administration; no service ever uses it.
 *
 * Runs only on the first initialisation of the mongo data volume.
 */
const databaseName = process.env.CATALOG_DB_NAME;
const username = process.env.CATALOG_DB_USER;
const password = process.env.CATALOG_DB_PASSWORD;

if (!databaseName || !username || !password) {
    throw new Error('CATALOG_DB_NAME, CATALOG_DB_USER and CATALOG_DB_PASSWORD must be set.');
}

const catalogDb = db.getSiblingDB(databaseName);

catalogDb.createUser({
    user: username,
    pwd: password,
    roles: [{ role: 'readWrite', db: databaseName }]
});

print(`Created user '${username}' with readWrite on '${databaseName}'.`);
