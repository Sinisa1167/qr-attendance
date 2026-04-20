import { useState, useEffect, useMemo, useRef } from 'react'
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

  const sectionRefs = useRef({})

  // Ključ za localStorage
  const STORAGE_KEY = 'dashboard_collapsed_states'

  useEffect(() => {
    fetchSubjects()

    // Učitaj sačuvano stanje iz localStorage
    const saved = localStorage.getItem(STORAGE_KEY)
    if (saved) {
      try {
        setCollapsed(JSON.parse(saved))
      } catch (e) {
        console.warn('Greška pri učitavanju stanja collapsible sekcija')
      }
    }
  }, [])

  // localStorage
  useEffect(() => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(collapsed))
  }, [collapsed])

  const fetchSubjects = async () => {
    try {
      const response = await api.get('/api/admin/subjects')
      const data = response.data
      setSubjects(data)
    } catch (err) {
      setError('Greška pri učitavanju predmeta')
    } finally {
      setLoading(false)
    }
  }

  const toggleCollapse = (key) => {
    setCollapsed(prev => {
      const newState = { ...prev, [key]: !prev[key] }
      return newState
    })

    // Scroll na sekciju
    if (!collapsed[key]) {
      setTimeout(() => {
        sectionRefs.current[key]?.scrollIntoView({ 
          behavior: 'smooth', 
          block: 'start' 
        })
      }, 100)
    }
  }

  const deleteSubject = async (subjectId) => {
    if (!window.confirm('PAŽNJA: Brisanjem grupe brišete sve njene sesije i evidenciju prisustva.')) return
    try {
      await api.delete(`/api/admin/subjects/${subjectId}`)
      fetchSubjects()
    } catch (err) {
      alert('Greška pri brisanju grupe')
    }
  }

  // Grupisanje podataka
  const groupedData = useMemo(() => {
    const result = {}

    subjects.forEach(subject => {
      const academicYear = subject.academicYear || 'Nepoznato'
      const studyYear = subject.studyYear || 0

      if (!result[academicYear]) result[academicYear] = {}
      if (!result[academicYear][studyYear]) result[academicYear][studyYear] = {}

      const subjectBaseKey = `${subject.name}-${subject.code}-${subject.teachingType}`

      if (!result[academicYear][studyYear][subjectBaseKey]) {
        result[academicYear][studyYear][subjectBaseKey] = {
          name: subject.name,
          code: subject.code,
          studyProgram: subject.studyProgram,
          semester: subject.semester,
          teachingType: subject.teachingType,
          groups: []
        }
      }

      result[academicYear][studyYear][subjectBaseKey].groups.push({
        id: subject.id,
        groupName: subject.groupName || '1'
      })
    })

    return result
  }, [subjects])

  const sortedAcademicYears = useMemo(() => 
    Object.keys(groupedData).sort((a, b) => b.localeCompare(a)), 
  [groupedData])

  return (
    <div className="max-w-7xl mx-auto p-4 lg:p-6">
      {/* HEADER */}
      <div className="flex flex-col md:flex-row justify-between items-start md:items-center mb-8 gap-4">
        <div>
          <h2 className="text-3xl font-extrabold text-gray-900">Moji predmeti</h2>
          <p className="text-gray-500 mt-1">Upravljanje nastavnim aktivnostima i evidencijom.</p>
        </div>
        <button
          onClick={() => setShowUpload(true)}
          className="bg-blue-600 hover:bg-blue-700 text-white px-6 py-3 rounded-2xl font-bold shadow-lg shadow-blue-200 transition-all flex items-center gap-2"
        >
          <span className="text-xl">+</span> Dodaj predmet
        </button>
      </div>

      {loading && (
        <div className="flex justify-center py-20">
          <div className="animate-spin rounded-full h-12 w-12 border-b-2 border-blue-600"></div>
        </div>
      )}

      {error && (
        <div className="bg-red-50 border-l-4 border-red-500 p-4 mb-6 rounded-xl">
          <p className="text-red-700 font-medium">{error}</p>
        </div>
      )}

      {sortedAcademicYears.map((academicYear) => (
        <div key={academicYear} className="mb-10">
          <div className="flex items-center gap-4 mb-5">
            <h3 className="text-xl font-black text-blue-900 uppercase tracking-widest">
              AKADEMSKA {academicYear}
            </h3>
            <div className="h-px flex-1 bg-gradient-to-r from-blue-100 to-transparent"></div>
          </div>

          {Object.keys(groupedData[academicYear])
            .sort((a, b) => Number(a) - Number(b))
            .map((studyYear) => {
              const collapseKey = `${academicYear}-${studyYear}`
              const isCollapsed = collapsed[collapseKey] ?? true
              const subjectsInYear = Object.values(groupedData[academicYear][studyYear])

              return (
                <div key={collapseKey} className="mb-8 scroll-mt-16" ref={el => sectionRefs.current[collapseKey] = el}>
                  <button
                    onClick={() => toggleCollapse(collapseKey)}
                    className="group flex items-center justify-between w-full bg-white hover:bg-gray-50 px-5 py-3 rounded-2xl border border-gray-100 shadow-sm mb-4 transition-all"
                  >
                    <div className="flex items-center gap-3">
                      <span className={`text-blue-600 transition-transform ${isCollapsed ? '' : 'rotate-90'}`}>▷</span>
                      <span className="font-bold text-lg text-gray-800">
                        {studyYear}. GODINA STUDIJA
                      </span>
                    </div>
                    <span className="text-sm bg-gray-100 text-gray-600 px-3.5 py-1 rounded-full font-medium">
                      {subjectsInYear.length} predmeta
                    </span>
                  </button>

                  {!isCollapsed && (
                    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5 ml-1">
                      {subjectsInYear.map((base) => {
                        const sortedGroups = [...base.groups].sort((a, b) =>
                          a.groupName.localeCompare(b.groupName, 'sr', { numeric: true })
                        )

                        return (
                          <div
                            key={base.name}
                            className="bg-white rounded-2xl p-5 border border-gray-100 hover:shadow-md transition-all"
                          >
                            <div className="flex justify-between items-start mb-3">
                              <div className="flex-1">
                                <span className="text-xs font-mono text-blue-600 font-bold tracking-wide">
                                  {base.code}
                                </span>
                                <h4 className="font-semibold text-gray-900 text-base leading-tight mt-1 line-clamp-2">
                                  {base.name}
                                </h4>
                              </div>
                              <span className="text-[10px] px-2.5 py-1 bg-indigo-50 text-indigo-700 font-bold rounded-md whitespace-nowrap">
                                {base.teachingType}
                              </span>
                            </div>

                            <p className="text-xs text-gray-500 mb-4">
                              Semestar {base.semester} • {base.studyProgram}
                            </p>

                            <div className="space-y-2">
                              {sortedGroups.map((group) => (
                                <div
                                  key={group.id}
                                  className="flex items-center justify-between bg-gray-50 hover:bg-white border border-gray-100 rounded-xl px-4 py-2.5 text-sm transition-all"
                                >
                                  <span className="font-medium text-emerald-700">Grupa {group.groupName}</span>

                                  <div className="flex gap-1">
                                    <button
                                      onClick={() => navigate(`/subject/${group.id}`)}
                                      className="px-4 py-1 bg-gray-900 text-white text-xs font-bold rounded-lg hover:bg-black transition-all"
                                    >
                                      Sesije
                                    </button>
                                    <button
                                      onClick={() => navigate(`/subject/${group.id}/analytics`)}
                                      className="px-3 py-1 bg-blue-50 text-blue-600 text-xs font-bold rounded-lg hover:bg-blue-100 transition-all"
                                    >
                                       📊 
                                    </button>
                                    <button
                                      onClick={() => deleteSubject(group.id)}
                                      className="text-gray-400 hover:text-red-500 px-1 transition-colors"
                                    >
                                      🗑️
                                    </button>
                                  </div>
                                </div>
                              ))}
                            </div>
                          </div>
                        )
                      })}
                    </div>
                  )}
                </div>
              )
            })}
        </div>
      ))}

      {showUpload && <UploadModal onClose={() => setShowUpload(false)} onSuccess={fetchSubjects} />}
    </div>
  )
}

export default Dashboard