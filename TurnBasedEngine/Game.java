// Game.java
import java.util.*;

public class Game {
    private final Scanner sc;
    private final Random rng = new Random();

    private final Map<String, Move> moves = Move.buildMoveLibrary();

    private List<Entity> playerTeam = new ArrayList<>();
    private List<Entity> enemyTeam = new ArrayList<>();

    // Field state
    private enum FieldType { NONE, RAIN, FIRE, BLIZZARD }
    private FieldType field = FieldType.NONE;
    private int fieldTurnsLeft = 0; // how long the field persists

    // --- NEW: print pacing / delays ---
    private static final long SHORT_DELAY_MS = 450;
    private static final long ACTION_DELAY_MS = 900;
    private static final long TURN_BREAK_DELAY_MS = 650;

    private void pause(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private void printlnPaced(String s, long delayMs) {
        System.out.println(s);
        if (delayMs > 0) pause(delayMs);
    }

    private void printSectionBreak() {
        System.out.println();
        pause(TURN_BREAK_DELAY_MS);
    }

    public Game(Scanner sc) {
        this.sc = sc;
    }

    public void run() {
        boolean running = true;

        // Start with a battle
        setupNewBattle(true);

        while (running) {
            runBattleLoop();

            // Battle ended
            System.out.println();
            System.out.println("=== Battle Over ===");
            boolean playerWon = allDead(enemyTeam);
            if (playerWon) System.out.println("Result: YOU WIN!");
            else System.out.println("Result: YOU LOSE!");

            System.out.println();
            System.out.println("What next?");
            System.out.println("1) Retry (same leader)");
            System.out.println("2) Retry (different leader)");
            System.out.println("3) New battle (different enemies + choose leader)");
            System.out.println("4) Quit");
            int choice = readIntRange(1, 4);

            if (choice == 1) {
                // Same leader: rebuild teams, reuse leader pick
                String leaderName = getCurrentLeaderName(playerTeam);
                setupNewBattle(false);
                setLeaderByName(playerTeam, leaderName);
            } else if (choice == 2) {
                setupNewBattle(false);
                chooseLeader(playerTeam);
            } else if (choice == 3) {
                setupNewBattle(true);
            } else {
                running = false;
            }
        }

        System.out.println("Goodbye.");
    }

    private void setupNewBattle(boolean newEnemies) {
        // Reset field
        field = FieldType.NONE;
        fieldTurnsLeft = 0;

        // Build player team
        playerTeam = buildPlayerParty();
        chooseLeader(playerTeam);

        // Build enemy team
        if (newEnemies) {
            enemyTeam = buildRandomEnemies();
        } else {
            // Rebuild enemies with same "count + template mix" pattern for a clean retry
            enemyTeam = buildRandomEnemies();
        }

        // Enemy leaders cannot be revived (leaders already default to no revive)
        // If you want multiple enemy leaders, pick 1–2 at random
        int leadersCount = Math.max(1, enemyTeam.size() / 2);
        Collections.shuffle(enemyTeam, rng);
        for (int i = 0; i < enemyTeam.size(); i++) {
            enemyTeam.get(i).isLeader = (i < leadersCount);
            enemyTeam.get(i).leaderNoRevive = enemyTeam.get(i).isLeader;
        }

        System.out.println();
        System.out.println("=== New Battle ===");
        pause(SHORT_DELAY_MS);
        System.out.println("Your team:");
        printTeam(playerTeam);
        pause(SHORT_DELAY_MS);
        System.out.println("Enemies:");
        printTeam(enemyTeam);
        printSectionBreak();
    }

    // --- Battle Loop ---
    private void runBattleLoop() {
        // Cycle-based initiative: order recalculated after everyone has had a turn
        while (true) {
            if (checkWinLoss()) return;

            List<Entity> cycleOrder = buildCycleOrder();
            for (Entity actor : cycleOrder) {
                if (checkWinLoss()) return;
                if (actor.isDead()) continue;

                // Each entity turn begins: determine/apply field (applies to all at once)
                fieldStep();

                if (checkWinLoss()) return;

                // Action (player or AI)
System.out.println("================================");
System.out.println("=== " + actor.name.toUpperCase() + "'S TURN ===");
System.out.println("================================");
pause(SHORT_DELAY_MS);

if (actor.isPlayer) {
    playerTurn(actor);
} else {
    enemyTurn(actor);
}


                // Win check after move resolves (per your rules)
                if (checkWinLoss()) return;

                // End-of-turn status effects only for acting entity
                List<String> tickLogs = actor.endOfTurnTick(rng);
                for (String log : tickLogs) printlnPaced("  " + log, SHORT_DELAY_MS);

                // Win check after end-of-turn effects (per your rules)
                if (checkWinLoss()) return;

                printSectionBreak();
            }
            // After everyone has had a turn, the next cycle will recalc order automatically.
        }
    }

    private List<Entity> buildCycleOrder() {
        List<Entity> all = new ArrayList<>();
        for (Entity e : playerTeam) if (!e.isDead()) all.add(e);
        for (Entity e : enemyTeam) if (!e.isDead()) all.add(e);

        all.sort((a, b) -> {
            int sa = a.getEffectiveStat(Move.StatType.SPD);
            int sb = b.getEffectiveStat(Move.StatType.SPD);
            if (sa != sb) return Integer.compare(sb, sa);
            // tie breaker: player first for determinism
            if (a.isPlayer != b.isPlayer) return a.isPlayer ? -1 : 1;
            return a.name.compareTo(b.name);
        });

        return all;
    }

    // --- Field ---
    private void fieldStep() {
        // Determine field at start of each turn:
        // If none, roll to start one (some more common/persistent)
        // If active, don't change unless ended by moves
        if (field == FieldType.NONE) {
            // 50% chance to start a field
            int roll = rng.nextInt(100);
            if (roll < 50) {
                // Choose type with weighted probability
                int pick = rng.nextInt(100);
                if (pick < 45) {
                    field = FieldType.RAIN;
                    fieldTurnsLeft = 4 + rng.nextInt(3); // 4-6
                } else if (pick < 80) {
                    field = FieldType.FIRE;
                    fieldTurnsLeft = 3 + rng.nextInt(3); // 3-5
                } else {
                    field = FieldType.BLIZZARD;
                    fieldTurnsLeft = 1; 
                }
                printlnPaced(">>> Field begins: " + field + " (" + fieldTurnsLeft + " turns)", SHORT_DELAY_MS);
            }
        }

        // Apply field to everyone at once at start of each turn; refresh duration each turn
        if (field != FieldType.NONE) {
            applyFieldToAll();

            // Field persistence
            fieldTurnsLeft--;
            if (fieldTurnsLeft <= 0) {
                printlnPaced(">>> Field ends.", SHORT_DELAY_MS);
                field = FieldType.NONE;
                fieldTurnsLeft = 0;
            }
        }
    }

    private void applyFieldToAll() {
        // Field refreshes status duration each turn.
        // We'll use duration=2 for refreshed statuses (kept refreshed anyway each turn).
        int refreshTurns = 2;

        if (field == FieldType.RAIN) {
            for (Entity e : allEntities()) e.refreshStatus(Move.StatusType.WET, refreshTurns);
            printlnPaced(">>> Rain drenches everyone (WET refreshed).", SHORT_DELAY_MS);
        } else if (field == FieldType.FIRE) {
            for (Entity e : allEntities()) e.refreshStatus(Move.StatusType.BURNT, refreshTurns);
            printlnPaced(">>> Flames scorch the area (BURNT refreshed where possible).", SHORT_DELAY_MS);
} else if (field == FieldType.BLIZZARD) {
    for (Entity e : allEntities()) {
        if (e.isDead()) continue;

        int freezeChance = 35; // % chance to be frozen per application
        if (rng.nextInt(100) < freezeChance) {
            e.refreshStatus(Move.StatusType.FROZEN, refreshTurns);
        }
    }
    printlnPaced(">>> Blizzard sweeps the field (chance to FREEZE).", SHORT_DELAY_MS);
}

    }

    private List<Entity> allEntities() {
        List<Entity> all = new ArrayList<>();
        all.addAll(playerTeam);
        all.addAll(enemyTeam);
        return all;
    }

    // --- Player Turn ---
    private void playerTurn(Entity actor) {
        printlnPaced("Your turn: " + actor.name + (actor.isLeader ? " [LEADER]" : ""), SHORT_DELAY_MS);
        printBattleState();

        // If frozen: skip action (still end-of-turn ticks happen in loop)
        if (actor.hasStatus(Move.StatusType.FROZEN)) {
            printlnPaced(actor.name + " is FROZEN and cannot act.", ACTION_DELAY_MS);
            return;
        }

        // If paralyzed: chance to lose action
        if (actor.hasStatus(Move.StatusType.PARALYZED)) {
            int roll = rng.nextInt(100);
            if (roll < 25) {
                printlnPaced(actor.name + " is PARALYZED and loses the turn!", ACTION_DELAY_MS);
                return;
            }
        }

        // Choose skill or guard
        List<String> options = new ArrayList<>();
        for (Move mv : actor.skills) options.add(mv.name);
        options.add("Guard");

        System.out.println("Choose action:");
        for (int i = 0; i < options.size(); i++) {
            System.out.println((i + 1) + ") " + options.get(i));
        }

        int choice = readIntRange(1, options.size());
        String picked = options.get(choice - 1);

        if (picked.equals("Guard")) {
            actor.guardActive = true;
            printlnPaced(actor.name + " takes a defensive stance (GUARD).", ACTION_DELAY_MS);
            return;
        }

        Move move = moves.get(picked);
        if (move == null) {
            printlnPaced("Invalid move. Skipping.", ACTION_DELAY_MS);
            return;
        }

        if (!canPayCost(actor, move)) {
            printlnPaced("Not enough resources to use " + move.name + ". Turn wasted.", ACTION_DELAY_MS);
            return;
        }

        // Select targets
        List<Entity> targets = selectTargetsForPlayer(move, actor);
        if (targets.isEmpty()) {
            printlnPaced("No valid targets.", ACTION_DELAY_MS);
            return;
        }

        // Pay cost
        actor.spendCost(move);

        // Resolve action
        resolveMove(actor, targets, move, actor.isPlayer);
    }

    private List<Entity> selectTargetsForPlayer(Move move, Entity actor) {
        List<Entity> targets = new ArrayList<>();

        if (move.targetMode == Move.TargetMode.SINGLE_ENEMY) {
            List<Entity> livingEnemies = living(enemyTeam);
            System.out.println("Choose enemy target:");
            for (int i = 0; i < livingEnemies.size(); i++) {
                Entity e = livingEnemies.get(i);
                System.out.println((i + 1) + ") " + e.name + " HP:" + e.hp + "/" + e.maxHp + " Status:" + e.shortStatusLine());
            }
            int t = readIntRange(1, livingEnemies.size());
            targets.add(livingEnemies.get(t - 1));
            return targets;
        }

        if (move.targetMode == Move.TargetMode.ALL_ENEMIES) {
            targets.addAll(living(enemyTeam));
            return targets;
        }

        if (move.targetMode == Move.TargetMode.SINGLE_ALLY) {
            // For revive, allow dead allies; for others, prefer living
            boolean wantsDead = (move.effectType == Move.EffectType.REVIVE_50);
            List<Entity> pool = wantsDead ? dead(playerTeam) : living(playerTeam);

            if (pool.isEmpty()) return targets;

            System.out.println("Choose ally target:");
            for (int i = 0; i < pool.size(); i++) {
                Entity e = pool.get(i);
                System.out.println((i + 1) + ") " + e.name + " HP:" + e.hp + "/" + e.maxHp + (e.isDead() ? " [DEAD]" : ""));
            }
            int t = readIntRange(1, pool.size());
            targets.add(pool.get(t - 1));
            return targets;
        }

        if (move.targetMode == Move.TargetMode.ALL_ALLIES) {
            targets.addAll(living(playerTeam));
            return targets;
        }

        if (move.targetMode == Move.TargetMode.SELF) {
            targets.add(actor);
            return targets;
        }

        return targets;
    }

    // --- Enemy Turn (simple AI) ---
    private void enemyTurn(Entity actor) {
        printlnPaced("Enemy turn: " + actor.name + (actor.isLeader ? " [LEADER]" : ""), SHORT_DELAY_MS);

        if (actor.hasStatus(Move.StatusType.FROZEN)) {
            printlnPaced(actor.name + " is FROZEN and cannot act.", ACTION_DELAY_MS);
            return;
        }
        if (actor.hasStatus(Move.StatusType.PARALYZED)) {
            int roll = rng.nextInt(100);
            if (roll < 25) {
                printlnPaced(actor.name + " is PARALYZED and loses the turn!", ACTION_DELAY_MS);
                return;
            }
        }

        // AI: pick first usable offensive move; if none, guard
        Move chosen = null;

        // Heals if low HP and has Heal
        if (!actor.isDead() && actor.hp < actor.maxHp * 0.35) {
            Move heal = moves.get("Heal");
            if (actor.skills.contains(heal) && canPayCost(actor, heal)) chosen = heal;
        }

        if (chosen == null) {
            // Prefer AoE sometimes
            List<Move> usable = new ArrayList<>();
            for (Move mv : actor.skills) {
                if (canPayCost(actor, mv)) usable.add(mv);
            }
            if (usable.isEmpty()) {
                actor.guardActive = true;
                printlnPaced(actor.name + " uses GUARD.", ACTION_DELAY_MS);
                return;
            }
            chosen = usable.get(rng.nextInt(usable.size()));
        }

        // Choose targets based on move
        List<Entity> targets = new ArrayList<>();
        if (chosen.targetMode == Move.TargetMode.SINGLE_ENEMY) {
            List<Entity> livingPlayers = living(playerTeam);
            targets.add(livingPlayers.get(rng.nextInt(livingPlayers.size())));
        } else if (chosen.targetMode == Move.TargetMode.ALL_ENEMIES) {
            targets.addAll(living(playerTeam));
        } else if (chosen.targetMode == Move.TargetMode.SINGLE_ALLY) {
            // Enemies don't revive leaders and we don't give enemy revive by default; still handle:
            if (chosen.effectType == Move.EffectType.REVIVE_50) {
                List<Entity> dead = dead(enemyTeam);
                if (!dead.isEmpty()) targets.add(dead.get(rng.nextInt(dead.size())));
            } else {
                List<Entity> livingEnemies = living(enemyTeam);
                targets.add(livingEnemies.get(rng.nextInt(livingEnemies.size())));
            }
        } else if (chosen.targetMode == Move.TargetMode.ALL_ALLIES) {
            targets.addAll(living(enemyTeam));
        } else if (chosen.targetMode == Move.TargetMode.SELF) {
            targets.add(actor);
        }

        if (targets.isEmpty()) {
            actor.guardActive = true;
            printlnPaced(actor.name + " uses GUARD.", ACTION_DELAY_MS);
            return;
        }

        actor.spendCost(chosen);
        resolveMove(actor, targets, chosen, actor.isPlayer);
    }

    // --- Move Resolution ---
    private void resolveMove(Entity user, List<Entity> targets, Move move, boolean userIsPlayer) {
        printlnPaced(user.name + " uses " + move.name + "!", SHORT_DELAY_MS);

        // Special: Attack Up buffs STR and MAG by +1 stage for 3 turns on one ally
        if (move.name.equals("Attack Up")) {
            Entity t = targets.get(0);
            t.applyBuff(Move.StatType.STR, +1, 3);
            t.applyBuff(Move.StatType.MAG, +1, 3);
            printlnPaced("  " + t.name + " gains STR+1 and MAG+1 for 3 turns.", ACTION_DELAY_MS);
            return;
        }

        // Guard is chosen separately, so move.name won't be Guard
        switch (move.effectType) {
            case HEAL_PERCENT:
                for (Entity t : targets) {
                    if (t.isDead()) continue;
                    t.healPercent(move.healPercent);
                    printlnPaced("  " + t.name + " heals " + move.healPercent + "% HP.", SHORT_DELAY_MS);
                }
                pause(ACTION_DELAY_MS);
                return;

            case REVIVE_50:
                Entity target = targets.get(0);
                if (!target.isDead()) {
                    printlnPaced("  But " + target.name + " is not dead.", ACTION_DELAY_MS);
                    return;
                }
                if (target.isLeader || target.leaderNoRevive) {
                    printlnPaced("  But leaders cannot be revived!", ACTION_DELAY_MS);
                    return;
                }
                target.hp = (int)Math.round(target.maxHp * 0.50);
                target.mp = Math.min(target.maxMp, target.mp); // leave MP as-is
                printlnPaced("  " + target.name + " is revived with 50% HP!", ACTION_DELAY_MS);
                return;

            case BUFF_STAT:
                for (Entity t : targets) {
                    t.applyBuff(move.buffStat, move.buffDeltaStages, 3);
                    printlnPaced("  " + t.name + " gains " + move.buffStat + (move.buffDeltaStages > 0 ? "+" : "") + move.buffDeltaStages + " for 3 turns.", SHORT_DELAY_MS);
                }
                pause(ACTION_DELAY_MS);
                return;
            case BUFF_STAT_ENEMY:
    for (Entity t : targets) {
        t.applyBuff(move.buffStat, move.buffDeltaStages, 3);
        printlnPaced("  " + t.name + " suffers " + move.buffStat + move.buffDeltaStages + " for 3 turns.", SHORT_DELAY_MS);
    }
    pause(ACTION_DELAY_MS);
    return;

            default:
                // continue to damage handling
        }

        // Damage phase (pace each target result)
        for (Entity t : targets) {
            if (t.isDead()) continue;

            int dmg = calculateDamage(user, t, move);
            boolean wasGuarding = t.guardActive;

            if (wasGuarding) {
                dmg = (int)Math.ceil(dmg / 2.0);
            }

            // Apply damage
            t.hp -= dmg;
            if (t.hp < 0) t.hp = 0;

            printlnPaced("  " + t.name + " takes " + dmg + " " + move.affinity + " damage" + (wasGuarding ? " (GUARDED)" : "") + ".", SHORT_DELAY_MS);

            // Consume guard after taking damage from ONE move
            if (wasGuarding) t.guardActive = false;

            // Apply statuses from moves (guard prevents status effects from moves)
            if (!wasGuarding) {
                applyMoveStatusIfAny(user, t, move);
            }

            if (t.isDead()) {
                printlnPaced("  " + t.name + " falls!", SHORT_DELAY_MS);
            }

            // Small pause after each target so multi-target attacks are readable
            pause(SHORT_DELAY_MS);
        }

        // Special: Tsunami applies wet to all enemies (already modeled as APPLY_STATUS_ALL)
        if (move.effectType == Move.EffectType.APPLY_STATUS_ALL) {
            for (Entity t : targets) {
                if (t.isDead()) continue;

if (t.guardActive) {
    printlnPaced("  " + t.name + " blocks status with GUARD.", SHORT_DELAY_MS);
    t.guardActive = false;
}
else if (t.blocksStatusByAffinity(move.statusToApply, move.affinity)) {
    printlnPaced("  >>> Status Resisted by Affinity <<<", SHORT_DELAY_MS);
}
else {
    t.applyStatus(move.statusToApply, move.statusDurationTurns);
    printlnPaced("  " + t.name + " is afflicted with " + move.statusToApply +
            " (" + move.statusDurationTurns + " turns).", SHORT_DELAY_MS);
}


                pause(SHORT_DELAY_MS);
            }
        }

        // Bigger pause after the action resolves so the player can read the results
        pause(ACTION_DELAY_MS);
    }
    

    private void applyMoveStatusIfAny(Entity user, Entity target, Move move) {
        if (move.effectType != Move.EffectType.APPLY_STATUS) return;
        if (move.statusToApply == null) return;
        if (target.isDead()) return;

        int roll = rng.nextInt(100);
if (roll < move.statusChancePercent) {
    if (target.blocksStatusByAffinity(move.statusToApply, move.affinity)) {
        printlnPaced("  >>> Status Resisted by Affinity <<<", SHORT_DELAY_MS);
    } else {
        target.applyStatus(move.statusToApply, move.statusDurationTurns);
        printlnPaced("  " + target.name + " is afflicted with " + move.statusToApply + " (" + move.statusDurationTurns + " turns).", SHORT_DELAY_MS);
    }
}
}


    // --- Damage (simple, keeps math minimal but functional) ---
    // We keep this intentionally simple and rules-driven:
    // - Choose attack stat (STR for physical, MAG otherwise)
    // - Scale with (stat/100)
    // - Reduce with endurance (percent reduction based on END)
    // - Multiply by affinity (weak/resist/immune)
    // - Crit chance from Luck (no crits on zero damage)
    private int calculateDamage(Entity attacker, Entity defender, Move move) {
        double affinityMult = defender.affinityMultiplier(move.affinity);
        if (affinityMult == 0.0) {
        printlnPaced("  >>> NO EFFECT <<<", SHORT_DELAY_MS);

            return 0;
        }

        int atkStat = (move.affinity == Move.AffinityType.PHYSICAL)
                ? attacker.getEffectiveStat(Move.StatType.STR)
                : attacker.getEffectiveStat(Move.StatType.MAG);

        int endStat = defender.getEffectiveStat(Move.StatType.END);

        // Base scaling (0-100 stats)
        double scaled = move.basePower * (atkStat / 100.0);

        // Endurance reduces damage: simple diminishing returns
        double endReduction = endStat / 200.0; // END=100 => 0.5 reduction
        if (endReduction > 0.70) endReduction = 0.70;
        double afterEnd = scaled * (1.0 - endReduction);

double afterAffinity = afterEnd * affinityMult;

// --- Status-element interaction bonuses ---
boolean statusBoost = false;
if (defender.hasStatus(Move.StatusType.FROZEN) && move.affinity == Move.AffinityType.FIRE) {
    afterAffinity *= 1.5;
    statusBoost = true;
}
if (defender.hasStatus(Move.StatusType.WET) && move.affinity == Move.AffinityType.ELECTRIC) {
    afterAffinity *= 1.5;
    statusBoost = true;
}

// --- Effectiveness messages ---
boolean affinityBoost = affinityMult > 1.0;
boolean affinityResist = affinityMult < 1.0;

if (affinityBoost && statusBoost) {
    printlnPaced("  >>> SUPER EFFECTIVE! <<<", SHORT_DELAY_MS);
} else if (affinityBoost || statusBoost) {
    printlnPaced("  >>> Effective! <<<", SHORT_DELAY_MS);
} else if (affinityResist) {
    printlnPaced("  >>> RESISTED <<<", SHORT_DELAY_MS);
}

        boolean crit = false;
        int luck = attacker.getEffectiveStat(Move.StatType.LUCK);
        int critChance = Math.min(50, 5 + (luck / 2)); // simple: 5%..55% capped to 50
        if (afterAffinity > 0.0) {
            int roll = rng.nextInt(100);
            crit = roll < critChance;
        }
        double critMult = crit ? 1.5 : 1.0;

        int dmg = (int)Math.round(afterAffinity * critMult);

        // Ensure non-immune hits do at least 1 damage if basePower > 0
        if (move.basePower > 0 && affinityMult > 0.0) dmg = Math.max(1, dmg);

        if (crit && dmg > 0) {
            printlnPaced("  Critical hit!", SHORT_DELAY_MS);
        }

        return dmg;
    }

    private boolean canPayCost(Entity user, Move move) {
        if (move.costType == Move.CostType.NONE) return true;
        if (move.costType == Move.CostType.MP) return user.mp >= move.costValue;
        if (move.costType == Move.CostType.HP_PERCENT) return user.hp > 0; // can always pay; may KO self
        return false;
    }

    // --- Win/Loss Checks ---
    private boolean checkWinLoss() {
        // Loss: player leader dead
        Entity leader = getLeader(playerTeam);
        if (leader == null || leader.isDead()) {
            return true;
        }

        // Win: all enemies dead at the same time
        if (allDead(enemyTeam)) {
            return true;
        }
        return false;
    }

    private boolean allDead(List<Entity> team) {
        for (Entity e : team) if (!e.isDead()) return false;
        return true;
    }

    private Entity getLeader(List<Entity> team) {
        for (Entity e : team) if (e.isLeader) return e;
        return null;
    }

    // --- Setup: Player Party (schemas) ---
    private List<Entity> buildPlayerParty() {
        List<Entity> party = new ArrayList<>();

        // Warden
        Entity warden = new Entity("Warden", "Warden", true, 200, 50,
                85, 20, 15, 55, 50);
        warden.setAffinity(Move.AffinityType.FIRE, Entity.AffinityRelation.RESIST);
        warden.setAffinity(Move.AffinityType.PHYSICAL, Entity.AffinityRelation.RESIST);
        warden.setAffinity(Move.AffinityType.WATER, Entity.AffinityRelation.WEAK);
        warden.setAffinity(Move.AffinityType.GROUND, Entity.AffinityRelation.WEAK);
        warden.addSkill(moves.get("Strike"));
        warden.addSkill(moves.get("Ember"));
        warden.addSkill(moves.get("Attack Up"));
        warden.addSkill(moves.get("Attack Down"));

        party.add(warden);

        // Priest
        Entity priest = new Entity("Priest", "Priest", true, 100, 150,
                20, 90, 50, 25, 50);
        priest.setAffinity(Move.AffinityType.ICE, Entity.AffinityRelation.RESIST);
        priest.setAffinity(Move.AffinityType.CURSE, Entity.AffinityRelation.WEAK);
        priest.setAffinity(Move.AffinityType.HOLY, Entity.AffinityRelation.IMMUNE);
        priest.addSkill(moves.get("Bright Judgement"));
        priest.addSkill(moves.get("Revive"));
        priest.addSkill(moves.get("Bufula"));
        priest.addSkill(moves.get("Heal"));

        party.add(priest);

        // Raiju
        Entity raiju = new Entity("Raiju", "Raiju", true, 125, 125,
                55, 55, 50, 25, 85);
        raiju.setAffinity(Move.AffinityType.GROUND, Entity.AffinityRelation.WEAK);
        raiju.setAffinity(Move.AffinityType.ELECTRIC, Entity.AffinityRelation.IMMUNE);
        raiju.addSkill(moves.get("Strike"));
        raiju.addSkill(moves.get("Thunder"));
        raiju.addSkill(moves.get("Speed Up"));
        raiju.addSkill(moves.get("Eiga"));
        raiju.addSkill(moves.get("Speed Down"));

        party.add(raiju);

        // Swamp
        Entity swamp = new Entity("Swamp", "Swamp", true, 150, 100,
                80, 50, 15, 80, 25);
        swamp.setAffinity(Move.AffinityType.FIRE, Entity.AffinityRelation.RESIST);
        swamp.setAffinity(Move.AffinityType.WATER, Entity.AffinityRelation.RESIST);
        swamp.setAffinity(Move.AffinityType.GROUND, Entity.AffinityRelation.RESIST);
        swamp.setAffinity(Move.AffinityType.PHYSICAL, Entity.AffinityRelation.WEAK);
        swamp.setAffinity(Move.AffinityType.ICE, Entity.AffinityRelation.WEAK);
        swamp.setAffinity(Move.AffinityType.ELECTRIC, Entity.AffinityRelation.IMMUNE);
        swamp.addSkill(moves.get("Sweep"));
        swamp.addSkill(moves.get("Tsunami"));
        swamp.addSkill(moves.get("Earthquake"));
        party.add(swamp);
        
        Entity Hero = new Entity("Hero", "Hero", true, 250, 0,
                100, 0, 80, 80, 90);
        Hero.setAffinity(Move.AffinityType.FIRE, Entity.AffinityRelation.RESIST);
        Hero.setAffinity(Move.AffinityType.WATER, Entity.AffinityRelation.RESIST);
        Hero.setAffinity(Move.AffinityType.ELECTRIC, Entity.AffinityRelation.RESIST);
        Hero.setAffinity(Move.AffinityType.CURSE, Entity.AffinityRelation.WEAK);
        Hero.setAffinity(Move.AffinityType.PHYSICAL, Entity.AffinityRelation.IMMUNE);
        Hero.addSkill(moves.get("Strike"));
        Hero.addSkill(moves.get("Sweep"));
        Hero.addSkill(moves.get("Attack Up"));
        party.add(Hero);

        // Leaders cannot be revived (will set on chosen leader)
        return party;
    }

    // --- Setup: Enemies ---
    private List<Entity> buildRandomEnemies() {
        int count = 2 + rng.nextInt(3); // 2-4 enemies
        List<Entity> enemies = new ArrayList<>();

        // Enemy templates (simple and consistent with your affinity system)
        for (int i = 1; i <= count; i++) {
            int pick = rng.nextInt(4);
            Entity e;
            if (pick == 0) {
                e = new Entity("Brute " + i, "Brute", false, 140, 40, 75, 20, 20, 60, 35);
                e.setAffinity(Move.AffinityType.PHYSICAL, Entity.AffinityRelation.RESIST);
                e.setAffinity(Move.AffinityType.WATER, Entity.AffinityRelation.WEAK);
                e.addSkill(moves.get("Strike"));
                e.addSkill(moves.get("Sweep"));
            } else if (pick == 1) {
                e = new Entity("Hexer " + i, "Hexer", false, 110, 120, 25, 75, 35, 30, 45);
                e.setAffinity(Move.AffinityType.CURSE, Entity.AffinityRelation.RESIST);
                e.setAffinity(Move.AffinityType.HOLY, Entity.AffinityRelation.WEAK);
                e.addSkill(moves.get("Eiga"));
                e.addSkill(moves.get("Poisonous Slash"));
                e.addSkill(moves.get("Heal"));
            } else if (pick == 2) {
                e = new Entity("Stormling " + i, "Stormling", false, 120, 110, 35, 70, 40, 25, 70);
                e.setAffinity(Move.AffinityType.ELECTRIC, Entity.AffinityRelation.RESIST);
                e.setAffinity(Move.AffinityType.GROUND, Entity.AffinityRelation.WEAK);
                e.addSkill(moves.get("Thunder"));
                e.addSkill(moves.get("Speed Up"));
                e.addSkill(moves.get("Strike"));
            } else {
                e = new Entity("Frostbite " + i, "Frostbite", false, 130, 90, 45, 65, 25, 35, 40);
                e.setAffinity(Move.AffinityType.ICE, Entity.AffinityRelation.RESIST);
                e.setAffinity(Move.AffinityType.FIRE, Entity.AffinityRelation.WEAK);
                e.addSkill(moves.get("Bufula"));
                e.addSkill(moves.get("Strike"));
            }

            // Enemy leaders cannot be revived if flagged leader
            e.leaderNoRevive = false;
            enemies.add(e);
        }

        return enemies;
    }

    // --- Leader Selection ---
    private void chooseLeader(List<Entity> party) {
        System.out.println("Choose your LEADER (loss if leader dies):");
        for (int i = 0; i < party.size(); i++) {
            System.out.println((i + 1) + ") " + party.get(i).name + " (" + party.get(i).schema + ")");
        }
        int pick = readIntRange(1, party.size());

        for (Entity e : party) {
            e.isLeader = false;
            e.leaderNoRevive = false;
        }

        Entity leader = party.get(pick - 1);
        leader.isLeader = true;
        leader.leaderNoRevive = true; // leaders can't be revived
        printlnPaced("Leader set to: " + leader.name, ACTION_DELAY_MS);
        System.out.println();
    }

    private void setLeaderByName(List<Entity> party, String name) {
        if (name == null) {
            chooseLeader(party);
            return;
        }
        for (Entity e : party) {
            e.isLeader = false;
            e.leaderNoRevive = false;
        }
        for (Entity e : party) {
            if (e.name.equalsIgnoreCase(name)) {
                e.isLeader = true;
                e.leaderNoRevive = true;
                return;
            }
        }
        chooseLeader(party);
    }

    private String getCurrentLeaderName(List<Entity> party) {
        Entity l = getLeader(party);
        return l == null ? null : l.name;
    }

    // --- UI Helpers ---
    private void printTeam(List<Entity> team) {
        for (Entity e : team) {
            System.out.println("- " + e.name
                    + (e.isLeader ? " [LEADER]" : "")
                    + " HP:" + e.hp + "/" + e.maxHp
                    + " MP:" + e.mp + "/" + e.maxMp
                    + " Status:" + e.shortStatusLine()
                    + " Buffs:" + e.shortBuffLine());
            pause(200);
        }
    }

    private void printBattleState() {
        System.out.println("Field: " + field + (field == FieldType.NONE ? "" : " (" + fieldTurnsLeft + " turns left)"));
        pause(200);
        System.out.println("Your team:");
        printTeam(playerTeam);
        pause(250);
        System.out.println("Enemies:");
        printTeam(enemyTeam);
        System.out.println();
        pause(SHORT_DELAY_MS);
    }

    // --- Collections helpers ---
    private List<Entity> living(List<Entity> team) {
        List<Entity> out = new ArrayList<>();
        for (Entity e : team) if (!e.isDead()) out.add(e);
        return out;
    }

    private List<Entity> dead(List<Entity> team) {
        List<Entity> out = new ArrayList<>();
        for (Entity e : team) if (e.isDead()) out.add(e);
        return out;
    }

    // --- Input helpers ---
    private int readIntRange(int min, int max) {
        while (true) {
            System.out.print("> ");
            String line = sc.nextLine().trim();
            try {
                int v = Integer.parseInt(line);
                if (v < min || v > max) {
                    System.out.println("Enter a number from " + min + " to " + max + ".");
                    continue;
                }
                return v;
            } catch (NumberFormatException ex) {
                System.out.println("Enter a valid number.");
            }
        }
    }
}