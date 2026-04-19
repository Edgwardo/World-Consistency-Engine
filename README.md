# World Consistency Engine

A Java project with two connected systems:

- **Turn-Based Battle Engine** — a playable console RPG battle simulator.
- **World Consistency Engine** — a rule-based validator that checks whether a battle state is logically legal under the rules of the system.

The battle engine runs the game. The consistency engine takes a snapshot of the world and determines whether that snapshot is possible — and if not, exactly which rules were broken.

---

## Why this project exists

This started as a self-guided reasoning project. The goal was to build a pure validation engine: given a world snapshot and a rule set, determine whether the world is logically possible. No gameplay, no combat simulation — just the reasoning layer.

To make that useful, I also built the game world it is reasoning about. The battle engine provides real states to validate. The consistency engine is where the project's reasoning layer lives.

The project is not mainly about shipping a game. It's about separating simulation from reasoning, and building a small system where the laws of the world are named, formalized, and machine-checkable.

---

## State rules vs. transition rules

The central design decision in this repo is the split between two kinds of rules:

**State rules** evaluate a single snapshot.
They answer: *"Can this configuration exist at all?"*
Examples: WET + BURNT cannot coexist. A dead entity cannot be marked as the active turn-taker. Field duration cannot exceed its cap.

**Transition rules** evaluate a change from one state to another.
They answer: *"Given what happened, is the new state legal?"*
Examples: poison should deduct 12.5% of max HP at end of turn; WET + ELECTRIC interactions should produce the correct 1.5x damage multiplier when the transition is resolved.

Transition rules need more than a single snapshot — they need *before-state + action + after-state*. `WorldState.java` supports this with optional `beforeTurnHp`, `beforeActionHp`, `expectedDamageMultiplier`, and `baseDamageDealt` fields. When those fields are empty, transition rules are skipped. When populated, they're validated.

The distinction matters because mixing them without labeling leads to blurred reasoning. State rules check possibility; transition rules check legality of change.

---

## What's in the repo

### Turn-Based Battle Engine (`TurnBasedEngine/`)

A fully playable console battle game. Run `Main.java` to play.

- `Main.java` — entry point
- `Move.java` — move model: affinity types, target modes, costs, effects, move library
- `Entity.java` — battle unit with HP/MP, stats, affinities, statuses, buffs, leader state, guard
- `Game.java` — the battle loop: setup, party/enemy generation, leader choice, turn order, field generation, target selection, enemy AI, move resolution, damage calculation, win/loss

Features:

- cycle-based initiative
- field effects (FIRE / RAIN / BLIZZARD)
- 5 statuses (BURNT / WET / FROZEN / PARALYZED / POISONED)
- buff/debuff stages capped at `[-5, +5]`
- affinity system (WEAK / RESIST / IMMUNE / NEUTRAL)
- HP-cost and MP-cost moves
- guard mechanic
- status + affinity interaction multipliers (WET+ELECTRIC and FROZEN+FIRE both 1.5x)

### World Consistency Engine (`WorldConsistencyEngine/`)

A standalone validator for checking whether a battle-world snapshot is logically legal. Run `TestRunner.java` to execute all 10 test worlds and see the report.

- `WorldState.java` — snapshot data structure. Self-contained; does not reference the battle engine. Mirrored enums for affinities, statuses, stats, fields, phases, costs. Supports both state and transition checks via optional before/after fields.
- `Rule.java` — 15 implemented rules across 6 categories:
  - **STATUS** (5): WET/BURNT mutual exclusion, FROZEN/BURNT mutual exclusion, poison tick correctness (transition), FROZEN entity cannot act, dead entity cannot act
  - **AFFINITY** (2): IMMUNE blocks status infliction, status+affinity combo must apply 1.5x multiplier (transition)
  - **FIELD** (3): only one active field, duration caps, active field status must apply to all non-immune entities
  - **CLASS** (3): HP-cost move must deduct HP, MP-cost move requires sufficient MP, revive only valid on dead targets
  - **RESOURCE** (1): HP/MP cannot exceed maximums
  - **BUFF** (1): buff stages must be in `[-5, +5]`
- `ConsistencyEngine.java` — runs all rules against a WorldState and returns a ValidationResult grouped by rule with human-readable violation messages.
- `TestRunner.java` — 10 hand-crafted test worlds:
  - **Valid** (W1, W2): well-formed states that should pass all rules
  - **Obviously invalid** (W3, W4): multiple violations designed to be easy to detect
  - **Subtly invalid** (W5, W6): single-rule violations that require careful reasoning
  - **Transition-valid** (W7, W9): correct poison tick and correct 1.5x combo damage
  - **Transition-invalid** (W8, W10): skipped poison damage, missing 1.5x multiplier

---

## How to run

Clone the repo, then:

### Run the battle engine
