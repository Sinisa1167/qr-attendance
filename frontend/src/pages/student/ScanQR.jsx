import { useState, useEffect, useRef } from 'react'
import { Html5QrcodeScanner } from 'html5-qrcode'
import api from '../../api/axiosInstance'

function ScanQR() {
  const [result, setResult] = useState(null)
  const [error, setError] = useState(null)
  const [loading, setLoading] = useState(false)
  const [scanned, setScanned] = useState(false)
  const scannerRef = useRef(null)

  useEffect(() => {
    const scanner = new Html5QrcodeScanner('qr-reader', {
      fps: 10,
      qrbox: { width: 250, height: 250 },
    })

    scanner.render(
      async (decodedText) => {
        if (scanned) return
        setScanned(true)
        scanner.clear()
        await checkIn(decodedText)
      },
      (err) => {
        // ignorišemo greške skeniranja
      }
    )

    scannerRef.current = scanner

    return () => {
      scanner.clear().catch(() => {})
    }
  }, [])

  const checkIn = async (token) => {
    setLoading(true)
    setError(null)
    try {
      const res = await api.post('/api/student/attendance/checkin', { token })
      setResult(res.data)
    } catch (err) {
      const msg = err.response?.data?.error || 'Greška pri evidentiranju prisustva'
      setError(msg)
      setScanned(false)
    } finally {
      setLoading(false)
    }
  }

  const resetScanner = () => {
    setResult(null)
    setError(null)
    setScanned(false)
    window.location.reload()
  }

  return (
    <div className="max-w-md mx-auto">
      <h2 className="text-2xl font-bold text-gray-800 mb-6 text-center">
        Evidencija prisustva
      </h2>

      {!result && !loading && (
        <div className="bg-white rounded-lg shadow p-4">
          <p className="text-gray-500 text-center mb-4 text-sm">
            Usmjerite kameru prema QR kodu
          </p>
          <div id="qr-reader" className="w-full" />
          {error && (
            <div className="mt-4 p-3 bg-red-50 border border-red-200 rounded">
              <p className="text-red-600 text-sm text-center">{error}</p>
              <button
                onClick={resetScanner}
                className="mt-2 w-full bg-red-600 text-white py-2 rounded hover:bg-red-700 text-sm"
              >
                Pokušaj ponovo
              </button>
            </div>
          )}
        </div>
      )}

      {loading && (
        <div className="bg-white rounded-lg shadow p-8 text-center">
          <p className="text-gray-500">Evidentiranje prisustva...</p>
        </div>
      )}

      {result && (
        <div className="bg-white rounded-lg shadow p-8 text-center">
          <div className="text-green-500 text-6xl mb-4">✓</div>
          <h3 className="text-xl font-bold text-green-600 mb-2">
            Prisustvo evidentirano!
          </h3>
          <p className="text-gray-500 text-sm">
            Vrijeme prijave: {new Date(result.checkInTime).toLocaleTimeString()}
          </p>
          <button
            onClick={resetScanner}
            className="mt-4 bg-blue-600 text-white px-4 py-2 rounded hover:bg-blue-700"
          >
            Skeniraj ponovo
          </button>
        </div>
      )}
    </div>
  )
}

export default ScanQR