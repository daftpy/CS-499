# Weight Tracker - Dev Stack
This is the Docker Compose file for the RESTful API which powers the backend for the WeightTracker app.

This folder's `docker-compose.dev.yml` starts a simple local stack:

- **mysql** – MySQL 8 database for weights/goals. Exposes `3306`.
- **keycloak** – OIDC auth server (imports `keycloak/realm.json`). Internal `8080`.
- **api** – Node/Express REST API (talks to MySQL, validates tokens). Exposes `3000`.
- **caddy** – Reverse proxy with local TLS for HTTPS (e.g. `https://api.10-0-2-2.sslip.io`). Exposes `80/443`.

All services run on the `app_network` bridge. MySQL data persists in the `mysql_data` volume.

## Quick start

```bash
# From the repo root
docker compose up -f docker-compose.dev.yml up -d --build

# Stop
docker compose -f docker-compose.dev.yml down
```

## Environment Variables

There are a few env files which help configure the different services.

### mysql/.env.dev
```
MYSQL_DATABASE=WeightTrackerAPI
MYSQL_USER=weight_tracker
MYSQL_PASSWORD=weight_tracker_passwordCHANGEME
MYSQL_ROOT_PASSWORD=rootpw_changeme
MYSQL_PORT=3306
```

### keycloak/.env.dev
```
# Keycloak Base Configuration
KC_HOSTNAME_STRICT=false
KC_PROXY=edge
KC_BOOTSTRAP_ADMIN_USERNAME=admin
KC_BOOTSTRAP_ADMIN_PASSWORD=admin

# Keycloak Database Configuration
KC_DB=mysql
KC_DB_USERNAME=weight_tracker
KC_DB_PASSWORD=weight_tracker_passwordCHANGEME
KC_DB_URL_HOST=mysql
KC_DB_URL_PORT=3306
KC_DB_URL_DATABASE=WeightTrackerAPI
```

### api/.env.dev
```
PORT=3000
```

## HTTPS on Android emulator
Caddy issues a local cert for local development. [You may need to trust the Caddy root CA on the emulator](https://stackoverflow.com/questions/71854047/install-ca-certificate-on-android-emulator).
