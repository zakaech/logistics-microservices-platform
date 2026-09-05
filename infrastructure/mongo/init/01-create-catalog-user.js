/*
 * Crée l'utilisateur applicatif de catalog-service, avec readWrite sur catalog_db
 * uniquement. Le compte root reste réservé à l'administration ; aucun service ne
 * l'utilise jamais.
 *
 * Ne s'exécute qu'à la première initialisation du volume de données mongo.
 */
const databaseName = process.env.CATALOG_DB_NAME;
const username = process.env.CATALOG_DB_USER;
const password = process.env.CATALOG_DB_PASSWORD;

if (!databaseName || !username || !password) {
    throw new Error('CATALOG_DB_NAME, CATALOG_DB_USER et CATALOG_DB_PASSWORD doivent être définis.');
}

const catalogDb = db.getSiblingDB(databaseName);

catalogDb.createUser({
    user: username,
    pwd: password,
    roles: [{ role: 'readWrite', db: databaseName }]
});

print(`Utilisateur '${username}' créé avec readWrite sur '${databaseName}'.`);
