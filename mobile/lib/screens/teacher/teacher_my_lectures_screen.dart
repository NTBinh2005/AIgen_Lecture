import 'dart:ui';
import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'dart:async';
import 'teacher_create_lecture_screen.dart';
import '../../services/lecture_service.dart';
import '../student/lecture_video_screen.dart';
class TeacherMyLecturesScreen extends StatefulWidget {
  const TeacherMyLecturesScreen({super.key});

  @override
  State<TeacherMyLecturesScreen> createState() => _TeacherMyLecturesScreenState();
}

class _TeacherMyLecturesScreenState extends State<TeacherMyLecturesScreen> with TickerProviderStateMixin {
  List<dynamic> _lectures = [];
  bool _isLoading = true;
  String? _error;
  String _searchQuery = '';
  Timer? _debounce;

  @override
  void initState() {
    super.initState();
    _fetchLectures();
  }

  @override
  void dispose() {
    _debounce?.cancel();
    super.dispose();
  }

  Future<void> _fetchLectures() async {
    setState(() {
      _isLoading = true;
      _error = null;
    });
    try {
      final res = await LectureService.getMyLectures(title: _searchQuery);
      setState(() {
        _lectures = res['content'] ?? [];
        _isLoading = false;
      });
    } catch (e) {
      setState(() {
        _error = e.toString();
        _isLoading = false;
      });
    }
  }

  void _onSearchChanged(String query) {
    if (_debounce?.isActive ?? false) _debounce!.cancel();
    _debounce = Timer(const Duration(milliseconds: 500), () {
      _searchQuery = query;
      _fetchLectures();
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.transparent, // Always transparent for the global background
      body: SafeArea(
        bottom: false,
        child: CustomScrollView(
          slivers: [
            SliverPadding(
              padding: const EdgeInsets.fromLTRB(24, 24, 24, 24),
              sliver: SliverToBoxAdapter(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    // Header
                    _FadeSlideUp(
                      delay: 0,
                      child: Text(
                        'Bài giảng\ncủa tôi',
                        style: GoogleFonts.plusJakartaSans(
                          fontSize: 42,
                          fontWeight: FontWeight.w800,
                          letterSpacing: -1.5,
                          color: Colors.white,
                          height: 1.1,
                        ),
                      ),
                    ),
                    const SizedBox(height: 32),
                    
                    // Search Bar
                    _FadeSlideUp(
                      delay: 100,
                      child: _buildSearchBar(),
                    ),
                    const SizedBox(height: 24),
                    
                    // Add Lecture Button
                    _FadeSlideUp(
                      delay: 150,
                      child: _MagneticButton(
                        onTap: () {
                          Navigator.push(
                            context,
                            PageRouteBuilder(
                              transitionDuration: const Duration(milliseconds: 200),
                              reverseTransitionDuration: const Duration(milliseconds: 200),
                              pageBuilder: (context, animation, secondaryAnimation) => const TeacherCreateLectureScreen(),
                              transitionsBuilder: (context, animation, secondaryAnimation, child) {
                                return FadeTransition(
                                  opacity: animation,
                                  child: SlideTransition(
                                    position: Tween<Offset>(begin: const Offset(0.0, 0.05), end: Offset.zero)
                                        .animate(CurvedAnimation(parent: animation, curve: Curves.easeOut)),
                                    child: child,
                                  ),
                                );
                              },
                            ),
                          );
                        },
                        text: 'Thêm bài giảng mới',
                      ),
                    ),
                  ],
                ),
              ),
            ),
            SliverPadding(
              padding: const EdgeInsets.fromLTRB(24, 0, 24, 120),
              sliver: _isLoading 
                ? const SliverToBoxAdapter(child: Center(child: CircularProgressIndicator(color: Colors.white)))
                : _error != null 
                  ? SliverToBoxAdapter(child: Center(child: Text(_error!, style: GoogleFonts.plusJakartaSans(color: Colors.redAccent))))
                  : _lectures.isEmpty
                    ? SliverToBoxAdapter(child: Center(child: Text('Chưa có bài giảng nào', style: GoogleFonts.plusJakartaSans(color: Colors.white70))))
                    : SliverList(
                        delegate: SliverChildBuilderDelegate(
                          (context, index) {
                            return _FadeSlideUp(
                              delay: 200 + (index * 100),
                              child: Padding(
                                padding: const EdgeInsets.only(bottom: 16),
                                child: _buildLectureCard(_lectures[index], index),
                              ),
                            );
                          },
                          childCount: _lectures.length,
                        ),
                      ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildSearchBar() {
    return ClipRRect(
      borderRadius: BorderRadius.circular(100),
      child: BackdropFilter(
        filter: ImageFilter.blur(sigmaX: 15, sigmaY: 15),
        child: Container(
          padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 4),
          decoration: BoxDecoration(
            color: Colors.black.withOpacity(0.15),
            borderRadius: BorderRadius.circular(100),
            border: Border.all(
              color: Colors.white.withOpacity(0.15),
              width: 1,
            ),
          ),
          child: TextField(
            onChanged: _onSearchChanged,
            style: GoogleFonts.plusJakartaSans(color: Colors.white),
            decoration: InputDecoration(
              hintText: 'Tìm kiếm bài giảng...',
              hintStyle: GoogleFonts.plusJakartaSans(color: Colors.white54),
              border: InputBorder.none,
              icon: const Icon(Icons.search_rounded, color: Colors.white54),
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildLectureCard(dynamic lecture, int index) {
    String title = lecture['title'] ?? 'Không có tiêu đề';
    String status = lecture['videoStatus'] ?? 'PENDING';
    int? lectureId = lecture['lectureId'];

    return GestureDetector(
      onTap: () {
        if (lectureId != null) {
          Navigator.push(
            context,
            MaterialPageRoute(
              builder: (context) => LectureVideoScreen(
                lectureId: lectureId,
                lectureTitle: title,
              ),
            ),
          ).then((_) {
            // Tải lại sau khi quay lại để lỡ video đã DONE
            _fetchLectures();
          });
        }
      },
      child: ClipRRect(
        borderRadius: BorderRadius.circular(24),
        child: BackdropFilter(
          filter: ImageFilter.blur(sigmaX: 15, sigmaY: 15),
          child: Container(
            padding: const EdgeInsets.all(16),
            decoration: BoxDecoration(
              color: Colors.black.withOpacity(0.15),
              borderRadius: BorderRadius.circular(24),
              border: Border.all(
                color: Colors.white.withOpacity(0.15),
                width: 1,
              ),
            ),
            child: Row(
              children: [
                Container(
                  width: 72,
                  height: 72,
                  decoration: BoxDecoration(
                    color: Colors.white.withOpacity(0.1),
                    borderRadius: BorderRadius.circular(20),
                    border: Border.all(color: Colors.white.withOpacity(0.2)),
                  ),
                  child: Center(
                    child: Icon(
                      status == 'DONE' ? Icons.play_arrow_rounded : Icons.pending_actions_rounded,
                      color: status == 'DONE' ? Colors.greenAccent : Colors.orangeAccent,
                      size: 36,
                    ),
                  ),
                ),
                const SizedBox(width: 16),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        title,
                        maxLines: 2,
                        overflow: TextOverflow.ellipsis,
                        style: GoogleFonts.plusJakartaSans(
                          fontSize: 16,
                          fontWeight: FontWeight.w700,
                          color: Colors.white,
                        ),
                      ),
                      const SizedBox(height: 6),
                      Text(
                        'Trạng thái video: $status',
                        style: GoogleFonts.plusJakartaSans(
                          fontSize: 13,
                          color: Colors.white54,
                        ),
                      ),
                    ],
                  ),
                ),
                const Icon(Icons.chevron_right_rounded, color: Colors.white54),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _MagneticButton extends StatefulWidget {
  final VoidCallback onTap;
  final String text;

  const _MagneticButton({
    required this.onTap,
    required this.text,
  });

  @override
  State<_MagneticButton> createState() => _MagneticButtonState();
}

class _MagneticButtonState extends State<_MagneticButton> with SingleTickerProviderStateMixin {
  late AnimationController _controller;
  late Animation<double> _scaleAnimation;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 150),
    );
    _scaleAnimation = Tween<double>(begin: 1.0, end: 0.95).animate(
      CurvedAnimation(parent: _controller, curve: Curves.easeOutCubic),
    );
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return GestureDetector(
      onTapDown: (_) => _controller.forward(),
      onTapUp: (_) {
        _controller.reverse();
        widget.onTap();
      },
      onTapCancel: () => _controller.reverse(),
      child: AnimatedBuilder(
        animation: _scaleAnimation,
        builder: (context, child) {
          return Transform.scale(
            scale: _scaleAnimation.value,
            child: ClipRRect(
              borderRadius: BorderRadius.circular(100),
              child: BackdropFilter(
                filter: ImageFilter.blur(sigmaX: 10, sigmaY: 10),
                child: Container(
                  padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 16),
                  decoration: BoxDecoration(
                    color: Colors.white.withOpacity(0.15),
                    borderRadius: BorderRadius.circular(100),
                    border: Border.all(
                      color: Colors.white.withOpacity(0.3),
                      width: 1.5,
                    ),
                  ),
                  child: Row(
                    mainAxisSize: MainAxisSize.min,
                    children: [
                      const Icon(Icons.add_rounded, color: Colors.white, size: 24),
                      const SizedBox(width: 8),
                      Text(
                        widget.text,
                        style: GoogleFonts.plusJakartaSans(
                          color: Colors.white,
                          fontWeight: FontWeight.bold,
                          fontSize: 16,
                        ),
                      ),
                    ],
                  ),
                ),
              ),
            ),
          );
        },
      ),
    );
  }
}

class _FadeSlideUp extends StatefulWidget {
  final Widget child;
  final int delay;

  const _FadeSlideUp({required this.child, required this.delay});

  @override
  State<_FadeSlideUp> createState() => _FadeSlideUpState();
}

class _FadeSlideUpState extends State<_FadeSlideUp> with SingleTickerProviderStateMixin {
  late AnimationController _controller;
  late Animation<double> _fadeAnimation;
  late Animation<Offset> _slideAnimation;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 800),
    );

    _fadeAnimation = Tween<double>(begin: 0.0, end: 1.0).animate(
      CurvedAnimation(parent: _controller, curve: const Cubic(0.32, 0.72, 0, 1)),
    );

    _slideAnimation = Tween<Offset>(begin: const Offset(0, 0.1), end: Offset.zero).animate(
      CurvedAnimation(parent: _controller, curve: const Cubic(0.32, 0.72, 0, 1)),
    );

    Future.delayed(Duration(milliseconds: widget.delay), () {
      if (mounted) _controller.forward();
    });
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return FadeTransition(
      opacity: _fadeAnimation,
      child: SlideTransition(
        position: _slideAnimation,
        child: widget.child,
      ),
    );
  }
}
