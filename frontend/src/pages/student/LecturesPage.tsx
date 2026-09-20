import { useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { getStudentLectures } from '@/api/lectureApi'
import { Input } from '@/components/ui/input'
import { Search, PlaySquare, Clock, Sparkles, RefreshCw } from 'lucide-react'

export default function StudentLecturesPage() {
  const [searchTerm, setSearchTerm] = useState('')
  // Giữ lại query string khi search (debounce có thể thêm sau)
  const [debouncedSearch, setDebouncedSearch] = useState('')

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault()
    setDebouncedSearch(searchTerm)
  }

  const { data: lecturesPage, isLoading } = useQuery({
    queryKey: ['studentLectures', debouncedSearch],
    queryFn: () => getStudentLectures({ title: debouncedSearch, size: 20 }),
  })

  const lectures = lecturesPage?.content || []

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row justify-between items-start sm:items-center gap-4">
        <div>
          <h1 className="text-3xl font-bold tracking-tight text-foreground">Tất cả bài giảng</h1>
          <p className="text-muted-foreground mt-1">Khám phá toàn bộ bài giảng trên hệ thống.</p>
        </div>

        <form onSubmit={handleSearch} className="relative w-full sm:w-72">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
          <Input 
            type="search" 
            placeholder="Tìm kiếm bài giảng..." 
            className="pl-9 bg-card"
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
          />
        </form>
      </div>

      {isLoading ? (
        <div className="flex justify-center py-20">
          <RefreshCw className="w-8 h-8 animate-spin text-muted-foreground" />
        </div>
      ) : lectures.length === 0 ? (
        <div className="text-center py-20 bg-card border rounded-2xl">
          <p className="text-muted-foreground">Không tìm thấy bài giảng nào.</p>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
          {lectures.map((lecture) => (
            <Link key={lecture.lectureId} to={`/student/lectures/${lecture.lectureId}`} className="group block">
              <div className="bg-card border border-border/50 rounded-2xl overflow-hidden hover:border-blue-600/50 hover:shadow-xl hover:shadow-blue-600/5 transition-all duration-300 h-full flex flex-col">
                <div className="aspect-video bg-muted relative overflow-hidden shrink-0">
                  <div className="absolute inset-0 bg-gradient-to-br from-indigo-500/20 to-purple-500/20 flex items-center justify-center group-hover:scale-105 transition-transform duration-500">
                    <PlaySquare size={48} className="text-blue-600/50" />
                  </div>
                  {lecture.videoStatus === 'DONE' ? (
                    <div className="absolute top-3 left-3 bg-green-500/90 backdrop-blur text-white text-xs font-medium px-2.5 py-1 rounded-full flex items-center gap-1.5">
                      <Sparkles size={12} />
                      Sẵn sàng
                    </div>
                  ) : (
                    <div className="absolute top-3 left-3 bg-amber-500/90 backdrop-blur text-white text-xs font-medium px-2.5 py-1 rounded-full flex items-center gap-1.5">
                      <Clock size={12} />
                      Đang xử lý
                    </div>
                  )}
                  <div className="absolute bottom-3 right-3 bg-black/70 text-white text-xs font-medium px-2 py-1 rounded-md flex items-center gap-1.5">
                    <Clock size={12} />
                    {new Date(lecture.createdAt).toLocaleDateString('vi-VN')}
                  </div>
                </div>
                <div className="p-5 flex-1 flex flex-col">
                  <h3 className="font-bold text-lg mb-2 group-hover:text-blue-600 transition-colors line-clamp-2">
                    {lecture.title}
                  </h3>
                  <p className="text-sm text-muted-foreground mb-4 mt-auto">
                    Giảng viên: {lecture.teacherName}
                  </p>
                </div>
              </div>
            </Link>
          ))}
        </div>
      )}
    </div>
  )
}
