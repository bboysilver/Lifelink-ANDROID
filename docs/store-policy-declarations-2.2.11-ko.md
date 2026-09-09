# LifeLink 2.2.11 스토어 정책 선언 기준

코드와 스토어 선언이 달라지지 않도록 아래 내용을 기준으로 제출한다.

## Google Play

- 광고: 광고 SDK와 `AD_ID` 권한이 없으므로 `광고 포함 안 함`, 광고 ID는 `아니요`로 선언한다.
- SMS 권한: `SEND_SMS`는 앱의 핵심 기능인 물리적 안전·긴급 알림을 사용자가 등록한 보호자에게 보내는 데만 사용한다. SMS/통화 기록은 읽지 않는다.
- 포그라운드 서비스: Manifest와 동일하게 `health`를 선언한다. 사용자가 시작한 장기 활동 감지가 지연되거나 중단되면 무활동 경보를 계산할 수 없다는 영향과 실제 실행 영상을 제출한다.
- 건강 앱: 신체 활동 권한을 안전 확인의 보조 신호로 사용하는 사실을 정확히 선언하고, 스토어 설명과 개인정보처리방침에 목적·제한·기기 요구사항을 표시한다.
- 의료 고지: 스토어 전체 설명에 `이 앱은 의료기기가 아니며 질병을 진단, 치료, 완치 또는 예방하지 않습니다. 의료 조언이나 119 및 전문 안전 서비스를 대신하지 않습니다.`를 포함한다.
- 개인정보: 공개 HTML 방침 URL과 앱 내부 링크를 유지한다. 서버 수집, 위치, 광고, 결제를 사용한다고 선언하지 않는다.
- 구독: 2.2.11에는 결제 SDK와 유료 권한이 없으므로 인앱 상품을 활성화하지 않는다. Family 기능을 실제 구현하기 전에는 요금을 청구하지 않는다.

## Apple App Store

- 현재 저장소에는 iOS 앱이 없으므로 이 Android AAB를 App Store에 제출하지 않는다.
- iOS 공개 API는 사용자의 전송 승인을 받는 문자 작성 화면만 제공하므로 Android와 같은 무인 자동 SMS를 기기에서 구현했다고 설명하지 않는다.
- iOS 안전 경보는 서버 heartbeat, 보호자 푸시와 서버 SMS를 구현한 뒤 제공한다. 백그라운드 작업의 정확한 실행 시각을 보장한다고 표현하지 않는다.
- 구독을 도입할 때에는 StoreKit 인앱 구독을 사용하고 지속적인 Family 서비스 가치를 제공한다. 무료 핵심 안전 기능을 구독 만료로 중단하지 않는다.
- App Privacy와 개인정보처리방침은 실제 서버 데이터, 보유 기간, 보호자 공유, 계정 삭제 흐름을 구현한 뒤 그 내용과 정확히 일치시킨다.

## 공식 기준

- Google Play SMS 권한: https://support.google.com/googleplay/android-developer/answer/10208820
- Google Play 건강 앱: https://support.google.com/googleplay/android-developer/answer/16679511
- Google Play 포그라운드 서비스: https://support.google.com/googleplay/android-developer/answer/13392821
- Apple App Review Guidelines: https://developer.apple.com/app-store/review/guidelines/
- Apple 문자 작성 API: https://developer.apple.com/documentation/messageui/mfmessagecomposeviewcontroller
