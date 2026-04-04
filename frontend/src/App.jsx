import { BrowserRouter, Routes, Route } from 'react-router-dom'
import keycloak from './keycloak'
import Dashboard from './pages/zaposleni/Dashboard'
import Sessions from './pages/zaposleni/Sessions'
import LiveSession from './pages/zaposleni/LiveSession'


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
            <span className="text-sm">{keycloak.tokenParsed?.email}</span>
            <button
              onClick={() => keycloak.logout()}
              className="bg-white text-blue-600 px-3 py-1 rounded font-medium hover:bg-gray-100"
            >
              Odjava
            </button>
          </div>
        </nav>

        <main className="p-6">
          {isZaposleni && (
            <Routes>
              <Route path="/" element={<Dashboard />} />
              <Route path="/subject/:subjectId" element={<Sessions />} />
              <Route path="/session/:sessionId/live" element={<LiveSession />} />
            </Routes>
          )}
          {isStudent && (
            <div className="text-center mt-10">
              <h2 className="text-2xl font-bold text-gray-800">Dobrodošli, studente!</h2>
              <p className="text-gray-500 mt-2">Skenirajte QR kod da evidentirate prisustvo.</p>
            </div>
          )}
          {!isZaposleni && !isStudent && (
            <p className="text-red-600">Nemate dodijeljenu ulogu!</p>
          )}
        </main>
      </div>
    </BrowserRouter>
  )
}

export default App