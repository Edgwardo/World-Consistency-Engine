// Entity.java
import java.util.*;

public class Entity {
    public enum AffinityRelation { WEAK, RESIST, IMMUNE, NEUTRAL }

    public final String name;
    public final String schema;
    public final boolean isPlayer;

    public boolean isLeader;
    public boolean leaderNoRevive; // leaders cannot be revived (both sides per spec)

    // Core stats + resources
    public final int maxHp;
    public int hp;

    public final int maxMp;
    public int mp;

    public final int baseStr, baseMag, baseLuck, baseEnd, baseSpd;

    // Skills (up to 4) + guard provided by engine
    public final List<Move> skills = new ArrayList<>();

    // Affinities
    private final EnumMap<Move.AffinityType, AffinityRelation> affinities =
            new EnumMap<>(Move.AffinityType.class);

    // Status durations (turns remaining). Status effects tick at end of THIS entity's turn.
    private final EnumMap<Move.StatusType, Integer> statuses =
            new EnumMap<>(Move.StatusType.class);

    // Buff stages and durations
    // Stages: -5..+5 (each stage is 20%), duration turns: 3 when applied
    private final EnumMap<Move.StatType, Integer> buffStage =
            new EnumMap<>(Move.StatType.class);
    private final EnumMap<Move.StatType, Integer> buffTurnsLeft =
            new EnumMap<>(Move.StatType.class);

    // Guard (consumed when this entity is hit by ONE move)
    public boolean guardActive = false;

    public Entity(String name, String schema, boolean isPlayer, int maxHp, int maxMp,
                  int str, int mag, int luck, int end, int spd) {
        this.name = name;
        this.schema = schema;
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

        for (Move.AffinityType t : Move.AffinityType.values()) {
            affinities.put(t, AffinityRelation.NEUTRAL);
        }
        for (Move.StatType st : Move.StatType.values()) {
            buffStage.put(st, 0);
            buffTurnsLeft.put(st, 0);
        }
    }

    public boolean isDead() {
        return hp <= 0;
    }

    public void setAffinity(Move.AffinityType type, AffinityRelation rel) {
        affinities.put(type, rel);
    }

    public double affinityMultiplier(Move.AffinityType type) {
        AffinityRelation rel = affinities.getOrDefault(type, AffinityRelation.NEUTRAL);
        switch (rel) {
            case WEAK: return 2.0;
            case RESIST: return 0.5;
            case IMMUNE: return 0.0;
            default: return 1.0;
        }
    }

    public void addSkill(Move move) {
        if (skills.size() >= 4) return;
        skills.add(move);
    }

    public int getEffectiveStat(Move.StatType stat) {
        int base;
        switch (stat) {
            case STR: base = baseStr; break;
            case MAG: base = baseMag; break;
            case END: base = baseEnd; break;
            case SPD: base = baseSpd; break;
            case LUCK: base = baseLuck; break;
            default: base = 0;
        }
        int stage = buffStage.getOrDefault(stat, 0);
        double mult = 1.0 + 0.2 * stage;
        if (mult < 0.0) mult = 0.0;
        return (int)Math.round(base * mult);
    }

    public int getBuffStage(Move.StatType stat) {
        return buffStage.getOrDefault(stat, 0);
    }

    public void applyBuff(Move.StatType stat, int deltaStages, int durationTurns) {
        int cur = buffStage.getOrDefault(stat, 0);
        int next = cur + deltaStages;
        if (next > 5) next = 5;
        if (next < -5) next = -5;
        buffStage.put(stat, next);

        // Refresh duration (always 3 turns per your rules)
        buffTurnsLeft.put(stat, Math.max(buffTurnsLeft.getOrDefault(stat, 0), durationTurns));
    }

    public boolean hasStatus(Move.StatusType st) {
        return statuses.getOrDefault(st, 0) > 0;
    }

    public int getStatusTurnsLeft(Move.StatusType st) {
        return statuses.getOrDefault(st, 0);
    }

    // State incompatibilities:
    // - Burnt and Wet cannot overlap
    // - Frozen and Burnt cannot overlap
public void applyStatus(Move.StatusType st, int turns) {
    if (isDead()) return;

    // --- incompatibility rules ---
    if (st == Move.StatusType.WET && statuses.containsKey(Move.StatusType.BURNT)) {
        statuses.remove(Move.StatusType.BURNT);
    }
    if (st == Move.StatusType.BURNT && statuses.containsKey(Move.StatusType.FROZEN)) {
        statuses.remove(Move.StatusType.FROZEN);
    }
    if (st == Move.StatusType.FROZEN && statuses.containsKey(Move.StatusType.BURNT)) {
        return; // frozen blocked by burn
    }

    statuses.put(st, Math.max(statuses.getOrDefault(st, 0), turns));
}
public boolean blocksStatusByAffinity(Move.StatusType status, Move.AffinityType source) {
    if (source == null) return false;

    // immunity blocks both damage AND status
    return affinityMultiplier(source) == 0.0;
}



public void refreshStatus(Move.StatusType st, int durationTurns) {
    if (durationTurns <= 0) return;

    // Always go through applyStatus so incompatibility rules are enforced
    applyStatus(st, durationTurns);
}


    public void clearStatus(Move.StatusType st) {
        statuses.put(st, 0);
    }

    public void healPercent(int percent) {
        if (isDead()) return;
        int amt = (int)Math.round(maxHp * (percent / 100.0));
        hp = Math.min(maxHp, hp + Math.max(1, amt));
    }

    public boolean spendCost(Move move) {
        if (move.costType == Move.CostType.NONE) return true;

        if (move.costType == Move.CostType.MP) {
            if (mp < move.costValue) return false;
            mp -= move.costValue;
            return true;
        }

        if (move.costType == Move.CostType.HP_PERCENT) {
            int cost = (int)Math.round(maxHp * (move.costValue / 100.0));
            cost = Math.max(1, cost);
            // You can use HP-cost moves even if it would kill you; engine will handle consequences.
            hp -= cost;
            return true;
        }

        return false;
    }

    // Called at the end of THIS entity's turn:
    // - Tick burn/poison damage
    // - Decrement status durations
    // - Decrement buff durations
    public List<String> endOfTurnTick(Random rng) {
        List<String> logs = new ArrayList<>();
        if (isDead()) {
            // Still decrement durations? We'll keep it simple: dead entities do not tick statuses/buffs.
            return logs;
        }

        // Status effects happen only on owner's turn end
        if (hasStatus(Move.StatusType.BURNT)) {
            int dmg = Math.max(1, (int)Math.round(maxHp * 0.10)); // 10% max HP (simple)
            hp -= dmg;
            logs.add(name + " takes " + dmg + " burn damage.");
        }
        if (hasStatus(Move.StatusType.POISONED)) {
            int dmg = Math.max(1, (int)Math.round(maxHp * 0.08)); // 8% max HP (simple)
            hp -= dmg;
            logs.add(name + " takes " + dmg + " poison damage.");
        }

        // Decrement status durations
        for (Move.StatusType st : Move.StatusType.values()) {
            int t = statuses.getOrDefault(st, 0);
            if (t > 0) statuses.put(st, t - 1);
        }

        // Decrement buff durations; if expires, clear stage
        for (Move.StatType st : Move.StatType.values()) {
            int t = buffTurnsLeft.getOrDefault(st, 0);
            if (t > 0) {
                t -= 1;
                buffTurnsLeft.put(st, t);
                if (t == 0) {
                    buffStage.put(st, 0);
                    logs.add(name + "'s " + st + " modifiers wear off.");
                }
            }
        }

        if (hp <= 0) {
            hp = 0;
            logs.add(name + " falls!");
        }

        return logs;
    }

    public String shortStatusLine() {
        List<String> s = new ArrayList<>();
        for (Move.StatusType st : Move.StatusType.values()) {
            int t = statuses.getOrDefault(st, 0);
            if (t > 0) s.add(st.name() + "(" + t + ")");
        }
        return s.isEmpty() ? "-" : String.join(", ", s);
    }

    public String shortBuffLine() {
        List<String> b = new ArrayList<>();
        for (Move.StatType st : Move.StatType.values()) {
            int stage = buffStage.getOrDefault(st, 0);
            int t = buffTurnsLeft.getOrDefault(st, 0);
            if (stage != 0 && t > 0) {
                b.add(st.name() + (stage > 0 ? "+" : "") + stage + "(" + t + ")");
            }
        }
        return b.isEmpty() ? "-" : String.join(", ", b);
    }
}