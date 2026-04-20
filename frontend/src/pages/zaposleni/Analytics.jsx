import { useState, useEffect, useMemo } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, BarChart, Bar } from 'recharts'
import { AlertTriangle, Users, FileBarChart, Download, TrendingUp, ArrowUpDown, ChevronDown, BarChart2 } from 'lucide-react'
import api from '../../api/axiosInstance'

function Analytics() {
  const { subjectId } = useParams()
  const [data, setData] = useState(null)
  const [loading, setLoading] = useState(true)
  const [sortDirection, setSortDirection] = useState('desc')
  const [showCharts, setShowCharts] = useState(false)

  useEffect(() => {
    api.get(`/api/admin/analytics/subject/${subjectId}`)
      .then(res => setData(res.data))
      .finally(() => setLoading(false))
  }, [subjectId])

  const downloadReport = async (format) => {
    try {
      const url = `/api/admin/export/subject/${subjectId}/${format}`;
      const filename = `Analitika_${data?.subjectName?.replace(/\s+/g, '_') || 'Predmet'}.${format}`;

      const response = await api.get(url, { responseType: 'blob' });
      
      const blob = new Blob([response.data], { 
        type: format === 'xlsx' 
          ? 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' 
          : 'application/pdf' 
      });

      const link = document.createElement('a');
      link.href = window.URL.createObjectURL(blob);
      link.download = filename;
      document.body.appendChild(link);
      link.click();
      link.remove();
      window.URL.revokeObjectURL(link.href);
    } catch (err) {
      console.error('Greška pri downloadu analitike:', err);
      alert("Nije moguće preuzeti izvještaj. Provjerite vezu sa serverom.");
    }
  };

  const sortedStudentStats = useMemo(() => {
    if (!data?.studentStats) return []
    return [...data.studentStats].sort((a, b) => 
      sortDirection === 'desc' ? b.percentage - a.percentage : a.percentage - b.percentage
    )
  }, [data?.studentStats, sortDirection])

  const toggleSort = () => setSortDirection(prev => prev === 'desc' ? 'asc' : 'desc')

  if (loading) return <div className="p-10 text-center font-bold">Učitavanje analitike...</div>
  if (!data) return <div className="p-10 text-center text-red-600">Greška pri učitavanju podataka</div>

  const groupChartData = data.groupStats 
    ? Object.entries(data.groupStats).map(([name, value]) => ({ name, procenat: Math.round(value || 0) }))
    : []

  return (
    <div className="max-w-7xl mx-auto p-4 lg:p-8 space-y-8 pb-12">
      {/* Header */}
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

      {/* KPI Kartice */}
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
            <p className="text-xs font-bold text-gray-400 uppercase">Ispod 70%</p>
            <p className="text-3xl font-black text-red-600">{data.studentStats.filter(s => s.isCritical).length}</p>
          </div>
        </div>
      </div>

      {/* Kontrola za grafove */}
      <div className="flex justify-center">
        <button onClick={() => setShowCharts(!showCharts)} className="flex items-center gap-3 bg-white border border-gray-200 px-8 py-3 rounded-2xl font-bold text-gray-700 hover:bg-gray-50 transition-all shadow-sm">
          <BarChart2 size={20} className="text-blue-600" />
          {showCharts ? 'Sakrij vizuelnu analitiku' : 'Prikaži vizuelnu analitiku'}
          <ChevronDown size={20} className={`transition-transform duration-300 ${showCharts ? 'rotate-180' : ''}`} />
        </button>
      </div>

      {/* Grafovi */}
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

      {/* Tabela */}
      <div className="bg-white rounded-3xl shadow-sm border border-gray-100 overflow-hidden">
        <div className="p-6 border-b border-gray-50 flex justify-between items-center">
          <h3 className="text-lg font-black text-gray-800">Kumulativna evidencija</h3>
          <button onClick={toggleSort} className="bg-gray-50 px-4 py-2 rounded-xl text-sm font-bold text-gray-600">Sortiraj po procentu</button>
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
              {sortedStudentStats.map((s, idx) => (
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
              ))}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  )
}

export default Analytics