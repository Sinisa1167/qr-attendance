import { useState, useEffect } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import api from '../../api/axiosInstance'

function Sessions() {
  const { subjectId } = useParams()
  const navigate = useNavigate()
  const [subject, setSubject] = useState(null)
  const [sessions, setSessions] = useState([])
  const [loading, setLoading] = useState(true)
  const [showCreateForm, setShowCreateForm] = useState(false)
  const [newSession, setNewSession] = useState({
    activityType: 'PREDAVANJE',
    date: '',
    startTime: '',
    endTime: '',  
  })

  useEffect(() => {
    fetchData()
  }, [subjectId])

  const fetchData = async () => {
    try {
      const [subjectsRes, sessionsRes] = await Promise.all([
        api.get('/api/admin/subjects'),
        api.get(`/api/admin/sessions/subject/${subjectId}`)
      ])
      const found = subjectsRes.data.find(s => s.id === subjectId)
      setSubject(found)
      setSessions(sessionsRes.data)
    } catch (err) {
      console.error(err)
    } finally {
      setLoading(false)
    }
  }

  const createSession = async () => {
    try {
      const sessionData = {
        subject: { id: subjectId },
        ...newSession,
        groupNumber: subject?.groupName
      }
      await api.post('/api/admin/sessions', sessionData)
      setShowCreateForm(false)
      fetchData()
    } catch (err) {
      console.error(err)
    }
  }

  const deleteSession = async (sessionId) => {
    if (!window.confirm('Da li ste sigurni da želite obrisati ovu sesiju?')) return
    try {
      await api.delete(`/api/admin/sessions/${sessionId}`)
      fetchData()
    } catch (err) {
      alert(err.response?.data?.error || 'Greška pri brisanju sesije')
    }
  }

  const activateSession = async (sessionId) => {
    try {
      await api.post(`/api/admin/sessions/${sessionId}/activate`)
      navigate(`/session/${sessionId}/live`)
    } catch (err) {
      console.error(err)
    }
  }

  const closeSession = async (sessionId) => {
    try {
      await api.post(`/api/admin/sessions/${sessionId}/close`)
      fetchData()
    } catch (err) {
      console.error(err)
    }
  }

  const getStatusColor = (status) => {
    switch (status) {
      case 'ACTIVE': return 'bg-green-100 text-green-700'
      case 'CLOSED': return 'bg-gray-100 text-gray-700'
      default: return 'bg-yellow-100 text-yellow-700'
    }
  }

  const getStatusLabel = (status) => {
    switch (status) {
      case 'ACTIVE': return 'Aktivna'
      case 'CLOSED': return 'Zatvorena'
      default: return 'Kreirana'
    }
  }

  const formatDate = (date) => {
    if (!date) return ''

    const [year, month, day] = date.split('-')
    return `${day}.${month}.${year}`
  }

  const downloadFile = async (url, filename) => {
  try {
    const response = await api.get(url, { responseType: 'blob' })
    const blob = new Blob([response.data])
    const link = document.createElement('a')
    link.href = window.URL.createObjectURL(blob)
    link.download = filename
    link.click()
    window.URL.revokeObjectURL(link.href)
  } catch (err) {
    console.error('Greška pri downloadu', err)
  }
}

  if (loading) return <p className="text-gray-500 p-6">Učitavanje...</p>

  return (
<div className="max-w-4xl mx-auto">
  <div className="flex justify-between items-start mb-6">
    <div>
      <h2 className="text-2xl font-bold text-gray-800">{subject?.name} ({subject?.code})</h2>
      <p className="text-gray-600 text-xs mb-2">Semestar {subject?.semester} | {subject?.studyProgram}</p>
      <div className="flex gap-2">
        <span className="bg-purple-200 text-purple-800 text-m px-2 py-1 rounded">
          {subject?.teachingType}
        </span>
        <span className="bg-green-200 text-green-800 text-m px-2 py-1 rounded">
          Grupa {subject?.groupName}
        </span>
      </div>
    </div>
    <div className="flex gap-2">
      <button
        onClick={() => downloadFile(
          `/api/admin/export/subject/${subjectId}/xlsx`, 
          `prisustvo_${subject?.code}_${subject?.teachingType?.substring(0,3)}_G${subject?.groupName}.xlsx`
        )}
        className="bg-green-600 text-white px-3 py-2 rounded hover:bg-green-700 text-sm"
      >
        Export xlsx
      </button>
      <button
        onClick={() => downloadFile(
          `/api/admin/export/subject/${subjectId}/pdf`, 
          `prisustvo_${subject?.code}_${subject?.teachingType?.substring(0,3)}_G${subject?.groupName}.pdf`
        )}
        className="bg-red-600 text-white px-3 py-2 rounded hover:bg-red-700 text-sm"
      >
        Export PDF
      </button>
    </div>
  </div>

      <div className="flex justify-between items-center mb-4">
        <h3 className="text-lg font-semibold text-gray-700">Sesije</h3>
        <button
          onClick={() => setShowCreateForm(!showCreateForm)}
          className="bg-blue-600 text-white px-4 py-2 rounded hover:bg-blue-700"
        >
          + Nova sesija
        </button>
      </div>

      {showCreateForm && (
        <div className="bg-white rounded-lg shadow p-4 mb-4">
          <h4 className="font-semibold mb-3">Kreiranje nove sesije</h4>
          <div className="grid grid-cols-2 gap-3">
            <div>
              <label className="text-sm text-gray-600">Tip aktivnosti</label>
              <select
                value={newSession.activityType}
                onChange={(e) => setNewSession({...newSession, activityType: e.target.value})}
                className="w-full border rounded p-2 mt-1"
              >
                <option value="PREDAVANJE">Predavanje</option>
                <option value="AUDITORNE_VJEZBE">Auditorne vježbe</option>
                <option value="LABORATORIJSKE_VJEZBE">Laboratorijske vježbe</option>
              </select>
            </div>
            <div>
              <label className="text-sm text-gray-600">Datum</label>
              <input
                type="date"
                value={newSession.date}
                onChange={(e) => setNewSession({...newSession, date: e.target.value})}
                className="w-full border rounded p-2 mt-1"
              />
            </div>
            <div>
              <label className="text-sm text-gray-600">Početak</label>
              <input
                type="time"
                value={newSession.startTime}
                onChange={(e) => setNewSession({...newSession, startTime: e.target.value})}
                className="w-full border rounded p-2 mt-1"
              />
            </div>
            <div>
              <label className="text-sm text-gray-600">Kraj</label>
              <input
                type="time"
                value={newSession.endTime}
                onChange={(e) => setNewSession({...newSession, endTime: e.target.value})}
                className="w-full border rounded p-2 mt-1"
              />
            </div>
          </div>
          <div className="flex gap-3 mt-4 justify-end">
            <button
              onClick={() => setShowCreateForm(false)}
              className="px-4 py-2 border border-gray-300 rounded hover:bg-gray-50"
            >
              Otkaži
            </button>
            <button
              onClick={createSession}
              className="px-4 py-2 bg-blue-600 text-white rounded hover:bg-blue-700"
            >
              Kreiraj
            </button>
          </div>
        </div>
      )}

      {sessions.length === 0 && (
        <div className="bg-white rounded-lg p-8 text-center shadow">
          <p className="text-gray-500">Nema sesija za ovaj predmet.</p>
        </div>
      )}

      <div className="space-y-3">
        {sessions.map((session) => (
          <div key={session.id} className="bg-white rounded-lg shadow p-4 flex justify-between items-center">
            <div>
              <div className="flex items-center gap-2 mb-1">
                <span className="font-semibold">
                  {session.activityType?.replace(/_/g, ' ')}
                </span>
                <span className={`text-xs px-2 py-1 rounded ${getStatusColor(session.status)}`}>
                  {getStatusLabel(session.status)}
                </span>
              </div>
              <p className="text-gray-500 text-sm">
                {formatDate(session.date)} | {session.startTime?.substring(0,5)} - {session.endTime?.substring(0,5)}
              </p>
            </div>
            <div className="flex gap-2">
              {session.status === 'CREATED' && (
                <div className="flex gap-2">
                  <button
                    onClick={() => activateSession(session.id)}
                    className="bg-green-600 text-white px-3 py-1 rounded hover:bg-green-700 text-sm"
                  >
                    Aktiviraj
                  </button>
                  <button
                    onClick={() => deleteSession(session.id)}
                    className="bg-red-600 text-white px-3 py-1 rounded hover:bg-red-700 text-sm"
                  >
                    Obriši
                  </button>
                </div>
              )}
              {session.status === 'ACTIVE' && (
                <>
                  <button
                    onClick={() => navigate(`/session/${session.id}/live`)}
                    className="bg-blue-600 text-white px-3 py-1 rounded hover:bg-blue-700 text-sm"
                  >
                    Dashboard
                  </button>
                  <button
                    onClick={() => closeSession(session.id)}
                    className="bg-red-600 text-white px-3 py-1 rounded hover:bg-red-700 text-sm"
                  >
                    Zatvori
                  </button>
                </>
              )}
              {session.status === 'CLOSED' && (
                <div className="flex gap-2">
                  <button
                    onClick={() => downloadFile(
                      `/api/admin/export/session/${session.id}/xlsx`, 
                      `prisustvo_${subject?.code}_G${subject?.groupName}_${formatDate(session.date)}.xlsx`
                    )}
                    className="bg-green-600 text-white px-3 py-1 rounded hover:bg-green-700 text-sm"
                  >
                    xlsx
                  </button>
                  <button
                    onClick={() => downloadFile(
                      `/api/admin/export/session/${session.id}/pdf`, 
                      `prisustvo_${subject?.code}_G${subject?.groupName}_${formatDate(session.date)}.pdf`
                    )}
                    className="bg-red-600 text-white px-3 py-1 rounded hover:bg-red-700 text-sm"
                  >
                    PDF
                  </button>
                  <button
                    onClick={() => deleteSession(session.id)}
                    className="border border-red-300 text-red-600 px-3 py-1 rounded hover:bg-red-50 text-sm"
                  >
                    Obriši
                  </button>
                </div>
              )}
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}

export default Sessions