package com.example.demo.entity;

/**
 * Trạng thái render video của một Lecture.
 * Mapping với video-service JobStatus.
 */
public enum VideoStatus {
    /** Chưa gửi yêu cầu render (lecture mới tạo) */
    PENDING,
    /** Đã gửi sang video-service, đang render */
    PROCESSING,
    /** Render hoàn thành, videoUrl có giá trị */
    DONE,
    /** Render thất bại, kiểm tra log để biết nguyên nhân */
    FAILED,
    /**
     * Tính năng render video hiện chưa được bật (chưa nối lại video-service).
     * Chỉ dùng trong response để FE ngừng poll; KHÔNG lưu vào DB.
     */
    NOT_AVAILABLE
}
