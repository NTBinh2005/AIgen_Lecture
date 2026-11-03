const CLASS_GRADIENTS = [
  'from-blue-500 to-indigo-600',
  'from-violet-500 to-fuchsia-600',
  'from-emerald-500 to-teal-600',
  'from-amber-500 to-orange-600',
  'from-rose-500 to-pink-600',
]

/** Gradient theo classId để mỗi lớp có màu nhận diện riêng. */
export function classGradient(classId: number) {
  return CLASS_GRADIENTS[classId % CLASS_GRADIENTS.length]
}
