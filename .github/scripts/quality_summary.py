#!/usr/bin/env python3
"""Resume cobertura (JaCoCo CSV) y mutation score (PIT XML) en Markdown para el resumen del job.

Uso: python quality_summary.py [--jacoco target/site/jacoco/jacoco.csv] [--pit target/pit-reports/mutations.xml]
Solo informa: los gates reales los aplican jacoco:check y pitest (mutationThreshold).
"""
import argparse
import csv
import os
import sys
import xml.etree.ElementTree as ET


def pct(covered: int, missed: int) -> float:
    total = covered + missed
    return 100.0 * covered / total if total else 100.0


def jacoco_summary(path: str) -> list[str]:
    if not os.path.exists(path):
        return [f"- Cobertura: reporte no encontrado ({path})"]
    line_c = line_m = br_c = br_m = 0
    with open(path, newline="", encoding="utf-8") as fh:
        for row in csv.DictReader(fh):
            line_c += int(row["LINE_COVERED"])
            line_m += int(row["LINE_MISSED"])
            br_c += int(row["BRANCH_COVERED"])
            br_m += int(row["BRANCH_MISSED"])
    lines, branches = pct(line_c, line_m), pct(br_c, br_m)
    icon = lambda v, t: "✅" if v >= t else "❌"
    return [
        "| Métrica | Valor | Umbral | Estado |",
        "|---|---|---|---|",
        f"| Cobertura de líneas | {lines:.1f}% ({line_c}/{line_c + line_m}) | 80% | {icon(lines, 80)} |",
        f"| Cobertura de ramas | {branches:.1f}% ({br_c}/{br_c + br_m}) | 80% | {icon(branches, 80)} |",
    ]


def pit_summary(path: str) -> list[str]:
    if not os.path.exists(path):
        return [f"| Mutation score | reporte no encontrado | 70% | ⚠️ |"]
    mutations = ET.parse(path).getroot().findall("mutation")
    detected = sum(1 for m in mutations if m.get("detected") == "true")
    total = len(mutations)
    score = pct(detected, total - detected)
    survived = [m for m in mutations if m.get("status") == "SURVIVED"]
    lines = [f"| Mutation score | {score:.1f}% ({detected}/{total}) | 70% | {'✅' if score >= 70 else '❌'} |"]
    if survived:
        lines += ["", "<details><summary>Mutantes sobrevivientes</summary>", ""]
        for m in survived[:30]:
            lines.append(f"- `{m.findtext('mutatedClass')}#{m.findtext('mutatedMethod')}` "
                         f"línea {m.findtext('lineNumber')}: {m.findtext('description')}")
        lines += ["", "</details>"]
    return lines


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--jacoco", default="target/site/jacoco/jacoco.csv")
    parser.add_argument("--pit", default="target/pit-reports/mutations.xml")
    args = parser.parse_args()
    out = ["## Quality Gates", ""] + jacoco_summary(args.jacoco) + pit_summary(args.pit)
    text = "\n".join(out) + "\n"
    summary_file = os.environ.get("GITHUB_STEP_SUMMARY")
    if summary_file:
        with open(summary_file, "a", encoding="utf-8") as fh:
            fh.write(text)
    sys.stdout.write(text)
    return 0


if __name__ == "__main__":
    sys.exit(main())
