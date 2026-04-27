import { useState, useEffect, useMemo } from 'react'
import { useParams } from 'react-router-dom'
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, BarChart, Bar } from 'recharts'
import { AlertTriangle, Users, FileBarChart, Download, TrendingUp, ChevronDown, BarChart2, Grid } from 'lucide-react'
import api from '../../api/axiosInstance'

const FILTER_ALL = 'all'
const FILTER_BELOW = 'below'
const FILTER_ABOVE = 'above'

function Analytics() {
  const { subjectId } = useParams()
  const [data, setData] = useState(null)
  const [loading, setLoading] = useState(true)
  const [sortDirection, setSortDirection] = useState('desc')
  const [showCharts, setShowCharts] = useState(false)
  const [showMatrix, setShowMatrix] = useState(false)
  const [filter, setFilter] = useState(FILTER_ALL)

  useEffect(() => {
    api.get(`/api/admin/analytics/subject/${subjectId}`)
      .then(res => setData(res.data))
      .finally(() => setLoading(false))
  }, [subjectId])

  const downloadReport = async (format) => {
    try {
      const url = `/api/admin/export/subject/${subjectId}/${format}`
      const filename = `Analitika_${data?.subjectName?.replace(/\s+/g, '_') || 'Predmet'}.${format}`
      const response = await api.get(url, { responseType: 'blob' })
      const blob = new Blob([response.data], {
        type: format === 'xlsx'
          ? 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet'
          : 'application/pdf'
      })
      const link = document.createElement('a')
      link.href = window.URL.createObjectURL(blob)
      link.download = filename
      document.body.appendChild(link)
      link.click()
      link.remove()
      window.URL.revokeObjectURL(link.href)
    } catch (err) {
      alert("Nije moguće preuzeti izvještaj. Provjerite vezu sa serverom.")
    }
  }

  const sortedAndFilteredStats = useMemo(() => {
    if (!data?.studentStats) return []
    let list = [...data.studentStats]
    if (filter === FILTER_BELOW) list = list.filter(s => s.isCritical)
    if (filter === FILTER_ABOVE) list = list.filter(s => !s.isCritical)
    return list.sort((a, b) =>
      sortDirection === 'desc' ? b.percentage - a.percentage : a.percentage - b.percentage
    )
  }, [data?.studentStats, sortDirection, filter])

  const toggleSort = () => setSortDirection(prev => prev === 'desc' ? 'asc' : 'desc')

  if (loading) return <div className="p-10 text-center font-bold">Učitavanje analitike...</div>
  if (!data) return <div className="p-10 text-center text-red-600">Greška pri učitavanju podataka</div>

  const groupChartData = data.groupStats
    ? Object.entries(data.groupStats).map(([name, value]) => ({ name, procenat: Math.round(value || 0) }))
    : []

  const sessionHeaders = data.sessionHeaders || []
  const threshold = data.threshold || 70

  const activityLabel = (type) => {
    if (!type) return ''
    if (type.includes('LABORATORIJSKE') || type.includes('LAB')) return 'Lab'
    if (type.includes('AUDITORNE') || type.includes('AUD')) return 'Aud'
    return 'Pre'
  }

  const belowCount = data.studentStats?.filter(s => s.isCritical).length ?? 0
  const aboveCount = (data.studentStats?.length ?? 0) - belowCount

  return (
    <div className="max-w-7xl mx-auto p-4 lg:p-8 space-y-8 pb-12">
      <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
        <div>
          <h1 className="text-2xl font-black text-gray-900">{data.subjectName}</h1>
          <p className="text-xl text-blue-700 font-medium">Grupa {data.groupName}</p>
        </div>
        <div className="flex gap-2">
          <button onClick={() => downloadReport('xlsx')} className="flex items-center gap-2 bg-emerald-600 hover:bg-emerald-700 text-white px-5 py-2.5 rounded-2xl font-bold text-sm transition-all shadow-sm">
            <Download size={18} /> XLSX
          </button>
          <button onClick={() => downloadReport('pdf')} className="flex items-center gap-2 bg-red-500 hover:bg-red-600 text-white px-5 py-2.5 rounded-2xl font-bold text-sm transition-all shadow-sm">
            <Download size={18} /> PDF
          </button>
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <div className="bg-white p-6 rounded-3xl shadow-sm border border-gray-100 flex items-center gap-4">
          <div className="p-3 bg-blue-50 text-blue-600 rounded-2xl"><Users size={28} /></div>
          <div>
            <p className="text-xs font-bold text-gray-400 uppercase">Ukupno studenata</p>
            <p className="text-3xl font-black">{data.studentStats.length}</p>
          </div>
        </div>
        <div className="bg-white p-6 rounded-3xl shadow-sm border border-gray-100 flex items-center gap-4">
          <div className="p-3 bg-emerald-50 text-emerald-600 rounded-2xl"><FileBarChart size={28} /></div>
          <div>
            <p className="text-xs font-bold text-gray-400 uppercase">Održanih sesija</p>
            <p className="text-3xl font-black">{data.totalSessions}</p>
          </div>
        </div>
        <div className="bg-white p-6 rounded-3xl shadow-sm border border-red-100 flex items-center gap-4">
          <div className="p-3 bg-red-50 text-red-600 rounded-2xl"><AlertTriangle size={28} /></div>
          <div>
            <p className="text-xs font-bold text-gray-400 uppercase">Ispod {threshold}%</p>
            <p className="text-3xl font-black text-red-600">{belowCount}</p>
          </div>
        </div>
      </div>

      <div className="flex justify-center gap-3 flex-wrap">
        <button
          onClick={() => setShowCharts(!showCharts)}
          className="flex items-center gap-3 bg-white border border-gray-200 px-8 py-3 rounded-2xl font-bold text-gray-700 hover:bg-gray-50 transition-all shadow-sm"
        >
          <BarChart2 size={20} className="text-blue-600" />
          {showCharts ? 'Sakrij grafove' : 'Prikaži grafove'}
          <ChevronDown size={20} className={`transition-transform duration-300 ${showCharts ? 'rotate-180' : ''}`} />
        </button>
        <button
          onClick={() => setShowMatrix(!showMatrix)}
          className="flex items-center gap-3 bg-white border border-gray-200 px-8 py-3 rounded-2xl font-bold text-gray-700 hover:bg-gray-50 transition-all shadow-sm"
        >
          <Grid size={20} className="text-purple-600" />
          {showMatrix ? 'Sakrij matricu' : 'Prisustvo po sesijama'}
          <ChevronDown size={20} className={`transition-transform duration-300 ${showMatrix ? 'rotate-180' : ''}`} />
        </button>
      </div>

      {showCharts && (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-8 overflow-x-auto animate-in fade-in slide-in-from-top-4 duration-500">
          <div className="bg-white p-8 rounded-3xl shadow-sm border border-gray-100 min-w-[450px]">
            <h3 className="text-lg font-black mb-6 flex items-center gap-2"><TrendingUp size={22} className="text-blue-600" /> Trend prisustva</h3>
            <div className="h-[350px]">
              <ResponsiveContainer width="100%" height="100%">
                <LineChart data={data.chartData || []} margin={{ bottom: 50 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" vertical={false} />
                  <XAxis dataKey="name" angle={-45} textAnchor="end" height={80} fontSize={11} interval={0} stroke="#94a3b8" />
                  <YAxis stroke="#94a3b8" />
                  <Tooltip contentStyle={{ borderRadius: '16px', border: 'none', boxShadow: '0 10px 15px -3px rgba(0,0,0,0.1)' }} />
                  <Line type="monotone" dataKey="prisutni" stroke="#2563eb" strokeWidth={4} dot={{ r: 5 }} />
                </LineChart>
              </ResponsiveContainer>
            </div>
          </div>
          <div className="bg-white p-8 rounded-3xl shadow-sm border border-gray-100 min-w-[450px]">
            <h3 className="text-lg font-black mb-6">Poređenje po grupama</h3>
            <div className="h-[350px]">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={groupChartData}>
                  <CartesianGrid strokeDasharray="3 3" stroke="#f0f0f0" vertical={false} />
                  <XAxis dataKey="name" fontSize={11} stroke="#94a3b8" />
                  <YAxis stroke="#94a3b8" />
                  <Tooltip contentStyle={{ borderRadius: '16px', border: 'none', boxShadow: '0 10px 15px -3px rgba(0,0,0,0.1)' }} />
                  <Bar dataKey="procenat" fill="#10b981" radius={[8, 8, 0, 0]} barSize={40} />
                </BarChart>
              </ResponsiveContainer>
            </div>
          </div>
        </div>
      )}

      {showMatrix && sessionHeaders.length > 0 && (
        <div className="bg-white rounded-3xl shadow-sm border border-gray-100 overflow-hidden animate-in fade-in slide-in-from-top-4 duration-500">
          <div className="p-6 border-b border-gray-50">
            <h3 className="text-lg font-black text-gray-800">Prisustvo po sesijama</h3>
            <p className="text-xs text-gray-400 mt-1 font-medium">Zeleno = prisutan · Crveno = odsutan</p>
          </div>
          <div className="overflow-x-auto">
            <table className="text-left w-max min-w-full">
              <thead className="bg-gray-50 text-[10px] uppercase font-black text-gray-500">
                <tr>
                  <th className="px-6 py-4 sticky left-0 bg-gray-50 z-10 min-w-[200px]">Student</th>
                  <th className="px-4 py-4 text-center min-w-[60px]">%</th>
                  {sessionHeaders.map((sh, i) => (
                    <th key={sh.sessionId} className="px-2 py-4 text-center min-w-[56px]">
                      <div className="flex flex-col items-center gap-1">
                        <span className={`text-[9px] px-1.5 py-0.5 rounded font-black ${
                          sh.activityType?.includes('LAB') || sh.activityType?.includes('LABORATORIJSKE')
                            ? 'bg-purple-100 text-purple-700'
                            : sh.activityType?.includes('AUD') || sh.activityType?.includes('AUDITORNE')
                            ? 'bg-amber-100 text-amber-700'
                            : 'bg-blue-100 text-blue-700'
                        }`}>
                          {activityLabel(sh.activityType)}
                        </span>
                        <span className="text-[9px] text-gray-400 font-medium">
                          {sh.date ? new Date(sh.date).toLocaleDateString('de-DE').replace(/\.\d{4}$/, '') : `S${i + 1}`}
                        </span>
                      </div>
                    </th>
                  ))}
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-50">
                {data.studentStats.map((s, idx) => (
                  <tr key={idx} className="hover:bg-blue-50/20 transition-colors">
                    <td className="px-6 py-3 sticky left-0 bg-white z-10 border-r border-gray-50">
                      <div className="font-semibold text-sm text-gray-900">{s.lastName} {s.firstName}</div>
                      <div className="font-mono text-[10px] text-gray-400">{s.index}</div>
                    </td>
                    <td className="px-4 py-3 text-center">
                      <span className={`text-xs font-black px-2 py-1 rounded-xl ${s.isCritical ? 'bg-red-100 text-red-700' : 'bg-emerald-100 text-emerald-700'}`}>
                        {s.percentage}%
                      </span>
                    </td>
                    {sessionHeaders.map((sh) => {
                      const present = s.sessionAttendance?.[sh.sessionId]
                      return (
                        <td key={sh.sessionId} className="px-2 py-3 text-center">
                          <span className={`inline-flex items-center justify-center w-7 h-7 rounded-lg text-sm font-black ${
                            present ? 'bg-emerald-100 text-emerald-600' : 'bg-red-50 text-red-400'
                          }`}>
                            {present ? '✓' : '✗'}
                          </span>
                        </td>
                      )
                    })}
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      )}

      <div className="bg-white rounded-3xl shadow-sm border border-gray-100 overflow-hidden">
        <div className="p-6 border-b border-gray-50 flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
          <h3 className="text-lg font-black text-gray-800">Kumulativna evidencija</h3>
          <div className="flex items-center gap-2 flex-wrap">
            <div className="flex bg-gray-100 rounded-xl p-1 gap-1">
              <button
                onClick={() => setFilter(FILTER_ALL)}
                className={`px-4 py-1.5 rounded-lg text-xs font-bold transition-all ${filter === FILTER_ALL ? 'bg-white shadow text-gray-900' : 'text-gray-500 hover:text-gray-700'}`}
              >
                Svi ({data.studentStats.length})
              </button>
              <button
                onClick={() => setFilter(FILTER_BELOW)}
                className={`px-4 py-1.5 rounded-lg text-xs font-bold transition-all ${filter === FILTER_BELOW ? 'bg-red-500 shadow text-white' : 'text-gray-500 hover:text-red-500'}`}
              >
                Ispod {threshold}% ({belowCount})
              </button>
              <button
                onClick={() => setFilter(FILTER_ABOVE)}
                className={`px-4 py-1.5 rounded-lg text-xs font-bold transition-all ${filter === FILTER_ABOVE ? 'bg-emerald-500 shadow text-white' : 'text-gray-500 hover:text-emerald-500'}`}
              >
                Iznad ({aboveCount})
              </button>
            </div>
            <button onClick={toggleSort} className="bg-gray-50 px-4 py-2 rounded-xl text-sm font-bold text-gray-600 border border-gray-100 hover:bg-gray-100 transition-all">
              Sortiraj ↕
            </button>
          </div>
        </div>
        <div className="overflow-x-auto">
          <table className="w-full text-left">
            <thead className="bg-gray-50 text-[10px] uppercase font-black text-gray-500">
              <tr>
                <th className="px-6 py-4">Indeks</th>
                <th className="px-6 py-4">Student</th>
                <th className="px-6 py-4 text-center">Dolazaka</th>
                <th className="px-6 py-4 text-right">Procenat</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {sortedAndFilteredStats.length === 0 ? (
                <tr>
                  <td colSpan={4} className="px-6 py-12 text-center text-gray-400 font-medium italic">
                    Nema studenata za odabrani filter.
                  </td>
                </tr>
              ) : (
                sortedAndFilteredStats.map((s, idx) => (
                  <tr key={idx} className="hover:bg-blue-50/30 transition-colors">
                    <td className="px-6 py-4 font-mono font-bold text-sm">{s.index}</td>
                    <td className="px-6 py-4 font-semibold">{s.lastName} {s.firstName}</td>
                    <td className="px-6 py-4 text-center font-bold text-emerald-600">{s.attended}</td>
                    <td className="px-6 py-4 text-right">
                      <span className={`inline-block px-4 py-1.5 rounded-2xl text-sm font-black ${s.isCritical ? 'bg-red-100 text-red-700' : 'bg-emerald-100 text-emerald-700'}`}>
                        {s.percentage}%
                      </span>
                    </td>
                  </tr>
                ))
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  )
}

export default Analytics