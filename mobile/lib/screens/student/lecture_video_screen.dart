import 'dart:async';
import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;
import 'package:video_player/video_player.dart';

// ─── Config ───────────────────────────────────────────────────────────────────

/// Base URL của backend Spring Boot.
/// Đổi thành IP/domain thật khi deploy.
const String kBackendBaseUrl = 'http://10.0.2.2:8080/api';

// ─── Model ────────────────────────────────────────────────────────────────────

enum VideoStatus { pending, processing, done, failed }

VideoStatus _parseStatus(String s) {
  switch (s.toUpperCase()) {
    case 'DONE':
      return VideoStatus.done;
    case 'PROCESSING':
      return VideoStatus.processing;
    case 'FAILED':
      return VideoStatus.failed;
    default:
      return VideoStatus.pending;
  }
}

// ─── Screen ───────────────────────────────────────────────────────────────────

/// Màn hình xem video bài giảng.
///
/// Nhận [lectureId] và [lectureTitle] từ màn hình danh sách.
/// - Nếu video DONE → hiển thị VideoPlayer
/// - Nếu PROCESSING/PENDING → poll mỗi 3 giây, hiển thị loading
/// - Nếu FAILED → hiển thị thông báo lỗi
class LectureVideoScreen extends StatefulWidget {
  final int lectureId;
  final String lectureTitle;

  const LectureVideoScreen({
    super.key,
    required this.lectureId,
    required this.lectureTitle,
  });

  @override
  State<LectureVideoScreen> createState() => _LectureVideoScreenState();
}

class _LectureVideoScreenState extends State<LectureVideoScreen> {
  VideoStatus _status = VideoStatus.pending;
  String? _videoUrl;
  VideoPlayerController? _controller;
  bool _isControllerInitialized = false;
  Timer? _pollTimer;

  @override
  void initState() {
    super.initState();
    _fetchStatus();
  }

  @override
  void dispose() {
    _pollTimer?.cancel();
    _controller?.dispose();
    super.dispose();
  }

  // ── API ─────────────────────────────────────────────────────────────────────

  Future<void> _fetchStatus() async {
    try {
      final uri = Uri.parse('$kBackendBaseUrl/lectures/${widget.lectureId}/video-status');
      final response = await http.get(uri);
      if (!mounted) return;

      if (response.statusCode == 200) {
        final json = jsonDecode(response.body) as Map<String, dynamic>;
        final status = _parseStatus(json['videoStatus'] as String? ?? '');
        final url = json['videoUrl'] as String?;

        setState(() {
          _status = status;
          _videoUrl = url;
        });

        if (status == VideoStatus.done && url != null) {
          _stopPolling();
          await _initPlayer(url);
        } else if (status == VideoStatus.failed) {
          _stopPolling();
        } else {
          // Vẫn đang pending/processing → tiếp tục poll
          _startPolling();
        }
      }
    } catch (_) {
      // Lỗi network tạm thời → bỏ qua, tiếp tục poll
    }
  }

  Future<void> _initPlayer(String url) async {
    final controller = VideoPlayerController.networkUrl(Uri.parse(url));
    await controller.initialize();
    if (!mounted) return;
    setState(() {
      _controller = controller;
      _isControllerInitialized = true;
    });
    controller.play();
  }

  void _startPolling() {
    _pollTimer?.cancel();
    _pollTimer = Timer.periodic(const Duration(seconds: 3), (_) => _fetchStatus());
  }

  void _stopPolling() {
    _pollTimer?.cancel();
    _pollTimer = null;
  }

  // ── UI ──────────────────────────────────────────────────────────────────────

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final colorScheme = theme.colorScheme;

    return Scaffold(
      backgroundColor: colorScheme.background,
      appBar: AppBar(
        title: Text(
          widget.lectureTitle,
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
        ),
      ),
      body: _buildBody(colorScheme),
    );
  }

  Widget _buildBody(ColorScheme colorScheme) {
    switch (_status) {
      case VideoStatus.done:
        return _isControllerInitialized && _controller != null
            ? _buildVideoPlayer(colorScheme)
            : const Center(child: CircularProgressIndicator());

      case VideoStatus.processing:
      case VideoStatus.pending:
        return _buildLoadingState(colorScheme);

      case VideoStatus.failed:
        return _buildErrorState(colorScheme);
    }
  }

  Widget _buildVideoPlayer(ColorScheme colorScheme) {
    return Column(
      children: [
        // Video player area
        Container(
          color: Colors.black,
          child: AspectRatio(
            aspectRatio: _controller!.value.aspectRatio,
            child: VideoPlayer(_controller!),
          ),
        ),

        // Controls
        _VideoControls(controller: _controller!),

        // Info section
        Expanded(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(20),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  widget.lectureTitle,
                  style: Theme.of(context).textTheme.titleLarge?.copyWith(
                    fontWeight: FontWeight.bold,
                  ),
                ),
                const SizedBox(height: 8),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
                  decoration: BoxDecoration(
                    color: Colors.green.withOpacity(0.1),
                    borderRadius: BorderRadius.circular(20),
                    border: Border.all(color: Colors.green.withOpacity(0.3)),
                  ),
                  child: const Text(
                    'Video đã sẵn sàng',
                    style: TextStyle(
                      fontSize: 12,
                      color: Colors.green,
                      fontWeight: FontWeight.w600,
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
      ],
    );
  }

  Widget _buildLoadingState(ColorScheme colorScheme) {
    final isProcessing = _status == VideoStatus.processing;
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(40),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Container(
              width: 80,
              height: 80,
              decoration: BoxDecoration(
                color: colorScheme.primary.withOpacity(0.1),
                borderRadius: BorderRadius.circular(20),
              ),
              child: Center(
                child: CircularProgressIndicator(
                  color: colorScheme.primary,
                  strokeWidth: 3,
                ),
              ),
            ),
            const SizedBox(height: 24),
            Text(
              isProcessing ? 'Đang tạo video bài giảng...' : 'Đang chờ xử lý...',
              style: Theme.of(context).textTheme.titleMedium?.copyWith(
                fontWeight: FontWeight.bold,
              ),
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 12),
            Text(
              'Remotion đang render video từ nội dung bài giảng.\nQuá trình này có thể mất từ 1–5 phút.',
              style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                color: colorScheme.onBackground.withOpacity(0.6),
              ),
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 32),
            // Dot indicator animation
            Row(
              mainAxisAlignment: MainAxisAlignment.center,
              children: List.generate(
                3,
                (i) => _AnimatedDot(delay: Duration(milliseconds: i * 200)),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildErrorState(ColorScheme colorScheme) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(40),
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(Icons.error_outline, size: 64, color: colorScheme.error),
            const SizedBox(height: 16),
            Text(
              'Không thể tạo video',
              style: Theme.of(context).textTheme.titleMedium?.copyWith(
                fontWeight: FontWeight.bold,
                color: colorScheme.error,
              ),
            ),
            const SizedBox(height: 8),
            Text(
              'Đã xảy ra lỗi khi render video bài giảng này. Vui lòng liên hệ giáo viên.',
              style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                color: colorScheme.onBackground.withOpacity(0.6),
              ),
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 24),
            OutlinedButton.icon(
              onPressed: () => Navigator.of(context).pop(),
              icon: const Icon(Icons.arrow_back),
              label: const Text('Quay lại'),
            ),
          ],
        ),
      ),
    );
  }
}

// ─── Video Controls Widget ────────────────────────────────────────────────────

class _VideoControls extends StatefulWidget {
  final VideoPlayerController controller;
  const _VideoControls({required this.controller});

  @override
  State<_VideoControls> createState() => _VideoControlsState();
}

class _VideoControlsState extends State<_VideoControls> {
  @override
  void initState() {
    super.initState();
    widget.controller.addListener(() {
      if (mounted) setState(() {});
    });
  }

  @override
  Widget build(BuildContext context) {
    final colorScheme = Theme.of(context).colorScheme;
    final isPlaying = widget.controller.value.isPlaying;
    final duration = widget.controller.value.duration;
    final position = widget.controller.value.position;

    String _format(Duration d) {
      final m = d.inMinutes.remainder(60).toString().padLeft(2, '0');
      final s = d.inSeconds.remainder(60).toString().padLeft(2, '0');
      return '$m:$s';
    }

    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      color: colorScheme.surface,
      child: Column(
        children: [
          VideoProgressIndicator(
            widget.controller,
            allowScrubbing: true,
            colors: VideoProgressColors(
              playedColor: colorScheme.primary,
              bufferedColor: colorScheme.primary.withOpacity(0.3),
              backgroundColor: colorScheme.outline.withOpacity(0.2),
            ),
          ),
          const SizedBox(height: 8),
          Row(
            children: [
              Text(
                '${_format(position)} / ${_format(duration)}',
                style: TextStyle(
                  fontSize: 12,
                  color: colorScheme.onSurface.withOpacity(0.6),
                ),
              ),
              const Spacer(),
              IconButton(
                onPressed: () {
                  if (isPlaying) {
                    widget.controller.pause();
                  } else {
                    widget.controller.play();
                  }
                },
                icon: Icon(
                  isPlaying ? Icons.pause_circle_filled : Icons.play_circle_filled,
                  size: 40,
                  color: colorScheme.primary,
                ),
                padding: EdgeInsets.zero,
              ),
              const Spacer(),
              IconButton(
                onPressed: () {
                  widget.controller.seekTo(Duration.zero);
                  widget.controller.play();
                },
                icon: Icon(
                  Icons.replay,
                  color: colorScheme.onSurface.withOpacity(0.6),
                ),
              ),
            ],
          ),
        ],
      ),
    );
  }
}

// ─── Animated Dot ─────────────────────────────────────────────────────────────

class _AnimatedDot extends StatefulWidget {
  final Duration delay;
  const _AnimatedDot({required this.delay});

  @override
  State<_AnimatedDot> createState() => _AnimatedDotState();
}

class _AnimatedDotState extends State<_AnimatedDot>
    with SingleTickerProviderStateMixin {
  late AnimationController _controller;
  late Animation<double> _animation;

  @override
  void initState() {
    super.initState();
    _controller = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 600),
    );
    _animation = Tween<double>(begin: 0.3, end: 1.0).animate(
      CurvedAnimation(parent: _controller, curve: Curves.easeInOut),
    );
    Future.delayed(widget.delay, () {
      if (mounted) _controller.repeat(reverse: true);
    });
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 4),
      child: FadeTransition(
        opacity: _animation,
        child: Container(
          width: 8,
          height: 8,
          decoration: BoxDecoration(
            shape: BoxShape.circle,
            color: Theme.of(context).colorScheme.primary,
          ),
        ),
      ),
    );
  }
}
