#!/usr/bin/env python3
"""质量趋势快照：从测试结果 + JaCoCo XML 计算指标，维护一条「质量趋势看板」issue。

设计要点：
- 只依赖标准库 + `gh` CLI（CI 与本地同一条命令），不引入第三方依赖；
- 数据源是 gradle 产物（`build/test-results/*.xml` + `build/reports/jacoco/.../jacocoTestReport.xml`），
  与 `./gradlew check` 完全一致，不额外跑一遍测试；
- issue 正文用 HTML 标记块包裹表格：每次重写该块（保留最近 N 行），并发安全靠单条 issue + 幂等重写。

用法：
    python3 scripts/quality/trend.py snapshot                     # 打印 Markdown 行 + 写 build/quality/snapshot.json
    python3 scripts/quality/trend.py upsert-issue                 # 把最新快照写入/更新看板 issue（需要 gh 登录）
    python3 scripts/quality/trend.py snapshot --json path/to.json # 自定义快照路径

`upsert-issue` 需要环境变量 GH_REPO（`owner/repo`），CI 里由 workflow 注入。
"""

from __future__ import annotations

import argparse
import json
import os
import subprocess
import sys
import time
from dataclasses import dataclass, asdict
from datetime import date
from pathlib import Path
from xml.etree import ElementTree

MARK_START = "<!-- quality-trend:start -->"
MARK_END = "<!-- quality-trend:end -->"
LABEL = "quality-trend"
# 所有自动化维护的 issue 共用这个标签：方便一次性过滤（不跟人工 issue 混在一起）
COMMON_LABEL = "automation"
ISSUE_TITLE = "quality: 质量趋势看板（夜间自动更新）"
MAX_ROWS = 30

# 门禁阈值（与 build.gradle.kts 的 jacocoTestCoverageVerification 保持一致）
THRESHOLDS = {"instruction": 78.0, "branch": 65.0, "line": 78.0}
# 相比上一行允许的正常波动（百分点）；超过即标记 ⚠️ 提示人工看一眼
DRIFT_TOLERANCE = 1.0

BODY_HEADER = """\
> 每日 01:30 夜间闭环自动更新（保留最近 30 次）。数据源：`build/test-results/*.xml` + JaCoCo XML，
> 与 `./gradlew check` 同一批产物。门禁：单测/集成必须全绿；覆盖率 INSTRUCTION ≥78% / BRANCH ≥65% / LINE ≥78%。
>
> 图例：✅ 全部达标；⚠️ 覆盖率跌 >1 个百分点 / 用例数减少 / 启动耗时 >2.5 倍 / 备份耗时 >3 倍（后两者为宽松提示，CI 机器差异大）。

"""


@dataclass
class Snapshot:
    date: str
    unit_tests: int
    integration_tests: int
    unit_failures: int
    integration_failures: int
    instruction: float
    branch: float
    line: float
    boot_ms: int | None = None
    backup_ms: int | None = None
    verdict: str = "✅"

    def row(self) -> str:
        boot = "-" if self.boot_ms is None else str(self.boot_ms)
        backup = "-" if self.backup_ms is None else str(self.backup_ms)
        return (
            f"| {self.date} | {self.unit_tests} | {self.integration_tests} "
            f"| {self.instruction:.1f}% | {self.branch:.1f}% | {self.line:.1f}% "
            f"| {boot} | {backup} | {self.verdict} |"
        )


def count_tests(results_dir: Path) -> tuple[int, int]:
    """返回 (用例数, 失败数+错误数)；目录不存在视为 0（调用方负责报错）。"""
    total = failed = 0
    if not results_dir.is_dir():
        return 0, 0
    for xml_file in sorted(results_dir.glob("*.xml")):
        try:
            root = ElementTree.parse(xml_file).getroot()
        except ElementTree.ParseError:
            continue
        if root.tag != "testsuite":
            continue
        total += int(root.get("tests", "0"))
        failed += int(root.get("failures", "0")) + int(root.get("errors", "0"))
    return total, failed


def read_coverage(jacoco_xml: Path) -> dict[str, float]:
    root = ElementTree.parse(jacoco_xml).getroot()
    coverage: dict[str, float] = {}
    wanted = {"INSTRUCTION": "instruction", "BRANCH": "branch", "LINE": "line"}
    for counter in root.findall("counter"):
        name = counter.get("type")
        if name in wanted:
            missed = int(counter.get("missed", "0"))
            covered = int(counter.get("covered", "0"))
            total = missed + covered
            coverage[wanted[name]] = round(covered * 100.0 / total, 1) if total else 0.0
    missing = {v for v in wanted.values()} - set(coverage)
    if missing:
        raise SystemExit(f"JaCoCo 报告缺少计数器: {sorted(missing)}（{jacoco_xml}）")
    return coverage


def read_perf(perf_dir: Path, label: str) -> tuple[int | None, int | None]:
    """读取自举 E2E 落下的性能快照（boot_ms / backup_ms）；没有就返回 (None, None)。"""
    path = perf_dir / f"perf-{label}.json"
    if not path.is_file():
        return None, None
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
    except json.JSONDecodeError:
        return None, None
    boot = data.get("boot_ms")
    backup = data.get("backup_ms")
    return (int(boot) if isinstance(boot, int) else None, int(backup) if isinstance(backup, int) else None)


def build_snapshot(test_results: Path, jacoco_xml: Path, perf_dir: Path | None = None) -> Snapshot:
    unit_total, unit_failed = count_tests(test_results / "test")
    it_total, it_failed = count_tests(test_results / "integrationTest")
    if unit_total == 0:
        raise SystemExit(f"未找到单测结果（{test_results}/test）——先跑 ./gradlew test")
    if it_total == 0:
        raise SystemExit(f"未找到集成测试结果（{test_results}/integrationTest）——先跑 ./gradlew integrationTest")
    coverage = read_coverage(jacoco_xml)
    verdict = "✅"
    if unit_failed or it_failed:
        verdict = "❌"
    elif any(coverage[k] < v for k, v in THRESHOLDS.items()):
        verdict = "❌"
    boot_ms, backup_ms = read_perf(perf_dir, "paper") if perf_dir else (None, None)
    return Snapshot(
        date=date.today().isoformat(),
        boot_ms=boot_ms,
        backup_ms=backup_ms,
        unit_tests=unit_total,
        integration_tests=it_total,
        unit_failures=unit_failed,
        integration_failures=it_failed,
        instruction=coverage["instruction"],
        branch=coverage["branch"],
        line=coverage["line"],
        verdict=verdict,
    )


def parse_rows(body: str) -> list[str]:
    """取标记块内的数据行（以 `| ` 开头且不是表头/分隔行）。"""
    if MARK_START not in body or MARK_END not in body:
        return []
    block = body.split(MARK_START, 1)[1].split(MARK_END, 1)[0]
    rows = []
    for line in block.strip().splitlines():
        line = line.strip()
        if not line.startswith("|"):
            continue
        if set(line) <= set("|-: "):
            continue
        if line.startswith("| 日期"):
            continue
        rows.append(line)
    return rows


def row_metrics(row: str) -> Snapshot | None:
    """从一行表格还原指标，用于与上一行比较。"""
    cells = [c.strip() for c in row.strip().strip("|").split("|")]
    if len(cells) < 7:
        return None

    def as_int(value: str) -> int | None:
        return int(value) if value.isdigit() else None

    try:
        # 9 列（新）：date|unit|integration|instr|branch|line|boot|backup|verdict
        # 7 列（旧）：date|unit|integration|instr|branch|line|verdict
        perf = len(cells) >= 9
        return Snapshot(
            date=cells[0],
            unit_tests=int(cells[1]),
            integration_tests=int(cells[2]),
            unit_failures=0,
            integration_failures=0,
            instruction=float(cells[3].rstrip("%")),
            branch=float(cells[4].rstrip("%")),
            line=float(cells[5].rstrip("%")),
            boot_ms=as_int(cells[6]) if perf else None,
            backup_ms=as_int(cells[7]) if perf else None,
        )
    except ValueError:
        return None


def check_drift(snapshot: Snapshot, previous_row: str | None) -> None:
    """与上一次比较，出现明显劣化就把 verdict 改成 ⚠️（不失败，只提示）。"""
    previous = row_metrics(previous_row) if previous_row else None
    if previous is None:
        return
    reasons = []
    for name in ("instruction", "branch", "line"):
        before, after = getattr(previous, name), getattr(snapshot, name)
        if before - after > DRIFT_TOLERANCE:
            reasons.append(f"{name} {before:.1f}%→{after:.1f}%")
    if snapshot.unit_tests < previous.unit_tests:
        reasons.append(f"单测 {previous.unit_tests}→{snapshot.unit_tests}")
    if snapshot.integration_tests < previous.integration_tests:
        reasons.append(f"集成 {previous.integration_tests}→{snapshot.integration_tests}")
    # 性能只做"明显劣化"提示（CI 机器差异大，阈值放很宽）
    if previous.boot_ms and snapshot.boot_ms and snapshot.boot_ms > previous.boot_ms * 2.5:
        reasons.append(f"启动 {previous.boot_ms}→{snapshot.boot_ms}ms")
    if previous.backup_ms and snapshot.backup_ms and snapshot.backup_ms > previous.backup_ms * 3:
        reasons.append(f"备份 {previous.backup_ms}→{snapshot.backup_ms}ms")
    if reasons and snapshot.verdict == "✅":
        snapshot.verdict = "⚠️"
        print("质量指标较上次下降: " + ", ".join(reasons), file=sys.stderr)


def render_body(rows: list[str], hotspots: str = "") -> str:
    table = [
        "| 日期 | 单测 | 集成 | INSTRUCTION | BRANCH | LINE | 启动(ms) | 备份(ms) | 结论 |",
        "|:--|--:|--:|--:|--:|--:|--:|--:|:--|",
        *rows,
    ]
    return (
        BODY_HEADER
        + MARK_START
        + "\n"
        + "\n".join(table)
        + "\n"
        + MARK_END
        + "\n\n"
        + (hotspots.strip() + "\n\n" if hotspots.strip() else "")
        + "> 更新方式：`.github/workflows/nightly.yml` → `scripts/quality/trend.py upsert-issue`。"
        + "手工补数不要改本表，直接重跑夜间流程或本地 `python3 scripts/quality/trend.py upsert-issue`。\n"
    )


def gh(*args: str) -> str:
    return subprocess.run(["gh", *args], check=True, capture_output=True, text=True).stdout


def find_issue() -> int | None:
    """查找看板 issue。

    刚创建的 issue 在 list 接口上可能短暂不可见（GitHub 侧的最终一致性，实测 1–3 秒），
    不重试会导致「每次夜间都新开一条看板」——去重必须带重试。
    """
    repo = os.environ.get("GH_REPO") or os.environ.get("GITHUB_REPOSITORY")
    if not repo:
        raise SystemExit("缺少 GH_REPO（owner/repo）")
    for attempt in range(5):
        out = gh(
            "api",
            # 取最早的一条作为看板（避免历史重复 issue 抢走更新目标）；顺带保证它是 open
            f"repos/{repo}/issues?labels={LABEL}&state=all&sort=created&direction=asc&per_page=1",
            "--jq",
            ".[0].number // empty",
        ).strip()
        if out:
            number = int(out)
            # 被误关的看板自动恢复（幂等，不改变内容）
            gh("api", "-X", "PATCH", f"repos/{repo}/issues/{number}", "-f", "state=open")
            return number
        if attempt < 4:
            time.sleep(2)
    return None


def upsert_issue(snapshot: Snapshot, hotspots_path: Path | None = None) -> None:
    repo = os.environ["GH_REPO"] if "GH_REPO" in os.environ else os.environ["GITHUB_REPOSITORY"]
    number = find_issue()
    body = gh("api", f"repos/{repo}/issues/{number}", "--jq", ".body") if number else ""
    rows = parse_rows(body)
    check_drift(snapshot, rows[-1] if rows else None)
    hotspots = ""
    if hotspots_path and hotspots_path.is_file():
        hotspots = hotspots_path.read_text(encoding="utf-8")
    rows.append(snapshot.row())
    rows = rows[-MAX_ROWS:]
    new_body = render_body(rows, hotspots)
    if number:
        # 同一天重跑时替换当天行，避免重复
        rows = [r for i, r in enumerate(rows) if not (r.startswith(f"| {snapshot.date} ") and i != len(rows) - 1)]
        new_body = render_body(rows[-MAX_ROWS:], hotspots)
        gh("api", "-X", "PATCH", f"repos/{repo}/issues/{number}", "-f", f"body={new_body}")
        # 保证共用标签在（历史 issue 可能只有专属标签）
        subprocess.run(
            ["gh", "issue", "edit", str(number), "--repo", repo, "--add-label", COMMON_LABEL],
            capture_output=True, text=True, check=False,
        )
        print(f"已更新看板 issue #{number}")
    else:
        # 标签已存在时 API 返回 422，忽略即可（幂等）
        for name, color, desc in (
            (LABEL, "0E8A16", "夜间质量趋势看板（自动维护）"),
            (COMMON_LABEL, "FBCA04", "自动化维护的看板/追踪 issue（机器写入，勿手工改表）"),
        ):
            subprocess.run(
                ["gh", "api", f"repos/{repo}/labels", "-f", f"name={name}", "-f", f"color={color}",
                 "-f", f"description={desc}"],
                capture_output=True, text=True, check=False,
            )
        create = subprocess.run(
            ["gh", "issue", "create", "--repo", repo, "--label", LABEL, "--label", COMMON_LABEL,
             "--title", ISSUE_TITLE, "--body-file", "-"],
            input=new_body, capture_output=True, text=True, check=True,
        ).stdout.strip()
        print(f"已创建看板 issue: {create}")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("command", choices=["snapshot", "upsert-issue"])
    parser.add_argument("--test-results", default="build/test-results")
    parser.add_argument("--jacoco", default="build/reports/jacoco/test/jacocoTestReport.xml")
    parser.add_argument("--json", default="build/quality/snapshot.json")
    parser.add_argument("--perf-dir", default="build/quality", help="自举 E2E 性能快照目录")
    parser.add_argument("--hotspots", default="build/quality/coverage-hotspots.md", help="覆盖率热点 Markdown")
    args = parser.parse_args()

    snapshot = build_snapshot(Path(args.test_results), Path(args.jacoco), Path(args.perf_dir))
    out = Path(args.json)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(asdict(snapshot), ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    if args.command == "snapshot":
        print(snapshot.row())
        summary = os.environ.get("GITHUB_STEP_SUMMARY")
        if summary:
            with open(summary, "a", encoding="utf-8") as fh:
                fh.write("### 质量指标快照\n\n")
                fh.write("| 单测 | 集成 | INSTRUCTION | BRANCH | LINE | 结论 |\n")
                fh.write("|--:|--:|--:|--:|--:|:--|\n")
                fh.write(
                    f"| {snapshot.unit_tests} | {snapshot.integration_tests} "
                    f"| {snapshot.instruction:.1f}% | {snapshot.branch:.1f}% "
                    f"| {snapshot.line:.1f}% | {snapshot.verdict} |\n"
                )
    else:
        upsert_issue(snapshot, Path(args.hotspots))


if __name__ == "__main__":
    main()
