import 'dart:ui';
import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:file_picker/file_picker.dart';
import 'dart:async';
import '../../services/lecture_service.dart';
import '../student/lecture_video_screen.dart';

class SlideForm {
  String id = UniqueKey().toString();
  String title = '';
  List<String> bulletPoints = [''];
  String narrationText = '';
}

class QuizForm {
  String id = UniqueKey().toString();
  String questionText = '';
  List<String> options = ['A. ', 'B. ', 'C. ', 'D. '];
  String correctAnswer = 'A';
}

class TeacherCreateLectureScreen extends StatefulWidget {
  const TeacherCreateLectureScreen({super.key});

  @override
  State<TeacherCreateLectureScreen> createState() => _TeacherCreateLectureScreenState();
}

class _TeacherCreateLectureScreenState extends State<TeacherCreateLectureScreen> with TickerProviderStateMixin {
  String _step = 'form'; // form, generating, done, failed
  String _lectureTitle = '';
  List<SlideForm> _slides = [SlideForm()];
  List<QuizForm> _quizzes = [];
  
  String _videoStatus = 'PENDING';
  String? _videoUrl;
  String? _error;
  Timer? _pollTimer;
  bool _isGeneratingLLM = false;
  int? _lectureId;

  void _addSlide() {
    setState(() => _slides.add(SlideForm()));
  }

  void _removeSlide(String id) {
    if (_slides.length > 1) {
      setState(() => _slides.removeWhere((s) => s.id == id));
    }
  }

  void _addBullet(SlideForm slide) {
    setState(() => slide.bulletPoints.add(''));
  }

  void _removeBullet(SlideForm slide, int index) {
    if (slide.bulletPoints.length > 1) {
      setState(() => slide.bulletPoints.removeAt(index));
    }
  }

  void _addQuiz() {
    setState(() => _quizzes.add(QuizForm()));
  }

  void _removeQuiz(String id) {
    setState(() => _quizzes.removeWhere((q) => q.id == id));
  }

  Future<void> _handleFileUpload() async {
    try {
      FilePickerResult? result = await FilePicker.platform.pickFiles(
        type: FileType.custom,
        allowedExtensions: ['pdf', 'docx', 'pptx'],
      );

      if (result == null || result.files.isEmpty) return;

      setState(() {
        _isGeneratingLLM = true;
        _error = null;
      });

      final data = await LectureService.generateFromFile(result.files.first);

      setState(() {
        if (data['slides'] != null && (data['slides'] as List).isNotEmpty) {
          _slides = (data['slides'] as List).map((s) {
            var form = SlideForm();
            form.title = s['title'] ?? '';
            form.bulletPoints = s['bulletPoints'] != null && (s['bulletPoints'] as List).isNotEmpty 
                ? List<String>.from(s['bulletPoints']) 
                : [''];
            form.narrationText = s['narrationText'] ?? '';
            return form;
          }).toList();
        } else {
          _error = 'AI không sinh được slide nào từ tài liệu này.';
        }

        if (data['quizzes'] != null && (data['quizzes'] as List).isNotEmpty) {
          _quizzes = (data['quizzes'] as List).map((q) {
            var form = QuizForm();
            form.questionText = q['questionText'] ?? '';
            form.options = q['options'] != null && (q['options'] as List).length == 4 
                ? List<String>.from(q['options']) 
                : ['A. ', 'B. ', 'C. ', 'D. '];
            form.correctAnswer = q['correctAnswer'] ?? 'A';
            return form;
          }).toList();
        }
      });
    } catch (e) {
      setState(() {
        _error = 'Lỗi khi gọi AI. Vui lòng thử lại. Lỗi: $e';
      });
    } finally {
      setState(() {
        _isGeneratingLLM = false;
      });
    }
  }

  void _startPolling(int lectureId) {
    _pollTimer = Timer.periodic(const Duration(seconds: 3), (timer) async {
      try {
        final status = await LectureService.getVideoStatus(lectureId);
        setState(() {
          _videoStatus = status['videoStatus'];
          if (_videoStatus == 'DONE') {
            _videoUrl = status['videoUrl'];
            _step = 'done';
            timer.cancel();
          } else if (_videoStatus == 'FAILED') {
            _step = 'failed';
            timer.cancel();
          }
        });
      } catch (_) {}
    });
  }

  Future<void> _handleSubmit() async {
    if (_lectureTitle.trim().isEmpty) {
      setState(() => _error = 'Vui lòng nhập tiêu đề bài giảng.');
      return;
    }
    
    int invalidSlide = _slides.indexWhere((s) => s.title.trim().isEmpty || s.narrationText.trim().isEmpty);
    if (invalidSlide != -1) {
      setState(() => _error = 'Slide ${invalidSlide + 1} chưa có tiêu đề hoặc nội dung đọc.');
      return;
    }

    setState(() {
      _error = null;
      _step = 'generating';
      _videoStatus = 'PENDING';
    });

    final slideDtos = _slides.map((s) => {
      'title': s.title,
      'bulletPoints': s.bulletPoints.where((b) => b.trim().isNotEmpty).toList(),
      'narrationText': s.narrationText,
    }).toList();

    final quizDtos = _quizzes.where((q) => q.questionText.trim().isNotEmpty).map((q) => {
      'questionText': q.questionText,
      'options': q.options,
      'correctAnswer': q.correctAnswer,
    }).toList();

    final payload = {
      'title': _lectureTitle,
      'slides': slideDtos,
    };
    if (quizDtos.isNotEmpty) {
      payload['quizzes'] = quizDtos;
    }

    try {
      final lecture = await LectureService.createLecture(payload);
      setState(() {
        _lectureId = lecture['lectureId'];
        _videoStatus = lecture['videoStatus'];
      });
      _startPolling(lecture['lectureId']);
    } catch (err) {
      setState(() {
        _error = 'Không thể tạo bài giảng. Vui lòng thử lại.';
        _step = 'form';
      });
    }
  }

  @override
  void dispose() {
    _pollTimer?.cancel();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFF0F172A), // Tông xanh biển đậm fallback
      body: Container(
        decoration: BoxDecoration(
          image: DecorationImage(
            image: const AssetImage('assets/images/mobile_bg.jpg'),
            fit: BoxFit.cover,
            colorFilter: ColorFilter.mode(Colors.black.withOpacity(0.6), BlendMode.darken),
          ),
        ),
        child: SafeArea(
          bottom: false,
          child: Column(
          children: [
            // Top Bar
            Padding(
              padding: const EdgeInsets.fromLTRB(20, 20, 20, 10),
              child: Row(
                children: [
                  GestureDetector(
                    onTap: () => Navigator.pop(context),
                    child: _buildGlassyIconButton(Icons.arrow_back_rounded),
                  ),
                  const SizedBox(width: 16),
                  Text(
                    'Tạo bài giảng',
                    style: GoogleFonts.plusJakartaSans(
                      fontSize: 24,
                      color: Colors.white,
                      fontWeight: FontWeight.bold,
                      letterSpacing: -0.5,
                    ),
                  ),
                ],
              ),
            ),

            Expanded(
              child: SingleChildScrollView(
                padding: const EdgeInsets.fromLTRB(20, 10, 20, 120),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    if (_error != null)
                      Container(
                        width: double.infinity,
                        margin: const EdgeInsets.only(bottom: 16),
                        padding: const EdgeInsets.all(16),
                        decoration: BoxDecoration(
                          color: Colors.redAccent.withOpacity(0.2),
                          borderRadius: BorderRadius.circular(16),
                          border: Border.all(color: Colors.redAccent.withOpacity(0.5)),
                        ),
                        child: Text(
                          _error!,
                          style: GoogleFonts.plusJakartaSans(color: Colors.white),
                        ),
                      ),
                    
                    if (_step == 'form') ...[
                      // AI Box
                      _buildSmokedGlassContainer(
                        child: Column(
                          crossAxisAlignment: CrossAxisAlignment.start,
                          children: [
                            Row(
                              children: [
                                const Icon(Icons.auto_awesome, color: Colors.blueAccent),
                                const SizedBox(width: 8),
                                Text(
                                  'Tạo nhanh bằng AI',
                                  style: GoogleFonts.plusJakartaSans(
                                    color: Colors.blueAccent,
                                    fontWeight: FontWeight.bold,
                                    fontSize: 16,
                                  ),
                                ),
                              ],
                            ),
                            const SizedBox(height: 8),
                            Text(
                              'Upload file PDF, DOCX, PPTX để AI tự động tạo Slides và Câu hỏi trắc nghiệm.',
                              style: GoogleFonts.plusJakartaSans(
                                color: Colors.white70,
                                fontSize: 13,
                              ),
                            ),
                            const SizedBox(height: 16),
                            SizedBox(
                              width: double.infinity,
                              child: ElevatedButton.icon(
                                onPressed: _isGeneratingLLM ? null : _handleFileUpload,
                                icon: _isGeneratingLLM 
                                    ? const SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white))
                                    : const Icon(Icons.upload_file, color: Colors.white),
                                label: Text(
                                  _isGeneratingLLM ? 'Đang tạo (15-20s)...' : 'Chọn tài liệu',
                                  style: GoogleFonts.plusJakartaSans(color: Colors.white, fontWeight: FontWeight.bold),
                                ),
                                style: ElevatedButton.styleFrom(
                                  backgroundColor: Colors.blueAccent,
                                  foregroundColor: Colors.white,
                                  shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(100)),
                                  padding: const EdgeInsets.symmetric(vertical: 12),
                                ),
                              ),
                            ),
                          ],
                        ),
                      ),
                      const SizedBox(height: 24),

                      // Title
                      Text(
                        'Tiêu đề bài giảng',
                        style: GoogleFonts.plusJakartaSans(color: Colors.white, fontWeight: FontWeight.w600, fontSize: 14),
                      ),
                      const SizedBox(height: 8),
                      _buildTransparentTextField(
                        hintText: 'VD: Cấu trúc dữ liệu...',
                        value: _lectureTitle,
                        onChanged: (val) => _lectureTitle = val,
                      ),
                      const SizedBox(height: 32),

                      // Slides
                      Text(
                        'Slides',
                        style: GoogleFonts.plusJakartaSans(color: Colors.white, fontWeight: FontWeight.w600, fontSize: 18),
                      ),
                      const SizedBox(height: 16),
                      ..._slides.asMap().entries.map((entry) {
                        int idx = entry.key;
                        SlideForm slide = entry.value;
                        return Padding(
                          padding: const EdgeInsets.only(bottom: 16),
                          child: _buildSmokedGlassContainer(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Row(
                                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                                  children: [
                                    Text('SLIDE ${idx + 1}', style: GoogleFonts.plusJakartaSans(color: Colors.white54, fontWeight: FontWeight.bold, fontSize: 12)),
                                    if (_slides.length > 1)
                                      GestureDetector(
                                        onTap: () => _removeSlide(slide.id),
                                        child: const Icon(Icons.delete_outline, color: Colors.redAccent, size: 20),
                                      ),
                                  ],
                                ),
                                const SizedBox(height: 16),
                                _buildTransparentTextField(
                                  hintText: 'Tiêu đề slide...',
                                  value: slide.title,
                                  onChanged: (val) => slide.title = val,
                                ),
                                const SizedBox(height: 16),
                                Text('Bullet Points', style: GoogleFonts.plusJakartaSans(color: Colors.white70, fontSize: 12)),
                                const SizedBox(height: 8),
                                ...slide.bulletPoints.asMap().entries.map((bEntry) {
                                  int bIdx = bEntry.key;
                                  return Padding(
                                    padding: const EdgeInsets.only(bottom: 8),
                                    child: Row(
                                      children: [
                                        Container(width: 6, height: 6, decoration: const BoxDecoration(color: Colors.white, shape: BoxShape.circle)),
                                        const SizedBox(width: 8),
                                        Expanded(
                                          child: _buildTransparentTextField(
                                            hintText: 'Điểm ${bIdx + 1}...',
                                            value: slide.bulletPoints[bIdx],
                                            onChanged: (val) => slide.bulletPoints[bIdx] = val,
                                          ),
                                        ),
                                        if (slide.bulletPoints.length > 1)
                                          IconButton(
                                            icon: const Icon(Icons.close, color: Colors.white54, size: 18),
                                            onPressed: () => _removeBullet(slide, bIdx),
                                          )
                                      ],
                                    ),
                                  );
                                }),
                                TextButton.icon(
                                  onPressed: () => _addBullet(slide),
                                  icon: const Icon(Icons.add, color: Colors.white, size: 16),
                                  label: Text('Thêm bullet', style: GoogleFonts.plusJakartaSans(color: Colors.white)),
                                ),
                                const SizedBox(height: 16),
                                Text('Nội dung đọc (Narration)', style: GoogleFonts.plusJakartaSans(color: Colors.white70, fontSize: 12)),
                                const SizedBox(height: 8),
                                _buildTransparentTextField(
                                  hintText: 'Văn bản sẽ được AI chuyển thành giọng đọc...',
                                  value: slide.narrationText,
                                  onChanged: (val) => slide.narrationText = val,
                                  maxLines: 3,
                                ),
                              ],
                            ),
                          ),
                        );
                      }),
                      OutlinedButton.icon(
                        onPressed: _addSlide,
                        icon: const Icon(Icons.add, color: Colors.white),
                        label: Text('Thêm Slide Mới', style: GoogleFonts.plusJakartaSans(color: Colors.white)),
                        style: OutlinedButton.styleFrom(
                          side: BorderSide(color: Colors.white.withOpacity(0.3)),
                          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(100)),
                          padding: const EdgeInsets.symmetric(vertical: 12, horizontal: 24),
                        ),
                      ),
                      const SizedBox(height: 32),

                      // Quizzes
                      Text(
                        'Câu hỏi trắc nghiệm',
                        style: GoogleFonts.plusJakartaSans(color: Colors.white, fontWeight: FontWeight.w600, fontSize: 18),
                      ),
                      const SizedBox(height: 16),
                      ..._quizzes.asMap().entries.map((entry) {
                        int idx = entry.key;
                        QuizForm quiz = entry.value;
                        return Padding(
                          padding: const EdgeInsets.only(bottom: 16),
                          child: _buildSmokedGlassContainer(
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Row(
                                  mainAxisAlignment: MainAxisAlignment.spaceBetween,
                                  children: [
                                    Text('CÂU ${idx + 1}', style: GoogleFonts.plusJakartaSans(color: Colors.blueAccent, fontWeight: FontWeight.bold, fontSize: 12)),
                                    GestureDetector(
                                      onTap: () => _removeQuiz(quiz.id),
                                      child: const Icon(Icons.delete_outline, color: Colors.redAccent, size: 20),
                                    ),
                                  ],
                                ),
                                const SizedBox(height: 16),
                                _buildTransparentTextField(
                                  hintText: 'Nhập câu hỏi...',
                                  value: quiz.questionText,
                                  onChanged: (val) => quiz.questionText = val,
                                ),
                                const SizedBox(height: 16),
                                ...quiz.options.asMap().entries.map((optEntry) {
                                  int optIdx = optEntry.key;
                                  String letter = ['A', 'B', 'C', 'D'][optIdx];
                                  bool isCorrect = quiz.correctAnswer == letter;
                                  return Padding(
                                    padding: const EdgeInsets.only(bottom: 8),
                                    child: Row(
                                      children: [
                                        GestureDetector(
                                          onTap: () => setState(() => quiz.correctAnswer = letter),
                                          child: Container(
                                            width: 32,
                                            height: 32,
                                            decoration: BoxDecoration(
                                              color: isCorrect ? Colors.green : Colors.white.withOpacity(0.1),
                                              shape: BoxShape.circle,
                                              border: Border.all(color: isCorrect ? Colors.greenAccent : Colors.white.withOpacity(0.3)),
                                            ),
                                            child: Center(
                                              child: Text(letter, style: GoogleFonts.plusJakartaSans(color: Colors.white, fontWeight: FontWeight.bold)),
                                            ),
                                          ),
                                        ),
                                        const SizedBox(width: 12),
                                        Expanded(
                                          child: _buildTransparentTextField(
                                            hintText: 'Đáp án $letter...',
                                            value: quiz.options[optIdx],
                                            onChanged: (val) => quiz.options[optIdx] = val,
                                          ),
                                        ),
                                      ],
                                    ),
                                  );
                                }),
                              ],
                            ),
                          ),
                        );
                      }),
                      OutlinedButton.icon(
                        onPressed: _addQuiz,
                        icon: const Icon(Icons.add, color: Colors.white),
                        label: Text('Thêm Câu Hỏi', style: GoogleFonts.plusJakartaSans(color: Colors.white)),
                        style: OutlinedButton.styleFrom(
                          side: BorderSide(color: Colors.white.withOpacity(0.3)),
                          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(100)),
                          padding: const EdgeInsets.symmetric(vertical: 12, horizontal: 24),
                        ),
                      ),
                      const SizedBox(height: 40),

                      SizedBox(
                        width: double.infinity,
                        height: 56,
                        child: ElevatedButton(
                          onPressed: _handleSubmit,
                          style: ElevatedButton.styleFrom(
                            backgroundColor: Colors.blueAccent,
                            foregroundColor: Colors.white,
                            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(100)),
                          ),
                          child: Text('Tạo Video Bài Giảng', style: GoogleFonts.plusJakartaSans(fontWeight: FontWeight.bold, fontSize: 16)),
                        ),
                      ),
                    ] else ...[
                      // Generating / Done states
                      _buildSmokedGlassContainer(
                        child: Center(
                          child: Column(
                            mainAxisAlignment: MainAxisAlignment.center,
                            children: [
                              if (_step == 'generating') const CircularProgressIndicator(color: Colors.white),
                              if (_step == 'done') const Icon(Icons.check_circle, color: Colors.greenAccent, size: 60),
                              if (_step == 'failed') const Icon(Icons.error, color: Colors.redAccent, size: 60),
                              
                              const SizedBox(height: 24),
                              Text(
                                _step == 'generating' ? 'Đang tạo video...' : _step == 'done' ? 'Video đã sẵn sàng!' : 'Tạo thất bại',
                                style: GoogleFonts.plusJakartaSans(color: Colors.white, fontSize: 20, fontWeight: FontWeight.bold),
                              ),
                              const SizedBox(height: 12),
                              Text(
                                _videoStatus,
                                style: GoogleFonts.plusJakartaSans(color: Colors.white70, fontSize: 14),
                              ),
                              const SizedBox(height: 24),

                              if (_step == 'done' || _step == 'failed')
                                Row(
                                  mainAxisAlignment: MainAxisAlignment.center,
                                  children: [
                                    if (_step == 'done' && _lectureId != null) ...[
                                      ElevatedButton.icon(
                                        onPressed: () {
                                          Navigator.push(
                                            context,
                                            MaterialPageRoute(
                                              builder: (context) => LectureVideoScreen(
                                                lectureId: _lectureId!,
                                                lectureTitle: _lectureTitle,
                                              ),
                                            ),
                                          );
                                        },
                                        icon: const Icon(Icons.play_circle_fill),
                                        label: const Text('Xem Video', style: TextStyle(fontWeight: FontWeight.bold)),
                                        style: ElevatedButton.styleFrom(
                                          backgroundColor: Colors.blueAccent,
                                          foregroundColor: Colors.white,
                                          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(100)),
                                          padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 12),
                                        ),
                                      ),
                                      const SizedBox(width: 16),
                                    ],
                                    ElevatedButton(
                                      onPressed: () {
                                        setState(() {
                                          _step = 'form';
                                          _slides = [SlideForm()];
                                          _quizzes = [];
                                          _lectureTitle = '';
                                          _videoUrl = null;
                                          _lectureId = null;
                                        });
                                      },
                                      style: ElevatedButton.styleFrom(
                                        backgroundColor: Colors.white.withOpacity(0.2),
                                        foregroundColor: Colors.white,
                                        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(100)),
                                        padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 12),
                                      ),
                                      child: const Text('Tạo bài khác', style: TextStyle(fontWeight: FontWeight.bold)),
                                    ),
                                  ],
                                ),
                            ],
                          ),
                        ),
                      ),
                    ],
                  ],
                ),
              ),
            ),
          ],
        ),
      ),
      ),
    );
  }

  Widget _buildGlassyIconButton(IconData icon) {
    return ClipRRect(
      borderRadius: BorderRadius.circular(100),
      child: BackdropFilter(
        filter: ImageFilter.blur(sigmaX: 10, sigmaY: 10),
        child: Container(
          width: 48,
          height: 48,
          decoration: BoxDecoration(
            color: const Color(0xFF0F172A).withOpacity(0.7),
            shape: BoxShape.circle,
            border: Border.all(color: Colors.blueAccent.withOpacity(0.4), width: 1.5),
            boxShadow: [
              BoxShadow(
                color: Colors.black.withOpacity(0.4),
                blurRadius: 10,
                offset: const Offset(0, 4),
              ),
            ],
          ),
          child: const Icon(Icons.arrow_back_rounded, color: Colors.white, size: 22),
        ),
      ),
    );
  }

  Widget _buildSmokedGlassContainer({required Widget child}) {
    return ClipRRect(
      borderRadius: BorderRadius.circular(24),
      child: BackdropFilter(
        filter: ImageFilter.blur(sigmaX: 15, sigmaY: 15),
        child: Container(
          padding: const EdgeInsets.all(20),
          decoration: BoxDecoration(
            color: const Color(0xFF0F172A).withOpacity(0.7),
            borderRadius: BorderRadius.circular(24),
            border: Border.all(color: Colors.blueAccent.withOpacity(0.4), width: 1.5),
            boxShadow: [
              BoxShadow(
                color: Colors.black.withOpacity(0.4),
                blurRadius: 20,
                offset: const Offset(0, 8),
              ),
            ],
          ),
          child: child,
        ),
      ),
    );
  }

  Widget _buildTransparentTextField({
    required String hintText,
    required String value,
    required Function(String) onChanged,
    int maxLines = 1,
  }) {
    return TextFormField(
      initialValue: value,
      onChanged: onChanged,
      maxLines: maxLines,
      style: GoogleFonts.plusJakartaSans(color: Colors.white, fontSize: 14),
      decoration: InputDecoration(
        hintText: hintText,
        hintStyle: GoogleFonts.plusJakartaSans(color: Colors.white54, fontSize: 14),
        filled: true,
        fillColor: Colors.black.withOpacity(0.2),
        contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: BorderSide(color: Colors.white.withOpacity(0.1)),
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: BorderSide(color: Colors.white.withOpacity(0.1)),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(12),
          borderSide: BorderSide(color: Colors.white.withOpacity(0.3)),
        ),
      ),
    );
  }
}
