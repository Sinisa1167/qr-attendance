#!/bin/sh
cat > /usr/share/nginx/html/config.js <<EOF
window.__APP_CONFIG__ = {
  keycloakUrl: "${KEYCLOAK_URL}",
  keycloakRealm: "${KEYCLOAK_REALM}",
  keycloakClientId: "${KEYCLOAK_CLIENT_ID}"
};
EOF