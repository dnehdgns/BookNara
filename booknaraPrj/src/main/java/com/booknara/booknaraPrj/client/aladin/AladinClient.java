package com.booknara.booknaraPrj.client.aladin;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@RequiredArgsConstructor
public class AladinClient {

    private final AladinProperties aladinProperties;

    private final RestClient restClient = RestClient.builder()
            .baseUrl("https://www.aladin.co.kr/ttb/api")
            .build();

    /**
     * 알라딘 OpenAPI Key를 라운드로빈 방식으로 선택하기 위한 인덱스.
     * AtomicInteger는 멀티스레드 환경에서 ++ 연산의 Race Condition을 방지.
     */
    private final AtomicInteger apiKeyIndex = new AtomicInteger(0);

     //429/403/401 등 제한/차단 상황에서 특정 키를 일정 시간동안 제외용
    private final ConcurrentHashMap<Integer, Long> cooldownUntil = new ConcurrentHashMap<>();

    /**
     요청에 사용된 key index"를 Service가 알 수 있도록 기록.
     RestClientResponseException 발생 시 해당 키를 cooldown 처리용
     */
    private final ThreadLocal<Integer> lastKeyIndex = new ThreadLocal<>();

    //Service에서 이번 호출에 사용된 keyIndex 조회
    public Integer getLastKeyIndex() {
        return lastKeyIndex.get();
    }

    public void clearLastKeyIndex() {
        lastKeyIndex.remove();
    }

    // 짦은 쿨다운(ex 15초) */
    public void cooldownKey(int keyIndex, long cooldownMs) {
        if (keyIndex < 0) return;
        cooldownUntil.put(keyIndex, System.currentTimeMillis() + cooldownMs);
    }

    // 특정 시각까지 쿨다운(ex:UTC+09:00으로 00:00(자정))
    public void cooldownKeyUntil(int keyIndex, long cooldownUntilEpochMs) {
        if (keyIndex < 0) return;
        cooldownUntil.put(keyIndex, cooldownUntilEpochMs);
    }

    /**
     * 사용 가능한 키 선택
     * 쿨다운 중인 키는 건너뛰고 다음 키로 넘어감.
     * 모든 키가 쿨다운이면: 배치가 멈추지 않도록 하나를 강제로 선택.
     */
    private String selectKey() {
        List<String> keys = aladinProperties.getKeys();
        if (keys == null || keys.isEmpty()) {
            throw new IllegalStateException("Aladin API keys not configured");
        }

        int size = keys.size();
        long now = System.currentTimeMillis();

        // 쿨다운이 끝난 키를 우선 선택
        for (int tries = 0; tries < size; tries++) {
            int idx = Math.floorMod(apiKeyIndex.getAndIncrement(), size);
            long until = cooldownUntil.getOrDefault(idx, 0L);

            if (now >= until) {
                lastKeyIndex.set(idx);
                return keys.get(idx);
            }
        }

        // 전부 쿨다운이면: 하나 강제 선택 (배치 중단 방지)
        int idx = Math.floorMod(apiKeyIndex.getAndIncrement(), size);
        lastKeyIndex.set(idx);
        return keys.get(idx);
    }

    /**
     * 알라딘 ItemLookUp API를 1회 호출해서 DTO로 반환
     * - 재시도/키교체/쿨다운 정책은 Service에서 처리.
     */
    public AladinResponse searchByIsbnOnce(String isbn13) {
        String ttbKey = selectKey();

        try {
            return restClient.get()
                    .uri(uriBuilder -> uriBuilder
                            .path("/ItemLookUp.aspx")
                            .queryParam("ttbkey", ttbKey)
                            .queryParam("itemIdType", "ISBN13")
                            .queryParam("ItemId", isbn13)
                            .queryParam("Output", "JS")
                            .queryParam("Version", "20131101")
                            .queryParam("Cover", "Big")
                            .build())
                    .retrieve()
                    .body(AladinResponse.class);

        } catch (RestClientResponseException e) {
            // HTTP 상태코드가 있는 응답 실패(401/403/429/5xx 등)
            // 정책 판단은 Service에서 수행
            throw e;
        }
    }
}
