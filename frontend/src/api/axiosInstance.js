// axiosInstance.js
import axios from 'axios'
import keycloak from '../keycloak'

const api = axios.create({
  baseURL: '',
})

api.interceptors.request.use(
  async (config) => {
    try {
      if (keycloak.authenticated) {
        await keycloak.updateToken(30)
        config.headers.Authorization = `Bearer ${keycloak.token}`
      }
    } catch (error) {
      console.error('Nije moguće osvježiti token', error)
    }
    return config
  },
  (error) => Promise.reject(error)
)

export default api