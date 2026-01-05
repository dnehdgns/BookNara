package com.booknara.booknaraPrj.bookSearch.dto;

import lombok.Data;

@Data
public class BookSearchConditionDTO {
    // 검색
    private String keyword;   // 검색어
    private String field;     // TITLE / AUTHOR / PUBLISHER / ALL
    private String mall;  // 국내도서 / 외국도서

    // 카테고리
    private Integer genreId;         // 소분류
    private Integer parentGenreId;   // 대분류

    // 필터
    private String ebookYn;    // ALL / Y / N

    // 정렬
    private String sort;       // NEW / RATING / REVIEW
}

