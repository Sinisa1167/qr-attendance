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
    // Funkcija za pokretanje skenera
    const startScanner = () => {
      // Ako već postoji instanca, prvo je očisti
      if (scannerRef.current) {
        scannerRef.current.clear()
      }

      const scanner = new Html5QrcodeScanner('qr-reader', {
        fps: 10,
        qrbox: { width: 250, height: 250 },
        // Dodajemo aspekte za bolji prikaz na mobilnim uređajima
        aspectRatio: 1.0 
      })

      scanner.render(onScanSuccess, onScanError)
      scannerRef.current = scanner
    }

    startScanner()

    // Cleanup funkcija
    return () => {
      if (scannerRef.current) {
        scannerRef.current.clear().catch(err => console.error("Greška pri gašenju skenera", err))
      }
    }
  }, [])

  const onScanSuccess = async (decodedText) => {
    // Spriječite višestruka skeniranja istog koda dok traje obrada
    if (scanned) return
    setScanned(true)

    // Zaustavi skener čim se pronađe kod
    if (scannerRef.current) {
      try {
        await scannerRef.current.clear()
      } catch (e) {
        console.warn("Skener već ugašen")
      }
    }

    await checkIn(decodedText)
  }

  const onScanError = (err) => {
    // Većina grešaka su samo "kod nije pronađen u ovom frejmu", ignorišemo
  }

  const checkIn = async (token) => {
    setLoading(true)
    setError(null)
    try {
      const res = await api.post('/api/student/attendance/checkin', { token })
      setResult(res.data)
    } catch (err) {
      const msg = err.response?.data?.error || 'Greška pri evidentiranju prisustva'
      setError(msg)
      // Dozvoli ponovno skeniranje ako je greška (npr. istekao kod)
      setScanned(false) 
    } finally {
      setLoading(false)
    }
  }

  // Resetovanje bez reload-a stranice
  const resetScanner = () => {
    setResult(null)
    setError(null)
    setScanned(false)
    
    // Malo sačekamo da se DOM element 'qr-reader' ponovo montira ako je bio sakriven
    setTimeout(() => {
      const scanner = new Html5QrcodeScanner('qr-reader', {
        fps: 10,
        qrbox: { width: 250, height: 250 },
      })
      scanner.render(onScanSuccess, onScanError)
      scannerRef.current = scanner
    }, 100)
  }

  return (
    <div className="max-w-md mx-auto p-4">
      <div className="text-center mb-8">
        <h2 className="text-3xl font-black text-gray-900">Scan QR</h2>
        <p className="text-gray-500 mt-2 font-medium">Evidencija prisustva na nastavi</p>
      </div>

      {/* Skener panel */}
      {!result && !loading && (
        <div className="bg-white rounded-3xl shadow-xl overflow-hidden border border-gray-100">
          <div className="p-6">
             <div id="qr-reader" className="w-full overflow-hidden rounded-2xl" />
          </div>
          
          {error && (
            <div className="px-6 pb-6 animate-in fade-in slide-in-from-bottom-2">
              <div className="bg-red-50 border border-red-100 rounded-2xl p-4">
                <p className="text-red-600 text-sm font-bold text-center mb-3">{error}</p>
                <button
                  onClick={resetScanner}
                  className="w-full bg-red-600 text-white py-3 rounded-xl font-bold hover:bg-red-700 transition-all shadow-lg shadow-red-100 uppercase text-xs tracking-widest"
                >
                  Pokušaj ponovo
                </button>
              </div>
            </div>
          )}
        </div>
      )}

      {/* Loading state */}
      {loading && (
        <div className="bg-white rounded-3xl shadow-xl p-12 text-center border border-gray-50">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600 mx-auto mb-4"></div>
          <p className="text-gray-500 font-bold uppercase text-xs tracking-widest">Obrađujem zahtjev...</p>
        </div>
      )}

      {/* Success state */}
      {result && (
        <div className="bg-white rounded-3xl shadow-xl p-10 text-center border border-green-50 animate-in zoom-in duration-300">
          <div className="bg-green-100 text-green-600 w-20 h-20 rounded-full flex items-center justify-center text-4xl mx-auto mb-6 shadow-inner">
            ✓
          </div>
          <h3 className="text-2xl font-black text-gray-900 mb-2">Uspješno!</h3>
          <p className="text-gray-500 font-medium mb-6">Vaše prisustvo je evidentirano.</p>
          
          <div className="bg-gray-50 rounded-2xl p-4 mb-8">
            <p className="text-[10px] text-gray-400 font-bold uppercase mb-1">Vrijeme prijave</p>
            <p className="text-gray-700 font-mono font-bold text-lg">
              {new Date(result.checkInTime).toLocaleTimeString()}
            </p>
          </div>

          <button
            onClick={resetScanner}
            className="w-full bg-gray-900 text-white py-4 rounded-2xl font-bold hover:bg-blue-600 transition-all shadow-xl shadow-gray-200"
          >
            Skeniraj ponovo
          </button>
        </div>
      )}
    </div>
  )
}

export default ScanQR