#!/usr/bin/env python3
"""OrzMC 视频分集构建器：`epNN.yml`（唯一事实源）→ 人读稿 / SRT / 卡片 spec / 口播稿。

零产物策略：所有输出写入 `--out`（默认 `videos/.build/<id>/`），仓库只跟踪本工具与源文件。

用法：
    build-episode.py <episode.yml> [--out DIR] [--check] [--lang zh-CN]

校验（`--check` 只校验不写文件，CI 用）：
    · 时长：各镜之和 ≤ `duration_cap_s`，且与 `target_duration_s` 偏差 ≤ ±25%
    · 口播：CJK/字母数字字数 vs `narration_chars`（±10% 提示）；语速落在 `speaking_rate_range`
      （默认 3.4–5.2 字/秒 = 语速 4.5 字/秒 × 0.85 留白折算，实测 `say -v Tingting` ≈4.48 字/秒）
    · 结构：镜头数 ≤ `max_shots`；信息点 ≤ `max_information_points`；镜号唯一、时长 > 0
    · 字幕：切分后单行 ≤ 18 字、单句 ≤ 6s（超限报错）
    · 事实：`facts`（命令 / 配置键 / 模板键）与 `documentation_anchors`（features.md §x.y）必须真实存在
    · 隐私：不得出现真实 IP / 域名 / QQ 号 / openid / 会话 key / Token（合规占位由 brand 约定）
"""

from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

import yaml

REPO = Path(__file__).resolve().parents[2]           # videos/tools/<file> → 仓库根
VIDEOS = REPO / "videos"
RESOURCES = REPO / "src" / "main" / "resources"
FEATURES = REPO / "docs" / "features.md"

SUBTITLE_LINE_MAX = 18      # 单行最大字数
SUBTITLE_CUE_MAX_S = 6.0    # 单句最大时长（秒）
CN_NUM = "零一二三四五六七八九"


class Issue(Exception):
    """校验失败（致命）。"""


def cn_numeral(n: int) -> str:
    """1..99 → 中文数字（features.md 的 `## 十五、` 风格标题）。"""
    if n < 10:
        return CN_NUM[n]
    if n < 20:
        return "十" + (CN_NUM[n % 10] if n % 10 else "")
    return CN_NUM[n // 10] + "十" + (CN_NUM[n % 10] if n % 10 else "")


def narration_chars(text: str) -> int:
    """统计口播有效字数：CJK + 拉丁字母数字（忽略标点、空白）。"""
    return len(re.findall(r"[\u4e00-\u9fff\w]", text or "", flags=re.UNICODE))


def split_subtitle(text: str, max_len: int = SUBTITLE_LINE_MAX) -> list[str]:
    """按标点切分为 ≤max_len 字的字幕行（贪心，保留标点）。"""
    text = (text or "").strip()
    if not text:
        return []
    parts = re.findall(r"[^，。！？；：、,.!?;:]+[，。！？；：、,.!?;:]?", text)
    lines: list[str] = []
    for part in parts:
        part = part.strip()
        if not part:
            continue
        while len(part) > max_len:
            lines.append(part[:max_len])
            part = part[max_len:]
        if part:
            lines.append(part)
    merged: list[str] = []
    for line in lines:  # 避免过短碎行，尽量合并到 max_len
        if merged and len(merged[-1]) + len(line) <= max_len:
            merged[-1] += line
        else:
            merged.append(line)
    return merged


def subtitle_lines(shot: dict) -> list[str]:
    """一镜的字幕句列表：字符串按标点自动切分；列表则视为已切好的多句（每句 ≤18 字）。"""
    value = shot.get("subtitle")
    if value is None:
        return []
    if isinstance(value, list):
        return [str(v).strip() for v in value if str(v).strip()]
    return split_subtitle(str(value))


def ts(seconds: float) -> str:
    ms = int(round(seconds * 1000))
    h, ms = divmod(ms, 3_600_000)
    m, ms = divmod(ms, 60_000)
    s, ms = divmod(ms, 1000)
    return f"{h:02d}:{m:02d}:{s:02d},{ms:03d}"


# ── 事实校验 ────────────────────────────────────────────────────────────────

def known_commands() -> set[str]:
    return set(re.findall(r"^\|\s*`(\$[a-z])`", FEATURES.read_text(encoding="utf-8"), flags=re.M))


def dotted_get(data: dict, dotted: str):
    cur = data
    for key in dotted.split("."):
        if not isinstance(cur, dict) or key not in cur:
            return None
        cur = cur[key]
    return cur


def check_config_key(key: str) -> bool:
    """支持 `config.yml:geoip.enable` 与 `geoip.enable`（默认 config.yml）。"""
    if ":" in key:
        fname, dotted = key.split(":", 1)
    else:
        fname, dotted = "config.yml", key
    path = RESOURCES / fname
    if not path.exists():
        return False
    return dotted_get(yaml.safe_load(path.read_text(encoding="utf-8")) or {}, dotted) is not None


def check_template_key(key: str) -> bool:
    path = RESOURCES / "templates.yml"
    return path.exists() and dotted_get(
        yaml.safe_load(path.read_text(encoding="utf-8")) or {}, key
    ) is not None


def check_anchor(anchor: str) -> bool:
    """`features.md §15.1` / `§7` → 对应标题存在。"""
    m = re.match(r"^features\.md\s*§(\d+)(?:\.(\d+))?$", anchor.strip())
    if not m:
        return False
    text = FEATURES.read_text(encoding="utf-8")
    major, minor = int(m.group(1)), m.group(2)
    if minor is None:
        return re.search(rf"^##\s*{cn_numeral(major)}、", text, flags=re.M) is not None
    return re.search(rf"^###\s*{major}\.{minor}\s", text, flags=re.M) is not None


# 官方/公开域名白名单（项目自有或平台地址，非敏感信息）
DOMAIN_ALLOWLIST = {
    "orzmc.jokerhub.cn",
    "jokerhub.cn",
    "github.com",
    "hangar.papermc.io",
    "modrinth.com",
    "papermc.io",
    "qq.com",
}

SECRET_PATTERNS = [
    (r"\b\d{1,3}(?:\.\d{1,3}){3}\b", "真实 IPv4 地址"),
    (r"\b(?:[a-zA-Z0-9-]+\.)+(?:com|cn|net|org|io|me|xyz)\b", "真实域名"),
    (r"\b\d{6,12}\b", "疑似 QQ 号 / 群号"),
    (r"\b[A-F0-9]{32,}\b", "疑似 openid / 会话 key（大写十六进制串）"),
    (r"(?i)\b(?:token|secret|password|api[_-]?key)\s*[:=]\s*\S+", "疑似凭据赋值"),
]


# ── 校验 ────────────────────────────────────────────────────────────────────

def validate(ep: dict, ep_path: Path, *, strict: bool = True) -> dict:
    problems: list[str] = []
    warnings: list[str] = []

    meta = ep.get("meta") or {}
    budget = ep.get("budget") or {}
    shots = ep.get("shots") or []
    ep_id = meta.get("id") or ep_path.stem

    if not shots:
        problems.append("shots 为空")
    if len(shots) > int(budget.get("max_shots", 10)):
        problems.append(f"镜头数 {len(shots)} 超过 max_shots={budget.get('max_shots')}（应拆集）")

    fps = [s for s in meta.get("information_points") or []]
    if fps and len(fps) > int(budget.get("max_information_points", 3)):
        problems.append(f"信息点 {len(fps)} 超过上限（应拆集）")

    total = sum(float(s.get("duration_s") or 0) for s in shots)
    cap = float(budget.get("duration_cap_s") or 0)
    target = float(budget.get("target_duration_s") or 0)
    if cap and total > cap:
        problems.append(f"总时长 {total:.0f}s 超过硬上限 {cap:.0f}s")
    if target and abs(total - target) > target * 0.25:
        warnings.append(f"总时长 {total:.0f}s 与目标 {target:.0f}s 偏差 >25%")

    seen: set = set()
    for s in shots:
        sid = s.get("id")
        if sid in seen:
            problems.append(f"镜号重复：{sid}")
        seen.add(sid)
        kind = s.get("kind")
        if kind not in {"card", "group_chat", "ingame", "terminal", "overlay"}:
            problems.append(f"镜 {sid}：未知 kind={kind}")
        if float(s.get("duration_s") or 0) <= 0:
            problems.append(f"镜 {sid}：duration_s 必须为正")
        if not (s.get("narration") or "").strip() and kind != "overlay":
            problems.append(f"镜 {sid}：缺少 narration")
        if not (s.get("asset") or "").strip():
            problems.append(f"镜 {sid}：缺少 asset（素材名）")
        for line in subtitle_lines(s):
            if len(line) > SUBTITLE_LINE_MAX:
                problems.append(f"镜 {sid}：字幕行超 {SUBTITLE_LINE_MAX} 字 → {line}")

    # 口播预算与语速
    spoken = " ".join((s.get("narration") or "") for s in shots)
    chars = narration_chars(spoken)
    budget_chars = int(budget.get("narration_chars") or 0)
    if budget_chars and abs(chars - budget_chars) > budget_chars * 0.10:
        warnings.append(f"口播 {chars} 字与预算 {budget_chars} 字偏差 >10%")
    rate_range = budget.get("speaking_rate_range") or [3.4, 5.2]  # 含留白/停顿（= 语速 4.5 字/秒 × 0.85）
    if total > 0:
        rate = chars / total
        if not (rate_range[0] <= rate <= rate_range[1]):
            problems.append(
                f"语速 {rate:.2f} 字/秒超出 {rate_range[0]}–{rate_range[1]}（改稿或调时长，勿靠加速）"
            )

    # 字幕单句时长
    for s in shots:
        lines = subtitle_lines(s)
        if lines:
            cue = float(s["duration_s"]) / len(lines)
            if cue > SUBTITLE_CUE_MAX_S:
                problems.append(
                    f"镜 {s.get('id')}：字幕单句 {cue:.1f}s 超过 {SUBTITLE_CUE_MAX_S}s"
                    f"（{len(lines)} 句 / {s['duration_s']}s，请把 subtitle 写成多句列表）"
                )

    # 事实与锚点
    facts = ep.get("facts") or {}
    known = known_commands()
    for cmd in facts.get("commands") or []:
        if cmd not in known:
            problems.append(f"facts.commands 中 `{cmd}` 不存在于 features.md §2.2 命令表")
    for key in facts.get("config_keys") or []:
        if not check_config_key(key):
            problems.append(f"facts.config_keys 中 `{key}` 在资源文件中不存在")
    for key in facts.get("template_keys") or []:
        if not check_template_key(key):
            problems.append(f"facts.template_keys 中 `{key}` 在 templates.yml 中不存在")
    for anchor in ep.get("documentation_anchors") or []:
        if not check_anchor(anchor):
            problems.append(f"documentation_anchors 中 `{anchor}` 无法在 docs/features.md 中定位")

    # 隐私
    blob = yaml.safe_dump(ep, allow_unicode=True)
    for pattern, label in SECRET_PATTERNS:
        for hit in re.findall(pattern, blob):
            hit_s = str(hit)
            allow = (
                hit_s.startswith(("1.0.", "0.0."))
                or hit_s in {"25565", "19132"}
                or any(hit_s == d or hit_s.endswith("." + d) for d in DOMAIN_ALLOWLIST)
            )
            if not allow:
                problems.append(f"疑似敏感信息（{label}）：{hit}")

    if problems and strict:
        raise Issue("\n".join(f"  · {p}" for p in problems))
    return {
        "id": ep_id,
        "shots": len(shots),
        "duration_s": total,
        "narration_chars": chars,
        "speaking_rate": (chars / total) if total else 0.0,
        "problems": problems,
        "warnings": warnings,
    }


# ── 生成 ────────────────────────────────────────────────────────────────────

def generate(ep: dict, out_dir: Path, lang: str) -> list[Path]:
    meta, budget, shots = ep.get("meta") or {}, ep.get("budget") or {}, ep.get("shots") or []
    ep_id, slug = meta.get("id"), meta.get("slug")
    out_dir.mkdir(parents=True, exist_ok=True)
    written: list[Path] = []

    # 1) SRT
    cues, t = [], 0.0
    for s in shots:
        dur, lines = float(s.get("duration_s") or 0), subtitle_lines(s)
        if lines:
            weights = [max(len(x), 1) for x in lines]
            total_w = sum(weights)
            start = t
            for line, w in zip(lines, weights):
                span = dur * w / total_w
                cues.append((start, start + span, line))
                start += span
        t += dur
    srt = out_dir / f"{ep_id}.{lang}.srt"
    srt.write_text(
        "".join(
            f"{i}\n{ts(a)} --> {ts(b)}\n{text}\n\n" for i, (a, b, text) in enumerate(cues, 1)
        ),
        encoding="utf-8",
    )
    written.append(srt)

    # 2) 口播稿（TTS 与配音用，覆盖式覆盖时保持固定顺序）
    nar = out_dir / f"{ep_id}-narration.txt"
    nar.write_text(
        "\n".join((s.get("narration") or "").strip() for s in shots) + "\n", encoding="utf-8"
    )
    written.append(nar)

    # 3) 卡片 spec（供 build-cards.py 渲染，确定性 JSON）
    cards = [
        {
            "id": f"{ep_id}-{s['id']:02d}",
            "asset": s.get("asset"),
            "title": (s.get("overlay") or meta.get("title") or "").strip(),
            "text": " / ".join(subtitle_lines(s)),
            "narration": s.get("narration") or "",
        }
        for s in shots
        if s.get("kind") in {"card", "overlay"}
    ]
    if meta.get("end_card"):
        cards.append({"id": f"{ep_id}-99", "asset": f"{ep_id}_99_end.png",
                      "title": meta["end_card"].get("title", ""), "text": meta["end_card"].get("text", "")})
    spec = out_dir / f"{ep_id}-cards.spec.json"
    spec.write_text(
        json.dumps(
            {
                "episode": ep_id,
                "slug": slug,
                "title": meta.get("title"),
                "aspects": ep.get("aspect_variants") or {},
                "cards": cards,
            },
            ensure_ascii=False,
            indent=2,
            sort_keys=True,
        )
        + "\n",
        encoding="utf-8",
    )
    written.append(spec)

    # 4) 人读稿（分镜表 + 口播逐字 + 素材清单）
    md = out_dir / f"{ep_id}-{slug}.md"
    lines = [
        f"# {ep_id} {meta.get('title')}",
        "",
        f"> 生成物（勿手改，改 `videos/episodes/{ep_id}-{slug}.yml` 后重跑 `make.sh`）｜ 基线版本 {meta.get('baseline_version')} ｜ 优先级 {meta.get('priority')}",
        "",
        f"- 受众：{meta.get('audience')}　前置：{'；'.join(meta.get('prerequisites') or []) or '—'}",
        f"- 时长：{sum(float(s['duration_s']) for s in shots):.0f}s（上限 {budget.get('duration_cap_s')}s，目标 {budget.get('target_duration_s')}s）"
        f"　镜头 {len(shots)}　口播 {narration_chars(' '.join(s.get('narration') or '' for s in shots))} 字",
        f"- 文档锚点：{'、'.join(ep.get('documentation_anchors') or []) or '—'}",
        "",
        "## 分镜表",
        "",
        "| 镜 | 时长 | 类型 | 素材 | 画面 | 覆层 / 过渡 |",
        "|---:|---:|:--|:--|:--|:--|",
    ]
    for s in shots:
        lines.append(
            f"| {s['id']} | {s['duration_s']}s | {s.get('kind')} | `{s.get('asset')}` | "
            f"{s.get('visual')} | {s.get('overlay') or '—'} / {s.get('transition') or '—'} |"
        )
    lines += ["", "## 口播逐字稿（配音 / TTS 用）", ""]
    for s in shots:
        lines += [f"**镜 {s['id']}（{s['duration_s']}s）** {s.get('narration')}", ""]
    lines += ["## 素材清单", "", "| 素材 | 类型 | 来源 |", "|:--|:--|:--|"]
    for s in shots:
        kind = s.get("kind")
        src = "脚本生成（build-cards.py）" if kind in {"card", "overlay"} else "录屏（见 checklist-recording.md）"
        lines.append(f"| `{s.get('asset')}` | {kind} | {src} |")
    lines += ["", "## 字幕文件", "", f"- `{ep_id}.{lang}.srt`（{len(cues)} 条字幕，单行 ≤{SUBTITLE_LINE_MAX} 字）", ""]
    md.write_text("\n".join(lines), encoding="utf-8")
    written.append(md)

    return written


def main() -> int:
    ap = argparse.ArgumentParser(description="OrzMC 视频分集构建器")
    ap.add_argument("episode", help="videos/episodes/epNN-*.yml")
    ap.add_argument("--out", help="输出目录（默认 videos/.build/<id>/）")
    ap.add_argument("--check", action="store_true", help="只校验，不生成")
    ap.add_argument("--lang", default="zh-CN")
    args = ap.parse_args()

    ep_path = Path(args.episode)
    if not ep_path.is_absolute():
        ep_path = (REPO / ep_path) if (REPO / ep_path).exists() else (VIDEOS / "episodes" / ep_path)
    if not ep_path.exists():
        print(f"❌ 找不到源文件：{ep_path}", file=sys.stderr)
        return 2
    ep = yaml.safe_load(ep_path.read_text(encoding="utf-8")) or {}
    ep_id = (ep.get("meta") or {}).get("id") or ep_path.stem

    try:
        report = validate(ep, ep_path)
    except Issue as exc:
        print(f"❌ {ep_id} 校验失败：\n{exc}", file=sys.stderr)
        return 1

    head = (
        f"✅ {ep_id}：{report['shots']} 镜 / {report['duration_s']:.0f}s / "
        f"口播 {report['narration_chars']} 字 / 语速 {report['speaking_rate']:.2f} 字/秒"
    )
    print(head)
    for w in report["warnings"]:
        print(f"   ⚠️ {w}")
    if args.check:
        return 0

    out_dir = Path(args.out) if args.out else VIDEOS / ".build" / ep_id
    for path in generate(ep, out_dir, args.lang):
        try:                                    # 产物可能在仓库外（VIDEO_WORKDIR）
            shown = path.relative_to(REPO)
        except ValueError:
            shown = path
        print(f"   → {shown}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
