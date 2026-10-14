/**
 * Logic một lượt làm bài: tự lưu đáp án (debounce), đếm ngược, ghi nhận rời tab, nộp bài.
 */
import { useCallback, useEffect, useRef, useState } from 'react'
import { getAttempt, recordTabSignal, saveAnswer, submitAttempt } from '@/api/quizApi'
import { getErrorStatus, parseDate } from '@/lib/format'
import type { Attempt, AttemptStart } from '@/types/student'

export type SaveState = 'idle' | 'saving' | 'saved' | 'error'

const SAVE_DELAY_MS = 600

export function useQuizAttempt(start: AttemptStart | null) {
  const [answers, setAnswers] = useState<Record<number, string>>({})
  const [saveState, setSaveState] = useState<SaveState>('idle')
  const [secondsLeft, setSecondsLeft] = useState<number | null>(null)
  const [submitting, setSubmitting] = useState(false)
  const [submitError, setSubmitError] = useState<unknown>(null)
  const [result, setResult] = useState<Attempt | null>(null)

  const versions = useRef<Record<number, number>>({})
  const timers = useRef<Record<number, ReturnType<typeof setTimeout>>>({})
  const pending = useRef<Set<number>>(new Set())
  const latest = useRef<Record<number, string>>({})
  const autoSubmitted = useRef(false)

  const attemptId = start?.attemptId

  // Khôi phục đáp án đã lưu khi làm tiếp một lượt đang dở
  useEffect(() => {
    if (!attemptId) return
    getAttempt(attemptId).then(a => {
      const restored: Record<number, string> = {}
      a.answers.forEach(ans => {
        restored[ans.questionId] = ans.response ?? ''
        versions.current[ans.questionId] = ans.answerVersion
      })
      latest.current = { ...restored }
      setAnswers(restored)
    }).catch(() => { /* lượt mới chưa có đáp án */ })
  }, [attemptId])

  /** Đồng bộ lại version của mọi câu từ server (dùng khi bị 409). */
  const refreshVersions = useCallback(async () => {
    if (!attemptId) return
    const a = await getAttempt(attemptId)
    a.answers.forEach(ans => { versions.current[ans.questionId] = ans.answerVersion })
  }, [attemptId])

  const persist = useCallback(async (questionId: number) => {
    if (!attemptId) return
    pending.current.add(questionId)
    setSaveState('saving')
    const send = async () => {
      const value = latest.current[questionId]
      const existed = versions.current[questionId] !== undefined
      await saveAnswer(attemptId, questionId, value === '' ? null : value, versions.current[questionId] ?? 0)
      // Response trả version TRƯỚC khi tăng (FIX.md #4): câu mới giữ 0, câu đã có thì +1
      versions.current[questionId] = existed ? (versions.current[questionId] ?? 0) + 1 : 0
    }
    try {
      try {
        await send()
      } catch (error) {
        // Lệch version (mở bài ở 2 tab, hoặc version server khác dự đoán) → lấy lại rồi thử 1 lần.
        // Backend hiện trả 500 thay vì 409 khi sai version (FIX.md #4) nên xử lý cả hai.
        const status = getErrorStatus(error)
        if (status !== 409 && status !== 500) throw error
        await refreshVersions()
        await send()
      }
      pending.current.delete(questionId)
      if (pending.current.size === 0) setSaveState('saved')
    } catch {
      pending.current.delete(questionId)
      setSaveState('error')
    }
  }, [attemptId, refreshVersions])

  const setAnswer = useCallback((questionId: number, value: string) => {
    latest.current[questionId] = value
    setAnswers(prev => ({ ...prev, [questionId]: value }))
    clearTimeout(timers.current[questionId])
    timers.current[questionId] = setTimeout(() => persist(questionId), SAVE_DELAY_MS)
  }, [persist])

  /** Lưu ngay các đáp án còn đang chờ debounce. */
  const flush = useCallback(async () => {
    const ids = Object.keys(timers.current).map(Number)
    ids.forEach(id => clearTimeout(timers.current[id]))
    timers.current = {}
    await Promise.all(ids.map(id => persist(id)))
  }, [persist])

  const submit = useCallback(async () => {
    if (!attemptId || submitting) return
    setSubmitting(true)
    setSubmitError(null)
    try {
      await flush()
      setResult(await submitAttempt(attemptId))
    } catch (error) {
      setSubmitError(error)
    } finally {
      setSubmitting(false)
    }
  }, [attemptId, flush, submitting])

  // Đếm ngược tới deadlineAt, hết giờ thì tự nộp
  useEffect(() => {
    const deadline = parseDate(start?.deadlineAt)?.getTime()
    if (!deadline || result) return
    const tick = () => {
      const left = Math.max(0, Math.floor((deadline - Date.now()) / 1000))
      setSecondsLeft(left)
      if (left === 0 && !autoSubmitted.current) {
        autoSubmitted.current = true
        submit()
      }
    }
    tick()
    const id = setInterval(tick, 1000)
    return () => clearInterval(id)
  }, [start?.deadlineAt, result, submit])

  // Ghi nhận rời tab (backend dùng để phát hiện gian lận)
  useEffect(() => {
    if (!attemptId || result) return
    const onVisibility = () => {
      if (document.visibilityState === 'hidden') recordTabSignal(attemptId).catch(() => {})
    }
    document.addEventListener('visibilitychange', onVisibility)
    return () => document.removeEventListener('visibilitychange', onVisibility)
  }, [attemptId, result])

  // Cảnh báo khi đóng tab lúc còn đáp án chưa lưu
  useEffect(() => {
    if (!attemptId || result) return
    const onBeforeUnload = (e: BeforeUnloadEvent) => {
      if (Object.keys(timers.current).length > 0 || pending.current.size > 0) e.preventDefault()
    }
    window.addEventListener('beforeunload', onBeforeUnload)
    return () => window.removeEventListener('beforeunload', onBeforeUnload)
  }, [attemptId, result])

  return { answers, setAnswer, saveState, secondsLeft, submit, submitting, submitError, result }
}
