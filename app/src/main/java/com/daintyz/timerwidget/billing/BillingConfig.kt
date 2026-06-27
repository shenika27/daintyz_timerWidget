package com.daintyz.timerwidget.billing

/**
 * 결제/보호 다운로드 설정. **로그인(배포) 후 채워야 하는 유일한 플러그 지점**이 여기 있다.
 *
 * Worker 배포(`wrangler deploy`)가 끝나면 출력되는 주소로 [WORKER_BASE_URL]만 교체하면
 * 유료 스킨 보호 다운로드가 동작한다. (SKU/이용권 ID는 catalog와 [BillingManager]에 이미 정의됨)
 */
object BillingConfig {

    /**
     * 결제 검증 Worker 주소. 배포 후 실제 주소로 교체할 것.
     * 예: "https://daintyz-billing.<account>.workers.dev"
     * (infra/billing-worker 참고). 빈 문자열/미교체 상태면 보호 다운로드는 시도해도 실패한다.
     */
    const val WORKER_BASE_URL = ""

    /** 보호 다운로드 엔드포인트 경로(Worker src/index.js의 라우트와 일치). */
    const val DOWNLOAD_PATH = "/v1/skins/download"

    /** Worker 주소가 설정돼 있는지(미설정이면 유료 다운로드를 시도하지 않는다). */
    val isConfigured: Boolean get() = WORKER_BASE_URL.isNotBlank()

    /** 보호 다운로드 전체 URL. */
    val downloadUrl: String get() = WORKER_BASE_URL.trimEnd('/') + DOWNLOAD_PATH
}
