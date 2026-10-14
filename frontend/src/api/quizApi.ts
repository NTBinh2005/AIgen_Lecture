/**
 * API làm bài kiểm tra cho Student (quiz assignment → attempt).
 */
import axiosInstance from './axiosInstance'
import type { Attempt, AttemptAnswer, AttemptStart, StudentAssignment } from '@/types/student'

export async function getClassAssignments(classId: number): Promise<StudentAssignment[]> {
  const res = await axiosInstance.get<StudentAssignment[]>(`/quiz-assignments/student/class/${classId}`)
  return res.data
}

/** Bắt đầu lượt làm mới, hoặc trả lại lượt đang IN_PROGRESS. */
export async function startAttempt(assignmentId: number): Promise<AttemptStart> {
  const res = await axiosInstance.post<AttemptStart>(`/quiz-assignments/${assignmentId}/start`)
  return res.data
}

export async function getAttempt(attemptId: number): Promise<Attempt> {
  const res = await axiosInstance.get<Attempt>(`/attempts/${attemptId}`)
  return res.data
}

/**
 * Lưu một câu trả lời (autosave). `answerVersion`: 0 cho câu mới, version hiện tại khi sửa;
 * sai version → 409 (optimistic lock).
 */
export async function saveAnswer(
  attemptId: number,
  questionId: number,
  response: string | null,
  answerVersion: number,
): Promise<AttemptAnswer> {
  const res = await axiosInstance.put<AttemptAnswer>(`/attempts/${attemptId}/answers/${questionId}`, {
    response,
    answerVersion,
  })
  return res.data
}

export async function submitAttempt(attemptId: number): Promise<Attempt> {
  const res = await axiosInstance.post<Attempt>(`/attempts/${attemptId}/submit`)
  return res.data
}

/** Ghi nhận rời tab trong lúc làm bài. */
export async function recordTabSignal(attemptId: number): Promise<void> {
  await axiosInstance.post(`/attempts/${attemptId}/signals`)
}

export async function getMyResult(assignmentId: number): Promise<Attempt> {
  const res = await axiosInstance.get<Attempt>(`/quiz-assignments/${assignmentId}/my-result`)
  return res.data
}
