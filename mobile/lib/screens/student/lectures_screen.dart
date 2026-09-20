import 'package:flutter/material.dart';
import 'lecture_video_screen.dart';

class LecturesScreen extends StatelessWidget {
  const LecturesScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final colorScheme = theme.colorScheme;
    final isDark = theme.brightness == Brightness.dark;

    return Scaffold(
      backgroundColor: colorScheme.background,
      appBar: AppBar(
        title: const Text(
          'Bài giảng tương tác',
          style: TextStyle(fontWeight: FontWeight.bold),
        ),
      ),
      body: Column(
        children: [
          // Search and Filter section
          Padding(
            padding: const EdgeInsets.all(16.0),
            child: Row(
              children: [
                Expanded(
                  child: TextField(
                    decoration: InputDecoration(
                      hintText: 'Tìm kiếm bài giảng...',
                      prefixIcon: const Icon(Icons.search),
                      contentPadding: const EdgeInsets.symmetric(vertical: 0, horizontal: 16),
                      border: OutlineInputBorder(
                        borderRadius: BorderRadius.circular(12),
                        borderSide: BorderSide(
                          color: isDark ? const Color(0xFF334155) : const Color(0xFFE2E8F0),
                        ),
                      ),
                      enabledBorder: OutlineInputBorder(
                        borderRadius: BorderRadius.circular(12),
                        borderSide: BorderSide(
                          color: isDark ? const Color(0xFF334155) : const Color(0xFFE2E8F0),
                        ),
                      ),
                    ),
                  ),
                ),
                const SizedBox(width: 12),
                Container(
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: colorScheme.primary.withOpacity(0.1),
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: Icon(
                    Icons.filter_list,
                    color: colorScheme.primary,
                  ),
                ),
              ],
            ),
          ),

          // Filters Category Scroll
          SingleChildScrollView(
            scrollDirection: Axis.horizontal,
            padding: const EdgeInsets.symmetric(horizontal: 16.0),
            child: Row(
              children: [
                _buildCategoryChip(context, 'Tất cả', true),
                _buildCategoryChip(context, 'Đang học', false),
                _buildCategoryChip(context, 'Đã hoàn thành', false),
                _buildCategoryChip(context, 'Mới nhất', false),
              ],
            ),
          ),
          const SizedBox(height: 16),

          // Lecture items list
          Expanded(
            child: ListView(
              padding: const EdgeInsets.symmetric(horizontal: 16.0),
              children: [
                _buildLectureCard(
                  context: context,
                  title: 'Cấu trúc dữ liệu & Giải thuật',
                  topic: 'Chương 3: Cây nhị phân và Cây tìm kiếm nhị phân',
                  questionsCount: 15,
                  timeEstimate: '30 phút',
                  progress: 0.8,
                  status: 'Đang học',
                  statusColor: const Color(0xFF3B82F6),
                ),
                const SizedBox(height: 16),
                _buildLectureCard(
                  context: context,
                  title: 'Nhập môn Công nghệ phần mềm',
                  topic: 'Chương 1: Quy trình phát triển phần mềm Agile/Scrum',
                  questionsCount: 10,
                  timeEstimate: '20 phút',
                  progress: 0.45,
                  status: 'Đang học',
                  statusColor: const Color(0xFF3B82F6),
                ),
                const SizedBox(height: 16),
                _buildLectureCard(
                  context: context,
                  title: 'Kiến trúc & Thiết kế phần mềm',
                  topic: 'Chương 5: Microservices & Event-Driven Architecture',
                  questionsCount: 20,
                  timeEstimate: '45 phút',
                  progress: 1.0,
                  status: 'Đã xong',
                  statusColor: const Color(0xFF10B981),
                ),
                const SizedBox(height: 16),
                _buildLectureCard(
                  context: context,
                  title: 'Phát triển ứng dụng di động',
                  topic: 'Chương 2: Widget tree và State management trong Flutter',
                  questionsCount: 8,
                  timeEstimate: '15 phút',
                  progress: 0.0,
                  status: 'Chưa học',
                  statusColor: const Color(0xFF64748B),
                ),
                const SizedBox(height: 16),
              ],
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildCategoryChip(BuildContext context, String text, bool isSelected) {
    final theme = Theme.of(context);
    final colorScheme = theme.colorScheme;
    final isDark = theme.brightness == Brightness.dark;

    return Container(
      margin: const EdgeInsets.only(right: 8.0),
      child: ChoiceChip(
        label: Text(text),
        selected: isSelected,
        onSelected: (bool selected) {},
        selectedColor: colorScheme.primary,
        backgroundColor: isDark ? const Color(0xFF1E293B) : const Color(0xFFF1F5F9),
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(100),
          side: BorderSide(
            color: isSelected
                ? Colors.transparent
                : (isDark ? const Color(0xFF334155) : const Color(0xFFE2E8F0)),
          ),
        ),
        labelStyle: TextStyle(
          fontWeight: isSelected ? FontWeight.bold : FontWeight.normal,
          color: isSelected ? Colors.white : (isDark ? Colors.white70 : Colors.black87),
        ),
      ),
    );
  }

  Widget _buildLectureCard({
    required BuildContext context,
    required String title,
    required String topic,
    required int questionsCount,
    required String timeEstimate,
    required double progress,
    required String status,
    required Color statusColor,
  }) {
    final theme = Theme.of(context);
    final colorScheme = theme.colorScheme;
    final isDark = theme.brightness == Brightness.dark;

    return Container(
      decoration: BoxDecoration(
        color: colorScheme.surface,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(
          color: isDark ? const Color(0xFF334155) : const Color(0xFFE2E8F0),
          width: 1,
        ),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Padding(
            padding: const EdgeInsets.all(16.0),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                  children: [
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                      decoration: BoxDecoration(
                        color: statusColor.withOpacity(0.1),
                        borderRadius: BorderRadius.circular(8),
                      ),
                      child: Text(
                        status,
                        style: TextStyle(
                          fontSize: 11,
                          fontWeight: FontWeight.bold,
                          color: statusColor,
                        ),
                      ),
                    ),
                    Row(
                      children: [
                        const Icon(Icons.help_outline, size: 14, color: Colors.grey),
                        const SizedBox(width: 4),
                        Text(
                          '$questionsCount câu hỏi',
                          style: const TextStyle(fontSize: 12, color: Colors.grey),
                        ),
                      ],
                    ),
                  ],
                ),
                const SizedBox(height: 12),
                Text(
                  title,
                  style: theme.textTheme.titleMedium?.copyWith(
                    fontWeight: FontWeight.bold,
                  ),
                ),
                const SizedBox(height: 4),
                Text(
                  topic,
                  style: theme.textTheme.bodyMedium?.copyWith(
                    color: isDark ? const Color(0xFF94A3B8) : const Color(0xFF64748B),
                  ),
                ),
                const SizedBox(height: 16),
                Row(
                  children: [
                    const Icon(Icons.access_time, size: 14, color: Colors.grey),
                    const SizedBox(width: 4),
                    Text(
                      timeEstimate,
                      style: const TextStyle(fontSize: 12, color: Colors.grey),
                    ),
                    const Spacer(),
                    Text(
                      'Tiến độ: ${(progress * 100).toInt()}%',
                      style: TextStyle(
                        fontSize: 12,
                        fontWeight: FontWeight.bold,
                        color: colorScheme.primary,
                      ),
                    ),
                  ],
                ),
              ],
            ),
          ),
          const Divider(height: 1, thickness: 1),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16.0, vertical: 12.0),
            child: Row(
              children: [
                Expanded(
                  child: ClipRRect(
                    borderRadius: BorderRadius.circular(4),
                    child: LinearProgressIndicator(
                      value: progress,
                      minHeight: 4,
                      backgroundColor: isDark ? const Color(0xFF334155) : const Color(0xFFE2E8F0),
                      valueColor: AlwaysStoppedAnimation<Color>(colorScheme.primary),
                    ),
                  ),
                ),
                const SizedBox(width: 16),
                ElevatedButton(
                  onPressed: () {
                    // TODO: Thay lectureId = 1 bằng ID thực từ API khi backend sẵn sàng
                    Navigator.of(context).push(
                      MaterialPageRoute(
                        builder: (context) => LectureVideoScreen(
                          lectureId: 1,
                          lectureTitle: title,
                        ),
                      ),
                    );
                  },
                  style: ElevatedButton.styleFrom(
                    backgroundColor: colorScheme.primary,
                    foregroundColor: Colors.white,
                    elevation: 0,
                    shape: RoundedRectangleBorder(
                      borderRadius: BorderRadius.circular(8),
                    ),
                    padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
                  ),
                  child: Text(progress == 1.0 ? 'Ôn tập' : (progress > 0.0 ? 'Tiếp tục' : 'Bắt đầu')),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}
