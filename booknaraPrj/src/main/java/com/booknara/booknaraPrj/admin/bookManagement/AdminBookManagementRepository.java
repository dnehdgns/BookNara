/*
package com.booknara.booknaraPrj.admin.bookManagement;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface AdminBookManagementRepository extends JpaRepository<AdminBooks, Long> {

    @Query("SELECT new com.booknara.booknaraPrj.admin.bookManagement.AdminBookListResponseDto(" +
            "B.BOOK_ID, I.ISBN13, I.BOOK_TITLE, I.AUTHORS, I.DESCRIPTION, I.PUBLISHER, I.PUB_DATE, " +
            "I.NAVER_IMAGE, I.ALADIN_IMAGE_BIG, I.EBOOK_YN, I.EPUB, G.GENRE_NM, B.BOOK_STATE) " +
            "FROM ADMIN_BOOKS B " +
            "LEFT JOIN B.BOOK_ISBN I " +
            "LEFT JOIN I.ADMIN_GENRE G " +
            "WHERE (:keyword IS NULL OR I.BOOK_TITLE LIKE %:keyword% OR I.AUTHORS LIKE %:keyword% OR I.ISBN13 = :keyword) " +
            "AND (:bookState IS NULL OR :bookState = '' OR B.BOOK_STATE = :bookState)")
    Slice<AdminBookListResponseDto> findByFiltersDto(
            @Param("keyword") String keyword,
            @Param("bookState") String bookState,
            Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
// [수정] WHERE 절을 b.bookId 기준으로 변경합니다.
    @Query("UPDATE ADMIN_BOOKS B SET B.BOOK_STATE = :bookState WHERE B.BOOK_ID = :bookId")
    void updateBookState(@Param("bookId") Long bookId, @Param("bookState") String bookState);
}*/

package com.booknara.booknaraPrj.admin.bookManagement;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface AdminBookManagementRepository extends JpaRepository<AdminBooks, Long> {

    // [수정 포인트]
    // 1. i.ebookYn -> i.eBookYn (방금 확인한 변수명 적용!)
    // 2. 나머지 변수명들도 AdminBooks, AdminBookIsbn 엔티티에 맞춰 CamelCase 유지
    @Query("SELECT new com.booknara.booknaraPrj.admin.bookManagement.AdminBookListResponseDto(" +
            "b.bookId, i.isbn13, i.bookTitle, i.authors, i.description, i.publisher, i.pubDate, " +
            "i.naverImage, i.aladinImageBig, i.eBookYn, i.epub, g.genreNm, b.bookState) " + // ✅ 여기가 핵심 수정 사항
            "FROM AdminBooks b " +
            "LEFT JOIN b.bookIsbn i " +
            "LEFT JOIN i.adminGenre g " +
            "WHERE (:keyword IS NULL OR i.bookTitle LIKE %:keyword% OR i.authors LIKE %:keyword% OR i.isbn13 = :keyword) " +
            "AND (:bookState IS NULL OR :bookState = '' OR b.bookState = :bookState)")
    Slice<AdminBookListResponseDto> findByFiltersDto(
            @Param("keyword") String keyword,
            @Param("bookState") String bookState,
            Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("UPDATE AdminBooks b SET b.bookState = :bookState WHERE b.bookId = :bookId")
    void updateBookState(@Param("bookId") Long bookId, @Param("bookState") String bookState);
}