package com.cnotes.social;

import com.cnotes.social.dto.AnnotationRequest;
import com.cnotes.social.dto.CommentDto;
import com.cnotes.social.dto.PublicAnnotationDto;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 社交互动:点赞 / 评论 / 公开批注 / 关注。
 * 能力门槛按文章生效分享级别;写操作要求真实登录(见 {@link SocialService})。
 */
@RestController
@RequiredArgsConstructor
public class SocialController {

    private final SocialService social;

    /* 点赞 */
    @PostMapping("/api/articles/{id}/like")
    public ResponseEntity<Void> like(@PathVariable String id) {
        social.like(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/articles/{id}/like")
    public ResponseEntity<Void> unlike(@PathVariable String id) {
        social.unlike(id);
        return ResponseEntity.noContent().build();
    }

    /* 评论 */
    @GetMapping("/api/articles/{id}/comments")
    public List<CommentDto> comments(@PathVariable String id) {
        return social.listComments(id);
    }

    @PostMapping("/api/articles/{id}/comments")
    public CommentDto addComment(@PathVariable String id, @RequestBody Map<String, String> body) {
        return social.addComment(id, body.get("body"), body.get("parentId"));
    }

    @DeleteMapping("/api/comments/{id}")
    public ResponseEntity<Void> deleteComment(@PathVariable String id) {
        return social.deleteComment(id) ? ResponseEntity.noContent().build() : ResponseEntity.notFound().build();
    }

    /* 公开批注 */
    @GetMapping("/api/articles/{id}/annotations")
    public List<PublicAnnotationDto> annotations(@PathVariable String id) {
        return social.listAnnotations(id);
    }

    @PostMapping("/api/articles/{id}/annotations")
    public PublicAnnotationDto addAnnotation(@PathVariable String id, @Valid @RequestBody AnnotationRequest req) {
        return social.addAnnotation(id, req);
    }

    /* 关注 */
    @PostMapping("/api/users/{id}/follow")
    public ResponseEntity<Void> follow(@PathVariable String id) {
        social.follow(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/users/{id}/follow")
    public ResponseEntity<Void> unfollow(@PathVariable String id) {
        social.unfollow(id);
        return ResponseEntity.noContent().build();
    }
}
