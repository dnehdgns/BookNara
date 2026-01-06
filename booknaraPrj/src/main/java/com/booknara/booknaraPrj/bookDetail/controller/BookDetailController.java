package com.booknara.booknaraPrj.bookDetail.controller;

import com.booknara.booknaraPrj.bookDetail.dto.BookDetailViewDTO;
import com.booknara.booknaraPrj.bookDetail.service.BookDetailService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.server.ResponseStatusException;

@Controller
@RequiredArgsConstructor
public class BookDetailController {

    private final BookDetailService bookDetailService;

    /**
     * 도서 상세 페이지
     * 예: /book/9788997592579
     */
    @GetMapping("/book/detail/{isbn13}")
    public String bookDetail(@PathVariable String isbn13, Model model) {

        BookDetailViewDTO view = bookDetailService.getBookDetailView(isbn13);
        if (view == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Book not found: " + isbn13);
        }

        model.addAttribute("view", view);
        return "book/bookDetail"; // templates/book/bookDetail.html
    }
}
