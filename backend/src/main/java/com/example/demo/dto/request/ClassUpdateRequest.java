package com.example.demo.dto.request;

import com.example.demo.entity.ClassStatus;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.List;

/** Patch DTO that distinguishes an omitted description from an explicit null. */
public class ClassUpdateRequest {
    private Integer teacherId;
    @Size(max = 150) private String className;
    @Size(max = 20) private String classCode;
    @Size(max = 30) private String semester;
    private LocalDateTime startsAt;
    private LocalDateTime endsAt;
    private LocalDateTime enrollmentOpensAt;
    private LocalDateTime enrollmentClosesAt;
    private Integer maxStudents;
    @Size(max = 500) private String description;
    private boolean descriptionSpecified;
    private boolean semesterSpecified, endsAtSpecified, maxStudentsSpecified;
    private boolean enrollmentOpensAtSpecified, enrollmentClosesAtSpecified;
    private ClassStatus status;
    private List<Integer> coTeacherIds;

    public Integer teacherId() { return teacherId; }
    public String className() { return className; }
    public String classCode() { return classCode; }
    public String semester() { return semester; }
    public LocalDateTime startsAt() { return startsAt; }
    public LocalDateTime endsAt() { return endsAt; }
    public LocalDateTime enrollmentOpensAt() { return enrollmentOpensAt; }
    public LocalDateTime enrollmentClosesAt() { return enrollmentClosesAt; }
    public Integer maxStudents() { return maxStudents; }
    public String description() { return description; }
    public boolean descriptionSpecified() { return descriptionSpecified; }
    public ClassStatus status() { return status; }
    public List<Integer> coTeacherIds() { return coTeacherIds; }

    public void setTeacherId(Integer value) { teacherId = value; }
    public void setClassName(String value) { className = value; }
    public void setClassCode(String value) { classCode = value; }
    public boolean semesterSpecified() { return semesterSpecified; }
    public boolean endsAtSpecified() { return endsAtSpecified; }
    public boolean maxStudentsSpecified() { return maxStudentsSpecified; }
    public boolean enrollmentOpensAtSpecified() { return enrollmentOpensAtSpecified; }
    public boolean enrollmentClosesAtSpecified() { return enrollmentClosesAtSpecified; }
    public void setSemester(String value) { semester = value; semesterSpecified = true; }
    public void setStartsAt(LocalDateTime value) { startsAt = value; }
    public void setEndsAt(LocalDateTime value) { endsAt = value; endsAtSpecified = true; }
    public void setEnrollmentOpensAt(LocalDateTime value) { enrollmentOpensAt = value; enrollmentOpensAtSpecified = true; }
    public void setEnrollmentClosesAt(LocalDateTime value) { enrollmentClosesAt = value; enrollmentClosesAtSpecified = true; }
    public void setMaxStudents(Integer value) { maxStudents = value; maxStudentsSpecified = true; }
    public void setDescription(String value) { description = value; descriptionSpecified = true; }
    public void setStatus(ClassStatus value) { status = value; }
    public void setCoTeacherIds(List<Integer> value) { coTeacherIds = value; }
}
