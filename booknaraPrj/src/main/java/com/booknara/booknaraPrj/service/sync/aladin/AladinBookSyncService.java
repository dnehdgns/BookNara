package com.booknara.booknaraPrj.service.sync.aladin;

import com.booknara.booknaraPrj.client.aladin.AladinClient;
import com.booknara.booknaraPrj.client.aladin.AladinDTO;
import com.booknara.booknaraPrj.client.aladin.AladinResponse;
import com.booknara.booknaraPrj.domain.BookIsbnTempDTO;
import com.booknara.booknaraPrj.mapper.BookBatchMapper;
import com.booknara.booknaraPrj.service.batch.hash.BookIsbnHash;
import com.booknara.booknaraPrj.service.policy.TempReadyPolicy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientResponseException;

import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class AladinBookSyncService {

    private final BookBatchMapper batchMapper;
    private final AladinClient aladinClient;
    private final TempReadyPolicy readyPolicy;

    // =========================
    // 튜닝 포인트
    // =========================
    private static final int MAX_RETRY_COUNT = 3;
    private static final long INITIAL_BACKOFF_MS = 200;

    // 429/5xx 발생 시 해당 키 잠깐 제외
    private static final long KEY_COOLDOWN_ON_429_MS = 15_000; // 15초
    private static final long KEY_COOLDOWN_ON_5XX_MS = 5_000;  // 5초

    // 같은 키에서 429가 반복되면 요청횟수 초과
    private static final int DAILY_429_COUNT_LIMIT = 3;
    private final ConcurrentHashMap<Integer, Integer> daily429Count = new ConcurrentHashMap<>();

    private static final ZoneId KST_ZONE = ZoneId.of("Asia/Seoul");
    private static final long DELAY_MINUTES = 5;

    /**
     * NOTREADY(0) 중 알라딘 보강이 필요한 ISBN을 limit 단위로 처리
     * - pubdate / genreId / aladinImageBig / description 보강
     * - DB 재조회 후 READY 조건 충족 시 STATUS_CD=1로 승격 (그리고 TEMP hash 저장)
     */
    public int syncOnce(int limit) {
        List<String> targetIsbnList = batchMapper.selectTempIsbnForAladin(limit);

        if (targetIsbnList == null || targetIsbnList.isEmpty()) {
            log.info("aladin sync: no targets");
            return 0;
        }

        int successCount = 0;
        for (String isbn13 : targetIsbnList) {
            if (syncOne(isbn13)) {
                successCount++;
            }
        }

        log.info("aladin sync: success={}/{}", successCount, targetIsbnList.size());

        return targetIsbnList.size();
    }

    public void syncLoop(int limit) {
        while (true) {
            int targetCount = syncOnce(limit);
            if (targetCount == 0) break;
        }
        log.info("aladin syncLoop finished");
    }

    /**
     * 단일 ISBN 보강
     * - 실패해도 예외 전파하지 않음 (NOTREADY 유지)
     * - 시도 시간 기록은(알라딘_fetched_at) 성공/실패 관계없이 남김
     */
    @Transactional
    public boolean syncOne(String isbn13) {
        LocalDateTime triedAt = LocalDateTime.now();

        try {
            AladinResponse apiResponse = fetchWithRetry(isbn13);

            BookIsbnTempDTO tempUpdate =
                    (apiResponse == null)
                            ? buildTriedOnlyDto(isbn13, triedAt)
                            : toTempUpdateDto(isbn13, apiResponse, triedAt);

            // 실패여도 "시도 기록"은 남겨서 무한 재시도 루프 방지
            tempUpdate.setAladinFetchedAt(triedAt);

            int updatedRows = batchMapper.updateTempFromAladin(tempUpdate);
            if (updatedRows == 0) {
                log.warn("aladin sync skipped (no row) isbn13={}", isbn13);
                return false;
            }

            BookIsbnTempDTO mergedTemp = batchMapper.selectTempByIsbn13(isbn13);
            if (mergedTemp != null && readyPolicy.isReady(mergedTemp)) {
                // READY 될 때만 해시 계산/저장
                String newHash = BookIsbnHash.compute(mergedTemp);
                batchMapper.updateTempDataHash(isbn13, newHash);
                batchMapper.markTempReady(isbn13);
            }

            return apiResponse != null;

        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            log.warn("aladin sync interrupted isbn13={}", isbn13);
            return false;

        } catch (Exception ex) {
            log.warn("aladin sync failed isbn13={}", isbn13, ex);
            return false;

        } finally {
            // 너무 공격적으로 치면 차단/지연이 생길 수 있으니 안전 마진
            try {
                Thread.sleep(120);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private BookIsbnTempDTO buildTriedOnlyDto(String isbn13, LocalDateTime triedAt) {
        BookIsbnTempDTO dto = new BookIsbnTempDTO();
        dto.setIsbn13(isbn13);
        dto.setAladinFetchedAt(triedAt);
        return dto;
    }

    /**
     * 알라딘 호출 + 에러 정책
     * - 429:
     *    (1) 먼저 짧은 쿨다운 + 백오프 재시도
     *    (2) 동일 키에서 반복 429면 오늘 소진으로 보고 자정+5분까지 제외
     * - 5xx: 다음 시도에서 다른 키가 선택되도록 짧은 쿨다운 + 재시도
     * - 401/403: 키/권한 문제 가능성 → 해당 키를 자정+5분까지 제외하고 포기
     * - 기타 4xx: 파라미터/요청 문제 가능성 → 재시도 의미 적음 → null
     */
    private AladinResponse fetchWithRetry(String isbn13) throws InterruptedException {
        long backoffMs = INITIAL_BACKOFF_MS;

        for (int attempt = 1; attempt <= MAX_RETRY_COUNT; attempt++) {
            try {
                AladinResponse resp = aladinClient.searchByIsbnOnce(isbn13);

                // 성공하면(200)
                Integer keyIdx = aladinClient.getLastKeyIndex();
                aladinClient.clearLastKeyIndex();
                if (keyIdx != null) daily429Count.remove(keyIdx);

                return resp;

            } catch (RestClientResponseException ex) {
                int statusCode = ex.getStatusCode().value();

                Integer keyIdx = aladinClient.getLastKeyIndex();
                aladinClient.clearLastKeyIndex();

                if (statusCode == 429) {
                    if (keyIdx != null) {
                        int count = daily429Count.merge(keyIdx, 1, Integer::sum);

                        // 반복 429 → 오늘 소진 판단
                        if (count >= DAILY_429_COUNT_LIMIT) {
                            long until = getNextScheduledTime();
                            aladinClient.cooldownKeyUntil(keyIdx, until);
                            daily429Count.remove(keyIdx);

                            log.warn("aladin key daily exhausted. keyIdx={} until={} isbn13={}",
                                    keyIdx, until, isbn13);
                            return null;
                        }

                        // 아직은 초당/일시적 제한으로 보고 짧은 쿨다운
                        aladinClient.cooldownKey(keyIdx, KEY_COOLDOWN_ON_429_MS);
                    }

                    long jitterMs = (long) (Math.random() * 150);
                    log.warn("aladin 429 rate-limited. isbn13={} attempt={} keyIdx={} backoff={}ms",
                            isbn13, attempt, keyIdx, backoffMs);

                    Thread.sleep(backoffMs + jitterMs);
                    backoffMs = Math.min(backoffMs * 2, 3000);
                    continue;
                }

                // 5xx → 일시 장애 가능성, 재시도
                if (statusCode >= 500 && statusCode <= 599) {
                    if (keyIdx != null) {
                        aladinClient.cooldownKey(keyIdx, KEY_COOLDOWN_ON_5XX_MS);
                    }

                    long jitterMs = (long) (Math.random() * 150);
                    log.warn("aladin 5xx. retry. isbn13={} attempt={} keyIdx={} status={} backoff={}ms",
                            isbn13, attempt, keyIdx, statusCode, backoffMs);

                    Thread.sleep(backoffMs + jitterMs);
                    backoffMs = Math.min(backoffMs * 2, 3000);
                    continue;
                }

                // 401/403 → 키/권한/차단 가능성: 오늘은 빼고 다음 키로
                if (statusCode == 401 || statusCode == 403) {
                    if (keyIdx != null) {
                        long until = getNextScheduledTime();
                        aladinClient.cooldownKeyUntil(keyIdx, until);
                        log.warn("aladin auth/forbidden. keyIdx={} until={} status={} isbn13={}",
                                keyIdx, until, statusCode, isbn13);
                    }
                    return null;
                }

                // 기타 4xx는 재시도 의미 적음
                log.warn("aladin request failed. isbn13={} status={} keyIdx={}", isbn13, statusCode, keyIdx);
                return null;
            }
        }

        log.warn("aladin retry exhausted isbn13={}", isbn13);
        return null;
    }

    private long getNextScheduledTime() {
        return ZonedDateTime.now(KST_ZONE)
                .plusDays(1)
                .toLocalDate()
                .atStartOfDay(KST_ZONE)
                .plusMinutes(DELAY_MINUTES)
                .toInstant()
                .toEpochMilli();
    }

    /**
     * 알라딘 응답을 TEMP 업데이트용 DTO로 변환
     * - AladinDTO: pubdate(String), cover(String), categoryId(String), description(String)
     */
    private BookIsbnTempDTO toTempUpdateDto(String isbn13, AladinResponse apiResponse, LocalDateTime triedAt) {
        BookIsbnTempDTO updateDto = new BookIsbnTempDTO();
        updateDto.setIsbn13(isbn13);
        updateDto.setAladinFetchedAt(triedAt);

        if (apiResponse == null || apiResponse.getItem() == null || apiResponse.getItem().isEmpty()) {
            return updateDto;
        }

        AladinDTO firstItem = apiResponse.getItem().get(0);

        updateDto.setPubdate(parsePubdate(firstItem.getPubdate()));
        updateDto.setGenreId(parseInteger(firstItem.getCategoryId()));
        updateDto.setAladinImageBig(normalizeText(firstItem.getCover()));
        updateDto.setDescription(normalizeText(firstItem.getDescription()));

        return updateDto;
    }

    private LocalDate parsePubdate(String pubdate) {
        String value = normalizeText(pubdate);
        if (value == null) return null;

        try {
            return LocalDate.parse(value, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException ignore) {}

        try {
            DateTimeFormatter compact = DateTimeFormatter.ofPattern("yyyyMMdd");
            return LocalDate.parse(value, compact);
        } catch (DateTimeParseException ignore) {}

        return null;
    }

    private Integer parseInteger(String numberText) {
        String value = normalizeText(numberText);
        if (value == null) return null;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String normalizeText(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
