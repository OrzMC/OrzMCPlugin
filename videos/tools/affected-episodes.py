#!/usr/bin/env python3
"""影响检测：功能改动 → 受影响的视频分集与更新等级。

依赖 `videos/coverage.yml`（功能点 ↔ 集号 双向映射）。**非阻塞**工具：报告用于提示与看板更新，
不阻断功能 PR（唯一阻塞项是 `verify.sh` 的零产物与源校验）。

用法：
    affected-episodes.py [--base origin/main | --since <tag>] [--head HEAD] [--changed FILE ...]
                         [--update-status] [--json] [--fail-on-impact]

等级：
    L1 仅文案      → 改字幕/口播即可（templates.yml 文案、i18n 文本），成片不重制
    L2 交互/界面变 → 单镜重录 + 该集重合成（config 键增减、命令语义、features.md 对应章节变更）
    L3 新增/退役   → 加集或归档集（新增顶层配置段、新增 Bot 命令、新增功能模块）
"""

from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
VIDEOS = REPO / "videos"
COVERAGE = VIDEOS / "coverage.yml"
STATUS = VIDEOS / "status.md"
FEATURES = REPO / "docs" / "features.md"

LEVEL_ORDER = {"L1": 1, "L2": 2, "L3": 3}
CN_NUM = "零一二三四五六七八九"


def git(*args: str) -> str:
    try:
        return subprocess.run(
            ["git", *args], cwd=REPO, capture_output=True, text=True, check=True
        ).stdout
    except (subprocess.CalledProcessError, FileNotFoundError) as exc:
        print(f"⚠️ git 调用失败（{exc}）", file=sys.stderr)
        return ""


def load_coverage() -> dict:
    try:
        import yaml
    except ImportError:
        print("⚠️ 缺少 PyYAML（pip install pyyaml），跳过影响检测", file=sys.stderr)
        raise SystemExit(0)
    return yaml.safe_load(COVERAGE.read_text(encoding="utf-8")) or {}


def cn_numeral(n: int) -> str:
    if n < 10:
        return CN_NUM[n]
    if n < 20:
        return "十" + (CN_NUM[n % 10] if n % 10 else "")
    return CN_NUM[n // 10] + "十" + (CN_NUM[n % 10] if n % 10 else "")


# ── features.md 章节定位 ───────────────────────────────────────────────────

def headings() -> list[tuple[int, str]]:
    out = []
    for i, line in enumerate(FEATURES.read_text(encoding="utf-8").splitlines(), start=1):
        m = re.match(r"^##\s*([一二三四五六七八九十]+)、", line)
        if m:
            out.append((i, f"§{m.group(1)}"))
            continue
        m = re.match(r"^###\s*(\d+)\.(\d+)\s", line)
        if m:
            out.append((i, f"§{m.group(1)}.{m.group(2)}"))
    return out


def chapter_at(line_no: int) -> str | None:
    found = None
    for start, label in headings():
        if start <= line_no:
            found = label
        else:
            break
    return found


def changed_chapters(base: str, head: str, rel: str) -> set[str]:
    """把 features.md 的 diff 行号映射为章节号（§N 或 §N.M）。"""
    diff = git("diff", "-U0", f"{base}..{head}", "--", rel)
    out: set[str] = set()
    for line in diff.splitlines():
        m = re.match(r"^@@ -\d+(?:,\d+)? \+(\d+)(?:,\d+)? @@", line)
        if m:
            label = chapter_at(int(m.group(1)))
            if label:
                minor = re.match(r"^§(\d+)\.(\d+)$", label)
                # 小数章节同时归到其所属大节，保证映射命中
                out.add(label)
                if minor:
                    out.add(f"§{minor.group(1)}")
    return out


# ── 配置/模板键定位 ───────────────────────────────────────────────────────

def file_key_map(rel: str, head: str) -> dict[int, tuple[str | None, str]]:
    """目标版本文件的「行号 → (所属顶层段, 键名)」映射（用于把 diff 行号落到具体键）。"""
    text = git("show", f"{head}:{rel}")
    out: dict[int, tuple[str | None, str]] = {}
    section: str | None = None
    for i, line in enumerate(text.splitlines(), start=1):
        m = re.match(r"^(\s*)([A-Za-z0-9_.\-]+):", line)
        if not m:
            continue
        indent, name = len(m.group(1)), m.group(2)
        if indent == 0:
            section = name
        out[i] = (section if indent > 0 else None, name)
    return out


def section_before(keymap: dict[int, tuple[str | None, str]], line_no: int) -> str | None:
    section = None
    for ln in sorted(keymap):
        if ln <= line_no:
            section = keymap[ln][0] or keymap[ln][1]
        else:
            break
    return section


def changed_keys(base: str, head: str, rel: str, *, prefix: str = "") -> tuple[set[str], bool]:
    """返回 (变更的点号键集合, 是否出现新的顶层段)。

    以 `head` 版文件的结构图为准：新增/修改行按行号取到 (段, 键) → `段.键`；
    纯删除行用所在段产出 `段.*`（由 pick() 的前缀匹配命中该段所有映射）。
    """
    keymap = file_key_map(rel, head)
    diff = git("diff", "-U0", f"{base}..{head}", "--", rel)
    keys: set[str] = set()
    new_section = False
    new_line = 0
    for line in diff.splitlines():
        m = re.match(r"^@@ -\d+(?:,\d+)? \+(\d+)(?:,\d+)? @@", line)
        if m:
            new_line = int(m.group(1))
            continue
        if line.startswith(("+++", "---", "diff ", "index ")) or not new_line:
            continue
        if line.startswith("+") and not line.startswith("+++"):
            entry = keymap.get(new_line)
            if entry:
                section, name = entry
                if section is None:
                    new_section = True
                    keys.add(name)
                else:
                    keys.add(f"{section}.{name}")
            new_line += 1
        elif line.startswith("-") and not line.startswith("---"):
            section = section_before(keymap, new_line)
            if section:
                keys.add(f"{section}.*")
        else:
            new_line += 1
    if prefix:
        keys = {k for k in keys if k.startswith(prefix)}
    return keys, new_section


# ── 主流程 ────────────────────────────────────────────────────────────────

def detect(changed: list[str], base: str, head: str, cov: dict) -> dict[str, dict]:
    sources = cov.get("sources") or {}
    chapters_map = cov.get("chapters") or {}
    commands_map = cov.get("commands") or {}
    config_map = cov.get("config_keys") or {}
    template_map = cov.get("template_keys") or {}
    hits: dict[str, dict] = {}

    def brief(items, limit: int = 5) -> str:
        """键列表过长时截断展示，避免报告刷屏。"""
        items = sorted(items)
        if len(items) <= limit:
            return ",".join(items)
        return ",".join(items[:limit]) + f" 等 {len(items)} 项"

    def add(ep: str, level: str, reason: str) -> None:
        cur = hits.setdefault(ep, {"level": "L1", "reasons": []})
        if LEVEL_ORDER[level] > LEVEL_ORDER[cur["level"]]:
            cur["level"] = level
        cur["reasons"].append(f"{level} {reason}")

    def pick(mapping: dict, keys: set[str], prefix_ok: bool = True) -> set[str]:
        found: set[str] = set()
        for key in keys:
            for pattern, eps in mapping.items():
                if key == pattern or (prefix_ok and key.startswith(pattern + ".")):
                    found.update(eps or [])
        return found

    for path in changed:
        rule = None
        for src, cfg in sources.items():
            if path == src or path.startswith(src.rstrip("/") + "/"):
                rule, cfg = src, cfg or {}
                break
        if rule is None:
            continue
        mode = cfg.get("mode")

        if mode == "chapters":
            chs = changed_chapters(base, head, path)
            eps = set()
            for ch in sorted(chs):
                eps.update(chapters_map.get(ch) or [])
                minor = re.match(r"^§(\d+)\.(\d+)$", ch)
                if minor:
                    eps.update(chapters_map.get(f"§{minor.group(1)}") or [])
            for ep in eps:
                add(ep, "L2", f"{path} 章节 {brief(chs) or '?'} 变更")
            if not eps:
                print(f"   ⚠️ {path} 有改动但未命中章节映射，请人工确认", file=sys.stderr)

        elif mode == "config_keys":
            keys, new_section = changed_keys(base, head, path)
            eps = pick(config_map, keys)
            for ep in eps:
                add(ep, "L3" if new_section else "L2", f"{path} 配置键 {brief(keys)}"
                    + ("（含顶层段增删）" if new_section else ""))
            if keys and not eps:
                print(f"   ⚠️ {path} 变更键 {sorted(keys)} 未在 coverage.config_keys 中登记", file=sys.stderr)

        elif mode == "template_keys":
            keys, _ = changed_keys(base, head, path)
            eps = pick(template_map, keys)
            for ep in eps:
                add(ep, "L1", f"{path} 文案键 {brief(keys)}")
            if keys and not eps:
                print(f"   ⚠️ {path} 变更模板键 {sorted(keys)} 未在 coverage.template_keys 中登记", file=sys.stderr)

        elif mode == "commands":
            # 命令表（features.md §2.2）或命令注册代码变更
            text = git("diff", f"{base}..{head}", "--", path)
            cmds = set(re.findall(r"^\+\|\s*`(\$[a-z])`", text, flags=re.M))
            eps = set()
            for cmd in cmds:
                eps.update(commands_map.get(cmd) or [])
            level = "L3" if cmds else "L2"
            for ep in eps:
                add(ep, level, f"{path} 命令 {brief(cmds, 4) or '语义'} 变更")

        elif mode == "literal":
            for ep in cfg.get("episodes") or []:
                add(ep, cfg.get("level", "L2"), f"{path} 变更（{cfg.get('note', '人工确认')}）")

    return hits


def update_status(hits: dict[str, dict]) -> int:
    text = STATUS.read_text(encoding="utf-8")
    lines = text.splitlines()
    changed = 0
    status_col = 5                       # 表格列：集|标题|源|基线版本|状态|成片链接|最后更新
    for line in lines:                  # 以表头定位「状态」列，避免列序变化后写错格
        if line.startswith("|") and "状态" in line:
            cells = [c.strip() for c in line.strip("|").split("|")]
            if "状态" in cells:
                status_col = cells.index("状态") + 1
            break
    for i, line in enumerate(lines):
        m = re.match(r"^\|\s*(EP\d+)\s*\|", line)
        if not m:
            continue
        ep = m.group(1)
        base_line = re.sub(r"\s*⚠️待更新[^|]*", "", line)      # 先清旧标记（幂等）
        if ep in hits:
            areas = base_line.split("|")
            mark = f" ⚠️待更新（{hits[ep]['level']}：{hits[ep]['reasons'][0].split(' ', 1)[-1][:40]}）"
            if len(areas) > status_col:
                areas[status_col] = f" {areas[status_col].strip()}{mark} "
            base_line = "|".join(areas)
            changed += 1
        if base_line != line:
            lines[i] = base_line
    if changed:
        STATUS.write_text("\n".join(lines) + "\n", encoding="utf-8")
    return changed


def main() -> int:
    ap = argparse.ArgumentParser(description="视频分集影响检测")
    ap.add_argument("--base", default="origin/main", help="对比基线（默认 origin/main）")
    ap.add_argument(
        "--since",
        metavar="REF",
        help="发版视角：等价于 --base <REF>（如 --since 1.0.27 比较该 tag 至今的影响）",
    )
    ap.add_argument("--head", default="HEAD")
    ap.add_argument("--changed", nargs="*", help="直接指定变更文件（跳过 git diff）")
    ap.add_argument("--update-status", action="store_true", help="把受影响集在看板标记 ⚠️待更新")
    ap.add_argument("--json", action="store_true")
    ap.add_argument("--fail-on-impact", action="store_true", help="有影响时以退出码 1 结束（默认非阻塞）")
    args = ap.parse_args()

    if args.since:                      # 发版视角别名（文档统一写 --since <上一 tag>）
        args.base = args.since

    cov = load_coverage()
    episodes = cov.get("episodes") or {}
    changed = args.changed if args.changed else [
        f for f in git("diff", "--name-only", f"{args.base}..{args.head}").splitlines() if f
    ]
    hits = detect(changed, args.base, args.head, cov)
    ordered = sorted(hits.items(), key=lambda kv: (-LEVEL_ORDER[kv[1]["level"]], kv[0]))

    if args.json:
        print(json.dumps({ep: v for ep, v in ordered}, ensure_ascii=False, indent=2))
    else:
        print(f"== 影响检测（{args.base}..{args.head}，变更 {len(changed)} 个文件）==")
        if not ordered:
            print("✅ 未命中任何分集映射（无需更新视频）")
        else:
            print("| 集 | 标题 | 等级 | 依据 |")
            print("|:--|:--|:--|:--|")
            for ep, info in ordered:
                title = (episodes.get(ep) or {}).get("title", "")
                print(f"| {ep} | {title} | **{info['level']}** | {'；'.join(info['reasons'])} |")
            print("\n处置见 videos/UPDATE.md：L1 改字幕不重录 · L2 单镜重录 + 该集重合成 · L3 加集/归档")

    if args.update_status:
        n = update_status(hits)
        print(f"\n看板已更新：{n} 行标记 ⚠️待更新（videos/status.md）")

    return 1 if (args.fail_on_impact and hits) else 0


if __name__ == "__main__":
    raise SystemExit(main())
