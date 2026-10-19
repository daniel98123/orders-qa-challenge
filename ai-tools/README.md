# ai-tools · Ingeniería de calidad asistida por IA

| Componente | Qué hace | IA |
|---|---|---|
| `traceability/traceability.py` | Matriz de trazabilidad RN → pruebas (unit, Karate, Playwright), cobertura de la máquina de estados (25 transiciones), priorización por riesgo, generación de esqueletos Karate para brechas y **quality gate** en CI. | Determinista. `--suggest` (opcional) pide a Claude priorización y escenarios extra. |
| `../.claude/skills/qa-test-designer` | Skill para asistentes de IA (Claude Code): procedimiento para diseñar pruebas por riesgo y capa de la pirámide al cambiar reglas. | Guía el uso de IA en el repo. |

## Uso

```bash
# Matriz + gate (lo que corre el pipeline)
python ai-tools/traceability/traceability.py --out target/traceability --fail-on-gaps

# Con sugerencias de IA (opcional)
export ANTHROPIC_API_KEY=...              # nunca se versiona
export ANTHROPIC_MODEL=claude-sonnet-5-5  # opcional
python ai-tools/traceability/traceability.py --suggest

# Pruebas de la herramienta
python -m unittest discover -s ai-tools/traceability/tests -v
```

Salidas en `target/traceability/`: `traceability.md`, `traceability.json`, `missing-scenarios.feature`
y, con `--suggest`, `ai-suggestions.md` (marcado como BORRADOR).

## Principios de uso responsable
1. **Lo determinista decide, la IA sugiere.** El gate del pipeline no depende de un LLM (resultados
   reproducibles y auditables).
2. **Mínimo dato.** A la API solo se envía el resumen de brechas (ids de reglas, estados); nunca código,
   datos de clientes ni secretos.
3. **Humano en el ciclo.** Lo generado (`missing-scenarios.feature`, `ai-suggestions.md`) va con `@ignore`
   o marcado como borrador; se incorpora solo tras revisión.
4. **Evidencia real.** La herramienta encontró 9 transiciones de prioridad ALTA sin prueba de aceptación:
   3 ya se probaban pero no estaban trazadas (se etiquetaron) y 6 eran escenarios nuevos, que se agregaron
   en `orders-adicionales.feature`. La cobertura de aceptación pasó de 11/25 a 20/25. Las 5 restantes
   (prioridad MEDIA) se aceptan como riesgo residual porque están cubiertas en unit (25/25).
