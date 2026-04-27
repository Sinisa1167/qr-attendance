import { BrowserRouter, Routes, Route, useLocation } from 'react-router-dom'
import keycloak from './keycloak'
import Dashboard from './pages/zaposleni/Dashboard'
import Sessions from './pages/zaposleni/Sessions'
import LiveSession from './pages/zaposleni/LiveSession'
import ScanQR from './pages/student/ScanQR'
import Analytics from './pages/zaposleni/Analytics'

function AppLayout() {
  const roles = keycloak.tokenParsed?.realm_access?.roles || []
  const isZaposleni = roles.includes('ZAPOSLENI')
  const isStudent = roles.includes('STUDENT')
  const location = useLocation()
  const isLive = location.pathname.includes('/live')

  return (
    <div className="h-screen flex flex-col bg-gray-100">
      <nav className="bg-blue-600 text-white px-4 py-3 flex justify-between items-center shrink-0">
  <h1 className="text-xl font-bold whitespace-nowrap">QR Attendance</h1>
  <div className="flex items-center gap-2 min-w-0">
    <span className="text-sm hidden sm:block">
  {keycloak.tokenParsed?.email}
</span>
    <button
      onClick={() => keycloak.logout()}
      className="bg-white text-blue-600 px-3 py-1 rounded font-medium hover:bg-gray-100 whitespace-nowrap text-sm shrink-0"
    >
      Odjava
    </button>
  </div>
</nav>

      {/* Na /live stranici nema paddinga i overflow je hidden — LiveSession sam upravlja layoutom */}
      <main className={`flex-1 min-h-0 ${isLive ? 'overflow-hidden' : 'overflow-auto p-2 sm:p-6'}`}>
        {isZaposleni && (
          <Routes>
            <Route path="/" element={<Dashboard />} />
            <Route path="/subject/:subjectId" element={<Sessions />} />
            <Route path="/subject/:subjectId/analytics" element={<Analytics />} />
            <Route path="/session/:sessionId/live" element={<LiveSession />} />
          </Routes>
        )}
        {isStudent && (
          <Routes>
            <Route path="/" element={<ScanQR />} />
          </Routes>
        )}
        {!isZaposleni && !isStudent && (
          <p className="text-red-600 p-6">Nemate dodijeljenu ulogu!</p>
        )}
      </main>
    </div>
  )
}

function App() {
  return (
    <BrowserRouter>
      <AppLayout />
    </BrowserRouter>
  )
}

export default App