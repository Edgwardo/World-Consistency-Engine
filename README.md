World-Consistency-Engine

A Java project made of two connected systems:

Turn-Based Battle Engine — a playable console RPG battle simulator.
World Consistency Engine — a rule-based validator that checks whether a battle state is logically valid.

The battle engine runs the game.
The consistency engine analyzes snapshots of the game world and explains contradictions, impossible states, or illegal transitions.

Overview

This repository explores a rules-driven combat system where battle logic and world validation are separated into different layers.

The battle engine handles turn order, combat actions, status effects, buffs, affinities, field effects, targeting, AI turns, and win/loss conditions. It is playable from the console through Main.java, which launches Game.java.

The World Consistency Engine does not run battles. Instead, it takes a WorldState snapshot and checks it against a set of explicit rules, returning whether the state is valid and listing any violations. It is designed to catch contradictions such as illegal status combinations, invalid revive usage, bad field durations, impossible buff stages, and incorrect transition outcomes.

Repository Structure
Turn-Based Battle Engine
Main.java
Game.java
Entity.java
Move.java
World Consistency Engine
WorldState.java
Rule.java
ConsistencyEngine.java
TestRunner.java
Turn-Based Battle Engine

The battle engine is a console-based, player-oriented combat system. The program starts in Main.java, creates a Game object, and runs a full battle loop with retries, leader selection, and rematches.

Core features
Cycle-based initiative recalculated after all living entities act, using effective SPD and deterministic tie-breaking.
Player-controlled turns and simple enemy AI.
Leader system where the player loses if their chosen leader dies, and leaders cannot be revived.
Field system with RAIN, FIRE, and BLIZZARD, each affecting battle conditions differently. Rain refreshes WET, Fire refreshes BURNT, and Blizzard has a 35% freeze chance.
Status system including BURNT, WET, FROZEN, PARALYZED, and POISONED.
Buff/debuff stage system with values capped from -5 to +5, with timed duration decay.
Affinity system with WEAK, RESIST, IMMUNE, and NEUTRAL, affecting both damage and status application.
HP-cost and MP-cost moves, including actions that can KO the user if the HP cost is too high.
Guard mechanic that halves the next incoming hit and blocks move-applied status effects.
Readable combat pacing with built-in delays for turn flow and action results.
File roles
Main.java

Entry point for the playable game. It creates a scanner, prints the title, and starts the battle engine.

Move.java

Defines the combat move model:

affinity types
target modes
cost types
effect types
stat and status enums
move factory methods
centralized move library

The move library includes offensive skills, status-inflicting skills, buffs, debuffs, healing, revive, and multi-target attacks such as Tsunami and Sweep.

Entity.java

Represents a battle unit. It stores:

identity and team alignment
HP/MP
base stats
affinities
skills
active statuses
buff stages and durations
leader restrictions
guard state

It also contains core behavior such as status application, buff application, cost spending, healing, effective stat calculation, and end-of-turn ticking for burn and poison damage.

Game.java

Implements the playable battle loop. It handles:

battle setup
player party and enemy generation
leader choice
turn order
field generation and persistence
target selection
enemy AI
move resolution
damage calculation
win/loss conditions
console UI flow

Damage is based on attacking stat, defender END, affinity multiplier, crit chance, and extra status-element interactions such as WET + ELECTRIC and FROZEN + FIRE, both of which increase damage by 1.5x.

World Consistency Engine

The World Consistency Engine is a separate validation layer. It checks whether a world snapshot is logically possible according to the rules of the battle system. Instead of simulating gameplay, it inspects states and transitions and reports violations by rule name.

Core features
Standalone WorldState model that does not depend on the live game engine.
Explicit rule objects with categories like STATUS, AFFINITY, FIELD, and CLASS.
Validation results grouped by rule with human-readable violation messages.
Support for both snapshot rules and transition-sensitive checks using before/after HP maps.
Hand-authored test worlds covering valid, invalid, and subtle edge cases.
File roles
WorldState.java

Defines a complete battle snapshot for validation. It includes:

mirrored enums for affinities, statuses, stats, fields, phases, and costs
EntitySnapshot
MoveSnapshot
active entity/action/target metadata
optional transition support such as beforeTurnHp, beforeActionHp, and baseDamageDealt

This lets the consistency engine evaluate both static impossibilities and some action-based legality checks.

Rule.java

Defines a rule as data plus a checker function. The rule set includes checks for:

mutually exclusive statuses
frozen/dead actors trying to act
immunity blocking status infliction
combo damage rules like WET + ELECTRIC
field duration limits
field-required statuses
HP-cost and MP-cost move legality
revive restrictions
resource ceiling checks
buff stage limits

The file distinguishes between rules that can be validated from a single snapshot and rules that need before/after transition data.

ConsistencyEngine.java

Loads all rules, validates a WorldState, and returns a ValidationResult. It can validate a single world or a list of worlds and prints a formatted summary showing which worlds passed and which failed.

TestRunner.java

Builds handcrafted test worlds and runs them through the consistency engine. The suite includes:

valid worlds
obviously invalid worlds
subtly invalid worlds
transition-valid poison/combo cases
transition-invalid poison/combo cases

The main method runs ten worlds total and prints a full validation report.

Design Idea

This project separates simulation from reasoning.

The battle engine answers: “What happens when players and enemies act?”
The consistency engine answers: “Is this world state actually legal under the rules of the system?”

That separation makes the project useful not just as a game prototype, but also as a logic and systems-design project focused on constraints, contradiction detection, and formal rule modeling.

Example Battle Engine Mechanics
Player party includes classes such as Warden, Priest, Raiju, Swamp, and Hero.
Enemy parties are randomly generated from templates like Brute, Hexer, Stormling, and Frostbite.
Some moves apply statuses, some buff allies, some debuff enemies, some heal, and some revive.
Burn and poison tick only at the end of the acting entity’s turn.
Example Consistency Rules
WET and BURNT cannot coexist.
FROZEN and BURNT cannot coexist.
Dead entities cannot act.
Revive cannot target an alive unit.
Fire, Rain, and Blizzard have duration limits.
Rain and Fire must apply their deterministic field statuses to all non-immune living entities.
How to Run
Run the battle engine

Compile the game files and run Main:

javac Main.java Game.java Entity.java Move.java
java Main
Run the consistency engine

Compile the validator files and run TestRunner:

javac WorldState.java Rule.java ConsistencyEngine.java TestRunner.java
java TestRunner
Why this project is interesting

This repo is not just a turn-based game. It is also a small experiment in formal reasoning for games.

It shows how a playable rules-driven combat engine can be paired with a validation engine that checks whether states and transitions obey the laws of the system. That makes it useful for debugging, testing, edge-case discovery, and thinking about game systems more rigorously.
