# LifeLink 2.3.6 (44) 검증 기록

기록일: 2026-10-18. 기존 R1 현장 점검 기능과 lint 정리를 포함하는 Closed test 1 업데이트 후보입니다. 공개 출시 준비 완료나 실기기 신뢰성 인증을 의미하지 않습니다.

## 경고 77개 분류와 처리

| 기존 lint 항목 | 수 | 처리 |
| --- | ---: | --- |
| UseKtx | 51 | SharedPreferences와 URI의 KTX 사용. 실제 commit 결과를 반환하거나 하나의 editor를 조건부로 구성하는 API는 사유를 주석으로 남기고 해당 함수만 예외 처리 |
| GradleDependency | 12 | 호환성 확인 후 라이브러리 업데이트 |
| NewerVersionAvailable | 5 | Kotlin/Compose compiler, KSP, Robolectric 등 업데이트 |
| AndroidGradlePluginVersion | 1 | AGP 9.1.1 의도적 고정, 버전 선언 한 곳만 예외 처리 |
| UnusedResources | 7 | 참조가 없는 초기 색상 리소스 제거 |
| UnnecessaryRequiredFeature | 1 | 핵심 SIM SMS에 필요한 telephony required=true 유지, 해당 uses-feature만 예외 처리 |

추가 Hint 1개(AutoboxingStateCreation)는 mutableIntStateOf로 처리했습니다. 이 항목들은 77개의 실행 버그를 뜻하지 않습니다. 전체 lint를 끄거나 baseline으로 숨기지 않았으며, 유지한 예외는 동작 보존과 검증된 빌드 조합을 위한 좁은 범위의 판단입니다.

## 보존한 동작과 호환성

- SMS 발송기, 콜백 결과, 연락처별 재시도/중복 방지 정책, 사건 ID, 타이머 계산, 부팅 복구, 센서 판정은 변경하지 않았습니다.
- SharedPreferences 키/값/잠금/쓰기 순서와 동기 commit 또는 비동기 apply 여부를 유지했습니다. Boolean commit 반환값을 Unit이나 임의 성공값으로 대체하지 않았습니다. 새 회귀 검사는 저장소 재생성, 동기/비동기 쓰기, commit 실패를 다룹니다.
- applicationId com.bboysilver.lifelink, minSdk 24(Android 7), targetSdk 36, JVM target 17, Room 스키마 v3와 서명을 유지합니다. compile SDK만 37.0으로 올렸습니다.
- 빌드 조합: AGP 9.1.1 / Gradle 9.3.1 / Java 21 / Kotlin 및 Compose compiler 2.4.21. 설치된 Studio와 검증된 KGP 호환 범위를 벗어나는 최신 AGP로 무조건 변경하지 않았습니다.
- Core 1.19.1, Lifecycle 2.11.0, Compose BOM 2026.09.00, KSP 2.3.12, Room 2.8.5, Coroutines 1.11.0, WorkManager 2.12.0, Robolectric 4.17을 사용합니다. CI도 SDK 37.0을 명시 설치합니다.
- 새 권한/네트워크/광고/위치/결제/서버를 추가하지 않았습니다. 최종 release Manifest에 AD_ID, INTERNET, 위치 권한이 없고 telephony required=true임을 확인했습니다.
- R1은 로컬 현장 점검과 명시적 미리보기/동의 후 수동 Android 공유창만 제공합니다. R2 서버/기관 관제는 설계 문서와 비활성 API 계약뿐이며 배포하지 않습니다.

## 실행한 검사

Windows / Android Studio JBR 21.0.10에서 다음 전체 명령을 실행했습니다.

```powershell
.\gradlew.bat test lint assembleDebug bundleRelease --console=plain
```

- BUILD SUCCESSFUL (52초).
- 테스트 284개: 실패 0, 오류 0, 건너뜀 0. 기존 R1 276개와 새 저장 방식 회귀 검사 8개(API 24/36)입니다.
- lint: 오류 0, 경고 0, Hint 0. 텍스트 보고서 `No issues found.`, XML issue 요소 0개.
- debug APK와 release AAB 생성. 로컬 release는 서명 설정이 없으므로 **제출용이 아닙니다**. 제출은 동일 main 커밋의 기존 Signed Android release CI에서 서명/인증서/체크섬 검사 후 생성한 파일만 사용합니다.
- 독립 읽기 전용 diff 검토에서 SharedPreferences 의미 변경, URI/PendingIntent 변경, 새 권한 등 구체적 회귀는 발견되지 않았습니다.
- `git diff --check` 통과. R2 계약 검사는 별도 스크립트로 재확인합니다.

업데이트 과정에서 Robolectric의 제거된 SensorEvent 생성 API와 Java 21의 jdk.internal.access 접근 제한 때문에 중간 검사에 실패했습니다. SensorEventBuilder와 공식 권고의 테스트 JVM 전용 add-opens로 수정한 후 위 전체 검사를 다시 통과했습니다. Android 앱의 보안 설정을 완화한 것이 아닙니다. 일부 테스트 SDK의 deprecation 컴파일 안내는 남으며, lint 경고 0과 모든 종류의 컴파일 안내 0은 구분합니다.

## 정책·실기기 한계

사용자가 기존 APK에서 SIM 기기의 시험 문자/무활동 SMS 수신을 확인했습니다. 최신 2.3.6의 실기기 시험을 대신하는 증거로 취급하지 않습니다. Galaxy A24 장기 절전·재부팅·통신 실패·멀티 SIM 시험과 기존 데이터 보존 확인은 승인된 동일 서명 업데이트로 계속해야 합니다. lint 정리가 OEM 백그라운드 지연이나 보호자의 실제 열람을 보증하지 않습니다.

R1 수동 공유는 기존 사용자 이름/번호/문자 원문 등을 제외한 허용 목록과 개별 동의를 사용합니다. Google Data safety의 이용자 주도 공유 예외와 실제 앱 고지를 대조했습니다. 앱의 네트워크 수집은 없으며, 자동 보호자 SMS 및 향후 클라우드는 수동 공유와 별개로 판단해야 합니다. 공개 개인정보처리방침의 R1 설명을 main Pages에 배포하고 실제 URL 확인 후 Play 제출합니다. Google의 권한 심사 승인을 보장하지 않습니다.

## 근거

- [AGP 9.1 호환성](https://developer.android.com/build/releases/agp-9-1-0-release-notes)
- [Kotlin Gradle 플러그인 호환성](https://kotlinlang.org/docs/gradle-configure-project.html)
- [AGP 내장 Kotlin 버전 재정의](https://developer.android.com/build/releases/agp-9-0-0-release-notes#runtime-dependency-on-kotlin-gradle-plugin)
- [Robolectric Java 17 이상 설정](https://robolectric.org/getting-started/#running-with-java-17-and-higher)
- [KSP 2.3.12](https://github.com/google/ksp/releases/tag/2.3.12)
- [Google Play Data safety](https://support.google.com/googleplay/android-developer/answer/10787469)

실제 업로드·심사 상태와 서명 파일 증거는 별도 `deployment-2.3.6.md`에 기록합니다.
