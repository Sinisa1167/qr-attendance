import { useState, useEffect, useRef } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { QRCodeSVG } from 'qrcode.react'
import api from '../../api/axiosInstance'

function LiveSession() {
  const { sessionId } = useParams()
  const navigate = useNavigate()
  const [token, setToken] = useState(null)
  const [attendance, setAttendance] = useState([])
  const [session, setSession] = useState(null)
  const [refreshInterval, setRefreshInterval] = useState(30)
  const [timeLeft, setTimeLeft] = useState(30)
  const [fullscreen, setFullscreen] = useState(false)
  const intervalRef = useRef(null)
  const countdownRef = useRef(null)

  useEffect(() => {
    fetchConfig()
    fetchToken()
    fetchAttendance()

    intervalRef.current = setInterval(() => {
      fetchAttendance()
    }, 5000)

    return () => {
      clearInterval(intervalRef.current)
      clearInterval(countdownRef.current)
    }
  }, [sessionId])

  useEffect(() => {
    if (token) {
      startCountdown()
    }
  }, [token])

  useEffect(() => {
    setTimeLeft(refreshInterval)
  }, [refreshInterval])

  const fetchConfig = async () => {
    try {
      const res = await api.get('/api/public/config')
      setRefreshInterval(res.data.qrRefreshInterval / 1000)
    } catch (err) {
      console.error(err)
    }
  }

  const fetchToken = async () => {
    try {
      const res = await api.get(`/api/admin/sessions/${sessionId}/token`)
      setToken(res.data.token)
      setTimeLeft(refreshInterval)
    } catch (err) {
      console.error(err)
    }
  }

  const fetchAttendance = async () => {
    try {
      const res = await api.get(`/api/admin/attendance/session/${sessionId}`)
      setAttendance(res.data)
    } catch (err) {
      console.error(err)
    }
  }

  const startCountdown = () => {
    clearInterval(countdownRef.current)
    setTimeLeft(refreshInterval)
    countdownRef.current = setInterval(() => {
      setTimeLeft(prev => {
        if (prev <= 1) {
          fetchToken()
          return refreshInterval
        }
        return prev - 1
      })
    }, 1000)
  }

  const closeSession = async () => {
    try {
      await api.post(`/api/admin/sessions/${sessionId}/close`)
      navigate(-1)
    } catch (err) {
      console.error(err)
    }
  }

  const deleteAttendance = async (attendanceId) => {
    try {
      await api.delete(`/api/admin/attendance/${attendanceId}`)
      fetchAttendance()
    } catch (err) {
      console.error(err)
    }
  }

  return (
    <div className="max-w-6xl mx-auto">
      <div className="flex justify-between items-center mb-6">
        <h2 className="text-2xl font-bold text-gray-800">Live sesija</h2>
        <button
          onClick={closeSession}
          className="bg-red-600 text-white px-4 py-2 rounded hover:bg-red-700"
        >
          Zatvori sesiju
        </button>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">

        {/* QR Kod */}
        <div className="bg-white rounded-lg shadow p-6 flex flex-col items-center">
          <div className="flex justify-between items-center w-full mb-4">
            <h3 className="text-lg font-semibold">QR Kod</h3>
            {token && (
              <button
                onClick={() => setFullscreen(true)}
                className="text-blue-600 hover:text-blue-800 text-sm border border-blue-300 px-2 py-1 rounded"
              >
                ⛶ Fullscreen
              </button>
            )}
          </div>
          {token ? (
            <>
              <div className="p-4 bg-white border-2 border-gray-200 rounded-lg cursor-pointer"
                onClick={() => setFullscreen(true)}>
                <QRCodeSVG value={token} size={200} />
              </div>
              <div className="mt-4 text-center">
                <p className="text-gray-500 text-sm">Osvježava se za</p>
                <p className={`text-3xl font-bold ${timeLeft <= 10 ? 'text-red-500' : 'text-blue-600'}`}>
                  {timeLeft}s
                </p>
              </div>
            </>
          ) : (
            <p className="text-gray-500">Učitavanje QR koda...</p>
          )}
        </div>

        {/* Prisutni studenti */}
        <div className="bg-white rounded-lg shadow p-6">
          <div className="flex justify-between items-center mb-4">
            <h3 className="text-lg font-semibold">Prisutni studenti</h3>
            <span className="bg-green-100 text-green-700 px-3 py-1 rounded-full font-bold">
              {attendance.length}
            </span>
          </div>

          {attendance.length === 0 ? (
            <p className="text-gray-500 text-center mt-8">Još nema prijavljenih studenata</p>
          ) : (
            <div className="space-y-2 max-h-80 overflow-y-auto">
              {attendance.map((a) => (
                <div key={a.id} className="flex justify-between items-center p-2 bg-gray-50 rounded">
                  <div>
                    <p className="font-medium text-sm">
                      {a.student.firstName} {a.student.lastName}
                    </p>
                    <p className="text-gray-400 text-xs">{a.checkInTime}</p>
                  </div>
                  <button
                    onClick={() => deleteAttendance(a.id)}
                    className="text-red-500 hover:text-red-700 text-sm"
                  >
                    Obriši
                  </button>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>

      {/* Fullscreen QR overlay */}
      {fullscreen && token && (
        <div
          className="fixed inset-0 bg-white flex flex-col items-center justify-center z-50 cursor-pointer"
          onClick={() => setFullscreen(false)}
        >
          <p className="text-gray-400 text-sm mb-8">Kliknite bilo gdje za izlaz</p>
          <div className="p-6 bg-white border-4 border-gray-200 rounded-2xl shadow-lg">
            <QRCodeSVG value={token} size={350} />
          </div>
          <div className="mt-8 text-center">
            <p className="text-gray-500 text-lg">Osvježava se za</p>
            <p className={`text-7xl font-bold mt-2 ${timeLeft <= 10 ? 'text-red-500' : 'text-blue-600'}`}>
              {timeLeft}s
            </p>
          </div>
          <p className="text-gray-300 text-xs mt-8">Prisustvo: {attendance.length} studenata</p>
        </div>
      )}
    </div>
  )
}

export default LiveSession