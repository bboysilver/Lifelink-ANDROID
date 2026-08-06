package com.example.ui

internal object LifeLinkFamilyPlan {
    const val MONTHLY_PRICE_WON = 3_900
    const val ANNUAL_PRICE_WON = 39_000
    const val TRIAL_DAYS = 14

    val benefits = listOf(
        "휴대전화·앱 연결 끊김 감지",
        "보호자용 상태 화면과 원격 안부 요청",
        "배터리·권한·SIM 이상 알림",
        "보호자 최대 5명과 단계별 알림",
        "최근 90일 안전 기록"
    )

    val monthlyPriceLabel = "월 3,900원"
    val annualPriceLabel = "연 39,000원"
}
