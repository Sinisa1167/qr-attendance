import { useState, useEffect, useMemo, useRef } from 'react'
import { useNavigate } from 'react-router-dom'
import api from '../../api/axiosInstance'
import UploadModal from '../../components/UploadModal'
import { Trash2, ChevronRight, BarChart2, BookOpen } from 'lucide-react'

function Dashboard() {
  const [subjects, setSubjects] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)
  const [showUpload, setShowUpload] = useState(false)

  const [collapsedYears, setCollapsedYears] = useState({})
  const [collapsedSubjects, setCollapsedSubjects] = useState({})

  const sectionRefs = useRef({})
  const navigate = useNavigate()

  const YEAR_KEY    = 'dashboard_collapsed_years'
  const SUBJECT_KEY = 'dashboard_collapsed_subjects'

  useEffect(() => {
    fetchSubjects()
    try {
      const y = localStorage.getItem(YEAR_KEY)
      const s = localStorage.getItem(SUBJECT_KEY)
      if (y) setCollapsedYears(JSON.parse(y))
      if (s) setCollapsedSubjects(JSON.parse(s))
    } catch (e) {}
  }, [])

  useEffect(() => {
    localStorage.setItem(YEAR_KEY, JSON.stringify(collapsedYears))
  }, [collapsedYears])

  useEffect(() => {
    localStorage.setItem(SUBJECT_KEY, JSON.stringify(collapsedSubjects))
  }, [collapsedSubjects])

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

  const toggleYear = (key) => {
  setCollapsedYears(prev => {
    const next = { ...prev, [key]: !prev[key] }

    // SCROLL SAMO KAD SE OTVARA
    if (next[key] === false) {
      setTimeout(() => {
        sectionRefs.current[key]?.scrollIntoView({
          behavior: 'smooth',
          block: 'start'
        })
      }, 0)
    }

    return next
  })
}

  const toggleSubject = (key) => {
    setCollapsedSubjects(prev => ({ ...prev, [key]: !prev[key] }))
  }

  // Briše jednu grupu
  const deleteGroup = async (e, subjectId) => {
    e.stopPropagation()
    if (!window.confirm('PAŽNJA: Brisanjem grupe brišete sve njene sesije i evidenciju prisustva.')) return
    try {
      await api.delete(`/api/admin/subjects/${subjectId}`)
      fetchSubjects()
    } catch (err) {
      alert('Greška pri brisanju grupe')
    }
  }

  const deleteSubjectAll = async (e, groups, subjectName) => {
    e.stopPropagation()
    if (!window.confirm(
      `PAŽNJA: Brisanjem predmeta "${subjectName}" brišete SVE grupe (${groups.length}), sesije i evidenciju prisustva. Ova akcija je nepovratna.`
    )) return
    try {
      await Promise.all(groups.map(g => api.delete(`/api/admin/subjects/${g.id}`)))
      fetchSubjects()
    } catch (err) {
      alert('Greška pri brisanju predmeta')
    }
  }

  const groupedData = useMemo(() => {
    const result = {}
    subjects.forEach(subject => {
      const academicYear = subject.academicYear || 'Nepoznato'
      const studyYear    = subject.studyYear || 0
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

          {/* Akademska godina header */}
          <div className="flex items-center gap-4 mb-5">
            <h3 className="text-xl font-black text-blue-900 uppercase tracking-widest">
              AKADEMSKA {academicYear}
            </h3>
            <div className="h-px flex-1 bg-gradient-to-r from-blue-100 to-transparent"></div>
          </div>

          {Object.keys(groupedData[academicYear])
            .sort((a, b) => Number(a) - Number(b))
            .map((studyYear) => {
              const yearKey          = `${academicYear}-${studyYear}`
              const isYearCollapsed  = collapsedYears[yearKey] ?? true
              const subjectsInYear   = Object.values(groupedData[academicYear][studyYear])

              return (
                <div key={yearKey} className="mb-8 scroll-mt-16"
                  ref={el => sectionRefs.current[yearKey] = el}>

                  {/* Godina studija toggle */}
                  <button
                    onClick={() => toggleYear(yearKey)}
                    className="group flex items-center justify-between w-full bg-white hover:bg-gray-50 px-5 py-3 rounded-2xl border border-gray-100 shadow-sm mb-4 transition-all"
                  >
                    <div className="flex items-center gap-3">
                      <ChevronRight
                        size={18}
                        className={`text-blue-600 transition-transform duration-200 ${isYearCollapsed ? '' : 'rotate-90'}`}
                      />
                      <span className="font-bold text-lg text-gray-800">
                        {studyYear}. GODINA STUDIJA
                      </span>
                    </div>
                    <span className="text-sm bg-gray-100 text-gray-600 px-3.5 py-1 rounded-full font-medium">
                      {subjectsInYear.length} predmeta
                    </span>
                  </button>

                  {!isYearCollapsed && (
                    <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-5 ml-1">
                      {subjectsInYear.map((base) => {
                        const subjectKey       = `${yearKey}-${base.code}-${base.teachingType}`
                        const isSubjCollapsed  = collapsedSubjects[subjectKey] ?? false
                        const sortedGroups     = [...base.groups].sort((a, b) =>
                          a.groupName.localeCompare(b.groupName, 'sr', { numeric: true })
                        )
                        const hasMultipleGroups = sortedGroups.length > 1

                        return (
                          <div
                            key={subjectKey}
                            className="bg-white rounded-2xl border border-gray-100 hover:shadow-md transition-all overflow-hidden"
                          >
                            {/* Kartica header — klikabilna za collapse */}
                            <div
                              className={`p-5 ${hasMultipleGroups ? 'cursor-pointer select-none' : ''}`}
                              onClick={() => hasMultipleGroups && toggleSubject(subjectKey)}
                            >
                              <div className="flex justify-between items-start mb-3">
                                <div className="flex-1 min-w-0">
                                  <span className="text-xs font-mono text-blue-600 font-bold tracking-wide">
                                    {base.code}
                                  </span>
                                  <h4 className="font-semibold text-gray-900 text-base leading-tight mt-1 line-clamp-2">
                                    {base.name}
                                  </h4>
                                </div>

                                <div className="flex items-center gap-1 ml-2 shrink-0">
                                  <span className="text-[10px] px-2.5 py-1 bg-indigo-50 text-indigo-700 font-bold rounded-md whitespace-nowrap">
                                    {base.teachingType}
                                  </span>
                                  {hasMultipleGroups && (
                                    <ChevronRight
                                      size={16}
                                      className={`text-gray-400 transition-transform duration-200 ${isSubjCollapsed ? '' : 'rotate-90'}`}
                                    />
                                  )}
                                </div>
                              </div>

                              <div className="flex items-center justify-between">
                                <p className="text-xs text-gray-500">
                                  Semestar {base.semester} • {base.studyProgram}
                                </p>

                                {/* Broj grupa badge + delete all dugme */}
                                <div className="flex items-center gap-3">
                                  {hasMultipleGroups && (
                                    <span className="inline-flex items-center whitespace-nowrap text-[10px] bg-blue-50 text-blue-600 font-bold px-2 py-0.5 rounded-full border border-blue-100">
                                      {sortedGroups.length} grupa
                                    </span>
                                  )}
                                  <button
                                    onClick={(e) => deleteSubjectAll(e, sortedGroups, base.name)}
                                    className="p-1.5 text-gray-400 hover:text-red-500 hover:bg-red-50 rounded-lg transition-all"
                                    title="Obriši sve grupe predmeta"
                                  >
                                    <Trash2 size={14} />
                                  </button>
                                </div>
                              </div>
                            </div>

                            {/* Grupe lista — collapsible ako ih ima više */}
                            {(!hasMultipleGroups || !isSubjCollapsed) && (
                              <div className="border-t border-gray-50 px-5 pb-5 pt-3 space-y-2">
                                {sortedGroups.map((group) => (
                                  <div
                                    key={group.id}
                                    className="flex items-center justify-between bg-gray-50 hover:bg-white border border-gray-100 rounded-xl px-4 py-2.5 text-sm transition-all"
                                  >
                                    <span className="font-medium text-emerald-700">
                                      Grupa {group.groupName}
                                    </span>

                                    <div className="flex gap-1 items-center">
                                      <button
                                        onClick={() => navigate(`/subject/${group.id}`)}
                                        className="px-3 py-1 bg-gray-900 text-white text-xs font-bold rounded-lg hover:bg-black transition-all"
                                        title="Sesije"
                                      >
                                        <BookOpen size={12} className="inline mr-1" />
                                        Sesije
                                      </button>
                                      <button
                                        onClick={() => navigate(`/subject/${group.id}/analytics`)}
                                        className="px-2 py-1 bg-blue-50 text-blue-600 text-xs font-bold rounded-lg hover:bg-blue-100 transition-all"
                                        title="Analitika"
                                      >
                                        <BarChart2 size={12} className="inline" />
                                      </button>
                                      <button
                                        onClick={(e) => deleteGroup(e, group.id)}
                                        className="p-1.5 text-gray-400 hover:text-red-500 hover:bg-red-50 rounded-lg transition-all"
                                        title="Obriši grupu"
                                      >
                                        <Trash2 size={12} />
                                      </button>
                                    </div>
                                  </div>
                                ))}
                              </div>
                            )}

                            {/* Collapsed summary — samo ako ima više grupa i collapsed je */}
                            {hasMultipleGroups && isSubjCollapsed && (
                              <div
                                className="border-t border-gray-50 px-5 py-3 flex flex-wrap gap-1.5 cursor-pointer"
                                onClick={() => toggleSubject(subjectKey)}
                              >
                                {sortedGroups.map((group) => (
                                  <span
                                    key={group.id}
                                    className="text-[10px] font-bold bg-emerald-50 text-emerald-700 border border-emerald-100 px-2 py-0.5 rounded-full"
                                  >
                                    {group.groupName}
                                  </span>
                                ))}
                              </div>
                            )}
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