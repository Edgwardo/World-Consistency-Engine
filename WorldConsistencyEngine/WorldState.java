import java.util.*;

/**
 * WorldState.java
 * ---------------
 * A complete snapshot of the game world at a given moment.
 * This is the data structure passed to ConsistencyEngine for validation.
 * It does NOT reference Game.java or Entity.java — it is standalone.
 */
public class WorldState {

    // ── Enums (mirrored from Move.java so this engine is self-contained) ──────

    public enum AffinityType {
        PHYSICAL, FIRE, ICE, WATER, ELECTRIC, WIND, HOLY, GROUND, CURSE
    }

    public enum AffinityRelation {
        WEAK, RESIST, IMMUNE, NEUTRAL
    }

    public enum StatusType {
        BURNT, WET, FROZEN, PARALYZED, POISONED
    }

    public enum StatType {
        STR, MAG, END, SPD, LUCK
    }

    public enum FieldType {
        NONE, FIRE, RAIN, BLIZZARD
    }

    public enum Phase {
        BEFORE_TURN, ACTION, AFTER_TURN
    }

    public enum CostType {
        MP, HP_PERCENT, NONE
    }

    // ── Nested: MoveSnapshot ─────────────────────────────────────────────────

    public static class MoveSnapshot {
        public final String name;
        public final AffinityType affinity;
        public final CostType costType;
        public final int costValue;   // MP amount or HP percent

        public MoveSnapshot(String name, AffinityType affinity, CostType costType, int costValue) {
            this.name = name;
            this.affinity = affinity;
            this.costType = costType;
            this.costValue = costValue;
        }
    }

    // ── Nested: EntitySnapshot ────────────────────────────────────────────────

    public static class EntitySnapshot {
        public final String name;
        public final boolean isPlayer;
        public boolean isLeader;
        public boolean isAlive;        // false = dead
        public boolean isGuarding;
        public boolean isActive;       // is this the acting entity this turn?

        // Resources
        public final int maxHp;
        public int hp;
        public final int maxMp;
        public int mp;

        // Stats
        public final int baseStr, baseMag, baseLuck, baseEnd, baseSpd;

        // Affinities
        public final Map<AffinityType, AffinityRelation> affinities = new EnumMap<>(AffinityType.class);

        // Statuses: status -> turns remaining (0 = not active)
        public final Map<StatusType, Integer> statuses = new EnumMap<>(StatusType.class);

        // Buff stages: stat -> stage (-5..+5)
        public final Map<StatType, Integer> buffStages = new EnumMap<>(StatType.class);

        // Buff durations: stat -> turns remaining
        public final Map<StatType, Integer> buffDurations = new EnumMap<>(StatType.class);

        // Moveset
        public final List<MoveSnapshot> moves = new ArrayList<>();

        public EntitySnapshot(String name, boolean isPlayer, int maxHp, int maxMp,
                              int str, int mag, int luck, int end, int spd) {
            this.name = name;
            this.isPlayer = isPlayer;
            this.maxHp = maxHp;
            this.hp = maxHp;
            this.maxMp = maxMp;
            this.mp = maxMp;
            this.baseStr = str;
            this.baseMag = mag;
            this.baseLuck = luck;
            this.baseEnd = end;
            this.baseSpd = spd;
            this.isAlive = true;

            // Default all affinities to NEUTRAL
            for (AffinityType at : AffinityType.values()) {
                affinities.put(at, AffinityRelation.NEUTRAL);
            }
            // Default all statuses to 0
            for (StatusType st : StatusType.values()) {
                statuses.put(st, 0);
            }
            // Default all buff stages/durations to 0
            for (StatType st : StatType.values()) {
                buffStages.put(st, 0);
                buffDurations.put(st, 0);
            }
        }

        public boolean hasStatus(StatusType st) {
            return statuses.getOrDefault(st, 0) > 0;
        }

        public boolean isImmuneTo(AffinityType at) {
            return affinities.getOrDefault(at, AffinityRelation.NEUTRAL) == AffinityRelation.IMMUNE;
        }
    }

    // ── WorldState fields ─────────────────────────────────────────────────────

    public final String label;               // human-readable test world name
    public final List<EntitySnapshot> entities = new ArrayList<>();

    public FieldType fieldType = FieldType.NONE;
    public int fieldDuration = 0;

    public Phase phase = Phase.BEFORE_TURN;

    // The active entity name (null if none)
    public String activeEntityName = null;

    // The action being performed (null if phase != ACTION / AFTER_TURN)
    public String actionName = null;

    // The target of the action (null if no target)
    public String targetName = null;

    // ── Transition support ────────────────────────────────────────────────────
    // For STATUS-3 (poison tick) and AFFINITY-2 (combo damage boost), transition
    // rules need to compare HP before and after an event. Populate these maps
    // when building a WorldState for a transition-phase test world.
    //
    // beforeTurnHp   : entity name -> HP at START of turn, before end-of-turn
    //                  status ticks. Populated for STATUS-3 validation.
    //                  If empty, STATUS-3 is skipped.
    //
    // beforeActionHp : entity name -> HP before the current action resolved.
    //                  Populated for AFFINITY-2 validation.
    //                  If empty, AFFINITY-2 is skipped.
    //
    // expectedDamageMultiplier : the multiplier that SHOULD have applied to the
    //                  target (1.5 for WET+ELECTRIC or FROZEN+FIRE combos).
    //                  0.0 = not applicable / no check.
    //
    // baseDamageDealt : the raw base damage before multipliers were applied.
    //                  -1 = not applicable.

    /** HP before end-of-turn status ticks. Used by STATUS-3. */
    public final Map<String, Integer> beforeTurnHp = new LinkedHashMap<>();

    /** HP before the current action resolved. Used by AFFINITY-2. */
    public final Map<String, Integer> beforeActionHp = new LinkedHashMap<>();

    /** Expected damage multiplier from status+affinity combo (1.5 or 0.0). */
    public double expectedDamageMultiplier = 0.0;

    /** Base damage dealt before multipliers. -1 = not set. */
    public int baseDamageDealt = -1;

    public WorldState(String label) {
        this.label = label;
    }

    // ── Convenience helpers ───────────────────────────────────────────────────

    public EntitySnapshot getEntity(String name) {
        for (EntitySnapshot e : entities) {
            if (e.name.equalsIgnoreCase(name)) return e;
        }
        return null;
    }

    public EntitySnapshot getActiveEntity() {
        if (activeEntityName == null) return null;
        return getEntity(activeEntityName);
    }

    public EntitySnapshot getTarget() {
        if (targetName == null) return null;
        return getEntity(targetName);
    }

    /** All entities (both teams) */
    public List<EntitySnapshot> allEntities() {
        return Collections.unmodifiableList(entities);
    }

    /** Returns the move snapshot the active entity used, or null. */
    public MoveSnapshot getActiveMove() {
        EntitySnapshot actor = getActiveEntity();
        if (actor == null || actionName == null) return null;
        for (MoveSnapshot m : actor.moves) {
            if (m.name.equalsIgnoreCase(actionName)) return m;
        }
        return null;
    }
}
