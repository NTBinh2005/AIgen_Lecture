import 'dart:convert';
import 'package:http/http.dart' as http;

class AuthService {
  // Use 10.0.2.2 for Android Emulator to connect to localhost:8080
  // Change to your machine's IP (e.g., 192.168.1.X) if running on physical device
  static const String baseUrl = 'http://10.0.2.2:8080/api/auth';

  static Future<Map<String, dynamic>> login(String email, String password) async {
    try {
      final response = await http.post(
        Uri.parse('$baseUrl/login'),
        headers: {'Content-Type': 'application/json'},
        body: jsonEncode({
          'email': email,
          'password': password,
        }),
      );

      final data = jsonDecode(response.body);

      if (response.statusCode == 200) {
        return data; // Returns AuthResponse (token, userId, etc.)
      } else {
        // Backend usually returns a message in case of error
        throw Exception(data['message'] ?? 'Login failed. Please check your credentials.');
      }
    } catch (e) {
      throw Exception(e.toString().replaceAll('Exception: ', ''));
    }
  }

  static Future<Map<String, dynamic>> register(String name, String email, String password) async {
    try {
      final response = await http.post(
        Uri.parse('$baseUrl/register'),
        headers: {'Content-Type': 'application/json'},
        body: jsonEncode({
          'name': name,
          'email': email,
          'password': password,
          'role': 'STUDENT', // Defaulting to STUDENT as per typical app flow
        }),
      );

      final data = jsonDecode(response.body);

      if (response.statusCode == 200) {
        return data; // Returns AuthResponse
      } else {
        throw Exception(data['message'] ?? 'Registration failed. Email might be in use.');
      }
    } catch (e) {
      throw Exception(e.toString().replaceAll('Exception: ', ''));
    }
  }
}
