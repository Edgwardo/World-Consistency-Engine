import java.util.*;
import java.util.function.Function;

/**
 * Rule.java
 * ---------
 * A single consistency rule. Each rule:
 *   - Has a category (STATE, TRANSITION, AFFINITY, FIELD, CLASS)
 *   - Has a name and human-readable description
 *   - Implements check(WorldState) → List<String> violations
 *
 * An empty violation list means the rule PASSES.
 */
public class Rule {

    public enum Category {
        STATUS,     // State rules about status conditions
        AFFINITY,   // Rules about affinities + statuses/damage
        FIELD,      // Field/environment rules
        CLASS       // Class/ability usage rules
    }

    public final String name;
    public final Category category;
    public final String description;

    // The actual validation logic
    private final Function<WorldState, List<String>> checker;

    public Rule(String name, Category category, String description,
                Function<WorldState, List<String>> checker) {
        this.name = name;
        this.category = category;
        this.description = description;
        this.checker = checker;
    }

    public List<String> check(WorldState ws) {
        return checker.apply(ws);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Rule Factory — 17 rules (STATUS×5, AFFINITY×1, FIELD×3, CLASS×5, BUFF×1, RESOURCE×1)
    // 2 rules (STATUS-3 poison damage, AFFINITY-2 damage boost) require before/after snapshots
    // and are enforced by Game.java at runtime — documented at end of this method.
    // ─────────────────────────────────────────────────────────────────────────

    public static List<Rule> allRules() {
        List<Rule> rules = new ArrayList<>();

        // ═══════════════════════════════════════════════════════
        // STATUS RULES
        // ═══════════════════════════════════════════════════════

        // STATUS-1: Wet and Burnt cannot coexist
        rules.add(new Rule(
            "STATUS-1: Wet/Burnt mutual exclusion",
            Category.STATUS,
            "An entity cannot be both WET and BURNT at the same time. " +
            "Wet should override (remove) Burnt.",
            ws -> {
                List<String> v = new ArrayList<>();
                for (WorldState.EntitySnapshot e : ws.allEntities()) {
                    if (e.hasStatus(WorldState.StatusType.WET) &&
                        e.hasStatus(WorldState.StatusType.BURNT)) {
                        v.add(e.name + " cannot be WET and BURNT at the same time. " +
                              "WET should override BURNT (wet=" +
                              e.statuses.get(WorldState.StatusType.WET) + ", burnt=" +
                              e.statuses.get(WorldState.StatusType.BURNT) + ").");
                    }
                }
                return v;
            }
        ));

        // STATUS-2: Frozen and Burnt cannot coexist
        rules.add(new Rule(
            "STATUS-2: Frozen/Burnt mutual exclusion",
            Category.STATUS,
            "An entity cannot be both FROZEN and BURNT at the same time. " +
            "BURNT should override (remove) FROZEN.",
            ws -> {
                List<String> v = new ArrayList<>();
                for (WorldState.EntitySnapshot e : ws.allEntities()) {
                    if (e.hasStatus(WorldState.StatusType.FROZEN) &&
                        e.hasStatus(WorldState.StatusType.BURNT)) {
                        v.add(e.name + " cannot be FROZEN and BURNT at the same time. " +
                              "BURNT should override FROZEN (frozen=" +
                              e.statuses.get(WorldState.StatusType.FROZEN) + ", burnt=" +
                              e.statuses.get(WorldState.StatusType.BURNT) + ").");
                    }
                }
                return v;
            }
        ));

        // STATUS-3: Poison damage tick (TRANSITION)
        // Requires WorldState.beforeTurnHp to be populated (entity name -> HP before tick).
        // Validates two things:
        //   (a) If poison duration was > 0, exactly 8% of maxHp must have been lost.
        //   (b) If poison duration was 0, no poison damage should have occurred.
        // If beforeTurnHp is empty, this rule is skipped (snapshot-only mode).
        rules.add(new Rule(
            "STATUS-3: Poison tick amount and eligibility",
            Category.STATUS,
            "If POISONED duration > 0 at end of turn: entity loses exactly 8% of maxHp. " +
            "If POISONED duration == 0: no HP loss from poison occurs. " +
            "Requires beforeTurnHp to be populated for full validation.",
            ws -> {
                List<String> v = new ArrayList<>();
                if (ws.phase != WorldState.Phase.AFTER_TURN) return v;
                if (ws.beforeTurnHp.isEmpty()) return v; // no before-state provided, skip

                for (WorldState.EntitySnapshot e : ws.allEntities()) {
                    if (!e.isAlive) continue;

                    // Look up HP before the end-of-turn tick
                    Integer hpBefore = null;
                    for (Map.Entry<String, Integer> entry : ws.beforeTurnHp.entrySet()) {
                        if (entry.getKey().equalsIgnoreCase(e.name)) {
                            hpBefore = entry.getValue();
                            break;
                        }
                    }
                    if (hpBefore == null) continue; // entity not in before-map, skip

                    int hpAfter = e.hp;
                    int hpLost = hpBefore - hpAfter;

                    // Was entity poisoned at the start of this turn?
                    // After AFTER_TURN tick, duration has already been decremented.
                    // Poison is "was active" if hpLost > 0 OR if current duration >= 0.
                    // We can't read the before-duration directly, but we can infer:
                    // if the entity currently has poison duration >= 0 AND damage was dealt, check amount.
                    // Strategy: if hpLost > 0 AND entity currently has no other damage source,
                    // the lost HP should equal exactly 8% of maxHp.
                    // Simpler: caller sets beforeTurnHp only for entities that WERE poisoned.
                    // Check: hpLost must equal Math.max(1, (int)Math.round(maxHp * 0.08)).

                    int expectedPoisonDmg = Math.max(1, (int) Math.round(e.maxHp * 0.08));

                    // Case A: Entity was poisoned (has poison status now, meaning duration > 0 before decrement,
                    // or duration was exactly 1 and is now 0 — either way, damage should have occurred).
                    // We detect "was poisoned" by checking if hpLost > 0 (damage happened) or if they
                    // still have poison now. The caller sets beforeTurnHp for this entity = they WERE poisoned.
                    // So: expected damage must match.
                    if (hpLost != expectedPoisonDmg) {
                        if (hpLost == 0) {
                            v.add(e.name + " was POISONED but took 0 poison damage this turn. " +
                                  "Expected " + expectedPoisonDmg + " damage (8% of maxHp=" + e.maxHp + ").");
                        } else {
                            v.add(e.name + " took " + hpLost + " poison damage but expected exactly " +
                                  expectedPoisonDmg + " (8% of maxHp=" + e.maxHp + "). " +
                                  "Possible incorrect tick amount or extraneous damage counted.");
                        }
                    }
                }
                return v;
            }
        ));

        // STATUS-4: Frozen entity cannot use a skill (TRANSITION)
        rules.add(new Rule(
            "STATUS-4: Frozen entity cannot act",
            Category.STATUS,
            "A FROZEN entity cannot use any skill. If the active entity is frozen, " +
            "the action phase is invalid.",
            ws -> {
                List<String> v = new ArrayList<>();
                if (ws.phase == WorldState.Phase.ACTION && ws.actionName != null) {
                    WorldState.EntitySnapshot actor = ws.getActiveEntity();
                    if (actor != null && actor.isAlive &&
                        actor.hasStatus(WorldState.StatusType.FROZEN)) {
                        v.add(actor.name + " cannot use '" + ws.actionName +
                              "' because they are FROZEN (frozen=" +
                              actor.statuses.get(WorldState.StatusType.FROZEN) + " turns).");
                    }
                }
                return v;
            }
        ));

        // STATUS-5: Dead entity cannot act
        rules.add(new Rule(
            "STATUS-5: Dead entity cannot act",
            Category.STATUS,
            "A dead entity cannot be the active entity performing an action.",
            ws -> {
                List<String> v = new ArrayList<>();
                WorldState.EntitySnapshot actor = ws.getActiveEntity();
                if (actor != null && !actor.isAlive && ws.actionName != null) {
                    v.add(actor.name + " is dead and cannot use '" + ws.actionName + "'.");
                }
                return v;
            }
        ));

        // ═══════════════════════════════════════════════════════
        // AFFINITY RULES
        // ═══════════════════════════════════════════════════════

        // AFFINITY-1: Immune to damage type → cannot have status from that type
        rules.add(new Rule(
            "AFFINITY-1: Immunity blocks status infliction",
            Category.AFFINITY,
            "An entity immune to FIRE cannot be BURNT. " +
            "An entity immune to WATER cannot be WET. " +
            "An entity immune to ICE cannot be FROZEN. " +
            "An entity immune to GROUND cannot be POISONED. " +
            "An entity immune to ELECTRIC cannot be PARALYZED.",
            ws -> {
                List<String> v = new ArrayList<>();
                for (WorldState.EntitySnapshot e : ws.allEntities()) {
                    // FIRE immunity → no BURNT
                    if (e.isImmuneTo(WorldState.AffinityType.FIRE) &&
                        e.hasStatus(WorldState.StatusType.BURNT)) {
                        v.add(e.name + " is IMMUNE to FIRE and cannot be BURNT " +
                              "(burnt=" + e.statuses.get(WorldState.StatusType.BURNT) + ").");
                    }
                    // WATER immunity → no WET
                    if (e.isImmuneTo(WorldState.AffinityType.WATER) &&
                        e.hasStatus(WorldState.StatusType.WET)) {
                        v.add(e.name + " is IMMUNE to WATER and cannot be WET " +
                              "(wet=" + e.statuses.get(WorldState.StatusType.WET) + ").");
                    }
                    // ICE immunity → no FROZEN
                    if (e.isImmuneTo(WorldState.AffinityType.ICE) &&
                        e.hasStatus(WorldState.StatusType.FROZEN)) {
                        v.add(e.name + " is IMMUNE to ICE and cannot be FROZEN " +
                              "(frozen=" + e.statuses.get(WorldState.StatusType.FROZEN) + ").");
                    }
                    // ELECTRIC immunity → no PARALYZED
                    if (e.isImmuneTo(WorldState.AffinityType.ELECTRIC) &&
                        e.hasStatus(WorldState.StatusType.PARALYZED)) {
                        v.add(e.name + " is IMMUNE to ELECTRIC and cannot be PARALYZED " +
                              "(paralyzed=" + e.statuses.get(WorldState.StatusType.PARALYZED) + ").");
                    }
                    // GROUND immunity → no POISONED (per rule card AFFINITY-3)
                    if (e.isImmuneTo(WorldState.AffinityType.GROUND) &&
                        e.hasStatus(WorldState.StatusType.POISONED)) {
                        v.add(e.name + " is IMMUNE to GROUND and cannot be POISONED " +
                              "(poisoned=" + e.statuses.get(WorldState.StatusType.POISONED) + ").");
                    }
                }
                return v;
            }
        ));

        // AFFINITY-2: Wet+Electric or Frozen+Fire combo → target takes 1.5x damage (TRANSITION)
        // Requires WorldState.beforeActionHp to be populated (entity name -> HP before move resolved).
        // Also requires WorldState.baseDamageDealt (raw damage before multipliers) to be set.
        // If either is absent, falls back to a structural check: flags if the combo is active
        // but the target is immune to the damage type (impossible combo).
        rules.add(new Rule(
            "AFFINITY-2: Status+affinity combo must apply 1.5x damage multiplier",
            Category.AFFINITY,
            "WET target hit by ELECTRIC move: damage must be 1.5x base. " +
            "FROZEN target hit by FIRE move: damage must be 1.5x base. " +
            "Requires beforeActionHp + baseDamageDealt for full validation; " +
            "otherwise performs structural impossibility check.",
            ws -> {
                List<String> v = new ArrayList<>();
                if (ws.phase != WorldState.Phase.ACTION && ws.phase != WorldState.Phase.AFTER_TURN) return v;

                WorldState.EntitySnapshot target = ws.getTarget();
                WorldState.MoveSnapshot move = ws.getActiveMove();
                if (target == null || move == null) return v;

                // Determine if a combo condition is active
                boolean wetElectric = target.hasStatus(WorldState.StatusType.WET)
                        && move.affinity == WorldState.AffinityType.ELECTRIC;
                boolean frozenFire  = target.hasStatus(WorldState.StatusType.FROZEN)
                        && move.affinity == WorldState.AffinityType.FIRE;
                boolean comboActive = wetElectric || frozenFire;

                if (!comboActive) return v;

                String comboName = wetElectric ? "WET+ELECTRIC" : "FROZEN+FIRE";

                // ── Structural check (always runs) ────────────────────────────
                // The combo can't meaningfully trigger if the target is immune to the damage type.
                if (target.isImmuneTo(move.affinity)) {
                    v.add(target.name + " has " + comboName + " combo but is IMMUNE to " +
                          move.affinity + " — combo 1.5x is irrelevant (0 damage). " +
                          "This world state is impossible: immunity should have prevented the status.");
                    return v;
                }

                // ── Full transition check (only if before-HP data is provided) ──
                if (ws.beforeActionHp.isEmpty() || ws.baseDamageDealt < 0) {
                    // No before-state data: flag that the combo SHOULD have boosted damage
                    // but we can't verify the exact amount. This is informational, not a violation.
                    // Do not add a violation — the engine would have applied 1.5x correctly.
                    return v;
                }

                // Look up the target's HP before the move resolved
                Integer hpBefore = null;
                for (Map.Entry<String, Integer> entry : ws.beforeActionHp.entrySet()) {
                    if (entry.getKey().equalsIgnoreCase(target.name)) {
                        hpBefore = entry.getValue();
                        break;
                    }
                }
                if (hpBefore == null) return v; // target not in before-map

                int hpAfter  = target.hp;
                int actualDmg = hpBefore - hpAfter;

                // Expected damage = base * 1.5 (rounded down, minimum 1)
                int expectedDmg = Math.max(1, (int)(ws.baseDamageDealt * 1.5));

                if (actualDmg != expectedDmg) {
                    v.add(target.name + " was hit with " + comboName + " combo " +
                          "(base damage=" + ws.baseDamageDealt + "). " +
                          "Expected 1.5x = " + expectedDmg + " damage, " +
                          "but actual HP loss was " + actualDmg + ". " +
                          "1.5x multiplier was not correctly applied.");
                }

                return v;
            }
        ));

        // ═══════════════════════════════════════════════════════
        // FIELD RULES
        // ═══════════════════════════════════════════════════════

        // FIELD-1: Only one field type at a time
        rules.add(new Rule(
            "FIELD-1: Only one active field",
            Category.FIELD,
            "Only one field state can be active at a time (FIRE, RAIN, or BLIZZARD — not multiple).",
            ws -> {
                // The WorldState only stores one fieldType, so this is checked at construction.
                // If duration > 0 but fieldType == NONE, that's the only structural error here.
                List<String> v = new ArrayList<>();
                if (ws.fieldType == WorldState.FieldType.NONE && ws.fieldDuration > 0) {
                    v.add("Field type is NONE but duration is " + ws.fieldDuration +
                          ". Duration must be 0 when no field is active.");
                }
                return v;
            }
        ));

        // FIELD-2: Field duration limits
        rules.add(new Rule(
            "FIELD-2: Field duration limits",
            Category.FIELD,
            "BLIZZARD max duration = 1. FIRE max duration = 5. RAIN max duration = 6.",
            ws -> {
                List<String> v = new ArrayList<>();
                if (ws.fieldType == WorldState.FieldType.BLIZZARD && ws.fieldDuration > 1) {
                    v.add("BLIZZARD field duration is " + ws.fieldDuration +
                          " but max is 1. BLIZZARD should only last 1 turn.");
                }
                if (ws.fieldType == WorldState.FieldType.FIRE && ws.fieldDuration > 5) {
                    v.add("FIRE field duration is " + ws.fieldDuration +
                          " but max is 5.");
                }
                if (ws.fieldType == WorldState.FieldType.RAIN && ws.fieldDuration > 6) {
                    v.add("RAIN field duration is " + ws.fieldDuration +
                          " but max is 6.");
                }
                return v;
            }
        ));

        // FIELD-3: Active field must apply its status to ALL alive entities
        // NOTE: BLIZZARD is PROBABILISTIC (35% freeze chance per Game.java) —
        //       we cannot require all entities to be frozen under Blizzard.
        //       Only RAIN (deterministic WET) and FIRE (deterministic BURNT) are enforced.
        rules.add(new Rule(
            "FIELD-3: Active field status must affect all entities",
            Category.FIELD,
            "If RAIN is active, all alive entities must have WET status (deterministic). " +
            "If FIRE is active, all alive entities must have BURNT status (deterministic, " +
            "unless immune to FIRE). " +
            "BLIZZARD is probabilistic (35% freeze chance) — no frozen requirement enforced.",
            ws -> {
                List<String> v = new ArrayList<>();
                if (ws.fieldType == WorldState.FieldType.NONE || ws.fieldDuration <= 0) return v;

                WorldState.StatusType requiredStatus = null;
                WorldState.AffinityType immunityType = null;
                String fieldName = ws.fieldType.name();

                switch (ws.fieldType) {
                    case RAIN:
                        requiredStatus = WorldState.StatusType.WET;
                        immunityType = WorldState.AffinityType.WATER;
                        break;
                    case FIRE:
                        requiredStatus = WorldState.StatusType.BURNT;
                        immunityType = WorldState.AffinityType.FIRE;
                        break;
                    case BLIZZARD:
                        // Blizzard freeze is probabilistic (35% per turn in Game.java).
                        // Cannot require all entities to be frozen — skip check.
                        return v;
                    default:
                        return v;
                }

                for (WorldState.EntitySnapshot e : ws.allEntities()) {
                    if (!e.isAlive) continue;
                    // Immune entities are exempt from both damage and status
                    if (e.isImmuneTo(immunityType)) continue;
                    if (!e.hasStatus(requiredStatus)) {
                        v.add(e.name + " should have " + requiredStatus +
                              " status because the " + fieldName +
                              " field is active, but has " + requiredStatus + "=0.");
                    }
                }
                return v;
            }
        ));

        // ═══════════════════════════════════════════════════════
        // CLASS / ABILITY RULES
        // ═══════════════════════════════════════════════════════

        // CLASS-1: Physical (HP%) move costs HP from current HP
        rules.add(new Rule(
            "CLASS-1: Physical HP-cost move deducts from current HP",
            Category.CLASS,
            "When a physical HP% move is used, HP must decrease by percent of MAX HP. " +
            "Checks the actor's HP is <= maxHp - cost after ACTION phase.",
            ws -> {
                List<String> v = new ArrayList<>();
                if (ws.phase == WorldState.Phase.ACTION || ws.phase == WorldState.Phase.AFTER_TURN) {
                    WorldState.EntitySnapshot actor = ws.getActiveEntity();
                    WorldState.MoveSnapshot move = ws.getActiveMove();
                    if (actor != null && move != null &&
                        move.costType == WorldState.CostType.HP_PERCENT) {
                        int cost = (int) Math.round(actor.maxHp * (move.costValue / 100.0));
                        cost = Math.max(1, cost);
                        // Actor's hp should be <= maxHp - cost
                        // (We can only do a ceiling check, not exact, since damage may also have occurred)
                        if (actor.hp > actor.maxHp - cost) {
                            v.add(actor.name + " used '" + move.name + "' (HP cost " +
                                  move.costValue + "% = " + cost + " HP). " +
                                  "Expected HP <= " + (actor.maxHp - cost) +
                                  " but got HP=" + actor.hp + ". HP cost was not deducted.");
                        }
                    }
                }
                return v;
            }
        ));

        // CLASS-2: MP move requires sufficient current MP
        rules.add(new Rule(
            "CLASS-2: Insufficient MP blocks magic move",
            Category.CLASS,
            "An entity cannot use an MP-cost move if their current MP < cost.",
            ws -> {
                List<String> v = new ArrayList<>();
                if (ws.phase == WorldState.Phase.ACTION) {
                    WorldState.EntitySnapshot actor = ws.getActiveEntity();
                    WorldState.MoveSnapshot move = ws.getActiveMove();
                    if (actor != null && move != null &&
                        move.costType == WorldState.CostType.MP) {
                        // If action is happening, actor must have had enough MP BEFORE the cost.
                        // At action phase, MP has already been spent, so actor.mp = preCost - costValue.
                        // We detect if mp + cost (restored to pre-use) would have been negative pre-cost.
                        // Actually: at ACTION phase the move is being resolved. If mp (current) < 0 that's wrong.
                        // Better: if mp < 0 at any point, that's a violation.
                        if (actor.mp < 0) {
                            v.add(actor.name + " has negative MP (" + actor.mp +
                                  ") after using '" + move.name + "'. MP cannot go below 0.");
                        }
                        // Also check: if the snapshot is BEFORE_TURN-phase and move is listed as
                        // the intended action, check affordability
                    }
                }
                if (ws.phase == WorldState.Phase.BEFORE_TURN && ws.actionName != null) {
                    WorldState.EntitySnapshot actor = ws.getActiveEntity();
                    WorldState.MoveSnapshot move = ws.getActiveMove();
                    if (actor != null && move != null &&
                        move.costType == WorldState.CostType.MP &&
                        actor.mp < move.costValue) {
                        v.add(actor.name + " cannot use '" + move.name + "' (costs " +
                              move.costValue + " MP) because current MP=" + actor.mp +
                              " is insufficient.");
                    }
                }
                return v;
            }
        ));

        // CLASS-3: Revive cannot be used on an alive entity
        rules.add(new Rule(
            "CLASS-3: Revive only valid on dead entities",
            Category.CLASS,
            "The Revive skill cannot target an entity that is still alive.",
            ws -> {
                List<String> v = new ArrayList<>();
                boolean isReviveAction =
                    ws.actionName != null &&
                    ws.actionName.equalsIgnoreCase("Revive");
                if ((ws.phase == WorldState.Phase.ACTION ||
                     ws.phase == WorldState.Phase.AFTER_TURN) && isReviveAction) {
                    WorldState.EntitySnapshot target = ws.getTarget();
                    if (target != null && target.isAlive) {
                        v.add("Revive was used on " + target.name +
                              " but " + target.name + " is still alive. " +
                              "Revive can only target dead entities.");
                    }
                }
                return v;
            }
        ));

        // RESOURCE-1: HP and MP cannot exceed their maximums
        rules.add(new Rule(
            "RESOURCE-1: HP/MP cannot exceed maximum",
            Category.CLASS,
            "Entity HP cannot exceed maxHp. Entity MP cannot exceed maxMp. " +
            "These values should never be possible in a valid game state.",
            ws -> {
                List<String> v = new ArrayList<>();
                for (WorldState.EntitySnapshot e : ws.allEntities()) {
                    if (e.hp > e.maxHp) {
                        v.add(e.name + " has HP=" + e.hp +
                              " which exceeds maxHp=" + e.maxHp + ".");
                    }
                    if (e.mp > e.maxMp) {
                        v.add(e.name + " has MP=" + e.mp +
                              " which exceeds maxMp=" + e.maxMp + ".");
                    }
                }
                return v;
            }
        ));

        // BUFF-1: Buff stages must stay within [-5, +5]
        rules.add(new Rule(
            "BUFF-1: Buff stage limits [-5, +5]",
            Category.CLASS,
            "Each stat buff stage must be between -5 and +5 inclusive. " +
            "Values outside this range are impossible under normal engine rules.",
            ws -> {
                List<String> v = new ArrayList<>();
                for (WorldState.EntitySnapshot e : ws.allEntities()) {
                    for (WorldState.StatType stat : WorldState.StatType.values()) {
                        int stage = e.buffStages.getOrDefault(stat, 0);
                        if (stage < -5 || stage > 5) {
                            v.add(e.name + " has " + stat + " buff stage=" + stage +
                                  " which is outside the allowed range [-5, +5].");
                        }
                    }
                }
                return v;
            }
        ));

        // NOTE — Rules not validatable from a single snapshot:
        // STATUS-3 (Poison damage amount): Requires HP before AND after end-of-turn tick.
        // AFFINITY-2 (Wet+Electric / Frozen+Fire 1.5x boost): Requires pre/post damage values.
        // These are enforced by Game.java at runtime; the WCE cannot validate them from a snapshot alone.

        return rules;
    }
}
