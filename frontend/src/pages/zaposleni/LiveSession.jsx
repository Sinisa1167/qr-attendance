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
  const [timeLeft, setTimeLeft] = useState(30)
  const intervalRef = useRef(null)
  const countdownRef = useRef(null)

  useEffect(() => {
    fetchToken()
    fetchAttendance()

    // Osvježavaj prisustvo svakih 5 sekundi
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

  const fetchToken = async () => {
    try {
      const res = await api.get(`/api/admin/sessions/${sessionId}/token`)
      setToken(res.data.token)
      setTimeLeft(30)
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
    setTimeLeft(30)
    countdownRef.current = setInterval(() => {
      setTimeLeft(prev => {
        if (prev <= 1) {
          fetchToken()
          return 30
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
          <h3 className="text-lg font-semibold mb-4">QR Kod</h3>
          {token ? (
            <>
              <div className="p-4 bg-white border-2 border-gray-200 rounded-lg">
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
    </div>
  )
}

export default LiveSession