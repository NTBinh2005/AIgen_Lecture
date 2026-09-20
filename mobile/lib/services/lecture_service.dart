import 'dart:convert';
import 'package:http/http.dart' as http;
import 'package:file_picker/file_picker.dart';
import 'package:shared_preferences/shared_preferences.dart';

class LectureService {
  static const String baseUrl = 'http://10.0.2.2:8080/api/lectures';

  static Future<String?> _getToken() async {
    final prefs = await SharedPreferences.getInstance();
    return prefs.getString('jwt_token');
  }

  static Future<Map<String, dynamic>> generateFromFile(PlatformFile file) async {
    var request = http.MultipartRequest('POST', Uri.parse('$baseUrl/generate-from-file'));
    
    final token = await _getToken();
    if (token != null) {
      request.headers['Authorization'] = 'Bearer $token';
    }

    if (file.path != null) {
      request.files.add(await http.MultipartFile.fromPath('file', file.path!));
    } else if (file.bytes != null) {
      request.files.add(http.MultipartFile.fromBytes('file', file.bytes!, filename: file.name));
    } else {
      throw Exception('File data not found');
    }

    var streamedResponse = await request.send();
    var response = await http.Response.fromStream(streamedResponse);

    if (response.statusCode >= 200 && response.statusCode < 300) {
      return json.decode(utf8.decode(response.bodyBytes));
    } else {
      throw Exception('Failed to generate from file: ${response.statusCode}');
    }
  }

  static Future<Map<String, dynamic>> createLecture(Map<String, dynamic> data) async {
    final token = await _getToken();
    final headers = {'Content-Type': 'application/json'};
    if (token != null) {
      headers['Authorization'] = 'Bearer $token';
    }

    final response = await http.post(
      Uri.parse(baseUrl),
      headers: headers,
      body: json.encode(data),
    );

    if (response.statusCode >= 200 && response.statusCode < 300) {
      return json.decode(utf8.decode(response.bodyBytes));
    } else {
      throw Exception('Failed to create lecture: ${response.statusCode}');
    }
  }

  static Future<Map<String, dynamic>> getVideoStatus(int lectureId) async {
    final token = await _getToken();
    final headers = <String, String>{};
    if (token != null) {
      headers['Authorization'] = 'Bearer $token';
    }

    final response = await http.get(
      Uri.parse('$baseUrl/$lectureId/video-status'),
      headers: headers,
    );

    if (response.statusCode >= 200 && response.statusCode < 300) {
      return json.decode(utf8.decode(response.bodyBytes));
    } else {
      throw Exception('Failed to get video status: ${response.statusCode}');
    }
  }

  static Future<Map<String, dynamic>> getMyLectures({int page = 0, int size = 10, String? title}) async {
    final token = await _getToken();
    final headers = <String, String>{};
    if (token != null) {
      headers['Authorization'] = 'Bearer $token';
    }

    var url = '$baseUrl?page=$page&size=$size';
    if (title != null && title.isNotEmpty) {
      url += '&title=${Uri.encodeComponent(title)}';
    }

    final response = await http.get(Uri.parse(url), headers: headers);

    if (response.statusCode >= 200 && response.statusCode < 300) {
      return json.decode(utf8.decode(response.bodyBytes));
    } else {
      throw Exception('Failed to get lectures: ${response.statusCode}');
    }
  }
}
