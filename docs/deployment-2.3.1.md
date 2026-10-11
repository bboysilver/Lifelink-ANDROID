# LifeLink 2.3.1 (39) Closed testing 배포 기록

기록일: 2026-10-11. 이 기록은 공개 출시나 Google 심사 승인을 의미하지 않습니다.

## 소스와 자동 검증

- 빌드 대상: `6bb2101b2c6e1f9a378866b56e67dba7419fdc8c` (GitHub main 반영).
- [Android verification](https://github.com/bboysilver/Lifelink-ANDROID/actions/runs/38138956759): 성공.
- [Signed Android release](https://github.com/bboysilver/Lifelink-ANDROID/actions/runs/38138956852): 성공. 기존 GitHub Secrets의 업로드 키 사용, 엄격한 JAR 서명 및 예상 인증서 검증 통과.
- 로컬 및 CI 단위/Robolectric/Compose 테스트: 217개, 실패·오류·건너뜀 0개.
- CI lint: 오류 0개, 경고 79개, Hint 1개. 로컬은 오류 0개, 경고 77개, Hint 1개입니다. 경고가 전부 해결되었다는 의미가 아닙니다.
- 최종 Manifest 검사 통과: 최소 API 24, target SDK 36. 광고 ID·인터넷·위치·SMS 읽기/수신 권한을 추가하지 않았습니다.
- [개인정보처리방침 배포](https://github.com/bboysilver/Lifelink-ANDROID/actions/runs/38138956757): 성공. [공개 페이지](https://bboysilver.github.io/Lifelink-ANDROID/privacy-policy.html)의 HTTP 200 응답과 2.3.1 안내를 확인했습니다. 데이터 처리 구조는 기존 로컬 전용 방식입니다.
- 상세 수정 및 회귀 검증: [verification-2.3.1.md](verification-2.3.1.md).

## 실제 업로드 파일

- 파일: `release-artifacts/2.3.1-39/lifelink-2.3.1-39-signed.aab` (3,530,425 bytes).
- AAB SHA-256: `048DF503E660375849FFD83EF896E7DFF1BDE3875692BDF60B7E05129939BCD3`.
- 업로드 인증서 SHA-256: `862CB532B1485B1AA97F7731716333C71A4D5A49AEC102D4026BFDB8226110CC`.
- 최종 소스의 서명 CI 산출물을 다운로드하여 로컬 체크섬과 인증서를 확인했습니다. 로컬 unsigned AAB나 이전 중간 커밋의 AAB를 제출하지 않았습니다.
- CI 증빙은 위 Actions 실행의 artifact와 로컬 `release-artifacts/2.3.1-39/signed-ci/`에 보관합니다. 개인 키·비밀번호는 다운로드하거나 이 문서에 포함하지 않았습니다.

## Play Console 처리

- 대상: 기존 **Closed testing — Closed test 1**. Production·다른 트랙·테스터 목록은 변경하지 않았습니다.
- 릴리스: `2.3.1 (39) 권한 설정·필수 알림 개선`, 릴리스 화면 ID 12.
- Play가 AAB를 처리한 결과: 버전 코드 39, 버전 이름 2.3.1, API 24 이상, target SDK 36, ReTrace mapping 포함.
- 이전 2.3.0 (38) 대비 지원 기기 증감 없음.
- 배포 차단 오류 없음. 네이티브 디버그 심볼 미첨부 경고 1개가 남아 있습니다. 난독화 mapping은 포함되어 있으며, 이 경고는 제출을 차단하지 않았습니다.
- 릴리스 저장 후 `Send changes for review`로 제출했습니다. 게시 개요에서 **Changes in review**, **Closed testing - Closed test 1**, 해당 2.3.1 (39) 릴리스를 확인했습니다.
- 제출 직후 Google의 사전 자동 검사가 진행 중입니다. 검사가 성공하면 심사에 전달됩니다. 심사 승인과 실제 테스터 배포 완료는 아직 확인되지 않았습니다.
- 제출 증빙: 로컬 `release-artifacts/2.3.1-39/play-review-submitted.jpg`.

## 승인 후 테스터 확인

이번 작업 시 ADB 연결 기기가 없어 실제 통신사 SMS, 삼성 장기 절전, 소리·진동은 검증하지 않았습니다. 자동 테스트 결과를 실기기 성공률로 해석하지 않습니다.

1. 기존 설치를 삭제하지 않고 Play에서 업데이트하여 보호자 연락처·SIM·12시간 설정·안부 설정이 유지되는지 확인합니다.
2. 신규 온보딩에서 SMS 권한 승인 직후 스위치가 갱신되는지 확인합니다. 다른 권한이 미승인 상태여도 뒤로 이동할 필요가 없어야 합니다.
3. 실행 상태 알림과 발송 완료 알림이 무음인지, 유효 활동 후 사전 경고가 없어지는지 확인합니다. 무활동 사전 경고·최종 발송 실패·모니터링 중단은 유지됩니다.
4. 정기 요청이 필요 없다면 `매일 안부 확인 → 사용 안 함`을 선택합니다. 기본 무활동 감지 문자와 SOS는 유지됩니다.
5. SMS는 등록된 보호자 번호에만 전송합니다. 사용자에게도 실제 SMS가 도착했다면 보호자 목록에 사용자 번호가 포함되어 있는지 먼저 확인합니다. 사용자 피드백만으로 특정 원인이 확정된 것은 아닙니다.
6. 동의한 보호자에게 시험 문자/SOS/무활동 문자를 확인하고 비행기 모드·SIM 변경·권한 해제 시 실패/중단 안내 및 재시도를 확인합니다. 문자 전송 결과는 보호자의 열람이나 사용자 안전 확인을 보장하지 않습니다.

필수 포그라운드 실행 알림은 Android 정책상 숨기지 않고 무음으로 유지합니다. 서버 heartbeat·원격 보호자·결제·광고 기능은 이번 배포에 추가하지 않았습니다.
