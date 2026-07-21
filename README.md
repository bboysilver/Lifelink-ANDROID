# LifeLink Android

LifeLink는 일정 시간 동안 스마트폰 활동이 감지되지 않으면 등록된 보호자(최대 3명)에게 기기 SIM으로 긴급 SMS를 보내는 Android 앱입니다.

## 현재 구현

- Foreground Service가 화면이 꺼진 동안에도 가속도, 화면 켜짐, 잠금 해제, 충전 연결 변화를 감지합니다.
- 마지막 활동 시각과 마감 시각을 기기에 영속 저장하여 앱 프로세스 재생성 후에도 남은 시간을 복원합니다.
- 재부팅 및 앱 업데이트 후 활성 상태의 모니터링을 다시 시작합니다.
- 마감 30분 전 고우선순위 알림에서 `무사합니다`를 누를 수 있습니다.
- SMS 발송/전달 결과 콜백을 기록하고, 같은 사건과 연락처에 대한 중복 발송 요청을 차단합니다.
- 위치나 배터리를 읽지 못하면 임의 값 대신 `확인 불가`를 보냅니다.
- Firebase, Twilio, 광고, 결제, 외부 백엔드는 사용하지 않습니다.

## 중요한 한계

이 앱은 119, 의료기기, 전문 보안 서비스를 대신하지 않습니다. 휴대전화 전원이 꺼진 경우, 사용자가 앱을 강제 종료한 경우, 제조사 절전 정책이 서비스를 중지한 경우, SIM/통신망이 사용할 수 없는 경우에는 감지 또는 SMS가 동작하지 않을 수 있습니다. SMS 전달 확인은 이동통신사와 단말 지원 여부에 따라 제공되지 않을 수 있습니다.

공개 출시 전에는 Google Play의 SMS 권한 정책 승인이 필요하며, 지원 대상 실기기에서 장시간/재부팅/절전/통신 실패 테스트를 완료해야 합니다.

## 개발 빌드

요구 사항: Android Studio JBR(Java 17)와 Android SDK 36.

```powershell
.\gradlew.bat test lint assembleDebug
```

디버그 APK: `app/build/outputs/apk/debug/app-debug.apk`

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

릴리스 AAB: `app/build/outputs/bundle/release/app-release.aab`

앱 ID는 `com.bboysilver.lifelink`, 현재 버전은 `2.1.0 (18)`입니다. 기존 Play 앱에 올릴 때는 반드시 같은 업로드 인증서로 서명하고 Play Console의 최신 versionCode보다 큰지 확인하세요.

## 개인정보

보호자 이름/전화번호와 이벤트 기록은 Room 데이터베이스에 기기 내부 저장되며 Android 백업에서 제외됩니다. 긴급 시 현재 위치(권한을 허용하고 조회에 성공한 경우), 배터리 상태, 고정 안내 문구가 등록 연락처로 SMS 전송됩니다. 앱 내 개인정보 안내와 Play Console Data Safety 답변은 이 동작과 동일하게 유지해야 합니다.
