package com.booknara.booknaraPrj.bookMark.controller;

import com.booknara.booknaraPrj.bookMark.service.BookmarkService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/bookmarks")
public class BookmarkController {

    private final BookmarkService bookmarkService;

    // 상태 조회 (선택)
    @GetMapping("/{isbn13}")
    public ResponseEntity<?> status(@PathVariable String isbn13, Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(401).body(Map.of("message", "UNAUTHORIZED"));
        }
        String userId = auth.getName();
        boolean bookmarked = bookmarkService.isBookmarked(isbn13, userId);
        return ResponseEntity.ok(Map.of("isbn13", isbn13, "bookmarked", bookmarked));
    }

    // 토글
    @PostMapping("/{isbn13}/toggle")
    public ResponseEntity<?> toggle(@PathVariable String isbn13, Authentication auth) {
        if (auth == null || !auth.isAuthenticated()) {
            return ResponseEntity.status(401).body(Map.of("message", "UNAUTHORIZED"));
        }
        String userId = auth.getName();
        boolean bookmarked = bookmarkService.toggle(isbn13, userId);
        return ResponseEntity.ok(Map.of("isbn13", isbn13, "bookmarked", bookmarked));
    }
}
