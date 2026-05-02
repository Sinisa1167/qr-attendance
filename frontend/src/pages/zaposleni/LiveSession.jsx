import { useState, useEffect, useRef } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { QRCodeSVG } from 'qrcode.react'
import { Client } from '@stomp/stompjs'
import SockJS from 'sockjs-client/dist/sockjs.min.js'
import api from '../../api/axiosInstance'

function LiveSession() {
  const { sessionId } = useParams()
  const navigate = useNavigate()
  const [token, setToken] = useState(null)
  const [attendance, setAttendance] = useState([])
  const [timeLeft, setTimeLeft] = useState(0)
  const [selectedStudent, setSelectedStudent] = useState(null)
  const [qrFullscreen, setQrFullscreen] = useState(false)

  const countdownRef = useRef(null)
  const stompClientRef = useRef(null)

  useEffect(() => {
    fetchToken()
    fetchAttendance()
    connectWebSocket()

    return () => {
      clearInterval(countdownRef.current)
      if (stompClientRef.current) {
        stompClientRef.current.deactivate()
      }
    }
  }, [sessionId])

  useEffect(() => {
    const onKey = (e) => { if (e.key === 'Escape') setQrFullscreen(false) }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [])

  const connectWebSocket = () => {
    const client = new Client({
      webSocketFactory: () => new SockJS('http://localhost:8081/ws'),
      reconnectDelay: 5000,
      onConnect: () => {
        client.subscribe(`/topic/session/${sessionId}`, (message) => {
          const data = JSON.parse(message.body)
          if (data.type === 'NEW_ATTENDANCE') {
            fetchAttendance()
          }
        })
      },
      onStompError: (frame) => {
        console.error('STOMP error:', frame)
      }
    })
    client.activate()
    stompClientRef.current = client
  }

  const fetchToken = async () => {
    try {
      const res = await api.get(`/api/admin/sessions/${sessionId}/token`)
      const newToken = res.data.token
      const duration = parseInt(res.data.expiresIn) || 30
      setToken(newToken)
      setTimeLeft(duration)
      startCountdown(duration)
    } catch (err) {
      console.error("Greška pri preuzimanju tokena:", err)
    }
  }

  const fetchAttendance = async () => {
    try {
      const res = await api.get(`/api/admin/attendance/session/${sessionId}`)
      setAttendance(Array.isArray(res.data) ? res.data : [])
    } catch (err) {
      console.error("Greška pri osvježavanju liste:", err)
    }
  }

  const startCountdown = (initialTime) => {
    if (countdownRef.current) clearInterval(countdownRef.current)
    let t = initialTime
    countdownRef.current = setInterval(() => {
      t -= 1
      if (t <= 0) {
        clearInterval(countdownRef.current)
        fetchToken()
      } else {
        setTimeLeft(t)
      }
    }, 1000)
  }

  const closeSession = async () => {
    if (window.confirm("Da li ste sigurni da želite zatvoriti sesiju? Prijave više neće biti moguće.")) {
      try {
        await api.post(`/api/admin/sessions/${sessionId}/close`)
        navigate(-1)
      } catch (err) { console.error(err) }
    }
  }

  const deleteEntry = async (e, id) => {
    e.stopPropagation()
    if (window.confirm("Ukloniti ovog studenta iz evidencije?")) {
      try {
        await api.delete(`/api/admin/attendance/${id}`)
        fetchAttendance()
      } catch (err) { console.error(err) }
    }
  }

  return (
    <div className="max-w-7xl mx-auto h-full flex flex-col p-4 lg:p-8 gap-6 box-border">

      <div className="flex flex-col md:flex-row justify-between items-start md:items-center shrink-0 gap-4">
        <div>
          <h2 className="text-3xl font-extrabold text-gray-900 tracking-tight">Live Monitoring</h2>
          <p className="text-gray-500 mt-1 flex items-center gap-2">
            Sesija: <span className="font-mono text-blue-600 bg-blue-50 px-2 py-0.5 rounded text-xs font-bold border border-blue-100">{sessionId}</span>
          </p>
        </div>
        <button
          onClick={closeSession}
          className="bg-red-600 hover:bg-red-700 text-white px-6 py-3 rounded-xl font-bold shadow-lg shadow-red-100 transition-all active:scale-95"
        >
          Završi i zatvori sesiju
        </button>
      </div>

      <div className="flex-1 grid grid-cols-1 lg:grid-cols-2 gap-8 min-h-0">

        {/* QR kartica — klik bilo gdje otvara fullscreen */}
        <div
          onClick={() => setQrFullscreen(true)}
          className="bg-white rounded-3xl shadow-sm border border-gray-100 flex flex-col min-h-0 overflow-hidden transition-all hover:shadow-md cursor-pointer group"
        >
          <div className="flex items-center justify-between px-6 pt-6 pb-2 shrink-0">
            <h3 className="text-lg font-bold text-gray-800">Skenirajte za prisustvo</h3>
            <div className="flex items-center gap-2 bg-gray-900 group-hover:bg-blue-600 text-white text-[10px] font-black uppercase tracking-widest px-4 py-2 rounded-xl transition-all shadow-sm">
              <svg xmlns="http://www.w3.org/2000/svg" className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2.5}>
                <path strokeLinecap="round" strokeLinejoin="round" d="M4 8V6a2 2 0 012-2h2M4 16v2a2 2 0 002 2h2m8-16h2a2 2 0 012 2v2m0 8v2a2 2 0 01-2 2h-2" />
              </svg>
              Fullscreen
            </div>
          </div>

          <div className="flex-1 flex items-center justify-center min-h-0 bg-white p-8">
            <div className="w-full h-full max-h-[400px] aspect-square flex items-center justify-center transition-all duration-500">
              {token ? (
                <QRCodeSVG
                  value={token}
                  style={{ width: '100%', height: '100%' }}
                  level="H"
                  includeMargin={false}
                />
              ) : (
                <div className="flex flex-col items-center text-gray-400">
                  <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600 mb-4"></div>
                  <p className="text-sm font-medium">Generisanje koda...</p>
                </div>
              )}
            </div>
          </div>

          <div className="px-6 pb-6 shrink-0">
            <div className="bg-blue-50 rounded-2xl px-6 py-4 flex items-center justify-between border border-blue-100/50">
              <div>
                <p className="text-blue-400 text-[10px] uppercase tracking-[0.2em] font-black">
                  Sledeće osvježavanje
                </p>
                <p className="text-xs text-blue-300 font-medium">Automatska rotacija koda</p>
              </div>
              <span className={`text-4xl font-black tabular-nums ${timeLeft <= 5 ? 'text-red-500 animate-pulse' : 'text-blue-700'}`}>
                {timeLeft}s
              </span>
            </div>
          </div>
        </div>

        <div className="bg-white rounded-3xl shadow-sm border border-gray-100 flex flex-col min-h-0 overflow-hidden transition-all hover:shadow-md">
          <div className="flex justify-between items-center px-6 pt-6 pb-4 shrink-0 border-b border-gray-50">
            <h3 className="text-lg font-bold text-gray-800">Prisutni studenti</h3>
            <div className="flex items-center gap-3 bg-emerald-50 text-emerald-700 px-4 py-1.5 rounded-full border border-emerald-100">
              <span className="relative flex h-2.5 w-2.5">
                <span className="animate-ping absolute inline-flex h-full w-full rounded-full bg-emerald-400 opacity-75"></span>
                <span className="relative inline-flex rounded-full h-2.5 w-2.5 bg-emerald-500"></span>
              </span>
              <span className="font-black text-xl leading-none">{attendance.length}</span>
            </div>
          </div>

          <div className="flex-1 overflow-y-auto px-6 py-4 min-h-0 custom-scrollbar">
            {attendance.length === 0 ? (
              <div className="flex flex-col items-center justify-center h-full text-gray-400 space-y-2">
                <p className="italic font-medium">Čekanje na prve prijave...</p>
              </div>
            ) : (
              <div className="space-y-3">
                {attendance.map((entry) => (
                  <div
                    key={entry.id}
                    onClick={() => setSelectedStudent(entry)}
                    className="group flex justify-between items-center p-4 bg-white hover:bg-blue-50 border border-gray-100 hover:border-blue-200 rounded-2xl cursor-pointer transition-all shadow-sm hover:shadow-md"
                  >
                    <div className="flex flex-col">
                      <span className="font-bold text-gray-900 group-hover:text-blue-700 transition-colors">
                        {entry.firstName} {entry.lastName}
                      </span>
                      <span className="text-[11px] text-gray-500 font-medium">
                        {entry.indexNumber || 'BEZ INDEKSA'} • {entry.email}
                      </span>
                    </div>
                    <div className="flex flex-col items-end gap-1">
                      <span className="text-xs font-black text-blue-600 bg-blue-100/50 px-2 py-0.5 rounded-md">
                        {entry.checkInTime}
                      </span>
                      <button
                        onClick={(e) => deleteEntry(e, entry.id)}
                        className="text-[10px] font-bold text-red-400 hover:text-red-600 uppercase tracking-tighter opacity-0 group-hover:opacity-100 transition-all"
                      >
                        Ukloni
                      </button>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      </div>

      {qrFullscreen && (
        <div
          className="fixed inset-0 bg-white z-[100] flex flex-col items-center justify-center p-8 animate-in fade-in duration-300"
          onClick={() => setQrFullscreen(false)}
        >
          <div className="absolute top-12 right-12 flex flex-col items-end">
            <p className="text-blue-400 text-xs font-black uppercase tracking-[0.3em] mb-1">Rotacija koda za</p>
            <span className={`text-8xl font-black tabular-nums ${timeLeft <= 5 ? 'text-red-500 animate-pulse' : 'text-blue-800'}`}>
              {timeLeft}s
            </span>
          </div>

          <div className="w-full h-[75vh] flex items-center justify-center">
            {token && (
              <QRCodeSVG
                value={token}
                style={{ width: 'auto', height: '100%', maxWidth: '90vw' }}
                level="H"
              />
            )}
          </div>

          <button className="mt-12 text-gray-400 text-sm font-medium flex items-center gap-2 hover:text-gray-600 transition-colors">
            <kbd className="px-2 py-1 bg-gray-100 rounded border text-xs font-sans">ESC</kbd> ili kliknite bilo gdje za zatvaranje
          </button>
        </div>
      )}

      {selectedStudent && (
        <div className="fixed inset-0 bg-gray-900/60 backdrop-blur-md flex items-center justify-center p-4 z-50 animate-in fade-in zoom-in duration-200">
          <div className="bg-white rounded-[2rem] p-8 max-w-md w-full shadow-2xl border border-white/20">
            <div className="flex justify-between items-start mb-6">
              <div>
                <h4 className="text-2xl font-black text-gray-900 leading-tight">Detalji prijave</h4>
                <p className="text-gray-500 text-xs font-medium mt-1 uppercase tracking-wider">Mrežna verifikacija studenta</p>
              </div>
              <button onClick={() => setSelectedStudent(null)} className="text-gray-400 hover:text-gray-600">✕</button>
            </div>

            <div className="space-y-4">
              <div className="bg-gray-50 p-4 rounded-2xl flex justify-between items-center">
                <span className="text-xs font-bold text-gray-400 uppercase">Student</span>
                <span className="font-bold text-gray-900">{selectedStudent.firstName} {selectedStudent.lastName}</span>
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div className="bg-gray-50 p-4 rounded-2xl">
                  <span className="text-[10px] font-bold text-gray-400 uppercase block mb-1">Vrijeme</span>
                  <span className="font-mono font-black text-blue-600">{selectedStudent.checkInTime}</span>
                </div>
                <div className="bg-gray-50 p-4 rounded-2xl">
                  <span className="text-[10px] font-bold text-gray-400 uppercase block mb-1">Status</span>
                  <span className="text-emerald-600 font-bold">Verifikovano</span>
                </div>
              </div>

              <div className="p-5 bg-red-50/50 rounded-2xl border border-red-100/50 space-y-4">
                <p className="text-[10px] font-black text-red-400 uppercase tracking-widest border-b border-red-100 pb-2">
                  Sigurnosni logovi
                </p>
                <div className="space-y-3">
                  <div>
                    <p className="text-[9px] text-gray-400 font-bold uppercase">IP Adresa uređaja</p>
                    <p className="font-mono text-sm font-bold text-red-800">{selectedStudent.ipAddress || '192.168.1.1'}</p>
                  </div>
                  <div>
                    <p className="text-[9px] text-gray-400 font-bold uppercase">Korišteni preglednik</p>
                    <p className="text-[11px] text-gray-700 leading-tight italic font-medium">{selectedStudent.userAgent}</p>
                  </div>
                </div>
              </div>
            </div>

            <button
              onClick={() => setSelectedStudent(null)}
              className="mt-8 w-full bg-gray-900 text-white py-4 rounded-2xl font-black hover:bg-black transition-all shadow-lg active:scale-[0.98]"
            >
              Zatvori pregled
            </button>
          </div>
        </div>
      )}
    </div>
  )
}

export default LiveSession