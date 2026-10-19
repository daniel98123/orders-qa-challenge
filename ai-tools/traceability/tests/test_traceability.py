"""Pruebas de la herramienta de trazabilidad (la herramienta de calidad también se prueba)."""
import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import traceability as trace  # noqa: E402

STATUS_JAVA = """
    static {
        ALLOWED.put(CREADA, EnumSet.of(PAGADA, CANCELADA));
        ALLOWED.put(PAGADA, EnumSet.of(ENVIADA, CANCELADA));
        ALLOWED.put(ENVIADA, EnumSet.of(ENTREGADA, CANCELADA));
        ALLOWED.put(ENTREGADA, EnumSet.noneOf(OrderStatus.class));
        ALLOWED.put(CANCELADA, EnumSet.noneOf(OrderStatus.class));
    }
"""

JUNIT = '''
@Tag("RN-06")
@DisplayName("x")
class OrderStatusTest {
    @ParameterizedTest
    @CsvSource({
            "CREADA,    PAGADA,    true",
            "ENTREGADA, CANCELADA, false"
    })
    void matrix(OrderStatus a, OrderStatus b, boolean c) {}

    @Tag("RN-05")
    @Test
    @DisplayName("RN-05")
    void cannotShipUnpaid() {}
}
'''

FEATURE = """
@ignore
Feature: helper
  Scenario: ignored
    * print 'x'
"""

FEATURE2 = """
Feature: f
  Background:
    * url baseUrl

  @obligatorio @RN-04 @transition=ENTREGADA_CANCELADA
  Scenario: cancelar entregada
    * print 1

  @RN-06
  Scenario Outline: invalida <origen> -> <destino>
    * print 1

    Examples:
      | origen | camino! | destino   |
      | CREADA | []      | ENTREGADA |
"""

SPEC = """
test('Crear orden @RN-03', async () => {});
test("sin regla", async () => {});
"""


class StateMachineTest(unittest.TestCase):
    def test_parses_allowed_transitions(self):
        allowed = trace.parse_state_machine(STATUS_JAVA)
        self.assertEqual(allowed["CREADA"], {"PAGADA", "CANCELADA"})
        self.assertEqual(allowed["ENTREGADA"], set())

    def test_fails_when_a_state_is_missing(self):
        with self.assertRaises(ValueError):
            trace.parse_state_machine('ALLOWED.put(CREADA, EnumSet.of(PAGADA));')


class ScannerTest(unittest.TestCase):
    def test_junit_class_and_method_tags_and_csv_transitions(self):
        cov = trace.Coverage()
        trace.scan_junit(Path("OrderStatusTest.java"), JUNIT, cov)
        self.assertIn("OrderStatusTest::OrderStatusTest", cov.rules["RN-06"]["unit"])
        self.assertIn("OrderStatusTest::cannotShipUnpaid", cov.rules["RN-05"]["unit"])
        self.assertIn(("CREADA", "PAGADA"), cov.transitions)
        self.assertIn(("ENTREGADA", "CANCELADA"), cov.transitions)

    def test_karate_tags_examples_and_ignore(self):
        cov = trace.Coverage()
        trace.scan_karate(Path("a.feature"), FEATURE, cov)
        self.assertEqual(cov.rules, {})
        trace.scan_karate(Path("b.feature"), FEATURE2, cov)
        self.assertEqual(cov.rules["RN-04"]["acceptance"], ["b.feature::cancelar entregada"])
        self.assertIn(("ENTREGADA", "CANCELADA"), cov.transitions)
        self.assertIn(("CREADA", "ENTREGADA"), cov.transitions)

    def test_playwright_titles(self):
        cov = trace.Coverage()
        trace.scan_playwright(Path("o.spec.ts"), SPEC, cov)
        self.assertEqual(list(cov.rules), ["RN-03"])


class ReportTest(unittest.TestCase):
    def setUp(self):
        self.allowed = trace.parse_state_machine(STATUS_JAVA)

    def test_expected_status_follows_business_rules(self):
        self.assertEqual(trace.expected_status("CREADA", "ENVIADA", self.allowed), (400, "RN-05"))
        self.assertEqual(trace.expected_status("ENTREGADA", "CANCELADA", self.allowed), (409, "RN-04"))
        self.assertEqual(trace.expected_status("CANCELADA", "PAGADA", self.allowed), (409, "RN-06"))
        self.assertEqual(trace.expected_status("CREADA", "PAGADA", self.allowed), (200, ""))

    def test_risk_prioritizes_terminal_states_and_valid_paths(self):
        self.assertEqual(trace.transition_risk("CANCELADA", "PAGADA", self.allowed), "ALTA")
        self.assertEqual(trace.transition_risk("CREADA", "PAGADA", self.allowed), "ALTA")
        self.assertEqual(trace.transition_risk("PAGADA", "PAGADA", self.allowed), "MEDIA")

    def test_gap_detection_and_skeleton(self):
        requirements = {"reglas": [
            {"id": "RN-04", "descripcion": "d", "impacto": 3, "probabilidad": 2, "capas_requeridas": ["unit", "acceptance"]}]}
        cov = trace.Coverage()
        cov.add_rule("RN-04", "acceptance", "x")
        report = trace.build_report(requirements, self.allowed, cov)
        self.assertEqual(report["reglas"][0]["capas_faltantes"], ["unit"])
        self.assertEqual(len(report["transiciones"]), 25)
        self.assertEqual(len(report["transiciones_sin_aceptacion"]), 25)
        skeleton = trace.generate_karate_skeleton(report["transiciones_sin_aceptacion"][:1], self.allowed)
        self.assertIn("@ignore @generado", skeleton)
        self.assertIn("Scenario:", skeleton)


class EndToEndTest(unittest.TestCase):
    def test_gate_fails_on_gaps_and_passes_when_covered(self):
        with tempfile.TemporaryDirectory() as tmp:
            root = Path(tmp)
            (root / "docs").mkdir()
            (root / "docs" / "requirements.json").write_text(json.dumps({"reglas": [
                {"id": "RN-05", "descripcion": "d", "impacto": 3, "probabilidad": 3,
                 "capas_requeridas": ["unit", "acceptance"]}]}), encoding="utf-8")
            src = root / "src" / "main" / "java" / "x"
            src.mkdir(parents=True)
            (src / "OrderStatus.java").write_text(STATUS_JAVA, encoding="utf-8")
            tests = root / "src" / "test" / "java"
            tests.mkdir(parents=True)
            (tests / "OrderStatusTest.java").write_text(JUNIT, encoding="utf-8")
            (root / "src" / "test" / "resources").mkdir(parents=True)

            self.assertEqual(trace.main(["--root", tmp, "--fail-on-gaps"]), 1)

            (root / "src" / "test" / "resources" / "f.feature").write_text(
                "Feature: f\n  @RN-05\n  Scenario: s\n    * print 1\n", encoding="utf-8")
            self.assertEqual(trace.main(["--root", tmp, "--fail-on-gaps"]), 0)
            self.assertTrue((root / "target" / "traceability" / "traceability.md").exists())


if __name__ == "__main__":
    unittest.main()
