#!/usr/bin/env python3
"""覆盖率热点：按「未覆盖分支数」排出最该补测试的类。

为什么按分支而不是行：行覆盖容易靠"执行过"拿到，分支才是最容易被漏掉的判定路径
（异常/降级/边界）。输出直接写进质量趋势看板的 issue，作为持续补测的待办清单。

用法：
  python3 scripts/quality/coverage_hotspots.py --jacoco build/reports/jacoco/test/jacocoTestReport.xml \
      --out build/quality/coverage-hotspots.md [--top 10]
"""

from __future__ import annotations

import argparse
from pathlib import Path
from xml.etree import ElementTree


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--jacoco", default="build/reports/jacoco/test/jacocoTestReport.xml")
    parser.add_argument("--out", default="build/quality/coverage-hotspots.md")
    parser.add_argument("--top", type=int, default=10)
    args = parser.parse_args()

    root = ElementTree.parse(Path(args.jacoco)).getroot()
    rows: list[tuple[int, int, str]] = []
    for package in root.findall("package"):
        for clazz in package.findall("class"):
            for counter in clazz.findall("counter"):
                if counter.get("type") != "BRANCH":
                    continue
                missed = int(counter.get("missed", "0"))
                covered = int(counter.get("covered", "0"))
                if missed > 0:
                    name = clazz.get("name", "?").split("/")[-1]
                    rows.append((missed, covered, name))

    rows.sort(reverse=True)
    total_missed = sum(r[0] for r in rows)
    table = [
        "### 覆盖率热点（按未覆盖分支排序）",
        "",
        f"未覆盖分支合计 **{total_missed}**；下表是收益最高的 {min(args.top, len(rows))} 个类。",
        "",
        "| 类 | 未覆盖分支 | 已覆盖分支 | 分支覆盖率 |",
        "|:--|--:|--:|--:|",
    ]
    for missed, covered, name in rows[: args.top]:
        total = missed + covered
        rate = f"{covered * 100.0 / total:.0f}%" if total else "-"
        table.append(f"| `{name}` | {missed} | {covered} | {rate} |")

    out = Path(args.out)
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text("\n".join(table) + "\n", encoding="utf-8")
    print("\n".join(table))


if __name__ == "__main__":
    main()
