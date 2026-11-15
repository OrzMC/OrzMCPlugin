#!/usr/bin/env python3
"""夜间失败分诊：把「哪里红了 + 为什么」压成一页 Markdown，供 issue / 人 / agent 直接接手。

收集内容（全部来自本次运行的产物，不额外跑测试）：
1. 失败/错误的测试用例（test-results/*.xml 里的 `<failure>` / `<error>`，含首行信息）；
2. 各真实服务端 run 目录的 `smoke.log` 尾部（启动失败、断言失败、线程异常都在这里）；
3. 最近提交与工作区状态（判断是否是刚合入的改动引起）。

用法：
    python3 scripts/quality/triage.py --out build/triage/triage.md
    python3 scripts/quality/triage.py --results build/test-results --run-dir run-e2e-paper --json build/triage/triage.json
"""

from __future__ import annotations

import argparse
import json
import subprocess
from dataclasses import dataclass, field, asdict
from pathlib import Path
from xml.etree import ElementTree

MAX_FAILURES = 25
MAX_LOG_LINES = 40
MAX_LOG_FILES = 5


@dataclass
class Triage:
    failed_tests: list[dict] = field(default_factory=list)
    log_tails: list[dict] = field(default_factory=list)
    recent_commits: list[str] = field(default_factory=list)
    git_status: list[str] = field(default_factory=list)


def collect_failed_tests(results_dir: Path) -> list[dict]:
    failures: list[dict] = []
    if not results_dir.is_dir():
        return failures
    for xml_file in sorted(results_dir.glob("*/*.xml")):
        try:
            root = ElementTree.parse(xml_file).getroot()
        except ElementTree.ParseError:
            failures.append({"suite": xml_file.parent.name, "test": f"(无法解析) {xml_file.name}", "message": ""})
            continue
        if root.tag != "testsuite":
            continue
        suite = root.get("name", xml_file.parent.name)
        for case in root.iter("testcase"):
            problem = case.find("failure")
            if problem is None:
                problem = case.find("error")
            if problem is None:
                continue
            message = (problem.get("message") or problem.text or "").strip().splitlines()
            failures.append(
                {
                    "suite": suite,
                    "test": f"{case.get('classname', '?')}.{case.get('name', '?')}",
                    "message": message[0][:300] if message else "",
                }
            )
    return failures[:MAX_FAILURES]


def collect_log_tails(run_dirs: list[Path]) -> list[dict]:
    tails: list[dict] = []
    for run_dir in run_dirs:
        log = run_dir / "smoke.log"
        if not log.is_file():
            continue
        lines = log.read_text(encoding="utf-8", errors="replace").splitlines()
        tails.append({"path": str(log), "lines": lines[-MAX_LOG_LINES:]})
        if len(tails) >= MAX_LOG_FILES:
            break
    return tails


def git(*args: str) -> list[str]:
    out = subprocess.run(["git", *args], capture_output=True, text=True)
    return [line for line in out.stdout.splitlines() if line.strip()]


def render(triage: Triage) -> str:
    parts = ["## 分诊\n"]

    parts.append(f"### 失败用例（{len(triage.failed_tests)}）\n")
    if triage.failed_tests:
        for item in triage.failed_tests:
            parts.append(f"- `{item['test']}` ({item['suite']})")
            if item["message"]:
                parts.append(f"  - {item['message']}")
    else:
        parts.append("_无失败用例（若失败发生在编译/起服/断言环节，见下方日志）_\n")

    parts.append(f"\n### 服务端日志尾部（{len(triage.log_tails)}）\n")
    if triage.log_tails:
        for tail in triage.log_tails:
            parts.append(f"<details><summary><code>{tail['path']}</code>（末尾 {len(tail['lines'])} 行）</summary>\n")
            parts.append("```")
            parts.extend(tail["lines"])
            parts.append("```\n</details>\n")
    else:
        parts.append("_无 smoke.log（可能没跑到起服阶段）_\n")

    parts.append("\n### 最近提交\n```")
    parts.extend(triage.recent_commits or ["(无)"])
    parts.append("```\n")

    if triage.git_status:
        parts.append("\n### 工作区改动\n```")
        parts.extend(triage.git_status[:20])
        parts.append("```\n")

    return "\n".join(parts)


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--results", default="build/test-results")
    parser.add_argument("--run-dir", action="append", default=None)
    parser.add_argument("--out", default="build/triage/triage.md")
    parser.add_argument("--json", default="build/triage/triage.json")
    args = parser.parse_args()

    run_dirs = [
        Path(p)
        for p in (
            args.run_dir
            or [
                "run-e2e-paper",
                "run-e2e-folia",
                "run-e2e-folia-lp",
                "run-folia-smoke",
                "run-e2e-im",
            ]
        )
    ]
    triage = Triage(
        failed_tests=collect_failed_tests(Path(args.results)),
        log_tails=collect_log_tails(run_dirs),
        recent_commits=git("log", "--oneline", "-8"),
        git_status=git("status", "--porcelain"),
    )

    out = Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(render(triage), encoding="utf-8")

    json_out = Path(args.json)
    json_out.parent.mkdir(parents=True, exist_ok=True)
    json_out.write_text(json.dumps(asdict(triage), ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    print(f"分诊已写入 {out}（失败用例 {len(triage.failed_tests)}，日志 {len(triage.log_tails)}）")


if __name__ == "__main__":
    main()
