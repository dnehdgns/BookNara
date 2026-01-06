package com.booknara.booknaraPrj.bookDetail.service;

import com.booknara.booknaraPrj.bookDetail.dto.BookDetailDTO;
import com.booknara.booknaraPrj.bookDetail.dto.BookDetailViewDTO;
import com.booknara.booknaraPrj.bookDetail.dto.BookInventoryDTO;
import com.booknara.booknaraPrj.bookDetail.dto.GenreCrumbDTO;
import com.booknara.booknaraPrj.bookDetail.dto.GenrePathDTO;
import com.booknara.booknaraPrj.bookDetail.mapper.BookDetailMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class BookDetailService {

    private final BookDetailMapper bookDetailMapper;

    /**
     * 도서 상세 화면 데이터 조립
     * - 여러 SELECT를 조립하므로 readOnly 트랜잭션을 걸어두면 일관성/성능에 유리
     */
    @Transactional(readOnly = true)
    public BookDetailViewDTO getBookDetailView(String isbn13) {

        // 1) 도서 메타 (없으면 null)
        BookDetailDTO detail = bookDetailMapper.selectBookDetail(isbn13);
        if (detail == null) {
            return null; // Controller에서 404 처리
        }

        // 2) 재고 집계
        BookInventoryDTO inventory = bookDetailMapper.selectInventory(isbn13);
        if (inventory == null) {
            // ISBN은 있는데 BOOKS가 아직 없을 수도 있으니 기본값 처리
            inventory = new BookInventoryDTO();
            inventory.setTotalCount(0);
            inventory.setAvailableCount(0);
            inventory.setLostCount(0);
        }

        // 3) breadcrumb 조립 (현재 + 부모 1단)
        GenrePathDTO genrePath = buildGenrePath(detail.getGenreId());

        // 4) ViewDTO 조립
        BookDetailViewDTO view = new BookDetailViewDTO();
        view.setBookDetailDTO(detail);
        view.setInventory(inventory);
        view.setGenrePath(genrePath);

        return view;
    }

    private GenrePathDTO buildGenrePath(Integer genreId) {
        GenrePathDTO path = new GenrePathDTO();

        if (genreId == null) {
            return path;
        }

        Map<String, Object> row = bookDetailMapper.selectGenreSelfAndParent(genreId);
        if (row == null || row.isEmpty()) {
            return path;
        }

        // map key는 XML alias와 동일
        String mall = (String) row.get("mall");
        Integer selfId = toInteger(row.get("genreId"));
        String selfNm = (String) row.get("genreNm");
        Integer parentId = toInteger(row.get("parentId"));
        String parentNm = (String) row.get("parentNm");

        path.setMall(mall);

        // 부모(있으면) -> 현재 순서로 crumbs 구성
        if (parentId != null && parentNm != null) {
            path.getCrumbs().add(new GenreCrumbDTO(parentId, parentNm));
        }
        if (selfId != null && selfNm != null) {
            path.getCrumbs().add(new GenreCrumbDTO(selfId, selfNm));
        }

        return path;
    }

    private Integer toInteger(Object o) {
        if (o == null) return null;
        if (o instanceof Integer) return (Integer) o;
        if (o instanceof Long) return ((Long) o).intValue();
        if (o instanceof Short) return ((Short) o).intValue();
        if (o instanceof String) return Integer.valueOf((String) o);
        throw new IllegalArgumentException("Cannot convert to Integer: " + o.getClass());
    }
}
