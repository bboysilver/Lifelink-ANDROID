# LifeLink Play Store assets

Generated assets are in `store-assets/play`.

## Play Console files

- App icon: `play/app-icon-512.png` (512 x 512)
- Feature graphic: `play/feature-graphic-1024x500.png` (1024 x 500)
- Phone screenshots: `play/phone` (4 images, 1080 x 1920)
- 7-inch tablet screenshots: `play/tablet-7` (2 images, 1920 x 1080)
- 10-inch tablet screenshots: `play/tablet-10` (2 images, 1920 x 1080)

The generated screenshots describe only features present in the current Android app:
local inactivity monitoring, daily check-in, five-second SOS cancellation, device SIM
SMS, and delivery-status logs. They do not claim location tracking, cloud monitoring,
or emergency-service dispatch.

## Listing copy

Short description:

> 일정 시간 활동이 없으면 등록한 보호자에게 자동 SMS를 보내는 안심 앱

Full description:

> 라이프링크는 혼자 지내는 사용자와 가족을 위한 안전 확인 보조 앱입니다.
>
> 한 번 설정하면 휴대전화의 잠금 해제, 반복 움직임, 걸음 등 여러 활동 신호를
> 바탕으로 마지막 활동 시각을 갱신합니다. 설정한 시간 동안 활동이 확인되지
> 않으면 먼저 큰 알림으로 사용자의 안부를 묻고, 응답이 없을 때 등록한
> 보호자에게 기기의 활성 SIM으로 긴급 SMS를 보냅니다.
>
> 주요 기능
>
> - 6~72시간 무활동 안전 확인
> - 매일 정한 시각의 간단한 안부 확인
> - 5초 안에 취소할 수 있는 수동 SOS
> - 최대 3명의 보호자 연락처
> - SMS 요청·발송·전달·재시도 상태 기록
> - 재부팅과 앱 업데이트 후 모니터링 복구
> - 큰 글자와 큰 버튼 중심의 간단한 화면
>
> 라이프링크는 위치 정보를 수집하거나 전송하지 않으며 광고 SDK, 광고 ID,
> Firebase, Twilio 또는 외부 클라우드를 사용하지 않습니다. 보호자 연락처와
> 안전 기록은 기기에만 저장되고 기기 백업에서 제외됩니다.
>
> 이 앱은 의료기기나 공공 긴급 신고 서비스가 아닙니다. 휴대전화 전원 종료,
> 강제 종료, 배터리 방전, 통신 불가 또는 제조사 절전 정책에 따라 알림이
> 지연되거나 중단될 수 있습니다. 긴급 상황에서는 112 또는 119 등 공식
> 긴급 서비스에 직접 연락하세요.

## Regeneration

Run:

```powershell
python tools\generate_store_assets.py
```

The script discovers Malgun Gothic, Apple SD Gothic Neo, or Noto Sans KR/CJK on
Windows, macOS, and Linux. For a custom licensed font, set
`LIFELINK_FONT_REGULAR` and `LIFELINK_FONT_BOLD` to the font file paths before
running it.
The script also refreshes the legacy launcher PNGs under `app/src/main/res/mipmap-*`.
Adaptive icon vectors remain under `app/src/main/res/drawable`.
