import { useState, useEffect } from 'react'
import { useNavigate } from 'react-router-dom'
import api from '../../api/axiosInstance'
import UploadModal from '../../components/UploadModal'

function Dashboard() {
  const [subjects, setSubjects] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [showUpload, setShowUpload] = useState(false)
  const [collapsed, setCollapsed] = useState({})
  const navigate = useNavigate()

  useEffect(() => {
    fetchSubjects()
  }, [])

  const fetchSubjects = async () => {
    try {
      const response = await api.get('/api/admin/subjects')
      setSubjects(response.data)
    } catch (err) {
      setError('Greška pri učitavanju predmeta')
    } finally {
      setLoading(false)
    }
  }

  const deleteSubject = async (subjectId) => {
    if (!window.confirm('Da li ste sigurni? Brišu se sve sesije i prisustvo.')) return
    try {
      await api.delete(`/api/admin/subjects/${subjectId}`)
      fetchSubjects()
    } catch (err) {
      alert('Greška pri brisanju predmeta')
    }
  }

  const toggleCollapse = (key) => {
    setCollapsed(prev => ({ ...prev, [key]: !prev[key] }))
  }

  // grupisanje: academicYear - studyYear - subjects
  const grouped = subjects.reduce((acc, subject) => {
    const year = subject.academicYear || 'Nepoznato'
    const studyYear = subject.studyYear || 0
    if (!acc[year]) acc[year] = {}
    if (!acc[year][studyYear]) acc[year][studyYear] = []
    acc[year][studyYear].push(subject)
    return acc
  }, {})

  // Sortiraj akademske godine opadajuce  
  const sortedAcademicYears = Object.keys(grouped).sort((a, b) => b.localeCompare(a))

  return (
    <div className="max-w-6xl mx-auto">
      <div className="flex justify-between items-center mb-6">
        <h2 className="text-2xl font-bold text-gray-800">Moji predmeti</h2>
        <button
          onClick={() => setShowUpload(true)}
          className="bg-blue-600 text-white px-4 py-2 rounded hover:bg-blue-700"
        >
          + Dodaj predmet
        </button>
      </div>

      {loading && <p className="text-gray-500">Učitavanje...</p>}
      {error && <p className="text-red-500">{error}</p>}

      {!loading && subjects.length === 0 && (
        <div className="bg-white rounded-lg p-8 text-center shadow">
          <p className="text-gray-500 text-lg">Nemate još predmeta.</p>
          <p className="text-gray-400 mt-2">Uploadujte Excel listu da dodate predmet.</p>
        </div>
      )}

      {sortedAcademicYears.map((academicYear) => (
        <div key={academicYear} className="mb-8">
          {/* Akademska godina - header */}
          <div className="bg-blue-600 text-white px-4 py-3 rounded-lg mb-4 flex justify-between items-center">
            <h3 className="text-lg font-bold">Akademska godina {academicYear}</h3>
            <span className="bg-white text-blue-600 text-sm px-2 py-1 rounded-full font-bold">
              {Object.values(grouped[academicYear]).flat().length} predmeta
            </span>
          </div>

          {/* Godina studija */}
          {Object.keys(grouped[academicYear])
            .sort((a, b) => Number(a) - Number(b))
            .map((studyYear) => {
              const key = `${academicYear}-${studyYear}`
              const isCollapsed = collapsed[key]
              const subjectsInYear = grouped[academicYear][studyYear]
                .sort((a, b) => {
                  // Sortiraj po tipu nastave pa po grupi
                  if (a.teachingType < b.teachingType) return -1
                  if (a.teachingType > b.teachingType) return 1
                  return (a.groupName || '').localeCompare(b.groupName || '')
                })

              return (
                <div key={key} className="mb-4 ml-2">
                  {/* Godina studija - header */}
                  <button
                    onClick={() => toggleCollapse(key)}
                    className="w-full flex justify-between items-center bg-gray-100 hover:bg-gray-200 px-4 py-2 rounded-lg mb-3 transition"
                  >
                    <span className="font-semibold text-gray-700">
                      {isCollapsed ? '▶' : '▼'} {studyYear}. godina studija
                    </span>
                    <span className="bg-gray-300 text-gray-700 text-xs px-2 py-1 rounded-full">
                      {subjectsInYear.length} predmeta
                    </span>
                  </button>

                  {/* Kartice predmeta */}
                  {!isCollapsed && (
                    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4 ml-2">
                      {subjectsInYear.map((subject) => (
                        <div key={subject.id} className="bg-white rounded-lg shadow p-4 hover:shadow-md transition">
                          <h3 className="font-bold text-gray-800 mb-1">{subject.name} ({subject.code})</h3>
                          <p className="text-gray-500 text-xs mb-2"> Sem. {subject.semester}  |  {subject.studyProgram}</p>
                          <div className="flex flex-wrap gap-1 mb-3">
                            <span className="bg-purple-100 text-purple-700 text-s px-2 py-1 rounded">
                              {subject.teachingType}
                            </span>
                            <span className="bg-green-100 text-green-700 text-s px-2 py-1 rounded">
                              Grupa {subject.groupName}
                            </span>
                          </div>
                          <button
                            onClick={() => navigate(`/subject/${subject.id}`)}
                            className="w-full bg-blue-600 text-white py-1.5 rounded hover:bg-blue-700 text-sm mb-2"
                          >
                            Upravljaj sesijama
                          </button>
                          <button
                            onClick={() => deleteSubject(subject.id)}
                            className="w-full border border-red-300 text-red-600 py-1.5 rounded hover:bg-red-50 text-sm"
                          >
                            Ukloni predmet
                          </button>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              )
            })}
        </div>
      ))}

      {showUpload && (
        <UploadModal
          onClose={() => setShowUpload(false)}
          onSuccess={fetchSubjects}
        />
      )}
    </div>
  )
}

export default Dashboard