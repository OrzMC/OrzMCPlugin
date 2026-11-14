#!/usr/bin/env python3
"""上游版本漂移探测：钉住的 MC 版本 / 依赖插件是否已落后于上游。

只做"发现与提示"，不做自动升级（升级要人判断：via 版本、插件兼容、发布窗口）。
数据源：
  · PaperMC Fill API v3：Paper / Folia 最新稳定版本 + 当前钉住版本的最新构建号；
  · Modrinth：LuckPerms 在当前 MC 版本下的最新 release 版本（与本地缓存 marker 里的 version 比较）。

用法：
  python3 scripts/quality/upstream_drift.py --out build/quality/upstream-drift.md --json build/quality/upstream-drift.json
退出码恒为 0（这是报告，不是门禁）；`DRIFT: yes` 行供 workflow 判断是否开 issue。
"""

from __future__ import annotations

import argparse
import json
import re
import urllib.request
from dataclasses import dataclass, asdict, field
from pathlib import Path

UA = {"User-Agent": "OrzMC/plugin-ci"}
FILL = "https://fill.papermc.io/v3/projects"
MODRINTH = "https://api.modrinth.com/v2/project"


def get_json(url: str) -> dict | list:
    req = urllib.request.Request(url, headers=UA)
    with urllib.request.urlopen(req, timeout=20) as resp:
        return json.loads(resp.read().decode("utf-8"))


def pinned_version(gradle_properties: Path) -> str:
    for line in gradle_properties.read_text(encoding="utf-8").splitlines():
        if line.startswith("plugin_debug_server_version="):
            return line.split("=", 1)[1].strip()
    raise SystemExit(f"gradle.properties 里没有 plugin_debug_server_version（{gradle_properties}）")


def latest_stable(project: str) -> str:
    """Fill API 的 versions 列表按新→旧排序，取第一个 SUPPORTED 的完整版本号（跳过 -rc/-pre）。"""
    data = get_json(f"{FILL}/{project}/versions")
    for item in data.get("versions", []):
        version = item.get("version", {})
        vid = version.get("id", "")
        status = (version.get("support") or {}).get("status")
        if status == "SUPPORTED" and re.fullmatch(r"\d+(\.\d+)+", vid):
            return vid
    return "?"


def latest_build(project: str, version: str) -> str:
    data = get_json(f"{FILL}/{project}/versions/{version}/builds/latest")
    return str(data.get("id", "?"))


def luckperms_latest(mc_version: str) -> str:
    data = get_json(
        f"{MODRINTH}/luckperms/version?loaders=%5B%22paper%22%5D&game_versions=%5B%22{mc_version}%22%5D"
    )
    for version in data:
        if version.get("version_type") == "release":
            return str(version.get("version_number", "?"))
    return str(data[0].get("version_number", "?")) if data else "?"


def cached_plugin_version(marker: Path) -> str | None:
    if not marker.is_file():
        return None
    for line in marker.read_text(encoding="utf-8").splitlines():
        if line.startswith("version="):
            return line.split("=", 1)[1].strip()
    return None


@dataclass
class Drift:
    pinned: str
    paper_latest_mc: str = "?"
    folia_latest_mc: str = "?"
    paper_build: str = "?"
    folia_build: str = "?"
    luckperms_latest: str = "?"
    luckperms_cached: str | None = None
    reasons: list[str] = field(default_factory=list)

    @property
    def drifted(self) -> bool:
        return bool(self.reasons)

    def markdown(self) -> str:
        lines = [
            "## 上游版本漂移报告",
            "",
            "| 项 | 值 |",
            "|:--|:--|",
            f"| 仓库钉住的 MC 版本 | **{self.pinned}** |",
            f"| Paper 最新稳定版 | {self.paper_latest_mc} |",
            f"| Folia 最新稳定版 | {self.folia_latest_mc} |",
            f"| Paper {self.pinned} 最新构建 | #{self.paper_build} |",
            f"| Folia {self.pinned} 最新构建 | #{self.folia_build} |",
            f"| LuckPerms（{self.pinned}，最新 release） | {self.luckperms_latest} |",
            f"| LuckPerms 本地缓存 | {self.luckperms_cached or '（未缓存）'} |",
            "",
        ]
        if self.reasons:
            lines += ["### 需要关注", ""] + [f"- {r}" for r in self.reasons] + [""]
        else:
            lines += ["无漂移：钉住的版本仍是上游最新稳定版，依赖也无更新。", ""]
        lines += [
            "> 本报告只提示，不自动升级（升级需人工判断兼容性与发布窗口）。",
            "> Paper/Folia 构建号每次下载 `builds/latest` 自动取最新，故构建号变化不算漂移。",
        ]
        return "\n".join(lines) + "\n"


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--gradle-properties", default="gradle.properties")
    parser.add_argument("--marker", default="", help="LuckPerms 缓存 marker（默认按钉住版本推断）")
    parser.add_argument("--out", default="build/quality/upstream-drift.md")
    parser.add_argument("--json", default="build/quality/upstream-drift.json")
    args = parser.parse_args()

    pinned = pinned_version(Path(args.gradle_properties))
    drift = Drift(pinned=pinned)
    drift.paper_latest_mc = latest_stable("paper")
    drift.folia_latest_mc = latest_stable("folia")
    drift.paper_build = latest_build("paper", pinned)
    drift.folia_build = latest_build("folia", pinned)
    drift.luckperms_latest = luckperms_latest(pinned)
    marker = Path(args.marker) if args.marker else Path(f"build/e2e-plugins/.luckperms-{pinned}.ok")
    drift.luckperms_cached = cached_plugin_version(marker)

    if drift.paper_latest_mc not in ("?", pinned):
        drift.reasons.append(
            f"Paper 已有更新的稳定版 **{drift.paper_latest_mc}**（仓库钉 {pinned}）：评估升级 `plugin_debug_server_version`"
        )
    if drift.folia_latest_mc not in ("?", pinned):
        drift.reasons.append(
            f"Folia 已有更新的稳定版 **{drift.folia_latest_mc}**（仓库钉 {pinned}）：评估升级（与 Paper 同步）"
        )
    if drift.luckperms_cached and drift.luckperms_latest not in ("?", drift.luckperms_cached):
        drift.reasons.append(
            f"LuckPerms 有新 release **{drift.luckperms_latest}**（本地缓存 {drift.luckperms_cached}）：下次 E2E 会自动拉新，注意行为变化"
        )

    out = Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(drift.markdown(), encoding="utf-8")
    Path(args.json).write_text(json.dumps(asdict(drift) | {"drifted": drift.drifted}, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    print(drift.markdown())
    print(f"DRIFT: {'yes' if drift.drifted else 'no'}")


if __name__ == "__main__":
    main()
