import { useState, useEffect } from 'react'
import api from '../../api/axiosInstance'
import keycloak from '../../keycloak'

function Dashboard() {
  const [subjects, setSubjects] = useState([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState(null)

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

  return (
    <div className="max-w-6xl mx-auto">
      <div className="flex justify-between items-center mb-6">
        <h2 className="text-2xl font-bold text-gray-800">Moji predmeti</h2>
        <button className="bg-blue-600 text-white px-4 py-2 rounded hover:bg-blue-700">
          + Novi predmet
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

      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4">
        {subjects.map((subject) => (
          <div key={subject.id} className="bg-white rounded-lg shadow p-4 hover:shadow-md transition">
            <h3 className="font-bold text-lg text-gray-800">{subject.name}</h3>
            <p className="text-gray-500 text-sm">{subject.code}</p>
            <p className="text-gray-500 text-sm">{subject.studyProgram}</p>
            <div className="mt-3 flex gap-2">
              <span className="bg-blue-100 text-blue-700 text-xs px-2 py-1 rounded">
                {subject.studyYear}. godina
              </span>
              <span className="bg-green-100 text-green-700 text-xs px-2 py-1 rounded">
                Semester {subject.semester}
              </span>
            </div>
            <button className="mt-4 w-full bg-blue-600 text-white py-2 rounded hover:bg-blue-700 text-sm">
              Upravljaj sesijama
            </button>
          </div>
        ))}
      </div>
    </div>
  )
}

export default Dashboard