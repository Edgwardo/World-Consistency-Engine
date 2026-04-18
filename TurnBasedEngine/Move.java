// Move.java
import java.util.*;

public class Move {
    public enum AffinityType {
        PHYSICAL, FIRE, ICE, WATER, ELECTRIC, WIND, HOLY, GROUND, CURSE
    }

    public enum TargetMode {
        SINGLE_ENEMY, ALL_ENEMIES, SINGLE_ALLY, ALL_ALLIES, SELF
    }

    public enum CostType {
        MP, HP_PERCENT, NONE
    }

public enum EffectType {
    NONE,
    APPLY_STATUS,
    APPLY_STATUS_ALL,
    BUFF_STAT,
    BUFF_STAT_ENEMY,   // <-- ADD THIS
    HEAL_PERCENT,
    REVIVE_50
}


    public enum StatType {
        STR, MAG, END, SPD, LUCK
    }

    public enum StatusType {
        BURNT, WET, FROZEN, PARALYZED, POISONED
    }

    public final String name;
    public final AffinityType affinity;
    public final TargetMode targetMode;
    public final CostType costType;
    public final int costValue;            // MP amount or HP percent (0-100)
    public final int basePower;            // abstract power rating
    public final EffectType effectType;

    // Effect details
    public final StatusType statusToApply;
    public final int statusChancePercent;  // 0-100
    public final int statusDurationTurns;  // duration when applied from move
    public final StatType buffStat;
    public final int buffDeltaStages;      // +1, -1, etc.
    public final int healPercent;          // heal % of target max HP

    private Move(
            String name,
            AffinityType affinity,
            TargetMode targetMode,
            CostType costType,
            int costValue,
            int basePower,
            EffectType effectType,
            StatusType statusToApply,
            int statusChancePercent,
            int statusDurationTurns,
            StatType buffStat,
            int buffDeltaStages,
            int healPercent
    ) {
        this.name = name;
        this.affinity = affinity;
        this.targetMode = targetMode;
        this.costType = costType;
        this.costValue = costValue;
        this.basePower = basePower;
        this.effectType = effectType;

        this.statusToApply = statusToApply;
        this.statusChancePercent = statusChancePercent;
        this.statusDurationTurns = statusDurationTurns;
        this.buffStat = buffStat;
        this.buffDeltaStages = buffDeltaStages;
        this.healPercent = healPercent;
    }

    // --- Factory helpers ---
    public static Move damage(
            String name,
            AffinityType affinity,
            TargetMode targetMode,
            CostType costType,
            int costValue,
            int basePower
    ) {
        return new Move(name, affinity, targetMode, costType, costValue, basePower,
                EffectType.NONE, null, 0, 0, null, 0, 0);
    }

    public static Move damageWithStatus(
            String name,
            AffinityType affinity,
            TargetMode targetMode,
            CostType costType,
            int costValue,
            int basePower,
            StatusType status,
            int chancePercent,
            int durationTurns
    ) {
        return new Move(name, affinity, targetMode, costType, costValue, basePower,
                EffectType.APPLY_STATUS, status, chancePercent, durationTurns, null, 0, 0);
    }

    public static Move damageAllWithStatusAll(
            String name,
            AffinityType affinity,
            TargetMode targetMode,
            CostType costType,
            int costValue,
            int basePower,
            StatusType status,
            int chancePercent,
            int durationTurns
    ) {
        return new Move(name, affinity, targetMode, costType, costValue, basePower,
                EffectType.APPLY_STATUS_ALL, status, chancePercent, durationTurns, null, 0, 0);
    }

    public static Move buff(
            String name,
            TargetMode targetMode,
            int mpCost,
            StatType stat,
            int deltaStages,
            int basePowerUnused
    ) {
        return new Move(name, AffinityType.PHYSICAL, targetMode, CostType.MP, mpCost, basePowerUnused,
                EffectType.BUFF_STAT, null, 0, 0, stat, deltaStages, 0);
    }

    public static Move healPercent(
            String name,
            TargetMode targetMode,
            int mpCost,
            int healPercent
    ) {
        return new Move(name, AffinityType.HOLY, targetMode, CostType.MP, mpCost, 0,
                EffectType.HEAL_PERCENT, null, 0, 0, null, 0, healPercent);
    }

    public static Move revive50(String name, int mpCost) {
        return new Move(name, AffinityType.HOLY, TargetMode.SINGLE_ALLY, CostType.MP, mpCost, 0,
                EffectType.REVIVE_50, null, 0, 0, null, 0, 0);
    }

    @Override
    public String toString() {
        return name;
    }

    // --- Move library (stored separately from characters) ---
    public static Map<String, Move> buildMoveLibrary() {
        Map<String, Move> m = new LinkedHashMap<>();

        // User-provided move list
        m.put("Strike", Move.damage("Strike", AffinityType.PHYSICAL, TargetMode.SINGLE_ENEMY, CostType.HP_PERCENT, 10, 45));
        m.put("Ember", Move.damageWithStatus("Ember", AffinityType.FIRE, TargetMode.SINGLE_ENEMY, CostType.MP, 4, 28,
                StatusType.BURNT, 70, 3));

        // Attack Up: buffs both STR and MAG in your spec (one stage, 3 turns). We'll implement as two buffs via a single move:
        // In engine we store it as a "BUFF_STAT" and handle special-case in Game by name (still rules-driven: "Attack Up buffs STR+MAG").
        m.put("Attack Up", Move.buff("Attack Up", TargetMode.SINGLE_ALLY, 10, StatType.STR, +1, 0));

        m.put("Bright Judgement", Move.damage("Bright Judgement", AffinityType.HOLY, TargetMode.ALL_ENEMIES, CostType.MP, 16, 40));
        m.put("Revive", Move.revive50("Revive", 12));
        m.put("Bufula", Move.damageWithStatus("Bufula", AffinityType.ICE, TargetMode.SINGLE_ENEMY, CostType.MP, 8, 42,
                StatusType.FROZEN, 50, 2));
        m.put("Heal", Move.healPercent("Heal", TargetMode.SINGLE_ALLY, 10, 10));

        m.put("Thunder", Move.damageWithStatus("Thunder", AffinityType.ELECTRIC, TargetMode.SINGLE_ENEMY, CostType.MP, 16, 60,
                StatusType.PARALYZED, 50, 3));
        m.put("Speed Up", Move.buff("Speed Up", TargetMode.SINGLE_ALLY, 10, StatType.SPD, +1, 0));
        m.put("Eiga", Move.damage("Eiga", AffinityType.CURSE, TargetMode.SINGLE_ENEMY, CostType.MP, 8, 44));

        m.put("Sweep", Move.damage("Sweep", AffinityType.PHYSICAL, TargetMode.ALL_ENEMIES, CostType.HP_PERCENT, 20, 55));
        m.put("Tsunami", Move.damageAllWithStatusAll("Tsunami", AffinityType.WATER, TargetMode.ALL_ENEMIES, CostType.MP, 20, 58,
                StatusType.WET, 100, 2));
        m.put("Earthquake", Move.damage("Earthquake", AffinityType.GROUND, TargetMode.ALL_ENEMIES, CostType.MP, 20, 58));

        // Optional: some enemies might apply poison
        m.put("Poisonous Slash", Move.damageWithStatus("Poisonous Slash", AffinityType.PHYSICAL, TargetMode.SINGLE_ENEMY, CostType.MP, 8, 35,
                StatusType.POISONED, 60, 3));
        // Attack Down
m.put("Attack Down", new Move(
        "Attack Down",
        AffinityType.CURSE,
        TargetMode.SINGLE_ENEMY,
        CostType.MP, 10,
        0,
        EffectType.BUFF_STAT_ENEMY,
        null, 0, 0,
        StatType.STR, -1,
        0
));


// Speed Down
m.put("Speed Down", new Move(
        "Speed Down",
        AffinityType.WIND,
        TargetMode.SINGLE_ENEMY,
        CostType.MP, 10,
        0,
        EffectType.BUFF_STAT_ENEMY,
        null, 0, 0,
        StatType.SPD, -1,
        0
));


        return m;
    }
}