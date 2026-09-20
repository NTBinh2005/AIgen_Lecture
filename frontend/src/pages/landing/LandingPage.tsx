import { Link, useSearchParams } from 'react-router-dom'
import {
  ArrowRight,
  Sparkles,
  BarChart3,
  Wand2,
  ChevronRight,
  PlaySquare
} from 'lucide-react'
import { Button } from '@/components/ui/button'
import { ThemeToggle } from '@/components/common/ThemeToggle'
import { LoginModal } from '@/components/auth/LoginModal'
import { motion, type Variants } from 'framer-motion'

const stats = [
  { label: 'Thời gian tạo bài giảng', value: '< 15s' },
  { label: 'Câu hỏi mỗi bài', value: '5-20' },
  { label: 'Độ chính xác AI', value: '98%' },
]

const previewCards = [
  { 
    label: 'Slide 01', 
    labelColor: 'text-indigo-500', 
    iconColor: 'text-indigo-400', 
    hoverBorder: 'hover:border-indigo-500/40',
    text: 'Mô hình hóa kiến thức thành danh sách ý chính', 
    Icon: Sparkles 
  },
  { 
    label: 'Interactive Quiz', 
    labelColor: 'text-violet-500', 
    iconColor: 'text-violet-400', 
    hoverBorder: 'hover:border-violet-500/40',
    text: 'Câu hỏi trắc nghiệm thông minh với 4 đáp án & feedback ngay', 
    Icon: Wand2 
  },
  { 
    label: 'AI Analytics', 
    labelColor: 'text-blue-500', 
    iconColor: 'text-blue-400', 
    hoverBorder: 'hover:border-blue-500/40',
    text: 'Heatmap lỗi sai và gợi ý cải tiến nội dung tự động', 
    Icon: BarChart3 
  },
]

// Variants
const containerVariants: Variants = {
  hidden: { opacity: 0 },
  show: { opacity: 1, transition: { staggerChildren: 0.1, delayChildren: 0.2 } }
}

const itemVariants: Variants = {
  hidden: { opacity: 0, y: 30 },
  show: { opacity: 1, y: 0, transition: { type: 'spring', stiffness: 300, damping: 24 } }
}

export default function LandingPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const isLoginOpen = searchParams.get('login') === 'true'

  const handleOpenChange = (open: boolean) => {
    const newParams = new URLSearchParams(searchParams)
    if (!open) {
      newParams.delete('login')
    } else {
      newParams.set('login', 'true')
    }
    setSearchParams(newParams)
  }

  return (
    <div className="min-h-screen bg-background text-foreground selection:bg-primary/30 overflow-x-hidden flex flex-col">
      <LoginModal isOpen={isLoginOpen} onOpenChange={handleOpenChange} />
      
      {/* Animated Background Orbs */}
      <div className="fixed inset-0 overflow-hidden pointer-events-none z-0" aria-hidden="true">
        <div className="animate-blob absolute -top-[10%] -left-[10%] w-[500px] h-[500px] rounded-full bg-primary/10 blur-[120px] dark:bg-primary/15" />
        <div className="animate-blob-delay-2 absolute top-[20%] -right-[10%] w-[400px] h-[400px] rounded-full bg-indigo-500/10 blur-[100px] dark:bg-indigo-500/15" />
        <div className="animate-blob-delay-4 absolute -bottom-[10%] left-[20%] w-[600px] h-[600px] rounded-full bg-violet-500/10 blur-[120px] dark:bg-violet-500/15" />
      </div>

      {/* Glassmorphism Header */}
      <header className="fixed top-0 inset-x-0 z-50 border-b border-border/40 bg-background/60 backdrop-blur-xl transition-all duration-300">
        <div className="mx-auto flex w-full max-w-6xl items-center justify-between px-6 py-4">
          <Link to="/" className="flex items-center gap-3 group cursor-pointer">
            <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-gradient-to-br from-primary to-violet-500 shadow-lg shadow-primary/25 text-white transition-transform group-hover:scale-105">
              <Sparkles size={18} className="animate-pulse" />
            </div>
            <div>
              <p className="text-base font-bold tracking-tight text-foreground">EduMind</p>
              <p className="text-[10px] uppercase tracking-wider text-muted-foreground font-semibold">AI Lecture Studio</p>
            </div>
          </Link>
          <div className="flex items-center gap-4">
            <ThemeToggle />
            <Button variant="outline" className="border-border hover:bg-muted/50 hidden sm:flex font-semibold transition-colors" asChild>
              <Link to="/?login=true">Đăng nhập</Link>
            </Button>
            <Button className="bg-primary text-primary-foreground shadow-lg shadow-primary/25 hover:shadow-primary/40 hover:-translate-y-0.5 transition-all duration-300 font-semibold" asChild>
              <Link to="/?login=true">Bắt đầu miễn phí</Link>
            </Button>
          </div>
        </div>
      </header>

      {/* ── Main Hero Section ── */}
      <motion.main 
        className="relative z-10 mx-auto grid w-full max-w-6xl grid-cols-1 items-center gap-12 px-6 pt-32 pb-20 lg:grid-cols-[1.15fr_0.85fr] lg:pt-0 lg:pb-0 flex-1"
        variants={containerVariants}
        initial="hidden"
        animate="show"
      >
        <div className="space-y-8">
          <motion.div variants={itemVariants} className="inline-flex items-center gap-2 rounded-full border border-primary/20 bg-primary/10 dark:bg-primary/15 px-4 py-1.5 text-sm font-medium text-primary shadow-sm backdrop-blur-sm">
            <Wand2 size={16} className="animate-pulse" />
            <span>EduMind AI v2.0 đã ra mắt</span>
            <ChevronRight size={14} />
          </motion.div>
          
          <motion.h1 variants={itemVariants} className="text-4xl font-bold tracking-tight md:text-6xl lg:text-[4rem] lg:leading-[1.15] text-foreground">
            Bài giảng tương tác <br />
            <span className="text-gradient-primary">
              được tạo bởi AI.
            </span>
          </motion.h1>
          
          <motion.p variants={itemVariants} className="max-w-[85%] text-lg text-muted-foreground md:text-xl leading-relaxed">
            Biến tài liệu khô khan thành trải nghiệm học tập sinh động. Giáo viên upload tài liệu, AI tạo slide và quiz theo cấu trúc, hệ thống tự động phân tích gợi ý cải tiến.
          </motion.p>
          
          <motion.div variants={itemVariants} className="flex flex-wrap items-center gap-4">
            <Button size="lg" className="h-12 px-8 text-base bg-primary text-primary-foreground shadow-xl shadow-primary/25 hover:shadow-primary/40 hover:-translate-y-1 transition-all duration-300 font-semibold" asChild>
              <Link to="/?login=true">
                Bắt đầu ngay <ArrowRight size={18} className="ml-2" />
              </Link>
            </Button>
            <Button variant="outline" size="lg" className="h-12 px-8 text-base border-border bg-background/50 backdrop-blur hover:bg-muted hover:-translate-y-1 transition-all duration-300 font-semibold text-foreground" asChild>
              <Link to="/?login=true">Đăng nhập</Link>
            </Button>
          </motion.div>
          
          <motion.div variants={itemVariants} className="grid grid-cols-1 sm:grid-cols-3 gap-6 border-t border-border/50 pt-8 mt-4">
            {stats.map((stat) => (
              <div key={stat.label} className="space-y-1.5 group cursor-default">
                <p className="text-2xl font-bold text-foreground group-hover:text-primary transition-colors">{stat.value}</p>
                <p className="text-[11px] font-bold text-muted-foreground uppercase tracking-wider">{stat.label}</p>
              </div>
            ))}
          </motion.div>
        </div>

        {/* Hero Visual */}
        <motion.div variants={itemVariants} className="relative w-full aspect-square lg:aspect-auto lg:h-[600px] flex items-center justify-center">
          <div className="absolute inset-0 bg-gradient-to-tr from-primary/10 to-indigo-500/10 rounded-[2.5rem] blur-2xl" />
          
          {/* Glass Card Container */}
          <div className="relative w-full max-w-sm rounded-[2.5rem] border border-border/50 bg-card/60 p-6 sm:p-8 shadow-2xl backdrop-blur-2xl ring-1 ring-white/10 dark:ring-white/5 bento-card">
            <div className="flex items-center justify-between border-b border-border/40 pb-4 mb-6">
              <div className="flex items-center gap-2" aria-hidden="true">
                <div className="w-3 h-3 rounded-full bg-destructive/80" />
                <div className="w-3 h-3 rounded-full bg-amber-500/80" />
                <div className="w-3 h-3 rounded-full bg-emerald-500/80" />
              </div>
              <span className="rounded-full bg-primary/10 px-3 py-1 text-xs font-semibold text-primary border border-primary/20">
                Live Preview
              </span>
            </div>
            
            <div className="space-y-4">
              {previewCards.map((card, idx) => (
                <motion.div 
                  key={card.label} 
                  initial={{ opacity: 0, x: 20 }}
                  animate={{ opacity: 1, x: 0 }}
                  transition={{ delay: 0.6 + idx * 0.2 }}
                  className={`group rounded-2xl border border-border/50 bg-background/80 dark:bg-background/60 p-4 shadow-sm transition-all duration-300 hover:shadow-md ${card.hoverBorder} hover:-translate-y-1 cursor-default`}
                >
                  <div className="flex items-center justify-between mb-2">
                    <p className={`text-[10px] font-bold uppercase tracking-widest ${card.labelColor}`}>{card.label}</p>
                    <card.Icon size={14} className={`${card.iconColor} opacity-50 group-hover:opacity-100 transition-opacity`} />
                  </div>
                  <p className="text-sm font-semibold text-foreground leading-relaxed">
                    {card.text}
                  </p>
                </motion.div>
              ))}
            </div>

            {/* Play overlay button for visual effect */}
            <motion.div 
              initial={{ scale: 0 }} 
              animate={{ scale: 1 }} 
              transition={{ delay: 1.2, type: 'spring', stiffness: 200 }}
              className="absolute -bottom-6 -right-6 w-20 h-20 bg-background border border-border/50 rounded-2xl shadow-xl flex items-center justify-center text-primary z-20 hover:scale-110 transition-transform cursor-pointer"
            >
              <PlaySquare size={32} className="ml-1" />
            </motion.div>
          </div>
        </motion.div>
      </motion.main>
    </div>
  )
}
