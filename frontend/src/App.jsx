import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import keycloak from './keycloak'

function App() {
  const roles = keycloak.tokenParsed?.realm_access?.roles || []
  const isZaposleni = roles.includes('ZAPOSLENI')
  const isStudent = roles.includes('STUDENT')

  return (
    <BrowserRouter>
      <div className="min-h-screen bg-gray-100">
        <nav className="bg-blue-600 text-white p-4 flex justify-between items-center">
          <h1 className="text-xl font-bold">QR Attendance</h1>
          <div className="flex items-center gap-4">
            <span>{keycloak.tokenParsed?.email}</span>
            <button
              onClick={() => keycloak.logout()}
              className="bg-white text-blue-600 px-3 py-1 rounded font-medium hover:bg-gray-100"
            >
              Odjava
            </button>
          </div>
        </nav>

        <main className="p-6">
          {isZaposleni && <p className="text-green-600 font-bold">Ulogovani ste kao Zaposleni</p>}
          {isStudent && <p className="text-blue-600 font-bold">Ulogovani ste kao Student</p>}
          {!isZaposleni && !isStudent && <p className="text-red-600">Nemate dodijeljenu ulogu!</p>}
        </main>
      </div>
    </BrowserRouter>
  )
}

export default App