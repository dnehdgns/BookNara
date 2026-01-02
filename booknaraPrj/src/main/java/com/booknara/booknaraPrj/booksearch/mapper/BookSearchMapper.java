package com.booknara.booknaraPrj.booksearch.mapper;

import com.booknara.booknaraPrj.booksearch.dto.BookSearchDTO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface BookSearchMapper {

    List<BookSearchDTO> searchBooks(
            @Param("keyword") String keyword,
            @Param("field") String field,        // TITLE / AUTHOR / PUBLISHER / ALL
            @Param("genreId") Integer genreId,
            @Param("ebookOnly") Boolean ebookOnly,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    long countBooks(
            @Param("keyword") String keyword,
            @Param("field") String field,
            @Param("genreId") Integer genreId,
            @Param("ebookOnly") Boolean ebookOnly
    );
}
