import Keycloak from 'keycloak-js'

const keycloak = new Keycloak({
  url: 'http://localhost:8080',
  realm: 'qr-attendance',
  clientId: 'qr-attendance-app',
})

export default keycloak