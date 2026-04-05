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

  // Mapa referenci za skrolovanje
  const sectionRefs = useRef({})

  useEffect(() => {
    fetchSubjects()
  }, [])

  const fetchSubjects = async () => {
    try {
      const response = await api.get('/api/admin/subjects')
      const data = response.data
      setSubjects(data)

      const initialCollapsed = {}
      data.forEach(subject => {
        const key = `${subject.academicYear || 'Nepoznato'}-${subject.studyYear || 0}`
        initialCollapsed[key] = true
      })
      setCollapsed(initialCollapsed)
    } catch (err) {
      setError('Greška pri učitavanju predmeta')
    } finally {
      setLoading(false)
    }
  }

  const toggleCollapse = (key) => {
    const isOpening = !!collapsed[key]
    setCollapsed(prev => ({ ...prev, [key]: !prev[key] }))

    if (isOpening) {
      // Smanjen timeout za brži odziv
      setTimeout(() => {
        if (sectionRefs.current[key]) {
          sectionRefs.current[key].scrollIntoView({
            behavior: 'smooth',
            block: 'start'
          })
        }
      }, 60); // 60ms je "sweet spot" za većinu ekrana
    }
  }

  const deleteSubject = async (subjectId) => {
    if (!window.confirm('PAŽNJA: Brisanjem predmeta brišete sve povezane sesije...')) return
    try {
      await api.delete(`/api/admin/subjects/${subjectId}`)
      fetchSubjects()
    } catch (err) {
      alert('Greška pri brisanju predmeta')
    }
  }

  const grouped = useMemo(() => {
    return subjects.reduce((acc, subject) => {
      const year = subject.academicYear || 'Nepoznato'
      const studyYear = subject.studyYear || 0
      if (!acc[year]) acc[year] = {}
      if (!acc[year][studyYear]) acc[year][studyYear] = []
      acc[year][studyYear].push(subject)
      return acc
    }, {})
  }, [subjects])

  const sortedAcademicYears = useMemo(() => {
    return Object.keys(grouped).sort((a, b) => b.localeCompare(a))
  }, [grouped])

  return (
    <div className="max-w-7xl mx-auto p-4 lg:p-8">
      {/* HEADER SEKCIJA */}
      <div className="flex flex-col md:flex-row justify-between items-start md:items-center mb-10 gap-4">
        <div>
          <h2 className="text-3xl font-extrabold text-gray-900">Moji predmeti</h2>
          <p className="text-gray-500 mt-1">Upravljanje nastavnim aktivnostima i evidencijom.</p>
        </div>
        <button
          onClick={() => setShowUpload(true)}
          className="bg-blue-600 hover:bg-blue-700 text-white px-6 py-3 rounded-xl font-bold shadow-lg shadow-blue-200 transition-all flex items-center gap-2"
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
        <div className="bg-red-50 border-l-4 border-red-500 p-4 mb-6">
          <p className="text-red-700 font-medium">{error}</p>
        </div>
      )}

      {sortedAcademicYears.map((academicYear) => (
        <div key={academicYear} className="mb-12">
          <div className="flex items-center gap-4 mb-6">
            <h3 className="text-xl font-black text-blue-900 whitespace-nowrap uppercase">
              AKADEMSKA {academicYear}
            </h3>
            <div className="h-[2px] w-full bg-gradient-to-r from-blue-100 to-transparent"></div>
          </div>

          {Object.keys(grouped[academicYear])
            .sort((a, b) => Number(a) - Number(b))
            .map((studyYear) => {
              const key = `${academicYear}-${studyYear}`
              const isCollapsed = collapsed[key]
              const subjectsInYear = grouped[academicYear][studyYear].sort((a, b) => 
                (a.teachingType + a.groupName).localeCompare(b.teachingType + b.groupName)
              )

              return (
                <div key={key} className="mb-6 ml-2 md:ml-4 scroll-mt-24" ref={el => sectionRefs.current[key] = el}>
                  <button
                    onClick={() => toggleCollapse(key)}
                    className="group flex items-center justify-between w-full bg-white hover:bg-gray-50 px-5 py-3 rounded-2xl border border-gray-100 shadow-sm transition-all"
                  >
                    <div className="flex items-center gap-3">
                      <span className={`text-blue-500 transition-transform duration-300 ${isCollapsed ? '' : 'rotate-90'}`}>
                        ▶
                      </span>
                      <span className="font-bold text-gray-700 tracking-tight">
                        {studyYear}. GODINA STUDIJA
                      </span>
                      <span className="bg-gray-100 text-gray-500 text-[10px] px-2 py-0.5 rounded-md font-black group-hover:bg-blue-100 group-hover:text-blue-600 transition-colors">
                        {subjectsInYear.length} PREDMETA
                      </span>
                    </div>
                  </button>

                  {!isCollapsed && (
                    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6 mt-6 ml-4">
                      {subjectsInYear.map((subject) => (
                        <div key={subject.id} className="relative group bg-white rounded-2xl p-6 shadow-sm border border-gray-100 hover:shadow-2xl hover:-translate-y-1 transition-all duration-300">
                          {/* DELETE DUGME */}
                          <button 
                            onClick={(e) => { e.stopPropagation(); deleteSubject(subject.id); }}
                            className="absolute top-4 right-4 p-2 text-gray-600 hover:text-red-500 hover:bg-red-50 rounded-lg transition-all"
                          >
                            <svg xmlns="http://www.w3.org/2000/svg" className="h-5 w-5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                            </svg>
                          </button>

                          <div className="mb-4">
                            <span className="text-[10px] font-black text-blue-500 uppercase tracking-[0.2em]">{subject.code}</span>
                            <h4 className="text-xl font-bold text-gray-900 leading-tight mt-1 group-hover:text-blue-700 transition-colors">
                              {subject.name}
                            </h4>
                            <p className="text-xs text-gray-400 mt-2 flex items-center gap-1 font-medium">
                               <span className="bg-gray-100 px-1.5 py-0.5 rounded text-gray-500 uppercase">Semestar {subject.semester}</span>
                               <span className="text-gray-300">|</span>
                               {subject.studyProgram}
                            </p>
                          </div>

                          <div className="flex gap-2 mb-8">
                            <span className="text-[10px] px-2 py-1 bg-indigo-50 text-indigo-600 font-black rounded border border-indigo-100 uppercase tracking-wider">
                              {subject.teachingType}
                            </span>
                            {/* UOČLJIVIJI BEDŽ ZA GRUPU */}
                            <span className="text-xs px-2.5 py-1 bg-emerald-600 text-white font-black rounded-lg shadow-sm shadow-emerald-200 uppercase tracking-wider">
                              Grupa {subject.groupName || '1'}
                            </span>
                          </div>

                          <button
                            onClick={() => navigate(`/subject/${subject.id}`)}
                            className="w-full bg-gray-900 text-white py-3 rounded-xl font-bold text-sm hover:bg-blue-600 shadow-md transition-all active:scale-95"
                          >
                            Upravljaj sesijama
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

      {showUpload && <UploadModal onClose={() => setShowUpload(false)} onSuccess={fetchSubjects} />}
    </div>
  )
}

export default Dashboard