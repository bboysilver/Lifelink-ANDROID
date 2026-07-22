# LifeLink Android

LifeLink는 일정 시간 동안 휴대전화 활동이 감지되지 않으면 등록한 보호자(최대 3명)에게 기기 SIM으로 긴급 문자를 보내는 Android 앱입니다.

## 현재 구현

- Foreground Service가 잠금 해제와 반복된 걸음 감지를 강한 활동 신호로 사용합니다. 걸음 센서가 없을 때만 10초 동안 4회 이상 반복된 움직임을 보조 신호로 사용하며, 화면 켜짐이나 한 번의 충격은 활동으로 처리하지 않습니다.
- 마지막 활동 시각과 마감 시각을 기기에 저장해 앱 프로세스가 다시 생성되어도 남은 시간을 복원합니다.
- 재부팅 및 앱 업데이트 후 사용자가 켜 둔 모니터링을 다시 시작합니다.
- 서비스 heartbeat가 끊기면 `정상 모니터링 중` 대신 오류와 복구 버튼을 표시합니다.
- 마감 30분 전 고우선순위 알림에서 `무사합니다`를 누를 수 있습니다.
- 연락처별 문자 상태를 `QUEUED -> SENT -> DELIVERED` 또는 실패 상태로 저장합니다.
- 실제 발송 실패나 콜백 시간 초과 시 5분 간격으로 최대 3회 재시도합니다.
- SMS 지원 여부와 활성 SIM을 확인하고, 멀티 SIM 단말에서는 사용자가 긴급 문자 회선을 직접 선택합니다.
- 초기 설정 후 각 보호자에게 1회 전용 테스트 문자를 보낼 수 있습니다. 테스트 문자는 자동 재시도하지 않으며 60초 쿨다운이 적용됩니다.
- 홈 화면 SOS는 5초 취소 시간을 제공하고, 요청을 기기에 저장한 뒤 보호자 문자 결과를 추적·재시도합니다.
- 매일 9시·12시·18시 중 선택한 시각에 AlarmManager로 독립 안부 확인을 예약하고, 2시간 동안 응답이 없으면 보호자에게 알립니다.
- 활성 SIM 변경과 센서 등록 실패를 즉시 감지해 정상 모니터링 표시를 중단합니다.
- 위치, Firebase, Twilio, 광고, 결제, 외부 백엔드는 사용하지 않습니다.

## 중요한 한계

이 앱은 119, 의료기기, 전문 보안 서비스를 대신하지 않습니다. 휴대전화 전원이 꺼진 경우, 사용자가 앱을 강제 종료한 경우, 제조사 절전 정책이 서비스를 중지한 경우, SIM/통신망이 사용할 수 없는 경우에는 감지 또는 문자 발송이 동작하지 않을 수 있습니다. SMS 전달 확인은 이동통신사와 단말 지원 여부에 따라 제공되지 않을 수 있습니다.

공개 출시 전에는 Google Play의 SMS 권한 정책 승인이 필요하며, 지원 대상 실기기에서 장시간·재부팅·절전·통신 실패 테스트를 완료해야 합니다.

## 개발 빌드

요구 사항은 Android Studio JBR(Java 17)와 Android SDK 36입니다.

```powershell
.\gradlew.bat test lint assembleDebug
```

디버그 APK는 `app/build/outputs/apk/debug/app-debug.apk`에 생성됩니다.

## 서명된 AAB

저장소에 비밀 정보를 커밋하지 마세요. 환경 변수 또는 gitignored `key.properties`를 사용합니다.

```properties
storeFile=C:\\secure\\upload-keystore.jks
storePassword=...
keyAlias=upload
keyPassword=...
```

```powershell
.\gradlew.bat bundleRelease
```

Signed Android release 워크플로를 실행하려면 Actions secret에 ANDROID_UPLOAD_KEYSTORE_BASE64, ANDROID_UPLOAD_STORE_PASSWORD, ANDROID_UPLOAD_KEY_ALIAS, ANDROID_UPLOAD_KEY_PASSWORD를 등록하고 Repository Variable ANDROID_UPLOAD_CERT_SHA256에 업로드 인증서 지문을 설정합니다. 워크플로는 테스트와 lint를 실행하고 AAB 서명·인증서·SHA-256을 검증한 뒤 증빙 artifact로 보관합니다.

릴리스 AAB는 `app/build/outputs/bundle/release/app-release.aab`에 생성됩니다. 앱 ID는 `com.bboysilver.lifelink`, 현재 버전은 `2.2.1 (22)`입니다. 기존 Play 앱에 올릴 때는 같은 업로드 인증서와 Play Console의 최신 versionCode보다 큰 번호를 사용해야 합니다.

## 개인정보

보호자 이름·전화번호, 기기 별칭, 선택한 SMS SIM, 모니터링과 독립적으로 예약되는 매일 안부 확인 설정·응답 상태, 최근 활동 시각, 문자 결과 기록은 기기 내부에만 저장되고 Android 백업에서 제외됩니다. 긴급 시 기기 별칭과 배터리 상태가 등록 연락처로 SMS 전송됩니다. 위치나 외부 클라우드는 사용하지 않습니다.

공개 개인정보처리방침 원본은 `docs/privacy-policy.html`이며 앱의 `기록 > 개인정보처리방침`에서 열 수 있습니다.
