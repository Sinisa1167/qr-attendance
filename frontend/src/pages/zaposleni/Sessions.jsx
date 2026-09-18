import { useState, useEffect } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import api from '../../api/axiosInstance'
import { Trash2 } from 'lucide-react'

const ACTIVITY_TYPE_MAP = {
  'лабораторијске вјежбе': 'LABORATORIJSKE_VJEZBE',
  'laboratorijske vjezbe': 'LABORATORIJSKE_VJEZBE',
  'лабораторијске вежбе': 'LABORATORIJSKE_VJEZBE',
  'auditorne vjezbe': 'AUDITORNE_VJEZBE',
  'аудиторне вјежбе': 'AUDITORNE_VJEZBE',
  'вјежбе': 'AUDITORNE_VJEZBE',
  'vjezbe': 'AUDITORNE_VJEZBE',
  'predavanje': 'PREDAVANJE',
  'предавање': 'PREDAVANJE',
}

function mapTeachingType(teachingType) {
  if (!teachingType) return 'PREDAVANJE'
  const key = teachingType.toLowerCase().trim()
  return ACTIVITY_TYPE_MAP[key] || 'PREDAVANJE'
}

function Sessions() {
  const { subjectId } = useParams()
  const navigate = useNavigate()
  const [subject, setSubject] = useState(null)
  const [sessions, setSessions] = useState([])
  const [loading, setLoading] = useState(true)
  const [showCreateForm, setShowCreateForm] = useState(false)
  const [newSession, setNewSession] = useState(null)

  useEffect(() => {
    fetchData()
  }, [subjectId])

  const fetchData = async () => {
    try {
      const [subjectsRes, sessionsRes] = await Promise.all([
        api.get('/api/admin/subjects'),
        api.get(`/api/admin/sessions/subject/${subjectId}`)
      ])
      const found = subjectsRes.data.find(s => s.id.toString() === subjectId.toString())
      setSubject(found)

      // Postavi defaulte na osnovu predmeta
      if (found) {
        setNewSession({
          activityType: mapTeachingType(found.teachingType),
          date: new Date().toISOString().split('T')[0],
          startTime: '08:00',
          endTime: '09:30',
          groupNumber: found.groupName || '1',
        })
      }

      const sorted = sessionsRes.data.sort((a, b) => {
        const order = { ACTIVE: 0, CREATED: 1, CLOSED: 2 }
        return order[a.status] - order[b.status]
      })
      setSessions(sorted)
    } catch (err) {
      console.error("Greška pri učitavanju:", err)
    } finally {
      setLoading(false)
    }
  }

  const fetchSessions = async () => {
    try {
      const res = await api.get(`/api/admin/sessions/subject/${subjectId}`)
      const sorted = res.data.sort((a, b) => {
        const order = { ACTIVE: 0, CREATED: 1, CLOSED: 2 }
        return order[a.status] - order[b.status]
      })
      setSessions(sorted)
    } catch (err) {
      console.error("Greška pri učitavanju sesija:", err)
    }
  }

    const createSession = async () => {
    if (!newSession.date || !newSession.startTime || !newSession.endTime) {
      alert("Molimo popunite sva polja")
      return
    }

    const start = new Date(`${newSession.date}T${newSession.startTime}`)
    const end = new Date(`${newSession.date}T${newSession.endTime}`)

    if (start < new Date()) {
      alert("Ne možete kreirati sesiju sa terminom u prošlosti")
      return
    }
    if (end <= start) {
      alert("Vrijeme završetka mora biti poslije vremena početka")
      return
    }

    try {
      const sessionData = {
        subject: { id: subjectId },
        ...newSession,
        status: 'CREATED'
      }
      await api.post('/api/admin/sessions', sessionData)
      setShowCreateForm(false)
      await fetchSessions()
    } catch (err) {
      alert(err.response?.data?.error || "Greška pri kreiranju sesije")
    }
  }

  const deleteSession = async (sessionId) => {
    if (!window.confirm('Da li ste sigurni da želite obrisati ovu sesiju?')) return
    try {
      await api.delete(`/api/admin/sessions/${sessionId}`)
      await fetchSessions()
    } catch (err) {
      alert(err.response?.data?.error || 'Greška pri brisanju sesije')
    }
  }

  const closeSession = async (sessionId) => {
    if (!window.confirm('Zatvori sesiju? Nakon zatvaranja prijave više nisu moguće.')) return
    try {
      await api.post(`/api/admin/sessions/${sessionId}/close`)
      await fetchSessions()
    } catch (err) {
      console.error(err)
    }
  }

  const activateSession = async (sessionId) => {
    try {
      await api.post(`/api/admin/sessions/${sessionId}/activate`)
      navigate(`/session/${sessionId}/live`)
    } catch (err) {
      alert("Nije moguće aktivirati sesiju. Provjerite da li već postoji aktivna sesija.")
    }
  }

  const sessionFilename = (session, ext) => {
    const tip = (session.activityType || 'Sesija').replace(/_/g, '-').toLowerCase()
    const datum = session.date
      ? new Date(session.date).toLocaleDateString('de-DE').replace(/\./g, '-').replace(/-+$/, '')
      : 'datum'
    const grupa = subject?.groupName || session.groupNumber || '1'
    return `Sesija_${tip}_${datum}_Grupa${grupa}.${ext}`
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
      alert("Greška prilikom generisanja fajla.")
    }
  }

  const getStatusBadge = (status) => {
    const styles = {
      ACTIVE: 'bg-green-100 text-green-700 border-green-200 animate-pulse',
      CLOSED: 'bg-gray-100 text-gray-700 border-gray-200',
      CREATED: 'bg-blue-100 text-blue-700 border-blue-200'
    }
    const labels = { ACTIVE: 'Aktivna', CLOSED: 'Zatvorena', CREATED: 'Kreirana' }
    return (
      <span className={`text-[10px] uppercase font-black px-2 py-0.5 rounded-full border ${styles[status] || styles.CREATED}`}>
        {labels[status] || status}
      </span>
    )
  }

  if (loading || !newSession) return (
    <div className="flex justify-center items-center h-64">
      <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-blue-600"></div>
    </div>
  )

  return (
    <div className="max-w-5xl mx-auto p-4">
      {/* SUBJECT INFO HEADER */}
      <div className="bg-white rounded-2xl shadow-sm border border-gray-100 p-6 mb-8 flex flex-col md:flex-row justify-between items-center gap-4">
        <div>
          <h2 className="text-3xl font-black text-gray-900">{subject?.name}</h2>
          <div className="flex items-center gap-3 mt-2">
            <span className="font-mono text-blue-600 font-bold">{subject?.code}</span>
            <span className="text-gray-300">|</span>
            <span className="text-gray-500 text-sm font-medium">{subject?.studyProgram} (Semestar {subject?.semester})</span>
          </div>
          <div className="flex gap-2 mt-4">
            <span className="bg-purple-50 text-purple-700 text-[10px] font-bold px-2 py-1 rounded border border-purple-100 uppercase tracking-wider">
              {subject?.teachingType}
            </span>
            <span className="bg-emerald-50 text-emerald-700 text-[10px] font-bold px-2 py-1 rounded border border-emerald-100 uppercase tracking-wider">
              Grupa {subject?.groupName}
            </span>
          </div>
        </div>

        <div className="flex flex-col gap-2 w-full md:w-auto">
          <button
            onClick={() => navigate(`/subject/${subjectId}/analytics`)}
            className="flex items-center justify-center gap-2 bg-violet-600 text-white px-4 py-2 rounded-xl font-bold hover:bg-violet-700 transition-all text-sm shadow-lg shadow-violet-100"
          >
            📈 Analitika predmeta
          </button>
          <div className="grid grid-cols-2 gap-2">
            <button
              onClick={() => downloadFile(`/api/admin/export/subject/${subjectId}/xlsx`, `Analitika_${subject?.code}_${subject?.teachingType?.replace(/\s+/g, '_') || ''}.xlsx`)}
              className="flex items-center justify-center gap-2 bg-emerald-600 text-white px-4 py-2 rounded-xl font-bold hover:bg-emerald-700 transition-all text-xs"
            >
              📊 XLSX
            </button>
            <button
              onClick={() => downloadFile(`/api/admin/export/subject/${subjectId}/pdf`, `Analitika_${subject?.code}_${subject?.teachingType?.replace(/\s+/g, '_') || ''}.pdf`)}
              className="flex items-center justify-center gap-2 bg-red-500 text-white px-4 py-2 rounded-xl font-bold hover:bg-red-600 transition-all text-xs"
            >
              📄 PDF
            </button>
          </div>
        </div>
      </div>

      <div className="flex justify-between items-center   mb-6">
        <h3 className="text-xl font-bold text-gray-800">Istorija sesija</h3>
        <button
          onClick={() => setShowCreateForm(!showCreateForm)}
          className={`px-5 py-2 rounded-xl font-bold transition-all ${showCreateForm ? 'bg-gray-100 text-gray-600' : 'bg-blue-600 text-white shadow-lg shadow-blue-100'}`}
        >
          {showCreateForm ? 'Zatvori' : '+ Nova sesija'}
        </button>
      </div>

      {/* CREATE FORM */}
      {showCreateForm && (
        <div className="bg-blue-50 border border-blue-100 rounded-2xl p-6 mb-8 animate-in fade-in slide-in-from-top-4 duration-300">
          <h4 className="font-bold text-blue-900 mb-4">Parametri nove sesije</h4>
          <div className="grid grid-cols-1 md:grid-cols-5 gap-4">
            <div>
              <label className="block text-[10px] font-bold text-blue-400 uppercase mb-1 ml-1">Tip</label>
              <select
                value={newSession.activityType}
                onChange={(e) => setNewSession({...newSession, activityType: e.target.value})}
                className="w-full bg-white border-0 rounded-xl p-3 text-sm focus:ring-2 focus:ring-blue-500 outline-none shadow-sm"
              >
                <option value="PREDAVANJE">Predavanje</option>
                <option value="AUDITORNE_VJEZBE">Auditorne vježbe</option>
                <option value="LABORATORIJSKE_VJEZBE">Laboratorijske vježbe</option>
              </select>
            </div>
            <div>
              <label className="block text-[10px] font-bold text-blue-400 uppercase mb-1 ml-1">Grupa</label>
              <input
                type="text"
                value={newSession.groupNumber}
                onChange={(e) => setNewSession({...newSession, groupNumber: e.target.value})}
                className="w-full bg-white border-0 rounded-xl p-3 text-sm focus:ring-2 focus:ring-blue-500 outline-none shadow-sm"
                placeholder="npr. G1"
              />
            </div>
            <div>
              <label className="block text-[10px] font-bold text-blue-400 uppercase mb-1 ml-1">Datum</label>
              <input
                type="date"
                min={new Date().toISOString().split('T')[0]}
                value={newSession.date}
                onChange={(e) => setNewSession({...newSession, date: e.target.value})}
                className="w-full bg-white border-0 rounded-xl p-3 text-sm focus:ring-2 focus:ring-blue-500 outline-none shadow-sm"
              />
            </div>
            <div>
              <label className="block text-[10px] font-bold text-blue-400 uppercase mb-1 ml-1">Početak</label>
              <input
                type="time"
                value={newSession.startTime}
                onChange={(e) => setNewSession({...newSession, startTime: e.target.value})}
                className="w-full bg-white border-0 rounded-xl p-3 text-sm focus:ring-2 focus:ring-blue-500 outline-none shadow-sm"
              />
            </div>
            <div>
              <label className="block text-[10px] font-bold text-blue-400 uppercase mb-1 ml-1">Kraj</label>
              <input
                type="time"
                value={newSession.endTime}
                onChange={(e) => setNewSession({...newSession, endTime: e.target.value})}
                className="w-full bg-white border-0 rounded-xl p-3 text-sm focus:ring-2 focus:ring-blue-500 outline-none shadow-sm"
              />
            </div>
          </div>
          <div className="flex justify-end mt-6">
            <button
              onClick={createSession}
              className="bg-blue-600 text-white px-8 py-3 rounded-xl font-bold hover:bg-blue-700 transition-all shadow-lg shadow-blue-200"
            >
              Potvrdi i kreiraj
            </button>
          </div>
        </div>
      )}

      {/* SESSIONS LIST */}
      <div className="space-y-4">
        {sessions.length === 0 ? (
          <div className="bg-white rounded-2xl p-12 text-center border border-dashed border-gray-200">
            <p className="text-gray-400 font-medium italic">Nema evidentiranih sesija za ovaj predmet.</p>
          </div>
        ) : (
          sessions.map((session) => (
            <div
              key={session.id}
              className="group bg-white rounded-2xl p-5 border border-gray-100 shadow-sm hover:shadow-md transition-all flex flex-col md:flex-row justify-between items-center gap-4"
            >
              <div className="flex items-center gap-4 w-full md:w-auto">
                <div className={`p-3 rounded-xl ${session.status === 'ACTIVE' ? 'bg-green-50 text-green-600' : 'bg-gray-50 text-gray-400'}`}>
                  📅
                </div>
                <div>
                  <div className="flex items-center gap-2 mb-1">
                    <span className="font-bold text-gray-800">
                      {session.activityType?.replace(/_/g, ' ')}
                    </span>
                    {session.groupNumber && (
                      <span className="text-[10px] font-bold text-emerald-700 bg-emerald-50 px-2 py-0.5 rounded-full border border-emerald-100">
                        Grupa {session.groupNumber}
                      </span>
                    )}
                    {getStatusBadge(session.status)}
                  </div>
                  <p className="text-gray-500 text-xs font-mono font-medium">
                    {new Date(session.date).toLocaleDateString('de-DE')} | {session.startTime?.substring(0,5)} - {session.endTime?.substring(0,5)}
                  </p>
                </div>
              </div>

              <div className="flex gap-2 w-full md:w-auto justify-end flex-wrap">
                {session.status === 'CREATED' && (
                  <>
                    <button
                      onClick={() => activateSession(session.id)}
                      className="flex-1 md:flex-none bg-green-600 text-white px-4 py-2 rounded-xl font-bold hover:bg-green-700 transition-all text-xs"
                    >
                      Aktiviraj
                    </button>
                    <button
                      onClick={() => deleteSession(session.id)}
                      className="p-2 text-gray-400 hover:text-red-500 transition-colors">
                      <Trash2 size={16} />
                    </button>
                  </>
                )}

                {session.status === 'ACTIVE' && (
                  <>
                    <button
                      onClick={() => navigate(`/session/${session.id}/live`)}
                      className="flex-1 md:flex-none bg-blue-600 text-white px-4 py-2 rounded-xl font-bold hover:bg-blue-700 transition-all text-xs"
                    >
                      Live Monitor
                    </button>
                    <button
                      onClick={() => closeSession(session.id)}
                      className="flex-1 md:flex-none bg-red-500 text-white px-4 py-2 rounded-xl font-bold hover:bg-red-600 transition-all text-xs"
                    >
                      Zatvori
                    </button>
                  </>
                )}

                {session.status === 'CLOSED' && (
                  <div className="flex gap-2 items-center">
                    <button
                      onClick={() => downloadFile(`/api/admin/export/session/${session.id}/xlsx`, sessionFilename(session, 'xlsx'))}
                      className="bg-emerald-50 text-emerald-700 px-3 py-2 rounded-lg font-bold hover:bg-emerald-100 transition-all text-[10px] uppercase border border-emerald-100"
                    >
                      xlsx
                    </button>
                    <button
                      onClick={() => downloadFile(`/api/admin/export/session/${session.id}/pdf`, sessionFilename(session, 'pdf'))}
                      className="bg-red-50 text-red-700 px-3 py-2 rounded-lg font-bold hover:bg-red-100 transition-all text-[10px] uppercase border border-red-100"
                    >
                      pdf
                    </button>
                    <button
                      onClick={() => deleteSession(session.id)}
                      className="p-2 text-gray-400 hover:text-red-500 transition-colors">
                      <Trash2 size={16} />
                    </button>
                  </div>
                )}
              </div>
            </div>
          ))
        )}
      </div>
    </div>
  )
}

export default Sessions