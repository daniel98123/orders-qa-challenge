#!/usr/bin/env python3
"""
Matriz de trazabilidad y detección de escenarios faltantes (Plus de innovación).

Qué hace (determinista, sin IA):
  1. Lee el catálogo de reglas (docs/requirements.json) con su riesgo (impacto x probabilidad).
  2. Lee la máquina de estados directamente del código (OrderStatus.java): única fuente de verdad.
  3. Recorre las pruebas de las tres capas de la pirámide y extrae qué cubre cada una:
       - unit       -> @Tag("RN-xx") en JUnit y tablas @CsvSource de transiciones
       - acceptance -> tags @RN-xx / @transition=ORIGEN_DESTINO y tablas Examples origen|destino en Karate
       - e2e        -> "@RN-xx" en los títulos de Playwright
  4. Genera: matriz RN x capa, cobertura de las 25 transiciones, priorización por riesgo y un
     .feature con esqueletos Karate para las transiciones sin prueba de aceptación.
  5. Con --fail-on-gaps actúa como quality gate: falla si una regla no está probada en sus capas requeridas.

Asistencia con IA (opcional, --suggest):
  Envía SOLO el resumen de brechas (no código ni datos sensibles) a la API de Claude y guarda las
  sugerencias como BORRADOR para revisión humana. Nunca modifica pruebas ni afecta el gate.

Uso:
  python ai-tools/traceability/traceability.py [--root .] [--out target/traceability] [--fail-on-gaps] [--suggest]
"""
from __future__ import annotations

import argparse
import json
import os
import re
import sys
import urllib.error
import urllib.request
from dataclasses import dataclass, field
from pathlib import Path

STATES = ["CREADA", "PAGADA", "ENVIADA", "ENTREGADA", "CANCELADA"]
TERMINAL_HINT = {"ENTREGADA", "CANCELADA"}
LAYERS = ["unit", "acceptance", "e2e"]
RULE_RE = re.compile(r"RN-\d{2}")
STATE_ALT = "|".join(STATES)


# --------------------------------------------------------------------------------------------
# Modelo
# --------------------------------------------------------------------------------------------
@dataclass
class Coverage:
    # regla -> capa -> lista de referencias "archivo::prueba"
    rules: dict[str, dict[str, list[str]]] = field(default_factory=dict)
    # (origen, destino) -> capa -> referencias
    transitions: dict[tuple[str, str], dict[str, list[str]]] = field(default_factory=dict)

    def add_rule(self, rule: str, layer: str, ref: str) -> None:
        refs = self.rules.setdefault(rule, {}).setdefault(layer, [])
        if ref not in refs:
            refs.append(ref)

    def add_transition(self, origin: str, target: str, layer: str, ref: str) -> None:
        refs = self.transitions.setdefault((origin, target), {}).setdefault(layer, [])
        if ref not in refs:
            refs.append(ref)


# --------------------------------------------------------------------------------------------
# Máquina de estados (desde el código fuente)
# --------------------------------------------------------------------------------------------
def parse_state_machine(java_source: str) -> dict[str, set[str]]:
    """Extrae ALLOWED.put(X, EnumSet.of(A, B)) / EnumSet.noneOf(...) de OrderStatus.java."""
    allowed: dict[str, set[str]] = {}
    for origin, body in re.findall(r"ALLOWED\.put\(\s*(\w+)\s*,\s*EnumSet\.(.*?)\)\s*;", java_source, re.S):
        if body.startswith("noneOf"):
            allowed[origin] = set()
        else:
            allowed[origin] = set(re.findall(rf"\b({STATE_ALT})\b", body.replace("of(", "", 1)))
    missing = [s for s in STATES if s not in allowed]
    if missing:
        raise ValueError(f"No se encontró la definición de transiciones para: {missing}")
    return allowed


# --------------------------------------------------------------------------------------------
# Escáneres por capa
# --------------------------------------------------------------------------------------------
def scan_junit(path: Path, text: str, cov: Coverage) -> None:
    name = path.stem
    # Tag a nivel de clase: aplica a todas sus pruebas
    for rule, cls in re.findall(r'@Tag\("(RN-\d{2})"\)\s*(?:@\w+(?:\([^)]*\))?\s*)*class\s+(\w+)', text):
        cov.add_rule(rule, "unit", f"{name}::{cls}")
    # Tag a nivel de método: el siguiente "void nombre(" sin cruzar una declaración de clase
    for rule, method in re.findall(r'@Tag\("(RN-\d{2})"\)(?:(?!\bclass\s)[^{;])*?\bvoid\s+(\w+)\s*\(', text, re.S):
        cov.add_rule(rule, "unit", f"{name}::{method}")
    # Filas de @CsvSource con pares de estados: "CREADA, PAGADA, ..." o "CREADA, PAGADA"
    for block in re.findall(r"@CsvSource\(\{(.*?)\}\)", text, re.S):
        for origin, target in re.findall(rf'"\s*({STATE_ALT})\s*,\s*({STATE_ALT})\b', block):
            cov.add_transition(origin, target, "unit", name)


def _karate_scenarios(text: str):
    """Itera (titulo, tags, filas_examples) por escenario de un .feature de Karate."""
    feature_tags: list[str] = []
    pending: list[str] = []
    current = None
    examples_header: list[str] | None = None
    seen_feature = False
    for raw in text.splitlines():
        line = raw.strip()
        if not line or line.startswith("#"):
            continue
        if line.startswith("@"):
            pending.extend(line.split())
            continue
        if line.startswith("Feature:"):
            feature_tags, pending, seen_feature = pending, [], True
            continue
        if re.match(r"Scenario( Outline)?:", line):
            if current:
                yield current
            current = {"title": line.split(":", 1)[1].strip(), "tags": feature_tags + pending, "rows": []}
            pending, examples_header = [], None
            continue
        if line.startswith("Examples:") and current is not None:
            current["tags"] = current["tags"] + pending
            pending, examples_header = [], None
            continue
        if line.startswith("|") and current is not None:
            cells = [c.strip() for c in line.strip("|").split("|")]
            if examples_header is None:
                examples_header = [c.rstrip("!") for c in cells]
            else:
                current["rows"].append(dict(zip(examples_header, cells)))
            continue
        pending = []  # tags sueltos no aplican a pasos
    if current:
        yield current
    _ = seen_feature


def scan_karate(path: Path, text: str, cov: Coverage) -> None:
    for scenario in _karate_scenarios(text):
        if "@ignore" in scenario["tags"]:
            continue
        ref = f"{path.name}::{scenario['title']}"
        for tag in scenario["tags"]:
            if RULE_RE.fullmatch(tag.lstrip("@")):
                cov.add_rule(tag.lstrip("@"), "acceptance", ref)
            if tag.startswith("@transition="):
                for pair in tag.split("=", 1)[1].split(","):
                    origin, _, target = pair.partition("_")
                    if origin in STATES and target in STATES:
                        cov.add_transition(origin, target, "acceptance", ref)
        for row in scenario["rows"]:
            if row.get("origen") in STATES and row.get("destino") in STATES:
                cov.add_transition(row["origen"], row["destino"], "acceptance", ref)


def scan_playwright(path: Path, text: str, cov: Coverage) -> None:
    for title in re.findall(r"""\btest\(\s*(['"`])(.*?)\1""", text):
        for rule in RULE_RE.findall(title[1]):
            cov.add_rule(rule, "e2e", f"{path.name}::{title[1]}")


def scan_repository(root: Path) -> Coverage:
    cov = Coverage()
    for path in sorted((root / "src" / "test" / "java").rglob("*.java")):
        scan_junit(path, path.read_text(encoding="utf-8"), cov)
    for path in sorted((root / "src" / "test" / "resources").rglob("*.feature")):
        scan_karate(path, path.read_text(encoding="utf-8"), cov)
    e2e_dir = root / "e2e" / "tests"
    if e2e_dir.exists():
        for path in sorted(e2e_dir.rglob("*.spec.ts")):
            scan_playwright(path, path.read_text(encoding="utf-8"), cov)
    return cov


# --------------------------------------------------------------------------------------------
# Análisis
# --------------------------------------------------------------------------------------------
def transition_risk(origin: str, target: str, allowed: dict[str, set[str]]) -> str:
    """Prioridad de prueba de una transición (estrategia basada en riesgo)."""
    if target in allowed[origin]:
        return "ALTA"  # camino de negocio real: si falla, se bloquea la operación
    if origin in TERMINAL_HINT or (origin == "CREADA" and target in {"ENVIADA", "ENTREGADA"}):
        return "ALTA"  # "resucitar" estados finales o saltarse el pago: impacto financiero/logístico
    if origin == target:
        return "MEDIA"  # reintentos / idempotencia
    return "MEDIA"


def build_report(requirements: dict, allowed: dict[str, set[str]], cov: Coverage) -> dict:
    rules = []
    for rule in requirements["reglas"]:
        layers = cov.rules.get(rule["id"], {})
        missing = [layer for layer in rule["capas_requeridas"] if not layers.get(layer)]
        rules.append({
            "id": rule["id"],
            "descripcion": rule["descripcion"],
            "riesgo": rule["impacto"] * rule["probabilidad"],
            "pruebas": {layer: layers.get(layer, []) for layer in LAYERS},
            "capas_faltantes": missing,
        })
    rules.sort(key=lambda r: (-r["riesgo"], r["id"]))

    transitions = []
    for origin in STATES:
        for target in STATES:
            layers = cov.transitions.get((origin, target), {})
            transitions.append({
                "origen": origin,
                "destino": target,
                "permitida": target in allowed[origin],
                "prioridad": transition_risk(origin, target, allowed),
                "unit": bool(layers.get("unit")),
                "acceptance": bool(layers.get("acceptance")),
            })
    gaps = [t for t in transitions if not t["acceptance"]]
    gaps.sort(key=lambda t: (t["prioridad"] != "ALTA", t["origen"], t["destino"]))
    return {"reglas": rules, "transiciones": transitions, "transiciones_sin_aceptacion": gaps}


def expected_status(origin: str, target: str, allowed: dict[str, set[str]]) -> tuple[int, str]:
    if target in allowed[origin]:
        return 200, ""
    if origin == "CREADA" and target == "ENVIADA":
        return 400, "RN-05"
    if origin == "ENTREGADA" and target == "CANCELADA":
        return 409, "RN-04"
    return 409, "RN-06"


def path_to(state: str) -> list[str]:
    return {
        "CREADA": [],
        "PAGADA": ["PAGADA"],
        "ENVIADA": ["PAGADA", "ENVIADA"],
        "ENTREGADA": ["PAGADA", "ENVIADA", "ENTREGADA"],
        "CANCELADA": ["CANCELADA"],
    }[state]


def generate_karate_skeleton(gaps: list[dict], allowed: dict[str, set[str]]) -> str:
    """Genera escenarios Karate listos para revisar a partir de la máquina de estados."""
    lines = [
        "# GENERADO AUTOMÁTICAMENTE por ai-tools/traceability/traceability.py - REVISAR ANTES DE INCORPORAR",
        "# Transiciones sin prueba de aceptación, ordenadas por prioridad de riesgo.",
        "@ignore @generado",
        "Feature: Transiciones pendientes de cubrir en aceptación",
        "",
        "  Background:",
        "    * url baseUrl",
        "    * def createOrder = function(){ return karate.call('classpath:karate/orders/common/create-order.feature').orderId }",
        "    * def moveTo = function(id, estados){ for (var i = 0; i < estados.length; i++) karate.call('classpath:karate/orders/common/change-status.feature', { id: id, estado: estados[i] }) }",
        "",
    ]
    for gap in gaps:
        origin, target = gap["origen"], gap["destino"]
        status, code = expected_status(origin, target, allowed)
        tags = f"@prioridad-{gap['prioridad'].lower()} @transition={origin}_{target}" + (f" @{code}" if code else "")
        camino = ", ".join(f"'{s}'" for s in path_to(origin))
        lines += [
            f"  {tags}",
            f"  Scenario: {origin} -> {target} responde {status}",
            "    * def orderId = createOrder()",
            f"    * eval moveTo(orderId, [{camino}])",
            "    Given path 'orders', orderId, 'status'",
            f"    And request {{ estado: '{target}' }}",
            "    When method patch",
            f"    Then status {status}",
        ]
        if code:
            lines.append(f"    And match response.code == '{code}'")
        lines.append("")
    return "\n".join(lines)


# --------------------------------------------------------------------------------------------
# Salidas
# --------------------------------------------------------------------------------------------
def render_markdown(report: dict) -> str:
    check = lambda refs: f"✅ {len(refs)}" if refs else "—"
    out = [
        "## Matriz de trazabilidad (RN → pruebas)",
        "",
        "| Regla | Riesgo | Unit | Aceptación (Karate) | E2E (Playwright) | Estado |",
        "|---|---|---|---|---|---|",
    ]
    for r in report["reglas"]:
        estado = "✅ OK" if not r["capas_faltantes"] else f"❌ falta: {', '.join(r['capas_faltantes'])}"
        out.append(f"| **{r['id']}** {r['descripcion']} | {r['riesgo']} | {check(r['pruebas']['unit'])} | "
                   f"{check(r['pruebas']['acceptance'])} | {check(r['pruebas']['e2e'])} | {estado} |")

    total = len(report["transiciones"])
    unit = sum(t["unit"] for t in report["transiciones"])
    acc = sum(t["acceptance"] for t in report["transiciones"])
    out += [
        "",
        "## Cobertura de la máquina de estados",
        "",
        f"- Transiciones probadas en unit: **{unit}/{total}**",
        f"- Transiciones probadas en aceptación: **{acc}/{total}**",
        "",
        "| Origen \\ Destino | " + " | ".join(STATES) + " |",
        "|---|" + "---|" * len(STATES),
    ]
    index = {(t["origen"], t["destino"]): t for t in report["transiciones"]}
    for origin in STATES:
        cells = []
        for target in STATES:
            t = index[(origin, target)]
            mark = "✔️" if t["permitida"] else "✖️"
            layers = ("U" if t["unit"] else "·") + ("A" if t["acceptance"] else "·")
            cells.append(f"{mark} `{layers}`")
        out.append(f"| **{origin}** | " + " | ".join(cells) + " |")
    out += ["", "✔️ permitida · ✖️ inválida · `U` unit · `A` aceptación", ""]

    gaps = report["transiciones_sin_aceptacion"]
    if gaps:
        out += ["## Escenarios de aceptación faltantes (priorizados por riesgo)", ""]
        for g in gaps:
            out.append(f"- [{g['prioridad']}] {g['origen']} → {g['destino']}"
                       f" ({'permitida' if g['permitida'] else 'inválida'})")
        out += ["", "Esqueletos Karate generados en `missing-scenarios.feature`.", ""]
    return "\n".join(out)


def ask_claude(report: dict, api_key: str, model: str) -> str:
    """Pide sugerencias a Claude enviando solo el resumen de brechas (sin código ni datos)."""
    summary = {
        "reglas": [{k: r[k] for k in ("id", "descripcion", "riesgo", "capas_faltantes")} for r in report["reglas"]],
        "transiciones_sin_aceptacion": report["transiciones_sin_aceptacion"],
    }
    prompt = (
        "Eres un ingeniero QA senior. Para una API REST de órdenes de compra con máquina de estados "
        "(CREADA, PAGADA, ENVIADA, ENTREGADA, CANCELADA) tienes este resumen de trazabilidad:\n\n"
        f"{json.dumps(summary, ensure_ascii=False, indent=2)}\n\n"
        "1) Prioriza las brechas por riesgo y justifica en una línea cada una.\n"
        "2) Propón hasta 8 escenarios adicionales en Gherkin (estilo Karate) que NO estén cubiertos: "
        "valores límite, concurrencia, idempotencia, datos maliciosos y contrato.\n"
        "3) Indica qué capa de la pirámide (unit, aceptación, e2e) es la más barata para cada uno.\n"
        "Responde en español y en Markdown."
    )
    payload = json.dumps({
        "model": model,
        "max_tokens": 2000,
        "messages": [{"role": "user", "content": prompt}],
    }).encode("utf-8")
    request = urllib.request.Request(
        "https://api.anthropic.com/v1/messages",
        data=payload,
        headers={"x-api-key": api_key, "anthropic-version": "2023-06-01", "content-type": "application/json"},
        method="POST",
    )
    with urllib.request.urlopen(request, timeout=90) as response:
        body = json.loads(response.read().decode("utf-8"))
    return "".join(block.get("text", "") for block in body.get("content", []))


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--root", default=".", help="Raíz del repositorio")
    parser.add_argument("--out", default="target/traceability", help="Directorio de salida")
    parser.add_argument("--fail-on-gaps", action="store_true", help="Falla si una regla no tiene sus capas requeridas")
    parser.add_argument("--suggest", action="store_true", help="Sugerencias con Claude (requiere ANTHROPIC_API_KEY)")
    args = parser.parse_args(argv)

    root = Path(args.root).resolve()
    out = Path(args.out)
    if not out.is_absolute():
        out = root / out
    out.mkdir(parents=True, exist_ok=True)

    requirements = json.loads((root / "docs" / "requirements.json").read_text(encoding="utf-8"))
    status_java = next((root / "src" / "main" / "java").rglob("OrderStatus.java"))
    allowed = parse_state_machine(status_java.read_text(encoding="utf-8"))
    report = build_report(requirements, allowed, scan_repository(root))

    (out / "traceability.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    markdown = render_markdown(report)
    (out / "traceability.md").write_text(markdown, encoding="utf-8")
    (out / "missing-scenarios.feature").write_text(
        generate_karate_skeleton(report["transiciones_sin_aceptacion"], allowed), encoding="utf-8")
    print(markdown)

    if args.suggest:
        api_key = os.environ.get("ANTHROPIC_API_KEY")
        if not api_key:
            print("⚠️  --suggest requiere ANTHROPIC_API_KEY; se omiten las sugerencias.", file=sys.stderr)
        else:
            model = os.environ.get("ANTHROPIC_MODEL", "claude-sonnet-5-5")
            try:
                text = ask_claude(report, api_key, model)
                (out / "ai-suggestions.md").write_text(
                    "> ⚠️ BORRADOR generado por IA. Requiere revisión humana antes de convertirse en pruebas.\n\n"
                    + text, encoding="utf-8")
                print(f"Sugerencias de IA guardadas en {out / 'ai-suggestions.md'}")
            except (urllib.error.URLError, TimeoutError, json.JSONDecodeError) as error:
                print(f"⚠️  No se pudieron obtener sugerencias de IA: {error}", file=sys.stderr)

    failing = [r for r in report["reglas"] if r["capas_faltantes"]]
    if args.fail_on_gaps and failing:
        print("❌ Quality gate de trazabilidad: reglas sin cobertura requerida -> "
              + ", ".join(f"{r['id']} ({', '.join(r['capas_faltantes'])})" for r in failing), file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
