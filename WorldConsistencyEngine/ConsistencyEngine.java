import java.util.*;

/**
 * ConsistencyEngine.java
 * ----------------------
 * Runs every Rule against a WorldState and collects all violations.
 * Usage:
 *   ConsistencyEngine engine = new ConsistencyEngine();
 *   ValidationResult result = engine.validate(worldState);
 *   result.print();
 */
public class ConsistencyEngine {

    private final List<Rule> rules;

    public ConsistencyEngine() {
        this.rules = Rule.allRules();
    }

    // ── Result ────────────────────────────────────────────────────────────────

    public static class ValidationResult {
        public final String worldLabel;
        public final boolean valid;
        public final List<String> violations;          // human-readable violation messages
        public final Map<String, List<String>> byRule; // rule name → its violations

        public ValidationResult(String worldLabel,
                                Map<String, List<String>> byRule) {
            this.worldLabel = worldLabel;
            this.byRule = Collections.unmodifiableMap(byRule);

            List<String> all = new ArrayList<>();
            for (List<String> msgs : byRule.values()) all.addAll(msgs);
            this.violations = Collections.unmodifiableList(all);
            this.valid = violations.isEmpty();
        }

        public void print() {
            System.out.println("╔══════════════════════════════════════════════════════╗");
            System.out.printf ("║  World: %-44s ║%n", worldLabel);
            System.out.printf ("║  Result: %-43s ║%n", valid ? "✓  VALID" : "✗  INVALID");
            System.out.println("╚══════════════════════════════════════════════════════╝");

            if (valid) {
                System.out.println("  No violations found.\n");
                return;
            }

            System.out.println("  Violations:");
            int idx = 1;
            for (Map.Entry<String, List<String>> entry : byRule.entrySet()) {
                if (entry.getValue().isEmpty()) continue;
                System.out.println("  [" + entry.getKey() + "]");
                for (String msg : entry.getValue()) {
                    System.out.println("    " + idx + ". " + msg);
                    idx++;
                }
            }
            System.out.println();
        }
    }

    // ── Core validate method ──────────────────────────────────────────────────

    public ValidationResult validate(WorldState ws) {
        Map<String, List<String>> byRule = new LinkedHashMap<>();
        for (Rule r : rules) {
            List<String> violations = r.check(ws);
            if (!violations.isEmpty()) {
                byRule.put(r.name, violations);
            }
        }
        return new ValidationResult(ws.label, byRule);
    }

    /** Validate a list of worlds and print all results. */
    public void validateAll(List<WorldState> worlds) {
        int passed = 0, failed = 0;
        for (WorldState ws : worlds) {
            ValidationResult result = validate(ws);
            result.print();
            if (result.valid) passed++; else failed++;
        }
        System.out.println("══════════════════════════════════════════════════════");
        System.out.printf ("  Summary: %d VALID  |  %d INVALID  |  %d total%n",
                           passed, failed, passed + failed);
        System.out.println("══════════════════════════════════════════════════════\n");
    }
}
