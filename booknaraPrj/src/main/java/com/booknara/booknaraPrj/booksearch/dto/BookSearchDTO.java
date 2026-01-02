package com.booknara.booknaraPrj.booksearch.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class BookSearchDTO {
    private String isbn13;
    private String title;
    private String authors;
    private String publisher;
    private LocalDate publishedDate;
    private String coverImageUrl;
    private Integer genreId;
    private boolean ebook;
}
