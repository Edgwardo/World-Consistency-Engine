import java.util.*;

/**
 * TestRunner.java
 * ---------------
 * Hard-coded test worlds from Section 1.
 *
 * Section 1 worlds (6 total):
 *   VALID:
 *     W1 — Image 1 (Rain, before turn, all wet, no other issues)
 *     W2 — Image 2 (No field, before turn, all clean)
 *   OBVIOUSLY INVALID:
 *     W3 — Image 1 invalid block (Rain+Fire, multiple statuses wrong)
 *     W4 — Image 3 invalid block (Fire+Blizzard, entity has frozen+burnt)
 *   SUBTLY INVALID:
 *     W5 — Image 3 subtle (Fire field, Swamp frozen yet used sweep)
 *     W6 — Image 4 subtle (Blizzard duration too high, revive on alive)
 *
 * Each entity uses the exact stats/affinities from schems.txt plus
 * the enemy schemas (Brute, Hexer) set as generic fighters.
 */
public class TestRunner {

    // ─────────────────────────────────────────────────────────────────────────
    // Schema builder helpers (affinities, moves exactly as per schems.txt)
    // ─────────────────────────────────────────────────────────────────────────

    /** Warden: 200 hp, 50 mp | resist: FIRE, PHYSICAL | weak: WATER, GROUND */
    private static WorldState.EntitySnapshot makeWarden(boolean isPlayer) {
        WorldState.EntitySnapshot e =
            new WorldState.EntitySnapshot("Warden", isPlayer, 200, 50, 70, 20, 20, 50, 50);
        e.affinities.put(WorldState.AffinityType.FIRE,     WorldState.AffinityRelation.RESIST);
        e.affinities.put(WorldState.AffinityType.PHYSICAL, WorldState.AffinityRelation.RESIST);
        e.affinities.put(WorldState.AffinityType.WATER,    WorldState.AffinityRelation.WEAK);
        e.affinities.put(WorldState.AffinityType.GROUND,   WorldState.AffinityRelation.WEAK);

        e.moves.add(new WorldState.MoveSnapshot("Strike",    WorldState.AffinityType.PHYSICAL, WorldState.CostType.HP_PERCENT, 10));
        e.moves.add(new WorldState.MoveSnapshot("Ember",     WorldState.AffinityType.FIRE,     WorldState.CostType.MP, 4));
        e.moves.add(new WorldState.MoveSnapshot("Attack Up", WorldState.AffinityType.PHYSICAL, WorldState.CostType.MP, 10));
        return e;
    }

    /** Priest: 100 hp, 150 mp | resist: ICE | weak: CURSE | immune: HOLY */
    private static WorldState.EntitySnapshot makePriest(boolean isPlayer) {
        WorldState.EntitySnapshot e =
            new WorldState.EntitySnapshot("Priest", isPlayer, 100, 150, 20, 70, 50, 20, 50);
        e.affinities.put(WorldState.AffinityType.ICE,   WorldState.AffinityRelation.RESIST);
        e.affinities.put(WorldState.AffinityType.CURSE, WorldState.AffinityRelation.WEAK);
        e.affinities.put(WorldState.AffinityType.HOLY,  WorldState.AffinityRelation.IMMUNE);

        e.moves.add(new WorldState.MoveSnapshot("Bright Judgement", WorldState.AffinityType.HOLY,  WorldState.CostType.MP, 16));
        e.moves.add(new WorldState.MoveSnapshot("Revive",           WorldState.AffinityType.HOLY,  WorldState.CostType.MP, 12));
        e.moves.add(new WorldState.MoveSnapshot("Bufula",           WorldState.AffinityType.ICE,   WorldState.CostType.MP, 8));
        e.moves.add(new WorldState.MoveSnapshot("Heal",             WorldState.AffinityType.HOLY,  WorldState.CostType.MP, 10));
        return e;
    }

    /** Raiju: 125 hp, 125 mp | weak: GROUND | immune: ELECTRIC */
    private static WorldState.EntitySnapshot makeRaiju(boolean isPlayer) {
        WorldState.EntitySnapshot e =
            new WorldState.EntitySnapshot("Raiju", isPlayer, 125, 125, 50, 50, 50, 20, 70);
        e.affinities.put(WorldState.AffinityType.GROUND,   WorldState.AffinityRelation.WEAK);
        e.affinities.put(WorldState.AffinityType.ELECTRIC, WorldState.AffinityRelation.IMMUNE);

        e.moves.add(new WorldState.MoveSnapshot("Strike",   WorldState.AffinityType.PHYSICAL, WorldState.CostType.HP_PERCENT, 10));
        e.moves.add(new WorldState.MoveSnapshot("Thunder",  WorldState.AffinityType.ELECTRIC, WorldState.CostType.MP, 16));
        e.moves.add(new WorldState.MoveSnapshot("Speed Up", WorldState.AffinityType.PHYSICAL, WorldState.CostType.MP, 10));
        e.moves.add(new WorldState.MoveSnapshot("Eiga",     WorldState.AffinityType.CURSE,    WorldState.CostType.MP, 8));
        return e;
    }

    /** Swamp: 150 hp, 100 mp | resist: FIRE,WATER,GROUND | weak: PHYSICAL,ICE | immune: ELECTRIC */
    private static WorldState.EntitySnapshot makeSwamp(boolean isPlayer) {
        WorldState.EntitySnapshot e =
            new WorldState.EntitySnapshot("Swamp", isPlayer, 150, 100, 70, 50, 20, 70, 20);
        e.affinities.put(WorldState.AffinityType.FIRE,     WorldState.AffinityRelation.RESIST);
        e.affinities.put(WorldState.AffinityType.WATER,    WorldState.AffinityRelation.RESIST);
        e.affinities.put(WorldState.AffinityType.GROUND,   WorldState.AffinityRelation.RESIST);
        e.affinities.put(WorldState.AffinityType.PHYSICAL, WorldState.AffinityRelation.WEAK);
        e.affinities.put(WorldState.AffinityType.ICE,      WorldState.AffinityRelation.WEAK);
        e.affinities.put(WorldState.AffinityType.ELECTRIC, WorldState.AffinityRelation.IMMUNE);

        e.moves.add(new WorldState.MoveSnapshot("Sweep",      WorldState.AffinityType.PHYSICAL, WorldState.CostType.HP_PERCENT, 20));
        e.moves.add(new WorldState.MoveSnapshot("Tsunami",    WorldState.AffinityType.WATER,    WorldState.CostType.MP, 20));
        e.moves.add(new WorldState.MoveSnapshot("Earthquake", WorldState.AffinityType.GROUND,   WorldState.CostType.MP, 20));
        return e;
    }

    /** Brute: generic physical fighter, 140 hp, 90 mp */
    private static WorldState.EntitySnapshot makeBrute(boolean isPlayer) {
        WorldState.EntitySnapshot e =
            new WorldState.EntitySnapshot("Brute", isPlayer, 140, 90, 60, 30, 30, 60, 40);
        e.moves.add(new WorldState.MoveSnapshot("Strike", WorldState.AffinityType.PHYSICAL, WorldState.CostType.HP_PERCENT, 10));
        return e;
    }

    /** Hexer: curse caster, 110 hp, 120 mp */
    private static WorldState.EntitySnapshot makeHexer(boolean isPlayer) {
        WorldState.EntitySnapshot e =
            new WorldState.EntitySnapshot("Hexer", isPlayer, 110, 120, 30, 60, 50, 30, 50);
        e.moves.add(new WorldState.MoveSnapshot("Eiga", WorldState.AffinityType.CURSE, WorldState.CostType.MP, 8));
        return e;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // World builders
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * WORLD 1 — VALID
     * Image 1: Rain(5), Before turn, Active=Priest
     * All entities are wet(3), no other status issues.
     */
    private static WorldState buildW1() {
        WorldState ws = new WorldState("W1 [VALID] Rain(5), Before Turn, All Wet");
        ws.fieldType = WorldState.FieldType.RAIN;
        ws.fieldDuration = 5;
        ws.phase = WorldState.Phase.BEFORE_TURN;
        ws.activeEntityName = "Priest";

        WorldState.EntitySnapshot warden = makeWarden(true);
        warden.hp = 190;
        warden.mp = 47;
        warden.statuses.put(WorldState.StatusType.WET, 3);

        WorldState.EntitySnapshot raiju = makeRaiju(true);
        raiju.statuses.put(WorldState.StatusType.WET, 3);

        WorldState.EntitySnapshot swamp = makeSwamp(true);
        swamp.statuses.put(WorldState.StatusType.WET, 3);

        WorldState.EntitySnapshot priest = makePriest(true);
        priest.isActive = true;
        priest.statuses.put(WorldState.StatusType.WET, 3);

        WorldState.EntitySnapshot brute = makeBrute(false);
        brute.statuses.put(WorldState.StatusType.WET, 3);

        WorldState.EntitySnapshot hexer = makeHexer(false);
        hexer.statuses.put(WorldState.StatusType.WET, 3);

        ws.entities.addAll(Arrays.asList(warden, raiju, swamp, priest, brute, hexer));
        return ws;
    }

    /**
     * WORLD 2 — VALID
     * Image 2: No field, Before turn, Active=Raiju, all clean.
     */
    private static WorldState buildW2() {
        WorldState ws = new WorldState("W2 [VALID] No Field, Before Turn, All Clean");
        ws.fieldType = WorldState.FieldType.NONE;
        ws.fieldDuration = 0;
        ws.phase = WorldState.Phase.BEFORE_TURN;
        ws.activeEntityName = "Raiju";

        WorldState.EntitySnapshot warden = makeWarden(true);
        WorldState.EntitySnapshot raiju  = makeRaiju(true);  raiju.isActive = true;
        WorldState.EntitySnapshot swamp  = makeSwamp(true);
        WorldState.EntitySnapshot priest = makePriest(true);
        WorldState.EntitySnapshot brute  = makeBrute(false);
        WorldState.EntitySnapshot hexer  = makeHexer(false);

        ws.entities.addAll(Arrays.asList(warden, raiju, swamp, priest, brute, hexer));
        return ws;
    }

    /**
     * WORLD 3 — OBVIOUSLY INVALID
     * Image 1 invalid block:
     *   Field = Rain(11) AND Fire(5) — two fields active (represented as Rain with duration 11)
     *   Duration > max (Rain max=6, this is 11)
     *   Multiple entities have both burnt AND wet (Raiju, Swamp, Priest, Brute, Hexer)
     * Expected violations:
     *   FIELD-2: Rain duration 11 > 6
     *   FIELD-3: Actually all entities have wet so field-3 passes — but burnt+wet coexist
     *   STATUS-1: Raiju, Swamp, Priest, Brute, Hexer have wet AND burnt simultaneously
     */
    private static WorldState buildW3() {
        WorldState ws = new WorldState("W3 [OBVIOUSLY INVALID] Rain(11)+Fire — Over-duration + WetBurnt");
        // Represent "two fields" as just Rain but with illegal duration to trigger FIELD-2.
        // The "Fire field also active" is represented by entities having BURNT (which contradicts WET).
        ws.fieldType = WorldState.FieldType.RAIN;
        ws.fieldDuration = 11; // > max of 6
        ws.phase = WorldState.Phase.BEFORE_TURN;
        ws.activeEntityName = "Warden";

        WorldState.EntitySnapshot warden = makeWarden(true);
        warden.statuses.put(WorldState.StatusType.WET, 3);
        // Warden is only wet, not burnt

        WorldState.EntitySnapshot raiju = makeRaiju(true);
        raiju.statuses.put(WorldState.StatusType.WET, 3);
        raiju.statuses.put(WorldState.StatusType.BURNT, 3); // INVALID: wet+burnt

        WorldState.EntitySnapshot swamp = makeSwamp(true);
        swamp.statuses.put(WorldState.StatusType.WET, 3);
        swamp.statuses.put(WorldState.StatusType.BURNT, 3); // INVALID: wet+burnt

        WorldState.EntitySnapshot priest = makePriest(true);
        priest.statuses.put(WorldState.StatusType.WET, 3);
        priest.statuses.put(WorldState.StatusType.BURNT, 3); // INVALID: wet+burnt

        WorldState.EntitySnapshot brute = makeBrute(false);
        brute.mp = 40;
        brute.statuses.put(WorldState.StatusType.WET, 3);
        brute.statuses.put(WorldState.StatusType.BURNT, 3); // INVALID: wet+burnt

        WorldState.EntitySnapshot hexer = makeHexer(false);
        hexer.statuses.put(WorldState.StatusType.WET, 3);
        hexer.statuses.put(WorldState.StatusType.BURNT, 3); // INVALID: wet+burnt

        ws.entities.addAll(Arrays.asList(warden, raiju, swamp, priest, brute, hexer));
        return ws;
    }

    /**
     * WORLD 4 — OBVIOUSLY INVALID
     * Fire(3) field is active, but most entities are missing BURNT status.
     * Raiju has BOTH burnt(3) AND frozen(5) — impossible coexistence.
     * (The original Section 1 world described "Fire + Blizzard" — represented here
     *  as Fire(3) only, since WorldState supports one field. The Blizzard effect is
     *  simulated by Raiju having frozen, which conflicts with burnt.)
     * Expected violations:
     *   STATUS-2: Raiju has FROZEN and BURNT simultaneously
     *   FIELD-3: Warden, Swamp, Priest, Brute, Hexer missing BURNT from Fire field
     */
    private static WorldState buildW4() {
        WorldState ws = new WorldState("W4 [OBVIOUSLY INVALID] Fire(3)+Blizzard(2) — FrozenBurnt + Missing statuses");
        // Show Fire field is active (duration 3 is legal), but entities are missing BURNT from it
        ws.fieldType = WorldState.FieldType.FIRE;
        ws.fieldDuration = 3;
        ws.phase = WorldState.Phase.BEFORE_TURN;
        ws.activeEntityName = "Swamp";

        WorldState.EntitySnapshot warden = makeWarden(true);
        // No burnt — violates FIELD-3 (fire field should give everyone burnt, Warden is resistant not immune)

        WorldState.EntitySnapshot raiju = makeRaiju(true);
        raiju.statuses.put(WorldState.StatusType.BURNT, 3);
        raiju.statuses.put(WorldState.StatusType.FROZEN, 5); // INVALID: burnt+frozen coexist

        WorldState.EntitySnapshot swamp = makeSwamp(true);
        swamp.isActive = true;
        // No burnt — violates FIELD-3 (Swamp RESISTS fire but is not immune, still gets burnt)

        WorldState.EntitySnapshot priest = makePriest(true);
        // No burnt — violates FIELD-3

        WorldState.EntitySnapshot brute = makeBrute(false);
        // No burnt — violates FIELD-3

        WorldState.EntitySnapshot hexer = makeHexer(false);
        // No burnt — violates FIELD-3

        ws.entities.addAll(Arrays.asList(warden, raiju, swamp, priest, brute, hexer));
        return ws;
    }

    /**
     * WORLD 5 — SUBTLY INVALID
     * Image 3 subtle block:
     *   Field = Fire(6) — duration over max (5)
     *   Phase = ACTION, Active = Swamp, Action = Sweep
     *   Swamp has FROZEN(1) — cannot act while frozen!
     *   Everyone has BURNT(3) from fire field (correct)
     *   But Swamp has FROZEN instead of BURNT
     * Expected violations:
     *   STATUS-4: Swamp is frozen and cannot use Sweep
     *   FIELD-2: Fire duration 6 > max 5
     *   STATUS-2: Swamp has frozen + (technically not burnt, so no STATUS-2... 
     *             but frozen entity in fire field should be burnt, not frozen — covered by FIELD-3)
     *   FIELD-3: Swamp should have BURNT from fire field, but has FROZEN instead (wet=0 ok)
     *   CLASS-1: HP cost not yet deducted (hp=150=max, action in progress, sweep costs 20%=30 HP)
     */
    private static WorldState buildW5() {
        WorldState ws = new WorldState("W5 [SUBTLY INVALID] Fire(6), Swamp Frozen yet used Sweep");
        ws.fieldType = WorldState.FieldType.FIRE;
        ws.fieldDuration = 6; // INVALID: max is 5
        ws.phase = WorldState.Phase.ACTION;
        ws.activeEntityName = "Swamp";
        ws.actionName = "Sweep";
        ws.targetName = "Brute";

        WorldState.EntitySnapshot warden = makeWarden(true);
        warden.statuses.put(WorldState.StatusType.BURNT, 3);

        WorldState.EntitySnapshot raiju = makeRaiju(true);
        raiju.statuses.put(WorldState.StatusType.BURNT, 3);

        WorldState.EntitySnapshot swamp = makeSwamp(true);
        swamp.isActive = true;
        swamp.hp = 120; // Cost already deducted at ACTION phase: 20% of maxHp(150) = 30 HP → 150-30=120
        // Still triggers CLASS-1 because frozen means the action should never have started
        swamp.statuses.put(WorldState.StatusType.FROZEN, 1); // INVALID: can't act while frozen

        WorldState.EntitySnapshot priest = makePriest(true);
        priest.statuses.put(WorldState.StatusType.BURNT, 3);

        WorldState.EntitySnapshot brute = makeBrute(false);
        brute.mp = 40;
        brute.statuses.put(WorldState.StatusType.BURNT, 3);

        WorldState.EntitySnapshot hexer = makeHexer(false);
        hexer.statuses.put(WorldState.StatusType.BURNT, 3);

        ws.entities.addAll(Arrays.asList(warden, raiju, swamp, priest, brute, hexer));
        return ws;
    }

    /**
     * WORLD 6 — SUBTLY INVALID
     * Field = Blizzard(2) — duration exceeds max of 1.
     * Phase = AFTER_TURN, Active = Priest, Action = Revive, Target = Raiju.
     * Raiju is ALIVE — Revive on alive entity is invalid.
     * Swamp mp=120 > maxMp(100) — resource overflow.
     * Note: FIELD-3 does NOT fire for Blizzard (probabilistic 35% freeze —
     *       entities may or may not be frozen, both are valid game states).
     * Expected violations:
     *   FIELD-2: Blizzard duration 2 > max 1
     *   CLASS-3: Revive used on alive entity (Raiju)
     *   RESOURCE-1: Swamp MP=120 > maxMp=100
     */
    private static WorldState buildW6() {
        WorldState ws = new WorldState("W6 [SUBTLY INVALID] Blizzard(2), Revive on Alive Raiju");
        ws.fieldType = WorldState.FieldType.BLIZZARD;
        ws.fieldDuration = 2; // INVALID: max is 1
        ws.phase = WorldState.Phase.AFTER_TURN;
        ws.activeEntityName = "Priest";
        ws.actionName = "Revive";
        ws.targetName = "Raiju";

        WorldState.EntitySnapshot warden = makeWarden(true);
        // No frozen — violates FIELD-3

        WorldState.EntitySnapshot raiju = makeRaiju(true);
        raiju.hp = 125; // ALIVE — Revive on alive is invalid
        raiju.isAlive = true;
        // No frozen — but Raiju is immune to ELECTRIC not ICE, so blizzard (ice) should affect them

        WorldState.EntitySnapshot swamp = makeSwamp(true);
        swamp.mp = 120; // INVALID: maxMp=100, mp cannot exceed max
        // No frozen — Swamp is not immune to ICE (actually WEAK to ICE), should be frozen

        WorldState.EntitySnapshot priest = makePriest(true);
        priest.isActive = true;
        priest.mp = 115; // Used Revive (cost 12) from 150 → 138. 115 is suspicious but not our rule.
        // No frozen — Priest resists ICE but is not immune, should still be frozen

        WorldState.EntitySnapshot brute = makeBrute(false);
        brute.hp = 110;
        brute.mp = 40;
        brute.statuses.put(WorldState.StatusType.FROZEN, 3);
        // Only Brute has frozen — everyone else should too

        WorldState.EntitySnapshot hexer = makeHexer(false);
        // No frozen — violates FIELD-3

        ws.entities.addAll(Arrays.asList(warden, raiju, swamp, priest, brute, hexer));
        return ws;
    }


    // ─────────────────────────────────────────────────────────────────────────
    // Transition rule test worlds (STATUS-3, AFFINITY-2)
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * WORLD 7 — TRANSITION VALID
     * STATUS-3: Raiju is poisoned, end-of-turn tick.
     * beforeTurnHp shows Raiju had 125 HP before tick.
     * After tick: 125 - 10 (8% of 125 = 10) = 115. Correct.
     * Expected: No violations.
     */
    private static WorldState buildW7() {
        WorldState ws = new WorldState("W7 [VALID] Poison tick correct: Raiju 125->115");
        ws.fieldType = WorldState.FieldType.NONE;
        ws.fieldDuration = 0;
        ws.phase = WorldState.Phase.AFTER_TURN;
        ws.activeEntityName = "Raiju";

        WorldState.EntitySnapshot warden = makeWarden(true);
        WorldState.EntitySnapshot raiju  = makeRaiju(true);
        raiju.isActive = true;
        raiju.hp = 115;   // After tick: 125 - 10 = 115
        raiju.statuses.put(WorldState.StatusType.POISONED, 2); // still has 2 turns left after decrement

        WorldState.EntitySnapshot swamp  = makeSwamp(true);
        WorldState.EntitySnapshot priest = makePriest(true);
        WorldState.EntitySnapshot brute  = makeBrute(false);
        WorldState.EntitySnapshot hexer  = makeHexer(false);

        ws.entities.addAll(Arrays.asList(warden, raiju, swamp, priest, brute, hexer));

        // Provide before-turn HP for STATUS-3 (only Raiju was poisoned this turn)
        ws.beforeTurnHp.put("Raiju", 125);

        return ws;
    }

    /**
     * WORLD 8 — TRANSITION INVALID (STATUS-3)
     * Raiju was poisoned but took 0 damage — poison tick did not fire.
     * beforeTurnHp shows Raiju had 80 HP. Current HP is still 80.
     * Expected violation: STATUS-3 — poison damage not applied.
     */
    private static WorldState buildW8() {
        WorldState ws = new WorldState("W8 [INVALID] Poison tick skipped — Raiju took 0 damage despite being poisoned");
        ws.fieldType = WorldState.FieldType.NONE;
        ws.fieldDuration = 0;
        ws.phase = WorldState.Phase.AFTER_TURN;
        ws.activeEntityName = "Raiju";

        WorldState.EntitySnapshot warden = makeWarden(true);
        WorldState.EntitySnapshot raiju  = makeRaiju(true);
        raiju.isActive = true;
        raiju.hp = 80;   // Same as before — poison damage never applied!
        raiju.statuses.put(WorldState.StatusType.POISONED, 0); // duration expired (was 1, ticked to 0)

        WorldState.EntitySnapshot swamp  = makeSwamp(true);
        WorldState.EntitySnapshot priest = makePriest(true);
        WorldState.EntitySnapshot brute  = makeBrute(false);
        WorldState.EntitySnapshot hexer  = makeHexer(false);

        ws.entities.addAll(Arrays.asList(warden, raiju, swamp, priest, brute, hexer));

        // beforeTurnHp for Raiju = 80 (was poisoned, should have taken 10 dmg → expected 70 after)
        ws.beforeTurnHp.put("Raiju", 80);

        return ws;
    }

    /**
     * WORLD 9 — TRANSITION VALID (AFFINITY-2)
     * Warden is WET(2), hit by Raiju's Thunder (ELECTRIC).
     * baseDamageDealt = 40. Expected damage = 40 * 1.5 = 60.
     * Warden HP before action: 200. After: 200 - 60 = 140. Correct.
     * Expected: No violations.
     */
    private static WorldState buildW9() {
        WorldState ws = new WorldState("W9 [VALID] WET+ELECTRIC combo: Warden takes 1.5x damage correctly");
        ws.fieldType = WorldState.FieldType.RAIN;
        ws.fieldDuration = 3;
        ws.phase = WorldState.Phase.AFTER_TURN;
        ws.activeEntityName = "Raiju";
        ws.actionName = "Thunder";
        ws.targetName = "Warden";

        WorldState.EntitySnapshot warden = makeWarden(true);
        warden.hp = 140;  // Was 200, took 60 damage (40 base * 1.5)
        warden.statuses.put(WorldState.StatusType.WET, 3);

        WorldState.EntitySnapshot raiju = makeRaiju(true);
        raiju.isActive = true;
        raiju.statuses.put(WorldState.StatusType.WET, 3);

        WorldState.EntitySnapshot swamp  = makeSwamp(true);
        swamp.statuses.put(WorldState.StatusType.WET, 3);

        WorldState.EntitySnapshot priest = makePriest(true);
        priest.statuses.put(WorldState.StatusType.WET, 3);

        WorldState.EntitySnapshot brute  = makeBrute(false);
        brute.statuses.put(WorldState.StatusType.WET, 3);

        WorldState.EntitySnapshot hexer  = makeHexer(false);
        hexer.statuses.put(WorldState.StatusType.WET, 3);

        ws.entities.addAll(Arrays.asList(warden, raiju, swamp, priest, brute, hexer));

        // Provide before-action HP for AFFINITY-2
        ws.beforeActionHp.put("Warden", 200);
        ws.baseDamageDealt = 40;

        return ws;
    }

    /**
     * WORLD 10 — TRANSITION INVALID (AFFINITY-2)
     * Warden is WET(2), hit by Thunder (ELECTRIC).
     * baseDamageDealt = 40. Expected 1.5x = 60.
     * But Warden only lost 40 HP (base, no multiplier applied).
     * Expected violation: AFFINITY-2 — 1.5x multiplier not applied.
     */
    private static WorldState buildW10() {
        WorldState ws = new WorldState("W10 [INVALID] WET+ELECTRIC combo: 1.5x multiplier not applied to Warden");
        ws.fieldType = WorldState.FieldType.RAIN;
        ws.fieldDuration = 3;
        ws.phase = WorldState.Phase.AFTER_TURN;
        ws.activeEntityName = "Raiju";
        ws.actionName = "Thunder";
        ws.targetName = "Warden";

        WorldState.EntitySnapshot warden = makeWarden(true);
        warden.hp = 160;  // Was 200, took only 40 damage — 1.5x was NOT applied (should be 60)
        warden.statuses.put(WorldState.StatusType.WET, 3);

        WorldState.EntitySnapshot raiju = makeRaiju(true);
        raiju.isActive = true;
        raiju.statuses.put(WorldState.StatusType.WET, 3);

        WorldState.EntitySnapshot swamp  = makeSwamp(true);
        swamp.statuses.put(WorldState.StatusType.WET, 3);

        WorldState.EntitySnapshot priest = makePriest(true);
        priest.statuses.put(WorldState.StatusType.WET, 3);

        WorldState.EntitySnapshot brute  = makeBrute(false);
        brute.statuses.put(WorldState.StatusType.WET, 3);

        WorldState.EntitySnapshot hexer  = makeHexer(false);
        hexer.statuses.put(WorldState.StatusType.WET, 3);

        ws.entities.addAll(Arrays.asList(warden, raiju, swamp, priest, brute, hexer));

        ws.beforeActionHp.put("Warden", 200);
        ws.baseDamageDealt = 40;

        return ws;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Main
    // ─────────────────────────────────────────────────────────────────────────

    public static void main(String[] args) {
        ConsistencyEngine engine = new ConsistencyEngine();

        List<WorldState> worlds = Arrays.asList(
            buildW1(),   // Valid
            buildW2(),   // Valid
            buildW3(),   // Obviously invalid
            buildW4(),   // Obviously invalid
            buildW5(),   // Subtly invalid
            buildW6(),   // Subtly invalid
            buildW7(),   // Transition valid   (STATUS-3: correct poison tick)
            buildW8(),   // Transition invalid (STATUS-3: poison tick skipped)
            buildW9(),   // Transition valid   (AFFINITY-2: correct 1.5x damage)
            buildW10()   // Transition invalid (AFFINITY-2: 1.5x not applied)
        );

        System.out.println("\n╔══════════════════════════════════════════════════════╗");
        System.out.println("║     WORLD CONSISTENCY ENGINE — FULL TEST RUN          ║");
        System.out.println("╠══════════════════════════════════════════════════════╣");
        System.out.println("║  W1-W6:  Section 1 worlds                            ║");
        System.out.println("║  W7-W8:  STATUS-3 transition (poison tick)           ║");
        System.out.println("║  W9-W10: AFFINITY-2 transition (combo damage boost)  ║");
        System.out.println("╚══════════════════════════════════════════════════════╝\n");

        engine.validateAll(worlds);
    }
}
