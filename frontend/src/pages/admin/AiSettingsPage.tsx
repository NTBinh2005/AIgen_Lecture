import { useState } from 'react'
import { motion, type Variants } from 'framer-motion'
import { BrainCircuit, Key, Save, Sparkles, Mic, Image as ImageIcon, CheckCircle2, Zap } from 'lucide-react'
import { Button } from '@/components/ui/button'

const containerVariants: Variants = {
  hidden: { opacity: 0 },
  show: { opacity: 1, transition: { staggerChildren: 0.1 } },
}
const itemVariants: Variants = {
  hidden: { opacity: 0, y: 20 },
  show: { opacity: 1, y: 0, transition: { type: 'spring', stiffness: 300, damping: 24 } },
}

export default function AiSettingsPage() {
  const [isSaving, setIsSaving] = useState(false)
  const [saved, setSaved] = useState(false)

  const handleSave = () => {
    setIsSaving(true)
    setTimeout(() => {
      setIsSaving(false)
      setSaved(true)
      setTimeout(() => setSaved(false), 3000)
    }, 1500)
  }

  return (
    <motion.div
      className="space-y-8 max-w-5xl mx-auto pb-10"
      variants={containerVariants}
      initial="hidden"
      animate="show"
    >
      {/* HEADER */}
      <motion.div variants={itemVariants} className="flex flex-col md:flex-row justify-between items-start md:items-end gap-4">
        <div>
          <p className="text-sm font-medium text-muted-foreground mb-1">Quản trị hệ thống</p>
          <h1 className="text-3xl md:text-4xl font-bold tracking-tight text-foreground flex items-center gap-3">
            Cấu hình <span className="bg-gradient-to-r from-violet-500 to-fuchsia-500 bg-clip-text text-transparent">AI & APIs</span>
            <BrainCircuit className="text-fuchsia-500" size={32} />
          </h1>
          <p className="text-muted-foreground mt-2">Quản lý các khóa API và tùy chỉnh các dịch vụ AI được tích hợp trong hệ thống.</p>
        </div>
        <Button 
          onClick={handleSave} 
          disabled={isSaving}
          className="bg-gradient-to-r from-violet-600 to-fuchsia-600 hover:from-violet-500 hover:to-fuchsia-500 text-white rounded-xl h-11 px-6 shadow-lg shadow-violet-500/25 transition-all"
        >
          {isSaving ? <Zap className="mr-2 h-4 w-4 animate-spin" /> : (saved ? <CheckCircle2 className="mr-2 h-4 w-4" /> : <Save className="mr-2 h-4 w-4" />)}
          {isSaving ? 'Đang lưu...' : (saved ? 'Đã lưu thành công' : 'Lưu thay đổi')}
        </Button>
      </motion.div>

      {/* GEMINI SECTION */}
      <motion.div variants={itemVariants} className="rounded-3xl border border-border/50 bg-card/60 backdrop-blur-xl shadow-sm overflow-hidden relative">
        <div className="absolute top-0 right-0 p-8 opacity-5 pointer-events-none">
          <Sparkles size={120} />
        </div>
        <div className="p-6 md:p-8 space-y-6 relative z-10">
          <div className="flex items-center gap-3 mb-2">
            <div className="w-12 h-12 rounded-2xl bg-blue-500/10 flex items-center justify-center text-blue-500">
              <Sparkles size={24} />
            </div>
            <div>
              <h2 className="text-xl font-bold text-foreground">Google Gemini (Văn bản)</h2>
              <p className="text-sm text-muted-foreground">Sử dụng để phân tích tài liệu, sinh slides, câu hỏi trắc nghiệm và hình ảnh prompt.</p>
            </div>
          </div>
          
          <div className="space-y-4">
            <div>
              <label className="text-sm font-semibold text-foreground flex items-center gap-2 mb-2">
                <Key size={14} className="text-muted-foreground" />
                Gemini API Key
              </label>
              <input
                type="password"
                defaultValue="AIzaSyAxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
                className="w-full px-4 py-3 rounded-xl border border-border/60 bg-background/50 text-foreground focus:outline-none focus:ring-2 focus:ring-blue-500/40 transition"
              />
              <p className="text-xs text-muted-foreground mt-1.5">Lấy key tại Google AI Studio. Model hiện tại: gemini-3.6-flash</p>
            </div>
          </div>
        </div>
      </motion.div>

      <div className="grid grid-cols-1 md:grid-cols-2 gap-8">
        {/* TEXT TO SPEECH SECTION */}
        <motion.div variants={itemVariants} className="rounded-3xl border border-border/50 bg-card/60 backdrop-blur-xl shadow-sm overflow-hidden">
          <div className="p-6 md:p-8 space-y-6">
            <div className="flex items-center gap-3 mb-2">
              <div className="w-12 h-12 rounded-2xl bg-emerald-500/10 flex items-center justify-center text-emerald-500">
                <Mic size={24} />
              </div>
              <div>
                <h2 className="text-xl font-bold text-foreground">Text-to-Speech</h2>
                <p className="text-sm text-muted-foreground">Dịch vụ lồng tiếng cho video bài giảng.</p>
              </div>
            </div>
            
            <div className="space-y-5">
              <div>
                <label className="text-sm font-semibold text-foreground mb-2 block">Nhà cung cấp (Provider)</label>
                <select className="w-full px-4 py-3 rounded-xl border border-border/60 bg-background/50 text-foreground focus:outline-none focus:ring-2 focus:ring-emerald-500/40 transition">
                  <option value="google">Google Translate TTS (Miễn phí)</option>
                  <option value="elevenlabs">ElevenLabs (Cao cấp)</option>
                </select>
              </div>
              <div>
                <label className="text-sm font-semibold text-foreground flex items-center gap-2 mb-2">
                  <Key size={14} className="text-muted-foreground" />
                  ElevenLabs API Key
                </label>
                <input
                  type="password"
                  defaultValue="sk_xxxxxxxxxxxxxxxxxxxxxxx"
                  className="w-full px-4 py-3 rounded-xl border border-border/60 bg-background/50 text-foreground focus:outline-none focus:ring-2 focus:ring-emerald-500/40 transition"
                />
              </div>
            </div>
          </div>
        </motion.div>

        {/* IMAGE GENERATION SECTION */}
        <motion.div variants={itemVariants} className="rounded-3xl border border-border/50 bg-card/60 backdrop-blur-xl shadow-sm overflow-hidden">
          <div className="p-6 md:p-8 space-y-6">
            <div className="flex items-center gap-3 mb-2">
              <div className="w-12 h-12 rounded-2xl bg-pink-500/10 flex items-center justify-center text-pink-500">
                <ImageIcon size={24} />
              </div>
              <div>
                <h2 className="text-xl font-bold text-foreground">Pollinations AI (Hình ảnh)</h2>
                <p className="text-sm text-muted-foreground">Vẽ hình minh họa cho các slide bài giảng.</p>
              </div>
            </div>
            
            <div className="space-y-5">
              <div>
                <label className="text-sm font-semibold text-foreground mb-2 block">Chất lượng hình ảnh</label>
                <select className="w-full px-4 py-3 rounded-xl border border-border/60 bg-background/50 text-foreground focus:outline-none focus:ring-2 focus:ring-pink-500/40 transition">
                  <option value="600">Standard (600x600)</option>
                  <option value="800">HD (800x800)</option>
                  <option value="1024">Ultra (1024x1024)</option>
                </select>
              </div>
              <div>
                <label className="text-sm font-semibold text-foreground mb-2 block">Phong cách vẽ mặc định (Style)</label>
                <select className="w-full px-4 py-3 rounded-xl border border-border/60 bg-background/50 text-foreground focus:outline-none focus:ring-2 focus:ring-pink-500/40 transition">
                  <option value="cinematic">Cinematic 4K (Thực tế)</option>
                  <option value="anime">Anime (Hoạt hình)</option>
                  <option value="3d-model">3D Model (Mô hình 3D)</option>
                  <option value="comic">Comic Book (Truyện tranh)</option>
                </select>
                <p className="text-xs text-muted-foreground mt-1.5">Pollinations hiện đang miễn phí và không yêu cầu API Key.</p>
              </div>
            </div>
          </div>
        </motion.div>
      </div>
    </motion.div>
  )
}
