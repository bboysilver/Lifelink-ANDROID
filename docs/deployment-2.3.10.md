# 2.3.10 (48) Closed testing 업로드 기록 — 2026-10-24

## 승인과 소스

- G0/G1 구현 후 사용자가 GitHub 푸시와 Play 업로드를 명시적으로 승인했습니다. 기존 Secret을 사용한 서명 빌드만 실행했으며 Secret 조회·재입력·변경은 없습니다.
- 검토 브랜치: `codex/g1-field-inspection-snapshot`. 구현 커밋 `603551e`, 릴리스 소스 `3d57f596a2b2982756575c4c4873ab454b609c34`.
- [Draft PR #27](https://github.com/bboysilver/Lifelink-ANDROID/pull/27). main 병합·자동 병합·프로덕션 전환·심사 최종 제출은 하지 않았습니다.
- 버전만2.3.10(48)로 올렸습니다. min24/target36/compile37.0, 기존 Manifest·Room·안전 엔진·권한은 유지합니다. 광고·INTERNET·클라우드·운영 결제는 추가하지 않았습니다.

## 실제 검증

| 항목 | 결과 |
| --- | --- |
| 로컬 `test lint assembleDebug bundleRelease` | SUCCESS /1분48초 /107 tasks(50 실행·57 up-to-date).357 tests/53 suites, failures0/errors0/skipped0, lint No issues found |
| [서명 CI](https://github.com/bboysilver/Lifelink-ANDROID/actions/runs/40075344470) | SUCCESS /job4분29초. 소스는 위3d57f59. 다운로드한 XML357 tests/53 suites, failures0/errors0/skipped0, lint No issues found |
| [PR 검증 CI](https://github.com/bboysilver/Lifelink-ANDROID/actions/runs/40075487644) | 소스3d57f59에서 SUCCESS. 테스트·lint·debug APK 검사 |
| 최종 Manifest·서명 | 기존 CI의 금지 권한/지원 기기 조건 검사, `jarsigner -verify -strict` 및 예상 인증서 대조 성공 |
| 실제 단말·SMS 수신·밤샘 Doze·삼성 절전·TalkBack | NOT RUN. 이 자동화 결과로 PASS 처리하지 않음 |

서명 검증을 ‘경고 없음’으로 표시하지 않습니다. CI/로컬 jarsigner는 `jar verified`와 함께 POSIX attributes 및 JarFile/JarInputStream의 manifest/entry 순서 관련 경고를 출력했습니다. 로컬 검증에는 별도 truststore를 넣지 않아 self-signed/인증서 체인/타임스탬프 경고도 있었습니다. CI의 기존 keystore 기반 strict 검증과 인증서 지문 대조는 성공했고, 실제 Play 처리에서도 차단 오류는 없었습니다. 이 경고를 없애려고 AAB를 재압축하거나 개인 키를 다운로드하지 않았습니다.

CI의 Gradle 업데이트 권고와 ubuntu-latest 이미지 변경 안내도 남아 있습니다. 이 릴리스에서 검증된 도구 버전을 무리하게 교체하지 않았습니다.

## 업로드한 실제 AAB

- 위치: `release-artifacts/2.3.10-48/signed-ci/outputs/bundle/release/app-release.aab` (Git 제외).
- 크기: **4,194,042 bytes**.
- SHA-256: `D78A6DF82AEC52A803F2526A770F421CA22A2487C17EE562A2A90A981C0B5599`.
- 업로드 인증서 SHA-256: `862CB532B1485B1AA97F7731716333C71A4D5A49AEC102D4026BFDB8226110CC`.
- 실제 파일 hash가 CI checksum과 일치하고 keytool로 읽은 인증서도 기존 업로드 인증서와 일치했습니다. 이전2.3.9(47) unsigned 개발 AAB나 로컬 unsigned2.3.10은 올리지 않았습니다.

## Console에서 확인한 내용

1. 작업 시작 시 Closed test1의2.3.9(47)은 `Available to selected testers`,10월23일 배포 상태였습니다.
2. 동일 Closed test1에 `2.3.10 (48) 현장 점검·문자 상태 안내 개선`을 만들고 위 서명 AAB와 `play-release-notes-2.3.10.txt`의 한국어 안내를 업로드했습니다. 이전47은 새 릴리스에 포함하지 않았습니다.
3. Play가48(2.3.10),API24+,target36,ABI4/화면유형4,ReTrace mapping,Enhanced를 인식했습니다. 지원 감소0/추가0, 카탈로그 Phone12,113/Tablet2,743/TV1(현재 시점 수치)입니다. 카탈로그 지원은 실제 SMS 가능 여부를 보장하지 않으며 런타임 SIM 검사는 유지합니다.
4. 검토 화면에 차단 오류0, 비차단 경고1: 기존과 같은 native debug symbols 미제공 권고입니다. 공급되지 않은 심볼을 가짜로 올리거나 SDK를 제거하지 않았습니다.
5. Save 후 `Your change has been saved` 안내를 확인했습니다. 게시 개요에서2.3.10 한 건과 `Changes not yet submitted for review` / `Submit 1 change for review`를 확인했습니다. 당시 quick checks는 최대13분 안내로 진행 중이며 완료/통과를 확인한 것은 아닙니다. **업로드·저장 완료이지 심사 제출·승인·테스터 업데이트 완료가 아닙니다.**
6. 테스터·국가·다른 트랙·managed publishing·정책 선언·보안 설정은 변경하지 않았습니다. 현재 개인정보처리방침의 기기 내 기술 점검·임시 화면 상태·개별 동의 공유 설명과 비교했고, 공유는 기존 제외 항목과 명시적 미리보기/동의 경계를 유지합니다. 공개 방침과 Console 선언을 자동 변경하거나 별도 정책 승인으로 간주하지 않았습니다.

완료 화면: `release-artifacts/2.3.10-48/play-upload-saved.jpg` (Git 제외). Console 탭은 사용자가 다음 심사 제출을 확인할 수 있게 열어 두었습니다.

## 그다음 순서

1. 게시 개요에서2.3.10 한 건을 확인하고 Closed testing 심사를 제출합니다. 승인/테스터 배포 전에는2.3.9가 계속 제공될 수 있습니다.
2. 배포 후 기존 Play 설치본을 **삭제하지 않고 업데이트**합니다. 앱에서2.3.10(48)을 확인하고 보호자·6/12시간 설정·사건 기록 보존을 확인합니다. 디버그 APK를 Play 설치본에 덮어쓰지 않습니다.
3. [G1 기기별 시험표](roadmap-g1-device-test-plan.md)로 A24/PIN·반복 걸음, 실제 무활동6/12시간 수신, 밤샘 화면OFF/Doze, 재부팅, SOS5초 취소, 점검 snapshot/공유 취소/200% 글꼴을 확인합니다. 시험 수신자에게 사전 동의와 SMS 비용을 설명합니다. 마지막 활동→마감→SENT→실제 수신 시각을 분리해 내부 시험표에 기록합니다. 점검을 위해 앱을 여는 행위도 활동에 반영될 수 있습니다.
4. 현재 Dashboard는 **12명·연속6일 참여**, production 신청 버튼 비활성화입니다. 참여가 계속 유지되면 최소 약8일이 더 필요합니다. 이메일 목록 인원은 실제 opt-in/사용/시험 증빙과 다르므로 이탈 여부와 실제 피드백을 확인합니다.
5. [Google의 공식 요건](https://support.google.com/googleplay/android-developer/answer/14151465)에 맞춰 최소12명의14일 연속 참여를 충족한 뒤 실제 모집·사용·발견 결함·수정·준비 상태에 근거해 Production access를 신청합니다. 테스터 수/기간 충족만으로 앱의 신뢰성이 입증되지는 않습니다.
6. 공개 전 SMS·FGS·건강 앱·Data safety의 실제 신고/심사 상태를 확인합니다. [물리적 안전/긴급 SMS 예외](https://support.google.com/googleplay/android-developer/answer/10208820)는 자동 승인이나 현장 안전 인증이 아닙니다. 설명되지 않는 누락/중복·정상 오표시·데이터 손실이 있으면 공개 확대를 중단합니다.

G0의 실기기/정책 게이트는 여전히 BLOCKED(증빙 필요)입니다. Draft PR 리뷰·main 병합은 별도 승인 후 진행하며, G2/Family/Care/구독 개발·운영은 NOT STARTED입니다.
