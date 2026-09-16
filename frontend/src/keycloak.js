import Keycloak from 'keycloak-js'

const cfg = window.__APP_CONFIG__ || {}

const keycloak = new Keycloak({
  url: cfg.keycloakUrl || 'http://localhost:8080',
  realm: cfg.keycloakRealm || 'qr-attendance',
  clientId: cfg.keycloakClientId || 'qr-attendance-app',
})

export default keycloak