package com.booknara.booknaraPrj.bookDetail.dto;

import lombok.Data;

@Data
public class BookDetailViewDTO {
    private BookDetailDTO bookDetailDTO;
    private GenrePathDTO genrePath;
    private BookInventoryDTO inventory;

    /**
     * 향후 확장 포인트
     * - 리뷰/별점, 대출/예약 정보 등
     * private ReviewSummaryDTO reviewSummary;
     * private LoanStatusDTO loanStatus;
     */
}
