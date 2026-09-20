package com.example.demo.service.serviceImpl;

import com.example.demo.dto.request.CommentCreateRequest;
import com.example.demo.dto.response.CommentResponse;
import com.example.demo.entity.Comment;
import com.example.demo.entity.Lecture;
import com.example.demo.entity.User;
import com.example.demo.common.exception.BadRequestException;
import com.example.demo.common.exception.ResourceNotFoundException;
import com.example.demo.repository.CommentRepository;
import com.example.demo.repository.LectureRepository;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.CommentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service xử lý bình luận Q&A dưới mỗi bài giảng.
 *
 * - getComments: Lấy toàn bộ comment gốc + replies lồng ghép
 * - addComment: Tạo mới comment hoặc reply
 * - deleteComment: Soft-delete (chỉ chủ sở hữu hoặc Admin)
 */
@Service
@RequiredArgsConstructor
public class CommentServiceImpl implements CommentService {

    private final CommentRepository commentRepository;
    private final LectureRepository lectureRepository;
    private final UserRepository userRepository;

    /**
     * Lấy toàn bộ comment của một bài giảng, đã được nhóm theo thread.
     * Comment gốc (parentComment = null) chứa list replies của chúng.
     */
    @Transactional(readOnly = true)
    public List<CommentResponse> getComments(Long lectureId) {
        if (!lectureRepository.existsById(lectureId)) {
            throw new ResourceNotFoundException("Lecture not found: " + lectureId);
        }

        List<Comment> all = commentRepository.findByLectureId(lectureId);

        // Nhóm replies theo parentCommentId
        Map<Long, List<CommentResponse>> repliesMap = all.stream()
                .filter(c -> c.getParentComment() != null)
                .map(this::toResponse)
                .collect(Collectors.groupingBy(CommentResponse::parentCommentId));

        // Chỉ trả về comment gốc, gắn replies vào
        return all.stream()
                .filter(c -> c.getParentComment() == null)
                .map(c -> {
                    CommentResponse base = toResponse(c);
                    List<CommentResponse> replies = repliesMap.getOrDefault(c.getCommentId(), List.of());
                    return new CommentResponse(
                            base.commentId(), base.parentCommentId(),
                            base.userId(), base.userName(), base.userRole(),
                            base.content(), base.createdAt(), replies
                    );
                })
                .collect(Collectors.toList());
    }

    /**
     * Thêm comment mới hoặc reply vào một bài giảng.
     */
    @Transactional
    public CommentResponse addComment(Long lectureId, Integer userId, CommentCreateRequest request) {
        Lecture lecture = lectureRepository.findById(lectureId)
                .orElseThrow(() -> new ResourceNotFoundException("Lecture not found: " + lectureId));

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        Comment comment = new Comment();
        comment.setLecture(lecture);
        comment.setUser(user);
        comment.setContent(request.content().trim());

        // Nếu là reply, validate parent comment tồn tại và cùng lecture
        if (request.parentCommentId() != null) {
            Comment parent = commentRepository.findById(request.parentCommentId())
                    .orElseThrow(() -> new ResourceNotFoundException("Parent comment not found"));
            if (!parent.getLecture().getLectureId().equals(lectureId)) {
                throw new BadRequestException("Parent comment không thuộc bài giảng này");
            }
            if (parent.getParentComment() != null) {
                throw new BadRequestException("Chỉ hỗ trợ reply 1 cấp");
            }
            comment.setParentComment(parent);
        }

        return toResponse(commentRepository.save(comment));
    }

    /**
     * Soft-delete comment. Chỉ chủ sở hữu hoặc ADMIN mới được xóa.
     */
    @Transactional
    public void deleteComment(Long commentId, Integer requestUserId, boolean isAdmin) {
        Comment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new ResourceNotFoundException("Comment not found: " + commentId));

        if (!isAdmin && !comment.getUser().getUserId().equals(requestUserId)) {
            throw new BadRequestException("Bạn không có quyền xóa bình luận này");
        }

        comment.setIsDeleted(true);
        commentRepository.save(comment);
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private CommentResponse toResponse(Comment c) {
        return new CommentResponse(
                c.getCommentId(),
                c.getParentComment() != null ? c.getParentComment().getCommentId() : null,
                c.getUser().getUserId(),
                c.getUser().getName(),
                c.getUser().getRole().name(),
                c.getContent(),
                c.getCreatedAt(),
                List.of()   // replies được gán lại ở getComments()
        );
    }
}
