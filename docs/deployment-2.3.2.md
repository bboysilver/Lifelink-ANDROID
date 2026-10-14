# LifeLink 2.3.2 (40) Closed testing 배포 기록

기록일: 2026-10-14. 공개 출시 또는 Google 심사 승인을 의미하지 않습니다.

## 소스와 검증

- 최종 빌드 소스: `42528d15eefd29d70d7c6000d821f7651741dd74`, GitHub main에 반영.
- [Android verification](https://github.com/bboysilver/Lifelink-ANDROID/actions/runs/38561364849): 성공.
- [Signed Android release](https://github.com/bboysilver/Lifelink-ANDROID/actions/runs/38561362982): 성공. 최종 Manifest 검사, 엄격한 JAR 서명 검사, 예상 업로드 인증서 일치 검사 통과.
- 로컬 및 서명 CI 테스트: 227개, 실패·오류·건너뜀 0개.
- CI lint: 오류 0개, 경고 79개, Hint 1개. 로컬은 오류 0개, 경고 77개, Hint 1개.
- [개인정보처리방침 배포](https://github.com/bboysilver/Lifelink-ANDROID/actions/runs/38561362725): 성공. [공개 페이지](https://bboysilver.github.io/Lifelink-ANDROID/privacy-policy.html)의 HTTP 200 응답과 2.3.2 표기를 확인했습니다. 새 데이터 수집/서버 처리 기능은 없습니다.
- 상세 원인, 변경 범위, 실기기 확인 절차: [verification-2.3.2.md](verification-2.3.2.md).
- 초기 커밋 `2cee441`의 서명 CI는 시간 의존 테스트 실패로 제출하지 않았습니다. 최종 빌드는 입력 간격 정책을 명시적 시간으로 검사한 위 소스입니다.

## 제출한 파일

- `release-artifacts/2.3.2-40/lifelink-2.3.2-40-signed.aab`, 3,529,896 bytes.
- AAB SHA-256: `8097B38376B675C855282F723CA9D5D39765A102D8672C5903CE6009BB362316`.
- 업로드 인증서 SHA-256: `862CB532B1485B1AA97F7731716333C71A4D5A49AEC102D4026BFDB8226110CC`.
- 최종 CI 산출물을 다운로드하여 체크섬과 공개 인증서를 다시 확인했습니다. 로컬 unsigned AAB나 초기 실패 빌드를 올리지 않았습니다.
- 테스트·lint·서명 증빙은 해당 Actions artifact와 로컬 `release-artifacts/2.3.2-40/signed-ci/`에 보관합니다. 개인 키나 비밀번호는 다운로드하거나 문서에 포함하지 않았습니다.

## Play Console 처리

- 기존 **Closed testing — Closed test 1**만 업데이트했습니다. Production, 다른 트랙, 국가, 테스터 목록은 변경하지 않았습니다.
- 이전 2.3.1 (39)이 `Available to selected testers`인 것을 확인한 뒤 새 릴리스를 만들었습니다.
- 릴리스: `2.3.2 (40) 활동 감지·불필요 배지 개선`, 릴리스 화면 ID 13.
- Play의 파일 처리 결과: 버전 코드 40, 버전 이름 2.3.2, 최소 API 24, target SDK 36, ReTrace mapping 포함.
- 이전 릴리스와 지원 기기 증감 없음. Phone 12,067대 모델, Tablet 2,743대 모델, TV 2대 모델로 표시됩니다. 설치 가능 여부와 실제 SMS 지원 여부는 별개로 런타임 검사를 유지합니다.
- 제출 차단 오류 없음. 네이티브 디버그 심볼 미첨부 권고 경고 1개가 남아 있습니다.
- 릴리스를 저장하고 `Send changes for review`를 눌렀습니다. 일시적인 Console 오류 배너가 있었으나, 이후 게시 개요에서 해당 릴리스와 **Changes in review**를 확인했습니다.
- 제출 직후 Google 사전 자동 검사 진행 상태입니다. 성공 시 심사에 전달되며 승인·실제 테스터 배포 완료는 아직 확인하지 않았습니다.
- 제출 화면 증빙: `release-artifacts/2.3.2-40/play-review-submitted.jpg`.

## 승인 후 확인

ADB 연결 기기가 없어 실제 삼성 잠금 해제 방송, 통신사 SMS, OEM 배지·장기 절전은 검증하지 않았습니다. 기존 설치를 삭제하지 않고 업데이트한 뒤, 잠금/해제 후 무터치 복귀와 10초 뒤 앱 메뉴 조작을 확인해야 합니다.

타이머는 활동 확인 시 설정 시간으로 갱신된 뒤 다시 감소하는 것이 정상입니다. 정상 실행/완료 알림은 배지를 만들지 않도록 수정했고, 필요한 사전 경고·중단·실패 경고는 유지했습니다. 사용자가 확인한 방향에 따라 `[라이프링크 안전 알림]` 핵심 무활동 문자 자체는 제거하지 않았습니다.
