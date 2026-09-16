import { useState, useRef } from 'react'
import api from '../api/axiosInstance'

function UploadModal({ onClose, onSuccess }) {
  const [files, setFiles] = useState([])
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)
  const [currentFileIndex, setCurrentFileIndex] = useState(0)
  const [isDragging, setIsDragging] = useState(false)
  const fileInputRef = useRef(null)

  const handleFileChange = (e) => {
    const selectedFiles = Array.from(e.target.files)
    setFiles(selectedFiles)
    setError(null)
  }

  const handleUpload = async () => {
    if (files.length === 0) {
      setError('Molimo odaberite bar jedan fajl.')
      return
    }

    setLoading(true)
    setError(null)

    let successCount = 0

    for (let i = 0; i < files.length; i++) {
      const file = files[i]
      setCurrentFileIndex(i + 1)

      const formData = new FormData()
      formData.append('file', file)

      try {
        await api.post('/api/admin/upload', formData, {
          headers: { 'Content-Type': 'multipart/form-data' }
        })
        successCount++
      } catch (err) {
        const msg = err.response?.data?.error || `Greška kod fajla: ${file.name}`
        setError(msg)
        setLoading(false)
        return
      }
    }

    setLoading(false)
    if (successCount === files.length) {
      onSuccess()
      onClose()
    }
  }

  const handleDragOver = (e) => {
    e.preventDefault()
    setIsDragging(true)
  }
  const handleDragLeave = () => setIsDragging(false)
  const handleDrop = (e) => {
    e.preventDefault()
    setIsDragging(false)
    if (e.dataTransfer.files && e.dataTransfer.files.length > 0) {
      setFiles(Array.from(e.dataTransfer.files))
      setError(null)
    }
  }

  return (
    <div className="fixed inset-0 bg-gray-900/60 backdrop-blur-sm flex items-center justify-center z-[60] p-4">
      <div className="bg-white rounded-3xl shadow-2xl w-full max-w-md overflow-hidden animate-in zoom-in duration-200">

        {/* HEADER */}
        <div className="bg-blue-600 p-6 text-white text-center">
          <div className="text-4xl mb-2">📥</div>
          <h3 className="text-xl font-bold italic tracking-tight">Import Studenata</h3>
          <p className="text-blue-100 text-[11px] mt-1 opacity-80 font-medium">Excel (.xlsx) ili CSV liste</p>
        </div>

        <div className="p-6">
          {/* DROP ZONA */}
          <div
            onDragOver={handleDragOver}
            onDragLeave={handleDragLeave}
            onDrop={handleDrop}
            onClick={() => !loading && fileInputRef.current.click()}
            className={`
              relative border-2 border-dashed rounded-2xl p-6 text-center transition-all cursor-pointer
              ${isDragging ? 'border-blue-500 bg-blue-50 scale-[1.02]' : 'border-gray-200 hover:border-blue-400 hover:bg-gray-50'}
              ${loading ? 'opacity-50 cursor-not-allowed' : ''}
            `}
          >
            <input
              type="file"
              accept=".xlsx,.csv"
              multiple
              onChange={handleFileChange}
              className="hidden"
              ref={fileInputRef}
              disabled={loading}
            />

            {files.length > 0 ? (
              <div className="space-y-2">
                <div className="max-h-40 overflow-y-auto px-1 space-y-1 custom-scrollbar">
                  {files.map((f, i) => (
                    <div
                      key={i}
                      title={f.name}
                      className="flex items-center gap-3 text-xs text-blue-700 bg-blue-50/50 px-3 py-2 rounded-xl border border-blue-100/50 hover:bg-blue-100 transition-colors"
                    >
                      <span className="shrink-0 text-blue-400">📄</span>
                      <span className="truncate font-mono font-medium flex-1 text-left">{f.name}</span>
                      <span className="shrink-0 text-[10px] text-blue-300 font-bold uppercase">
                        {(f.size / 1024).toFixed(0)} KB
                      </span>
                    </div>
                  ))}
                </div>
                {!loading && (
                  <p className="text-[10px] text-gray-400 font-bold uppercase mt-3 tracking-widest">
                    Kliknite za promjenu liste
                  </p>
                )}
              </div>
            ) : (
              <div className="py-4">
                <p className="font-black text-gray-600 text-sm uppercase tracking-tighter">Odaberite fajlove</p>
                <p className="text-xs text-gray-400 mt-1 italic">Ili ih jednostavno prevucite ovdje</p>
              </div>
            )}
          </div>

          {/* PROGRESS BAR */}
          {loading && (
            <div className="mt-6 animate-in fade-in duration-500">
              <div className="flex justify-between text-[10px] font-black text-blue-600 mb-1.5 uppercase tracking-widest">
                <span>Upload u toku...</span>
                <span>{currentFileIndex} / {files.length}</span>
              </div>
              <div className="w-full bg-gray-100 h-2.5 rounded-full overflow-hidden shadow-inner">
                <div
                  className="bg-blue-600 h-full transition-all duration-500 ease-out shadow-[0_0_10px_rgba(37,99,235,0.4)]"
                  style={{ width: `${(currentFileIndex / files.length) * 100}%` }}
                />
              </div>
            </div>
          )}

          {/* ERROR */}
          {error && (
            <div className="mt-4 p-3 bg-red-50 border border-red-100 rounded-xl flex gap-3 items-start animate-shake">
              <span className="text-red-500">⚠️</span>
              <p className="text-red-700 text-[11px] font-bold leading-snug">{error}</p>
            </div>
          )}

          {/* AKCIJE */}
          <div className="flex gap-3 mt-8">
            <button
              onClick={onClose}
              disabled={loading}
              className="flex-1 px-4 py-3.5 border border-gray-200 text-gray-500 rounded-2xl font-black text-xs uppercase tracking-widest hover:bg-gray-50 transition-all disabled:opacity-30"
            >
              Odustani
            </button>
            <button
              onClick={handleUpload}
              disabled={loading || files.length === 0}
              className="flex-1 px-4 py-3.5 bg-gray-900 text-white rounded-2xl font-black text-xs uppercase tracking-widest hover:bg-blue-600 shadow-xl shadow-gray-200 transition-all active:scale-95 disabled:bg-gray-200 disabled:shadow-none disabled:text-gray-400"
            >
              {loading ? 'Slanje...' : `Import (${files.length})`}
            </button>
          </div>
        </div>
      </div>
    </div>
  )
}

export default UploadModal