# LifeLink 2.3.0 (38) Closed testing 배포 기록

기록일: 2026-10-11. 공개 출시나 Google 심사 승인을 의미하지 않습니다.

## 소스와 자동 검증

- 빌드 대상: `2395ca1a543110793c3e65ce4350807a0f9a69c3` (GitHub main 반영).
- [Android verification](https://github.com/bboysilver/Lifelink-ANDROID/actions/runs/38129680460): 성공.
- [Signed Android release](https://github.com/bboysilver/Lifelink-ANDROID/actions/runs/38129682780): 성공. 기존 GitHub Secrets의 업로드 키 사용, 엄격한 서명 및 인증서 검증 통과.
- CI 단위/Robolectric/Compose 테스트: 204개, 실패·오류·건너뜀 0개.
- CI lint: 오류 0개, 경고 79개, Hint 1개. 로컬보다 추가된 2개는 최신 Android 타깃 및 Gradle 버전 권고입니다. 이를 경고가 없다는 의미로 해석하지 않습니다.
- 최종 Manifest 안전 검사 통과: 최소 API 24, 전화 기능 필수, 광고 ID·인터넷·위치 권한 없음.
- [개인정보처리방침 배포](https://github.com/bboysilver/Lifelink-ANDROID/actions/runs/38129680483): 성공. [실제 공개 페이지](https://bboysilver.github.io/Lifelink-ANDROID/privacy-policy.html)에서 2.3.0 안내와 수동 가족 기록·문자 전송 항목을 확인했습니다.

## 서명 파일

- 로컬 파일: `release-artifacts/2.3.0-38/lifelink-2.3.0-38-signed.aab` (3,529,005 bytes).
- AAB SHA-256: `E50FC4C287240FB9AE895A73554E9D137F6FE5EE14B44E3FB116F66EC6A4F1CE`.
- 업로드 인증서 SHA-256: `862CB532B1485B1AA97F7731716333C71A4D5A49AEC102D4026BFDB8226110CC`.
- 로컬 체크섬과 JAR 서명을 다시 확인했으며, 위 인증서가 Play Console의 업로드 인증서와 일치합니다. 기기 배포 APK에는 Google Play의 별도 앱 서명 키가 사용됩니다.
- 빌드·검증 증빙은 해당 Actions 실행의 artifact와 로컬 `signed-ci/` 폴더에 보관합니다. Actions artifact 보존 기간은 30일입니다. 개인 키·비밀번호는 이 파일에 포함하지 않습니다.

## Play Console 처리

- 대상: 기존 **Closed testing — Closed test 1**만 업데이트. Production·다른 테스트 트랙·테스터 목록은 변경하지 않았습니다.
- 릴리스: `2.3.0 (38) 작동 점검·가족 확인·간편 홈`, 릴리스 화면 ID 11.
- AAB 처리 성공: 버전 코드 38, 버전 이름 2.3.0, API 24 이상, target SDK 36. 이전 릴리스 대비 지원 기기 증감 없음.
- 배포 차단 오류 없음. 네이티브 디버그 심볼 미첨부 경고 1개가 남아 있습니다. Kotlin/Java 난독화 mapping은 번들에 포함되어 있습니다.
- 릴리스를 저장하고 `Send changes for review`를 눌렀습니다. 제출 직후 `Changes in review` 및 Google 사전 자동 검사 진행 상태를 확인했습니다. 검사가 성공하면 심사에 전달되며, 심사 승인·테스터 배포 완료 여부는 이후 Console 상태로 별도 확인해야 합니다.
- 제출 증빙: 로컬 `release-artifacts/2.3.0-38/play-review-submitted.jpg`.

## 승인 후 실기기 검증

이번 작업 시 ADB에 연결된 기기가 없어 실제 SIM 발송·기기 업그레이드·장기 절전 검증은 실행하지 않았습니다. 자동 테스트 결과를 실제 기기 성공률로 해석하지 않습니다.

1. 기존 테스터가 Play에서 업데이트한 뒤 연락처·선택 SIM·12시간 설정·안부 설정이 유지되는지 확인합니다. 앱을 삭제해서 업데이트하지 않습니다.
2. 잠금 해제·유효 활동 후 무활동 마감시각 갱신과 매일 안부 자동 완료, 화면을 끈 상태·재부팅 후 복구를 확인합니다.
3. 동의를 받은 보호자에게 시험 문자, SOS 취소/발송, 무활동 알림을 확인합니다. 일반 버전에서 시간을 기다리거나 별도 개발 기기의 디버그 테스트 시간을 사용하며, 기본 문자 앱에서 실제 SMS가 되는 활성 SIM을 사용합니다.
4. 비행기 모드·권한 거부·SIM 변경·배터리 제한에서 실패/중단 안내와 재시도 상태를 확인합니다. 문자 발송·전달은 보호자의 열람·안부 확인을 의미하지 않습니다.
5. 간편 홈·200% 글자 크기·작동 점검·가족 확인 기록/삭제를 확인합니다. 가족 기록이 SMS 재시도나 모니터링을 취소하지 않는지 확인합니다.

장기 베타 검증과 정책 심사가 끝나기 전에는 안전 보장, 휴대전화 전원 꺼짐 감지 또는 보호자 원격 확인 기능이 제공된다고 안내하지 않습니다. 서버·구독·원격 보호자 기능은 이번 배포에 포함하지 않습니다.
