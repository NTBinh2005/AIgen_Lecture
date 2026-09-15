import 'package:flutter/material.dart';
import 'package:google_fonts/google_fonts.dart';
import 'screens/student/student_main_screen.dart';
import 'screens/shared/auth_screen.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    // Primary brand color: Slate Blue / Indigo
    const primaryColor = Color(0xFF2563EB);

    return MaterialApp(
      title: 'AIGen Lecture',
      debugShowCheckedModeBanner: false,
      themeMode: ThemeMode.system, // Automatically switches based on OS setting
      
      // Premium Light Theme Design
      theme: ThemeData(
        useMaterial3: true,
        brightness: Brightness.light,
        colorScheme: ColorScheme.fromSeed(
          seedColor: primaryColor,
          brightness: Brightness.light,
          primary: primaryColor,
          background: const Color(0xFFF8FAFC), // Slate 50
          surface: Colors.white,
          onBackground: const Color(0xFF0F172A), // Slate 900
          onSurface: const Color(0xFF0F172A),
        ),
        textTheme: GoogleFonts.beVietnamProTextTheme(
          ThemeData.light().textTheme,
        ),
        appBarTheme: const AppBarTheme(
          backgroundColor: Colors.white,
          foregroundColor: Color(0xFF0F172A),
          elevation: 0,
          scrolledUnderElevation: 1,
          centerTitle: false,
        ),
        navigationBarTheme: NavigationBarThemeData(
          backgroundColor: Colors.white,
          indicatorColor: primaryColor.withOpacity(0.12),
          iconTheme: MaterialStateProperty.resolveWith((states) {
            if (states.contains(MaterialState.selected)) {
              return const IconThemeData(color: primaryColor, size: 26);
            }
            return const IconThemeData(color: Color(0xFF64748B), size: 24); // Slate 500
          }),
          labelTextStyle: MaterialStateProperty.resolveWith((states) {
            if (states.contains(MaterialState.selected)) {
              return const TextStyle(
                fontSize: 12,
                fontWeight: FontWeight.w600,
                color: primaryColor,
              );
            }
            return const TextStyle(
              fontSize: 12,
              fontWeight: FontWeight.w500,
              color: Color(0xFF64748B),
            );
          }),
        ),
      ),

      // Premium Dark Theme Design
      darkTheme: ThemeData(
        useMaterial3: true,
        brightness: Brightness.dark,
        colorScheme: ColorScheme.fromSeed(
          seedColor: primaryColor,
          brightness: Brightness.dark,
          primary: const Color(0xFF3B82F6), // Lighter blue for dark mode contrast
          background: const Color(0xFF0F172A), // Slate 900
          surface: const Color(0xFF1E293B), // Slate 800 (elevated background)
          onBackground: const Color(0xFFF8FAFC),
          onSurface: const Color(0xFFF8FAFC),
        ),
        textTheme: GoogleFonts.beVietnamProTextTheme(
          ThemeData.dark().textTheme,
        ),
        appBarTheme: const AppBarTheme(
          backgroundColor: Color(0xFF1E293B),
          foregroundColor: Color(0xFFF8FAFC),
          elevation: 0,
          scrolledUnderElevation: 1,
          centerTitle: false,
        ),
        navigationBarTheme: NavigationBarThemeData(
          backgroundColor: const Color(0xFF0F172A),
          indicatorColor: const Color(0xFF3B82F6).withOpacity(0.15),
          iconTheme: MaterialStateProperty.resolveWith((states) {
            if (states.contains(MaterialState.selected)) {
              return const IconThemeData(color: Color(0xFF3B82F6), size: 26);
            }
            return const IconThemeData(color: Color(0xFF94A3B8), size: 24); // Slate 400
          }),
          labelTextStyle: MaterialStateProperty.resolveWith((states) {
            if (states.contains(MaterialState.selected)) {
              return const TextStyle(
                fontSize: 12,
                fontWeight: FontWeight.w600,
                color: Color(0xFF3B82F6),
              );
            }
            return const TextStyle(
              fontSize: 12,
              fontWeight: FontWeight.w500,
              color: Color(0xFF94A3B8),
            );
          }),
        ),
      ),
      home: const AuthScreen(),
    );
  }
}
