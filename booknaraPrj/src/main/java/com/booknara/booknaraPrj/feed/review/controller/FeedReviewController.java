package com.booknara.booknaraPrj.feed.review.controller;

import com.booknara.booknaraPrj.feed.review.dto.ReviewPermissionDTO;
import com.booknara.booknaraPrj.feed.review.dto.ReviewSaveRequestDTO;
import com.booknara.booknaraPrj.feed.review.service.FeedReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/book/review")
public class FeedReviewController {

    private final FeedReviewService service;

    private String userId(Authentication auth) {
        return auth == null ? null : auth.getName();
    }

    @GetMapping("/permission")
    public ReviewPermissionDTO permission(@RequestParam String isbn13, Authentication auth) {
        return service.getReviewPermission(isbn13, userId(auth));
    }

    @PostMapping("/save")
    public Map<String, Object> save(@RequestBody ReviewSaveRequestDTO req, Authentication auth) {
        try {
            String feedId = service.saveReview(req, userId(auth));
            return Map.of("ok", true, "feedId", feedId);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
        }
    }

}
