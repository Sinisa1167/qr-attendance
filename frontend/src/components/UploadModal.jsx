import { useState } from 'react'
import api from '../api/axiosInstance'

function UploadModal({ onClose, onSuccess }) {
  const [files, setFiles] = useState([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)
  const [progress, setProgress] = useState('')

  const handleUpload = async () => {
    if (files.length === 0) {
      setError('Odaberite fajl')
      return
    }

    setLoading(true)
    setError(null)

    for (let i = 0; i < files.length; i++) {
      const file = files[i]
      setProgress(`Uploadujem ${i + 1}/${files.length}: ${file.name}`)
      
      const formData = new FormData()
      formData.append('file', file)

      try {
  await api.post('/api/admin/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
} catch (err) {
  const msg = err.response?.data?.error || `Greška pri uploadu: ${file.name}`
  setError(msg)
  setLoading(false)
  return
}
    }

    setLoading(false)
    setProgress('')
    onSuccess()
    onClose()
  }

  return (
    <div className="fixed inset-0 bg-black bg-opacity-50 flex items-center justify-center z-50">
      <div className="bg-white rounded-lg p-6 w-full max-w-md">
        <h3 className="text-xl font-bold mb-4">Upload liste studenata</h3>

        <div className="border-2 border-dashed border-gray-300 rounded-lg p-6 text-center mb-4">
          <input
            type="file"
            accept=".xlsx,.csv"
            multiple
            onChange={(e) => setFiles(Array.from(e.target.files))}
            className="hidden"
            id="fileInput"
          />
          <label htmlFor="fileInput" className="cursor-pointer">
            {files.length > 0 ? (
              <div className="text-left space-y-1 max-h-40 overflow-y-auto">
                {files.map((f, i) => (
                  <p key={i} className="text-blue-600 text-sm truncate" title={f.name}>
                    📄 {f.name}
                  </p>
                ))}
              </div>
            ) : (
              <div className="text-gray-500">
                <p className="text-lg mb-1">📁 Kliknite da odaberete fajlove</p>
                <p className="text-sm">Podržani formati: .xlsx, .csv</p>
                <p className="text-sm text-gray-400">Možete odabrati više fajlova</p>
              </div>
            )}
          </label>
        </div>

        {progress && <p className="text-blue-600 text-sm mb-2">{progress}</p>}
        {error && <p className="text-red-500 text-sm mb-4">{error}</p>}

        <div className="flex gap-3 justify-end">
          <button
            onClick={onClose}
            className="px-4 py-2 border border-gray-300 rounded hover:bg-gray-50"
          >
            Otkaži
          </button>
          <button
            onClick={handleUpload}
            disabled={loading}
            className="px-4 py-2 bg-blue-600 text-white rounded hover:bg-blue-700 disabled:opacity-50"
          >
            {loading ? 'Uploadujem...' : `Upload (${files.length})`}
          </button>
        </div>
      </div>
    </div>
  )
}

export default UploadModal