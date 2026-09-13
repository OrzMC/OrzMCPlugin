#!/usr/bin/env python3
"""OrzMC 视频卡片渲染器（Pillow）：`<id>-cards.spec.json` + `brand/brand.yml` → PNG 卡片。

为什么用 Pillow 而不是 ffmpeg：本机 ffmpeg 8.1.1（Homebrew）**未编译 drawtext / subtitles / ass**
（无 libfreetype / libass，`ffmpeg -filters` 无相关滤镜），故文本渲染走 Pillow；ffmpeg 仅用于
合成动画、音频转码与时长探测。字幕以独立 SRT 交付（平台侧上传或剪辑软件导入），不做烧录。

用法：
    build-cards.py --spec videos/.build/ep00/ep00-cards.spec.json --out DIR [--aspect landscape|portrait]
                   [--brand videos/brand/brand.yml]

输出为确定性 PNG（不含时间戳元数据），配合 `verify.sh` 的幂等校验。
"""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

REPO = Path(__file__).resolve().parents[2]
FONT_CANDIDATES = {
    "PingFang SC": "/System/Library/Fonts/PingFang.ttc",
    "Hiragino Sans GB": "/System/Library/Fonts/Hiragino Sans GB.ttc",
    "STHeiti": "/System/Library/Fonts/STHeiti Medium.ttc",
    "Noto Sans SC": "/System/Library/Fonts/Supplemental/NotoSansSC-Regular.otf",
    "JetBrains Mono": "/Library/Fonts/JetBrainsMono-Regular.ttf",
    "Menlo": "/System/Library/Fonts/Menlo.ttc",
    "Monaco": "/System/Library/Fonts/Monaco.ttf",
}


def hex_rgb(value: str) -> tuple[int, int, int]:
    v = value.strip().lstrip("#")
    return tuple(int(v[i : i + 2], 16) for i in (0, 2, 4))  # type: ignore[return-value]


def rgba(value: str) -> tuple[int, int, int, int]:
    """`#RRGGBB` 或 `rgba(r,g,b,a)` → RGBA 元组。"""
    if value.startswith("rgba"):
        nums = [float(x) for x in value[value.index("(") + 1 : value.index(")")].split(",")]
        return (int(nums[0]), int(nums[1]), int(nums[2]), int(round(nums[3] * 255)))
    r, g, b = hex_rgb(value)
    return (r, g, b, 255)


def pick_font(brand: dict, size: int) -> tuple[ImageFont.FreeTypeFont, str]:
    for name in (brand.get("fonts") or {}).get("subtitle_zh") or []:
        path = FONT_CANDIDATES.get(name)
        if path and Path(path).exists():
            try:
                return ImageFont.truetype(path, size), name
            except OSError:
                continue
    print("   ⚠️ 未找到配置中的中文字体，降级为 Pillow 默认位图字体（中文可能显示为方块）", file=sys.stderr)
    return ImageFont.load_default(), "default"


def wrap(draw: ImageDraw.ImageDraw, text: str, font, max_width: int) -> list[str]:
    lines, cur = [], ""
    for ch in text:
        if draw.textlength(cur + ch, font=font) <= max_width:
            cur += ch
        else:
            lines.append(cur)
            cur = ch
    if cur:
        lines.append(cur)
    return lines


def render_card(brand: dict, card: dict, canvas: tuple[int, int]) -> Image.Image:
    w, h = canvas
    palette = brand["palette"]
    img = Image.new("RGB", (w, h), hex_rgb(palette.get("primary_dark", "#0288D1")))
    draw = ImageDraw.Draw(img)

    # 竖向渐变背景（主色 → 深色）
    top, bottom = hex_rgb(palette.get("primary", "#4FC3F7")), hex_rgb(palette.get("primary_dark", "#0288D1"))
    for y in range(h):
        k = y / max(h - 1, 1)
        draw.line([(0, y), (w, y)], fill=tuple(int(top[i] + (bottom[i] - top[i]) * k) for i in range(3)))

    # 半透明信息卡（品牌 card_bg）
    pad_x, pad_y = int(w * 0.08), int(h * 0.18)
    overlay = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    od = ImageDraw.Draw(overlay)
    radius = int(brand.get("ui", {}).get("corner_radius_px", 8)) * 3
    od.rounded_rectangle([pad_x, pad_y, w - pad_x, h - pad_y], radius=radius, fill=rgba(palette["card_bg"]))
    img = Image.alpha_composite(img.convert("RGBA"), overlay).convert("RGB")
    draw = ImageDraw.Draw(img)

    # 强调条
    bar_w = max(6, w // 240)
    draw.rectangle([pad_x, pad_y + int(h * 0.06), pad_x + bar_w, h - pad_y - int(h * 0.06)], fill=hex_rgb(palette["accent"]))

    title_font, font_name = pick_font(brand, int(h * 0.075))
    text_font, _ = pick_font(brand, int(h * 0.045))
    small_font, _ = pick_font(brand, int(h * 0.028))
    left = pad_x + bar_w + int(w * 0.035)
    max_w = w - left - pad_x - int(w * 0.03)

    y = pad_y + int(h * 0.10)
    for line in wrap(draw, card.get("title") or "", title_font, max_w):
        draw.text((left, y), line, font=title_font, fill=hex_rgb(palette["text"]))
        y += int(h * 0.095)
    y += int(h * 0.02)
    for line in wrap(draw, card.get("text") or "", text_font, max_w):
        draw.text((left, y), line, font=text_font, fill=hex_rgb(palette.get("text_muted", "#B0BEC5")))
        y += int(h * 0.06)

    # 水印
    mark = (brand.get("ui", {}).get("watermark") or {})
    wm = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    ImageDraw.Draw(wm).text(
        (w - int(w * 0.03), h - int(h * 0.06)),
        mark.get("text", "OrzMC"),
        font=small_font,
        fill=(255, 255, 255, int(255 * float(mark.get("opacity", 0.15)))),
        anchor="rs",
    )
    img = Image.alpha_composite(img.convert("RGBA"), wm).convert("RGB")
    print(f"   · 卡片字体：{font_name}")
    return img


def main() -> int:
    ap = argparse.ArgumentParser(description="OrzMC 视频卡片渲染器")
    ap.add_argument("--spec", required=True, help="<id>-cards.spec.json")
    ap.add_argument("--out", required=True, help="输出目录（通常 .build/<id>/cards/）")
    ap.add_argument("--aspect", default="landscape", choices=["landscape", "portrait"])
    ap.add_argument("--brand", default=str(REPO / "videos" / "brand" / "brand.yml"))
    args = ap.parse_args()

    import yaml  # 局部导入：仅卡片渲染需要

    brand = yaml.safe_load(Path(args.brand).read_text(encoding="utf-8"))
    spec = json.loads(Path(args.spec).read_text(encoding="utf-8"))
    canvas = tuple(brand["canvas"][args.aspect])  # type: ignore[arg-type]
    out_dir = Path(args.out)
    out_dir.mkdir(parents=True, exist_ok=True)

    if not spec.get("cards"):
        print("   ⚠️ spec 中没有 card/overlay 镜头，跳过卡片渲染")
        return 0
    for card in spec["cards"]:
        name = card.get("asset") or f"{card['id']}.png"
        if args.aspect == "portrait":
            name = name.replace(".png", "-9x16.png")
        path = out_dir / name
        render_card(brand, card, canvas).save(path, optimize=True)
        print(f"   → {path}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
