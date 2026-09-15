import 'dart:math';
import 'dart:ui';
import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'package:shared_preferences/shared_preferences.dart';
import '../../services/auth_service.dart';
import '../student/student_main_screen.dart';
import '../teacher/teacher_main_screen.dart';

class AuthScreen extends StatefulWidget {
  const AuthScreen({super.key});

  @override
  State<AuthScreen> createState() => _AuthScreenState();
}

class _AuthScreenState extends State<AuthScreen> with TickerProviderStateMixin {
  late AnimationController _bgController;
  late AnimationController _formController;
  
  bool _isLogin = true;
  bool _isLoading = false;

  final TextEditingController _emailController = TextEditingController();
  final TextEditingController _passwordController = TextEditingController();
  final TextEditingController _nameController = TextEditingController();

  @override
  void initState() {
    super.initState();
    _bgController = AnimationController(
      vsync: this,
      duration: const Duration(seconds: 10),
    )..repeat();

    _formController = AnimationController(
      vsync: this,
      duration: const Duration(milliseconds: 600),
    );
  }

  @override
  void dispose() {
    _bgController.dispose();
    _formController.dispose();
    _emailController.dispose();
    _passwordController.dispose();
    _nameController.dispose();
    super.dispose();
  }

  void _toggleForm() {
    setState(() {
      _isLogin = !_isLogin;
      // Clear fields when toggling
      _emailController.clear();
      _passwordController.clear();
      _nameController.clear();
    });
    if (_isLogin) {
      _formController.reverse();
    } else {
      _formController.forward();
    }
  }

  Future<void> _submitAuth() async {
    final email = _emailController.text.trim();
    final password = _passwordController.text;
    final name = _nameController.text.trim();

    if (email.isEmpty || password.isEmpty || (!_isLogin && name.isEmpty)) {
      _showError('Vui lòng điền đầy đủ thông tin');
      return;
    }

    setState(() {
      _isLoading = true;
    });

    try {
      if (_isLogin) {
        final response = await AuthService.login(email, password);
        _handleSuccess(response);
      } else {
        final response = await AuthService.register(name, email, password);
        _handleSuccess(response);
      }
    } catch (e) {
      _showError(e.toString());
    } finally {
      if (mounted) {
        setState(() {
          _isLoading = false;
        });
      }
    }
  }

  Future<void> _handleSuccess(Map<String, dynamic> response) async {
    if (response.containsKey('token')) {
      final prefs = await SharedPreferences.getInstance();
      await prefs.setString('jwt_token', response['token']);
    }

    if (!mounted) return;

    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text('Welcome ${response['name']}!'),
        backgroundColor: Colors.green,
        behavior: SnackBarBehavior.floating,
      ),
    );

    final role = response['role'];
    if (role == 'INSTRUCTOR' || role == 'TEACHER') {
      _navigateToTeacherMain();
    } else {
      _navigateToStudentMain();
    }
  }

  void _showError(String message) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(message),
        backgroundColor: Colors.redAccent,
        behavior: SnackBarBehavior.floating,
      ),
    );
  }

  void _navigateToStudentMain() {
    Navigator.of(context).pushReplacement(
      PageRouteBuilder(
        pageBuilder: (context, animation, secondaryAnimation) => const StudentMainScreen(),
        transitionsBuilder: (context, animation, secondaryAnimation, child) {
          return _buildTransition(animation, child);
        },
        transitionDuration: const Duration(milliseconds: 800),
      ),
    );
  }

  void _navigateToTeacherMain() {
    Navigator.of(context).pushReplacement(
      PageRouteBuilder(
        pageBuilder: (context, animation, secondaryAnimation) => const TeacherMainScreen(),
        transitionsBuilder: (context, animation, secondaryAnimation, child) {
          return _buildTransition(animation, child);
        },
        transitionDuration: const Duration(milliseconds: 800),
      ),
    );
  }

  Widget _buildTransition(Animation<double> animation, Widget child) {
    const begin = Offset(0.0, 1.0);
    const end = Offset.zero;
    const curve = Curves.easeInOutQuart;
    var tween = Tween(begin: begin, end: end).chain(CurveTween(curve: curve));
    var offsetAnimation = animation.drive(tween);
    return SlideTransition(position: offsetAnimation, child: child);
  }

  @override
  Widget build(BuildContext context) {
    final size = MediaQuery.of(context).size;
    final primaryColor = Theme.of(context).colorScheme.primary;

    return Scaffold(
      body: Stack(
        children: [
          // Dynamic Background
          AnimatedBuilder(
            animation: _bgController,
            builder: (context, child) {
              return Stack(
                children: [
                  Positioned(
                    top: size.height * 0.1 + sin(_bgController.value * 2 * pi) * 50,
                    left: size.width * 0.1 + cos(_bgController.value * 2 * pi) * 50,
                    child: _buildBlob(primaryColor.withOpacity(0.4), 250),
                  ),
                  Positioned(
                    bottom: size.height * 0.1 + cos(_bgController.value * 2 * pi) * 50,
                    right: size.width * 0.1 + sin(_bgController.value * 2 * pi) * 50,
                    child: _buildBlob(const Color(0xFF9333EA).withOpacity(0.4), 300), // Purple-ish
                  ),
                  Positioned(
                    top: size.height * 0.4 + sin(_bgController.value * pi) * 80,
                    left: size.width * 0.3 - cos(_bgController.value * pi) * 80,
                    child: _buildBlob(const Color(0xFF06B6D4).withOpacity(0.3), 200), // Cyan-ish
                  ),
                ],
              );
            },
          ),
          
          // Glassmorphism Overlay
          Positioned.fill(
            child: BackdropFilter(
              filter: ImageFilter.blur(sigmaX: 30, sigmaY: 30),
              child: Container(
                color: Theme.of(context).colorScheme.background.withOpacity(0.4),
              ),
            ),
          ),

          // Content
          SafeArea(
            child: SingleChildScrollView(
              physics: const BouncingScrollPhysics(),
              child: ConstrainedBox(
                constraints: BoxConstraints(
                  minHeight: size.height - MediaQuery.of(context).padding.top - MediaQuery.of(context).padding.bottom,
                ),
                child: IntrinsicHeight(
                  child: Padding(
                    padding: const EdgeInsets.symmetric(horizontal: 24.0, vertical: 40.0),
                    child: Column(
                      mainAxisAlignment: MainAxisAlignment.center,
                      crossAxisAlignment: CrossAxisAlignment.stretch,
                      children: [
                        // Logo / Header Area
                        const Icon(
                          Icons.auto_awesome,
                          size: 64,
                          color: Color(0xFF2563EB),
                        ),
                        const SizedBox(height: 24),
                        Text(
                          'AIGen Lecture',
                          textAlign: TextAlign.center,
                          style: GoogleFonts.outfit(
                            fontSize: 36,
                            fontWeight: FontWeight.bold,
                            letterSpacing: -0.5,
                            color: Theme.of(context).colorScheme.onBackground,
                          ),
                        ),
                        const SizedBox(height: 8),
                        Text(
                          'Master your skills with AI',
                          textAlign: TextAlign.center,
                          style: GoogleFonts.beVietnamPro(
                            fontSize: 16,
                            color: Theme.of(context).colorScheme.onBackground.withOpacity(0.7),
                          ),
                        ),
                        const SizedBox(height: 48),

                        // Form Card (Glassmorphism)
                        Container(
                          decoration: BoxDecoration(
                            color: Theme.of(context).colorScheme.surface.withOpacity(0.6),
                            borderRadius: BorderRadius.circular(32),
                            border: Border.all(
                              color: Colors.white.withOpacity(0.2),
                              width: 1.5,
                            ),
                            boxShadow: [
                              BoxShadow(
                                color: Colors.black.withOpacity(0.05),
                                blurRadius: 20,
                                spreadRadius: -5,
                              )
                            ],
                          ),
                          child: ClipRRect(
                            borderRadius: BorderRadius.circular(32),
                            child: BackdropFilter(
                              filter: ImageFilter.blur(sigmaX: 20, sigmaY: 20),
                              child: Padding(
                                padding: const EdgeInsets.all(32.0),
                                child: AnimatedSwitcher(
                                  duration: const Duration(milliseconds: 500),
                                  switchInCurve: Curves.easeOutBack,
                                  switchOutCurve: Curves.easeInBack,
                                  transitionBuilder: (Widget child, Animation<double> animation) {
                                    return FadeTransition(
                                      opacity: animation,
                                      child: SlideTransition(
                                        position: Tween<Offset>(
                                          begin: const Offset(0.0, 0.1),
                                          end: Offset.zero,
                                        ).animate(animation),
                                        child: child,
                                      ),
                                    );
                                  },
                                  child: _isLogin
                                      ? _buildLoginForm(key: const ValueKey('login'))
                                      : _buildRegisterForm(key: const ValueKey('register')),
                                ),
                              ),
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildBlob(Color color, double size) {
    return Container(
      width: size,
      height: size,
      decoration: BoxDecoration(
        shape: BoxShape.circle,
        color: color,
        boxShadow: [
          BoxShadow(
            color: color.withOpacity(0.5),
            blurRadius: size / 2,
            spreadRadius: size / 4,
          )
        ],
      ),
    );
  }

  Widget _buildLoginForm({Key? key}) {
    return Column(
      key: key,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text(
          'Welcome Back',
          style: GoogleFonts.outfit(
            fontSize: 28,
            fontWeight: FontWeight.bold,
          ),
        ),
        const SizedBox(height: 8),
        Text(
          'Sign in to continue your learning journey',
          style: GoogleFonts.beVietnamPro(
            fontSize: 14,
            color: Theme.of(context).colorScheme.onBackground.withOpacity(0.6),
          ),
        ),
        const SizedBox(height: 32),
        _buildTextField(controller: _emailController, label: 'Email', icon: Icons.email_outlined),
        const SizedBox(height: 16),
        _buildTextField(controller: _passwordController, label: 'Password', icon: Icons.lock_outline, isPassword: true),
        const SizedBox(height: 12),
        Align(
          alignment: Alignment.centerRight,
          child: TextButton(
            onPressed: () {},
            style: TextButton.styleFrom(
              foregroundColor: Theme.of(context).colorScheme.primary,
              textStyle: const TextStyle(fontWeight: FontWeight.w600),
            ),
            child: const Text('Forgot Password?'),
          ),
        ),
        const SizedBox(height: 24),
        _buildSubmitButton('Sign In'),
        const SizedBox(height: 24),
        _buildToggleText('Don\'t have an account? ', 'Sign Up'),
      ],
    );
  }

  Widget _buildRegisterForm({Key? key}) {
    return Column(
      key: key,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Text(
          'Create Account',
          style: GoogleFonts.outfit(
            fontSize: 28,
            fontWeight: FontWeight.bold,
          ),
        ),
        const SizedBox(height: 8),
        Text(
          'Join us to unlock premium AI courses',
          style: GoogleFonts.beVietnamPro(
            fontSize: 14,
            color: Theme.of(context).colorScheme.onBackground.withOpacity(0.6),
          ),
        ),
        const SizedBox(height: 32),
        _buildTextField(controller: _nameController, label: 'Full Name', icon: Icons.person_outline),
        const SizedBox(height: 16),
        _buildTextField(controller: _emailController, label: 'Email', icon: Icons.email_outlined),
        const SizedBox(height: 16),
        _buildTextField(controller: _passwordController, label: 'Password', icon: Icons.lock_outline, isPassword: true),
        const SizedBox(height: 32),
        _buildSubmitButton('Sign Up'),
        const SizedBox(height: 24),
        _buildToggleText('Already have an account? ', 'Sign In'),
      ],
    );
  }

  Widget _buildTextField({required TextEditingController controller, required String label, required IconData icon, bool isPassword = false}) {
    return TextFormField(
      controller: controller,
      obscureText: isPassword,
      style: const TextStyle(fontWeight: FontWeight.w500),
      decoration: InputDecoration(
        labelText: label,
        prefixIcon: Icon(icon, size: 22, color: Theme.of(context).colorScheme.onBackground.withOpacity(0.5)),
        filled: true,
        fillColor: Theme.of(context).colorScheme.surface.withOpacity(0.5),
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(16),
          borderSide: BorderSide.none,
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(16),
          borderSide: BorderSide.none,
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(16),
          borderSide: BorderSide(color: Theme.of(context).colorScheme.primary, width: 2),
        ),
        contentPadding: const EdgeInsets.symmetric(vertical: 18, horizontal: 16),
      ),
    );
  }

  Widget _buildSubmitButton(String text) {
    return Container(
      height: 56,
      decoration: BoxDecoration(
        borderRadius: BorderRadius.circular(16),
        gradient: LinearGradient(
          colors: [
            Theme.of(context).colorScheme.primary,
            const Color(0xFF60A5FA), // Lighter blue
          ],
          begin: Alignment.topLeft,
          end: Alignment.bottomRight,
        ),
        boxShadow: [
          BoxShadow(
            color: Theme.of(context).colorScheme.primary.withOpacity(0.3),
            blurRadius: 12,
            offset: const Offset(0, 4),
          ),
        ],
      ),
      child: Material(
        color: Colors.transparent,
        child: InkWell(
          onTap: _isLoading ? null : _submitAuth,
          borderRadius: BorderRadius.circular(16),
          child: Center(
            child: _isLoading 
                ? const SizedBox(
                    width: 24,
                    height: 24,
                    child: CircularProgressIndicator(color: Colors.white, strokeWidth: 3),
                  )
                : Text(
                    text,
                    style: const TextStyle(
                      color: Colors.white,
                      fontSize: 16,
                      fontWeight: FontWeight.bold,
                      letterSpacing: 0.5,
                    ),
                  ),
          ),
        ),
      ),
    );
  }

  Widget _buildToggleText(String normalText, String linkText) {
    return Row(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        Text(
          normalText,
          style: TextStyle(
            color: Theme.of(context).colorScheme.onBackground.withOpacity(0.7),
            fontWeight: FontWeight.w500,
          ),
        ),
        GestureDetector(
          onTap: _isLoading ? null : _toggleForm,
          child: Text(
            linkText,
            style: TextStyle(
              color: Theme.of(context).colorScheme.primary,
              fontWeight: FontWeight.bold,
            ),
          ),
        ),
      ],
    );
  }
}
