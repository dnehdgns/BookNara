package com.booknara.booknaraPrj.bookSearch.service;

import com.booknara.booknaraPrj.bookSearch.dto.BookSearchConditionDTO;
import com.booknara.booknaraPrj.bookSearch.dto.BookSearchDTO;
import com.booknara.booknaraPrj.bookSearch.dto.PageInsertDTO;
import com.booknara.booknaraPrj.bookSearch.mapper.BookSearchMapper;
import com.booknara.booknaraPrj.common.dto.PageResultDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 도서 검색 서비스
 *
 * <p>
 * - 사용자 입력(검색어/필터/정렬/페이징)을 받아 "안전한 값"으로 정규화(기본값/상한선/화이트리스트)한 뒤
 *   MyBatis Mapper를 통해 실제 검색 결과를 조회한다.
 * - 입력값 검증의 목적은 SQL 인젝션(#{ 바인딩으로 상당부분 방어됨)보다는
 *   "이상값/폭탄 요청(size/page)"에 의해 DB가 과부하 되는 것을 방지하고,
 *   화면이 항상 일관된 기본 동작(NEW 정렬, ALL 검색 등)을 하게 만드는 것이다.
 * </p>
 */
@Service
@RequiredArgsConstructor
public class BookSearchService {

    private final BookSearchMapper mapper;

    /**
     * 검색 조건 + 페이징을 기반으로 도서 목록을 조회하고, 페이지 메타 정보와 함께 반환한다.
     *
     * @param cond 검색 조건(검색어, 필드, 카테고리, 전자책 필터, 정렬)
     * @param page 페이징 정보(page, size) + 내부 계산용 offset
     * @return PageResultDTO(목록 + 페이지 정보)
     */
    public PageResultDTO<BookSearchDTO> search(BookSearchConditionDTO cond, PageInsertDTO page) {
        // null 방어: 컨트롤러 바인딩이 실패하거나 호출자가 null로 넘겨도 서비스가 죽지 않도록
        if (cond == null) cond = new BookSearchConditionDTO();
        if (page == null) page = new PageInsertDTO();

        // ---------------------------------------------------------------------
        // 1) 페이징 입력값 방어
        // - page는 1부터 시작, size는 상한을 둬서 대량 조회(폭탄 요청) 방지
        // - offset은 DB LIMIT/OFFSET 조회를 위한 내부 계산값
        // ---------------------------------------------------------------------
        int safePage = clamp(page.getPage(), 1, 1_000_000, 1);
        int safeSize = clamp(page.getSize(), 1, 60, 20); // 필요 시 상한(60) 조정

        page.setPage(safePage);
        page.setSize(safeSize);

        // offset 오버플로우 방지: long으로 계산 후 int로 안전 변환
        page.setOffset(Math.toIntExact(((long) safePage - 1) * safeSize));

        // ---------------------------------------------------------------------
        // 2) 검색어 정규화
        // - null/공백 입력은 검색 조건 미적용(null)
        // - 너무 긴 검색어는 잘라서 DB 부하/로그 문제 방지
        // - 연속 공백은 1개로 정리(선택)
        // ---------------------------------------------------------------------
        cond.setKeyword(normalizeKeyword(cond.getKeyword()));

        // ---------------------------------------------------------------------
        // 3) field/sort/ebookYn 화이트리스트
        // - 사용자가 임의의 값을 보내도(개발자도구/직접 URL 입력 등) 안전하게 기본값으로 강제
        // - 대소문자 혼용도 대비해 대문자로 통일
        // ---------------------------------------------------------------------
        String field = upperOrNull(cond.getField());
        String sort = upperOrNull(cond.getSort());
        String ebook = upperOrNull(cond.getEbookYn());

        cond.setField(whitelistOrDefault(field, "ALL", "ALL", "TITLE", "AUTHOR", "PUBLISHER"));
        cond.setSort(whitelistOrDefault(sort, "NEW", "NEW", "RATING", "REVIEW"));
        cond.setEbookYn(whitelistOrDefault(ebook, "ALL", "ALL", "ALL", "N"));

        // ---------------------------------------------------------------------
        // 4) 카테고리 값 방어
        // - 0/음수로 들어오는 경우 의미 없는 값이므로 null 처리
        //   (MyBatis 동적 SQL에서 조건이 붙지 않도록)
        // ---------------------------------------------------------------------
        // genreId: 소분류는 실제 GENRE_ID만 허용 (0/음수는 무효)
        if (cond.getGenreId() != null && cond.getGenreId() <= 0) {
            cond.setGenreId(null);
        }

        // parentGenreId: -1(기타)은 허용, 0 이하 중 -1만 예외
        if (cond.getParentGenreId() != null) {
            Integer pid = cond.getParentGenreId();
            if (pid == 0) {                 // 0은 무효
                cond.setParentGenreId(null);
            } else if (pid < -1) {          // -2 이하 같은 값은 무효(필요시)
                cond.setParentGenreId(null);
            }
            // pid == -1 은 기타로 유효
        }

        // ---------------------------------------------------------------------
        // 5) DB 조회
        // - total(전체 건수) 먼저 구하고, 0이면 리스트 조회는 생략하여 불필요 쿼리 방지
        // ---------------------------------------------------------------------
        long total = mapper.countBooks(cond);
        List<BookSearchDTO> items = (total == 0) ? List.of() : mapper.searchBooks(cond, page);

        // ---------------------------------------------------------------------
        // 6) 공통 페이지 응답 포맷으로 반환
        // ---------------------------------------------------------------------
        return PageResultDTO.of(items, page.getPage(), page.getSize(), total);
    }

    /**
     * 검색어 정규화:
     * - null이면 null
     * - trim 후 빈 문자열이면 null
     * - 길이 제한(기본 100자)
     * - 연속 공백은 1개로 축소(선택)
     */
    private static String normalizeKeyword(String keyword) {
        if (keyword == null) return null;

        String k = keyword.trim();
        if (k.isEmpty()) return null;

        // 너무 긴 검색어 제한(성능/UX/로그)
        if (k.length() > 100) k = k.substring(0, 100);

        // 연속 공백 정리(예: "자바   스프링" -> "자바 스프링")
        k = k.replaceAll("\\s{2,}", " ");

        return k;
    }

    /**
     * 화이트리스트 기반 값 검증:
     * - value가 allowed 목록 중 하나면 value 반환
     * - 아니면 defaultValue 반환
     *
     * <p>주의: 절대 `${}` 같은 문자열 치환 SQL에 이 값을 그대로 쓰지 말 것.
     * 지금 구조처럼 MyBatis `<choose>` / `#{}` 바인딩과 함께 쓰면 안전하다.</p>
     */
    private static String whitelistOrDefault(String value, String defaultValue, String... allowed) {
        if (value == null) return defaultValue;
        for (String a : allowed) {
            if (a.equals(value)) return value;
        }
        return defaultValue;
    }

    /**
     * 숫자 입력값 보정(클램프):
     * - min 미만이면 defaultValue로 보정
     * - max 초과면 max로 제한
     * - 정상 범위면 그대로 반환
     * @param v            입력값
     * @param min          허용 최소값
     * @param max          허용 최대값
     * @param defaultValue min 미만일 때 적용할 기본값
     */
    private static int clamp(int v, int min, int max, int defaultValue) {
        if (v < min) return defaultValue;
        return Math.min(v, max);
    }

    /**
     * 문자열을 대문자로 변환(입력값 통일)
     * - null이면 null
     * - 공백만이면 null
     */
    private static String upperOrNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;
        return t.toUpperCase();
    }
}
