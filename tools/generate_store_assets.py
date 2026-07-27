from pathlib import Path
import os

from PIL import Image, ImageDraw, ImageFilter, ImageFont


ROOT = Path(__file__).resolve().parents[1]
SOURCE_DIR = ROOT / "store-assets" / "source"
OUTPUT_DIR = ROOT / "store-assets" / "play"

NAVY = "#102A43"
TEAL = "#1F8A80"
CORAL = "#F0645A"
IVORY = "#F6F4EF"
MIST = "#DDF1EE"
SLATE = "#526575"
WHITE = "#FFFFFF"
PALE_CORAL = "#FFE4E0"
LINE = "#DCE3E8"

FONT_CANDIDATES = {
    False: (
        "C:/Windows/Fonts/malgun.ttf",
        "/System/Library/Fonts/AppleSDGothicNeo.ttc",
        "/Library/Fonts/NotoSansKR-Regular.otf",
        "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
        "/usr/share/fonts/truetype/noto/NotoSansKR-Regular.ttf",
    ),
    True: (
        "C:/Windows/Fonts/malgunbd.ttf",
        "/System/Library/Fonts/AppleSDGothicNeo.ttc",
        "/Library/Fonts/NotoSansKR-Bold.otf",
        "/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc",
        "/usr/share/fonts/truetype/noto/NotoSansKR-Bold.ttf",
    ),
}


def resolve_font_path(bold: bool) -> Path:
    override_name = "LIFELINK_FONT_BOLD" if bold else "LIFELINK_FONT_REGULAR"
    override = os.environ.get(override_name)
    candidates = ([override] if override else []) + list(FONT_CANDIDATES[bold])
    for candidate in candidates:
        if candidate and Path(candidate).is_file():
            return Path(candidate)
    raise FileNotFoundError(
        f"Korean font not found. Install Noto Sans KR/CJK or set {override_name}."
    )


def font(size: int, bold: bool = False) -> ImageFont.FreeTypeFont:
    return ImageFont.truetype(str(resolve_font_path(bold)), size)


def center_text(
    draw: ImageDraw.ImageDraw,
    box: tuple[int, int, int, int],
    text: str,
    text_font: ImageFont.FreeTypeFont,
    fill: str,
) -> None:
    left, top, right, bottom = box
    bbox = draw.textbbox((0, 0), text, font=text_font)
    x = left + (right - left - (bbox[2] - bbox[0])) / 2
    y = top + (bottom - top - (bbox[3] - bbox[1])) / 2 - bbox[1]
    draw.text((x, y), text, font=text_font, fill=fill)


def rounded_panel(
    image: Image.Image,
    box: tuple[int, int, int, int],
    radius: int,
    fill: str,
    shadow: bool = True,
) -> None:
    if shadow:
        shadow_layer = Image.new("RGBA", image.size, (0, 0, 0, 0))
        shadow_draw = ImageDraw.Draw(shadow_layer)
        offset_box = (box[0], box[1] + 12, box[2], box[3] + 12)
        shadow_draw.rounded_rectangle(offset_box, radius=radius, fill=(16, 42, 67, 38))
        shadow_layer = shadow_layer.filter(ImageFilter.GaussianBlur(18))
        image.alpha_composite(shadow_layer)
    ImageDraw.Draw(image).rounded_rectangle(box, radius=radius, fill=fill)


def draw_icon_mark(draw: ImageDraw.ImageDraw, box: tuple[int, int, int, int]) -> None:
    left, top, right, bottom = box
    size = min(right - left, bottom - top)
    cx = left + (right - left) / 2
    cy = top + (bottom - top) / 2
    arc_box = (
        int(cx - size * 0.38),
        int(cy - size * 0.38),
        int(cx + size * 0.38),
        int(cy + size * 0.38),
    )
    width = max(3, int(size * 0.13))
    draw.arc(arc_box, start=195, end=345, fill=TEAL, width=width)
    draw.arc(arc_box, start=15, end=165, fill=CORAL, width=width)

    shield = [
        (cx, cy - size * 0.24),
        (cx + size * 0.20, cy - size * 0.11),
        (cx + size * 0.18, cy + size * 0.17),
        (cx, cy + size * 0.30),
        (cx - size * 0.18, cy + size * 0.17),
        (cx - size * 0.20, cy - size * 0.11),
    ]
    draw.polygon(shield, fill=IVORY)

    heart_center_y = cy + size * 0.01
    heart_radius = size * 0.075
    draw.ellipse(
        (
            cx - heart_radius * 1.65,
            heart_center_y - heart_radius,
            cx - heart_radius * 0.05,
            heart_center_y + heart_radius * 0.65,
        ),
        fill=CORAL,
    )
    draw.ellipse(
        (
            cx + heart_radius * 0.05,
            heart_center_y - heart_radius,
            cx + heart_radius * 1.65,
            heart_center_y + heart_radius * 0.65,
        ),
        fill=CORAL,
    )
    draw.polygon(
        [
            (cx - heart_radius * 1.55, heart_center_y),
            (cx + heart_radius * 1.55, heart_center_y),
            (cx, heart_center_y + heart_radius * 2.0),
        ],
        fill=CORAL,
    )
    pulse = [
        (cx - size * 0.10, cy + size * 0.02),
        (cx - size * 0.04, cy + size * 0.02),
        (cx, cy - size * 0.04),
        (cx + size * 0.04, cy + size * 0.09),
        (cx + size * 0.08, cy + size * 0.02),
        (cx + size * 0.12, cy + size * 0.02),
    ]
    draw.line(pulse, fill=WHITE, width=max(2, int(size * 0.025)), joint="curve")


def create_store_icon() -> Image.Image:
    scale = 4
    image = Image.new("RGB", (512 * scale, 512 * scale), NAVY)
    draw = ImageDraw.Draw(image)
    draw_icon_mark(draw, (40 * scale, 40 * scale, 472 * scale, 472 * scale))
    return image.resize((512, 512), Image.Resampling.LANCZOS)


def draw_brand_pill(image: Image.Image, x: int, y: int) -> None:
    draw = ImageDraw.Draw(image)
    pill = (x, y, x + 292, y + 84)
    draw.rounded_rectangle(pill, radius=42, fill=WHITE)
    draw.rounded_rectangle((x + 10, y + 10, x + 74, y + 74), radius=20, fill=NAVY)
    draw_icon_mark(draw, (x + 13, y + 13, x + 71, y + 71))
    draw.text((x + 92, y + 19), "라이프링크", font=font(31, bold=True), fill=NAVY)


def draw_status_bar(draw: ImageDraw.ImageDraw, box: tuple[int, int, int, int]) -> None:
    left, top, right, _ = box
    draw.text((left + 34, top + 20), "9:41", font=font(20, bold=True), fill=NAVY)
    draw.rounded_rectangle((right - 88, top + 26, right - 38, top + 46), radius=7, outline=NAVY, width=3)
    draw.rectangle((right - 34, top + 32, right - 28, top + 40), fill=NAVY)
    draw.rounded_rectangle((right - 83, top + 31, right - 45, top + 41), radius=4, fill=TEAL)


def draw_bottom_nav(draw: ImageDraw.ImageDraw, screen: tuple[int, int, int, int], selected: int) -> None:
    left, _, right, bottom = screen
    top = bottom - 118
    draw.line((left + 20, top, right - 20, top), fill=LINE, width=2)
    labels = ["안심", "연락처", "기록"]
    centers = [left + (right - left) * 0.18, left + (right - left) * 0.50, left + (right - left) * 0.82]
    for index, (label, center) in enumerate(zip(labels, centers)):
        color = TEAL if index == selected else SLATE
        if index == 0:
            draw.rounded_rectangle((center - 18, top + 22, center + 18, top + 52), radius=10, outline=color, width=4)
        elif index == 1:
            draw.ellipse((center - 15, top + 20, center + 15, top + 50), outline=color, width=4)
        else:
            for row in range(3):
                y = top + 23 + row * 13
                draw.line((center - 17, y, center + 17, y), fill=color, width=4)
        center_text(draw, (int(center - 65), top + 58, int(center + 65), bottom - 8), label, font(21, bold=index == selected), color)


def draw_dashboard(draw: ImageDraw.ImageDraw, screen: tuple[int, int, int, int]) -> None:
    left, top, right, bottom = screen
    x1, x2 = left + 42, right - 42
    y = top + 88
    draw.text((x1, y), "라이프링크", font=font(33, bold=True), fill=NAVY)
    draw.ellipse((x2 - 32, y, x2, y + 32), fill=TEAL)
    y += 72
    draw.rounded_rectangle((x1, y, x2, y + 285), radius=32, fill=MIST)
    center_text(draw, (x1, y + 26, x2, y + 76), "정상 모니터링 중", font(28, bold=True), NAVY)
    center_text(draw, (x1, y + 82, x2, y + 118), "다음 안전 확인까지", font(20), SLATE)
    center_text(draw, (x1, y + 115, x2, y + 200), "11:42:18", font(55, bold=True), NAVY)
    center_text(draw, (x1, y + 205, x2, y + 252), "마지막 활동: 방금 전", font(21), SLATE)
    y += 315
    draw.rounded_rectangle((x1, y, x2, y + 96), radius=28, fill=TEAL)
    center_text(draw, (x1, y, x2, y + 96), "무사합니다", font(30, bold=True), WHITE)
    y += 116
    draw.rounded_rectangle((x1, y, x2, y + 96), radius=28, fill=CORAL)
    center_text(draw, (x1, y, x2, y + 96), "SOS · 보호자에게 도움 요청", font(25, bold=True), WHITE)
    y += 126
    draw.rounded_rectangle((x1, y, x2, y + 190), radius=28, fill=WHITE, outline=LINE, width=2)
    draw.text((x1 + 26, y + 24), "매일 안부 확인", font=font(26, bold=True), fill=NAVY)
    draw.text((x1 + 26, y + 70), "오늘 오전 9시에 확인합니다.", font=font(20), fill=SLATE)
    draw.rounded_rectangle((x1 + 26, y + 112, x2 - 26, y + 164), radius=20, fill=IVORY)
    center_text(draw, (x1 + 26, y + 112, x2 - 26, y + 164), "안부 확인 시각을 기다리는 중", font(19, bold=True), SLATE)
    draw_bottom_nav(draw, screen, selected=0)


def draw_checkin(draw: ImageDraw.ImageDraw, screen: tuple[int, int, int, int]) -> None:
    left, top, right, bottom = screen
    draw_dashboard(draw, screen)
    dim_top = top + 70
    draw.rounded_rectangle((left + 12, dim_top, right - 12, bottom - 120), radius=28, fill=(16, 42, 67, 56))
    panel = (left + 62, top + 300, right - 62, top + 890)
    draw.rounded_rectangle(panel, radius=38, fill=WHITE)
    cx = (panel[0] + panel[2]) // 2
    draw.ellipse((cx - 52, panel[1] + 52, cx + 52, panel[1] + 156), fill=MIST)
    draw.line((cx - 22, panel[1] + 106, cx - 2, panel[1] + 126, cx + 30, panel[1] + 86), fill=TEAL, width=12, joint="curve")
    center_text(draw, (panel[0] + 20, panel[1] + 180, panel[2] - 20, panel[1] + 250), "오늘도 괜찮으신가요?", font(34, bold=True), NAVY)
    center_text(draw, (panel[0] + 45, panel[1] + 252, panel[2] - 45, panel[1] + 325), "큰 버튼 하나로 오늘의 안부를 알려 주세요.", font(21), SLATE)
    draw.rounded_rectangle((panel[0] + 38, panel[1] + 350, panel[2] - 38, panel[1] + 440), radius=28, fill=TEAL)
    center_text(draw, (panel[0] + 38, panel[1] + 350, panel[2] - 38, panel[1] + 440), "괜찮아요", font(30, bold=True), WHITE)
    draw.rounded_rectangle((panel[0] + 38, panel[1] + 462, panel[2] - 38, panel[1] + 535), radius=24, fill=PALE_CORAL)
    center_text(draw, (panel[0] + 38, panel[1] + 462, panel[2] - 38, panel[1] + 535), "도움이 필요해요", font(25, bold=True), CORAL)


def draw_sos(draw: ImageDraw.ImageDraw, screen: tuple[int, int, int, int]) -> None:
    left, top, right, bottom = screen
    draw_dashboard(draw, screen)
    draw.rounded_rectangle((left + 12, top + 70, right - 12, bottom - 120), radius=28, fill=(16, 42, 67, 56))
    panel = (left + 62, top + 300, right - 62, top + 820)
    draw.rounded_rectangle(panel, radius=38, fill=WHITE)
    cx = (panel[0] + panel[2]) // 2
    draw.ellipse((cx - 58, panel[1] + 54, cx + 58, panel[1] + 170), fill=PALE_CORAL)
    center_text(draw, (cx - 58, panel[1] + 54, cx + 58, panel[1] + 170), "5", font(62, bold=True), CORAL)
    center_text(draw, (panel[0] + 20, panel[1] + 190, panel[2] - 20, panel[1] + 260), "SOS 전송까지 5초", font(36, bold=True), NAVY)
    center_text(draw, (panel[0] + 35, panel[1] + 265, panel[2] - 35, panel[1] + 350), "취소하지 않으면 등록한 보호자에게\n도움 요청 문자를 보냅니다.", font(21), SLATE)
    draw.rounded_rectangle((panel[0] + 38, panel[1] + 380, panel[2] - 38, panel[1] + 470), radius=28, fill=NAVY)
    center_text(draw, (panel[0] + 38, panel[1] + 380, panel[2] - 38, panel[1] + 470), "취소", font(30, bold=True), WHITE)


def draw_logs(draw: ImageDraw.ImageDraw, screen: tuple[int, int, int, int]) -> None:
    left, top, right, bottom = screen
    x1, x2 = left + 42, right - 42
    y = top + 88
    draw.text((x1, y), "발송 및 동작 기록", font=font(31, bold=True), fill=NAVY)
    draw.text((x1, y + 48), "문자 결과와 안전 확인 기록을 한눈에 봅니다.", font=font(19), fill=SLATE)
    y += 118
    draw.rounded_rectangle((x1, y, x2, y + 135), radius=28, fill=MIST)
    draw.text((x1 + 25, y + 22), "현재 전송 방식", font=font(23, bold=True), fill=NAVY)
    draw.text((x1 + 25, y + 62), "선택한 활성 SIM을 통한 자동 문자", font=font(20), fill=NAVY)
    draw.text((x1 + 25, y + 96), "위치 정보는 수집하거나 전송하지 않습니다.", font=font(17), fill=SLATE)
    events = [
        ("보호자 전달 확인", "오늘 09:42", TEAL, "긴급 문자가 보호자 기기에 전달되었습니다."),
        ("오늘의 안부 확인 완료", "오늘 09:03", NAVY, "사용자가 '괜찮아요'로 응답했습니다."),
        ("활동 확인", "오늘 08:17", TEAL, "잠금 해제와 반복 움직임을 확인했습니다."),
    ]
    y += 165
    for title, time, accent, detail in events:
        draw.rounded_rectangle((x1, y, x2, y + 178), radius=28, fill=WHITE, outline=LINE, width=2)
        draw.ellipse((x1 + 24, y + 28, x1 + 54, y + 58), fill=accent)
        draw.text((x1 + 72, y + 22), title, font=font(24, bold=True), fill=NAVY)
        draw.text((x1 + 72, y + 62), time, font=font(18), fill=SLATE)
        draw.text((x1 + 24, y + 112), detail, font=font(18), fill=SLATE)
        y += 198
    draw_bottom_nav(draw, screen, selected=2)


def draw_phone_mock(image: Image.Image, box: tuple[int, int, int, int], screen_name: str) -> None:
    outer = box
    rounded_panel(image, outer, radius=70, fill=NAVY, shadow=True)
    screen = (outer[0] + 20, outer[1] + 20, outer[2] - 20, outer[3] - 20)
    ImageDraw.Draw(image).rounded_rectangle(screen, radius=54, fill=IVORY)
    draw = ImageDraw.Draw(image)
    draw.rounded_rectangle(
        (
            (outer[0] + outer[2]) // 2 - 84,
            outer[1] + 34,
            (outer[0] + outer[2]) // 2 + 84,
            outer[1] + 62,
        ),
        radius=14,
        fill=NAVY,
    )
    draw_status_bar(draw, screen)
    if screen_name == "dashboard":
        draw_dashboard(draw, screen)
    elif screen_name == "checkin":
        draw_checkin(draw, screen)
    elif screen_name == "sos":
        draw_sos(draw, screen)
    elif screen_name == "logs":
        draw_logs(draw, screen)
    else:
        raise ValueError(f"Unknown screen: {screen_name}")


PHONE_SPECS = [
    (
        "01-monitoring.png",
        "한 번 설정하면,\n안심 확인은 자동으로",
        "활동이 없을 때 등록한 보호자에게 기기 SIM으로 알립니다.",
        "dashboard",
        IVORY,
    ),
    (
        "02-daily-check-in.png",
        "매일 한 번,\n큰 버튼으로 안부 확인",
        "정해진 시각에 ‘괜찮아요’ 한 번이면 충분합니다.",
        "checkin",
        "#EDF7F5",
    ),
    (
        "03-sos.png",
        "도움이 필요할 땐,\n5초 안에 SOS",
        "실수로 눌렀다면 전송 전 바로 취소할 수 있습니다.",
        "sos",
        "#FFF2EF",
    ),
    (
        "04-delivery-log.png",
        "발송 결과까지,\n기기에서 또렷하게",
        "요청·발송·전달 상태와 재시도 기록을 확인합니다.",
        "logs",
        "#EEF2F5",
    ),
]


def create_phone_screenshot(
    headline: str,
    subtitle: str,
    screen_name: str,
    background: str,
) -> Image.Image:
    image = Image.new("RGBA", (1080, 1920), background)
    draw_brand_pill(image, 82, 72)
    draw = ImageDraw.Draw(image)
    draw.multiline_text(
        (82, 185),
        headline,
        font=font(66, bold=True),
        fill=NAVY,
        spacing=8,
    )
    draw.multiline_text(
        (82, 355),
        subtitle,
        font=font(29),
        fill=SLATE,
        spacing=6,
    )
    draw_phone_mock(image, (124, 500, 956, 1840), screen_name)
    return image.convert("RGB")


def create_tablet_screenshot(
    headline: str,
    subtitle: str,
    screen_name: str,
    background: str,
) -> Image.Image:
    image = Image.new("RGBA", (1920, 1080), background)
    draw_brand_pill(image, 100, 88)
    draw = ImageDraw.Draw(image)
    draw.multiline_text((100, 235), headline, font=font(64, bold=True), fill=NAVY, spacing=10)
    draw.multiline_text((100, 430), subtitle, font=font(29), fill=SLATE, spacing=8)
    draw.rounded_rectangle((100, 630, 760, 812), radius=40, fill=NAVY)
    center_text(draw, (100, 630, 760, 812), "설정 후에는 자동으로\n안전 확인을 이어갑니다", font(29, bold=True), WHITE)
    phone_source = create_phone_screenshot(headline, subtitle, screen_name, background).convert("RGBA")
    phone_crop = phone_source.crop((96, 470, 984, 1870))
    phone_crop.thumbnail((660, 952), Image.Resampling.LANCZOS)
    image.alpha_composite(phone_crop, (1110, 78))
    return image.convert("RGB")


def create_feature_graphic(icon: Image.Image) -> Image.Image:
    source = Image.open(SOURCE_DIR / "feature-background-ai.png").convert("RGB")
    target_ratio = 1024 / 500
    source_ratio = source.width / source.height
    if source_ratio > target_ratio:
        width = int(source.height * target_ratio)
        left = (source.width - width) // 2
        source = source.crop((left, 0, left + width, source.height))
    else:
        height = int(source.width / target_ratio)
        top = (source.height - height) // 2
        source = source.crop((0, top, source.width, top + height))
    image = source.resize((1024, 500), Image.Resampling.LANCZOS).convert("RGBA")
    overlay = Image.new("RGBA", image.size, (0, 0, 0, 0))
    overlay_draw = ImageDraw.Draw(overlay)
    for x in range(720):
        alpha = max(0, int(220 * (1 - x / 720)))
        overlay_draw.line((x, 0, x, 500), fill=(16, 42, 67, alpha))
    image = Image.alpha_composite(image, overlay)
    icon_small = icon.resize((84, 84), Image.Resampling.LANCZOS)
    image.alpha_composite(icon_small.convert("RGBA"), (54, 54))
    draw = ImageDraw.Draw(image)
    draw.text((158, 67), "라이프링크", font=font(31, bold=True), fill=WHITE)
    draw.text((54, 182), "일상을 지키는 안심", font=font(56, bold=True), fill=WHITE)
    draw.text((54, 270), "활동이 없으면 보호자에게 자동 SMS", font=font(28, bold=True), fill=WHITE)
    draw.text((54, 330), "위치·광고 없이, 기기에서 직접", font=font(22), fill="#DDF1EE")
    return image.convert("RGB")


def save_launcher_icons(icon: Image.Image) -> None:
    sizes = {
        "mipmap-mdpi": 48,
        "mipmap-hdpi": 72,
        "mipmap-xhdpi": 96,
        "mipmap-xxhdpi": 144,
        "mipmap-xxxhdpi": 192,
    }
    for directory, size in sizes.items():
        target_dir = ROOT / "app" / "src" / "main" / "res" / directory
        target_dir.mkdir(parents=True, exist_ok=True)
        art_size = int(size * 0.86)
        offset = (size - art_size) // 2
        art = icon.resize((art_size, art_size), Image.Resampling.LANCZOS).convert("RGBA")

        legacy = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        legacy_mask = Image.new("L", (art_size, art_size), 0)
        ImageDraw.Draw(legacy_mask).rounded_rectangle(
            (0, 0, art_size - 1, art_size - 1),
            radius=max(2, int(art_size * 0.22)),
            fill=255,
        )
        legacy.paste(art, (offset, offset), legacy_mask)
        legacy.save(target_dir / "ic_launcher.png", optimize=True)

        round_icon = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        round_mask = Image.new("L", (art_size, art_size), 0)
        ImageDraw.Draw(round_mask).ellipse((0, 0, art_size - 1, art_size - 1), fill=255)
        round_icon.paste(art, (offset, offset), round_mask)
        round_icon.save(target_dir / "ic_launcher_round.png", optimize=True)


def main() -> None:
    phone_dir = OUTPUT_DIR / "phone"
    tablet_7_dir = OUTPUT_DIR / "tablet-7"
    tablet_10_dir = OUTPUT_DIR / "tablet-10"
    for directory in (OUTPUT_DIR, phone_dir, tablet_7_dir, tablet_10_dir):
        directory.mkdir(parents=True, exist_ok=True)

    icon = create_store_icon()
    icon.save(OUTPUT_DIR / "app-icon-512.png", optimize=True)
    save_launcher_icons(icon)
    create_feature_graphic(icon).save(OUTPUT_DIR / "feature-graphic-1024x500.png", optimize=True)

    rendered = {}
    for filename, headline, subtitle, screen_name, background in PHONE_SPECS:
        screenshot = create_phone_screenshot(headline, subtitle, screen_name, background)
        screenshot.save(phone_dir / filename, optimize=True)
        rendered[screen_name] = (headline, subtitle, background)

    tablet_mapping = [
        (tablet_7_dir / "01-monitoring.png", "dashboard"),
        (tablet_7_dir / "02-daily-check-in.png", "checkin"),
        (tablet_10_dir / "01-sos.png", "sos"),
        (tablet_10_dir / "02-delivery-log.png", "logs"),
    ]
    for path, screen_name in tablet_mapping:
        headline, subtitle, background = rendered[screen_name]
        create_tablet_screenshot(headline, subtitle, screen_name, background).save(path, optimize=True)

    print(f"Generated Play assets in {OUTPUT_DIR}")


if __name__ == "__main__":
    main()
