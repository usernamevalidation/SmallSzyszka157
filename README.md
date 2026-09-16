   # SmallSzyszka157

   A Paper/Spigot plugin that changes a player's size based on the helmet they're wearing. Wearing a helmet with a configured display name applies a scale to that player. Take damage, and the size is temporarily disabled for a configurable cooldown.

   > **Inspired by a player request.** Someone on a server I play on asked for a way to change player size with helmets, and I built it.

   ---

   ## Table of contents

   - [Features](#features)
   - [Requirements](#requirements)
   - [Installation](#installation)
   - [Commands](#commands)
   - [Permissions](#permissions)
   - [Configuration](#configuration)
   - [Messages](#messages)
   - [The debug command](#the-debug-command)
   - [How it works](#how-it-works)
   - [Building from source](#building-from-source)
   - [Credits](#credits)

   ---

   ## Features

   - **Named-helmet scaling** — a helmet whose display name matches a key in `config.yml` applies the configured scale to the wearer.
   - **Colored-helmet scaling** — optional support for matching leather armor by RGB color (commented out by default).
   - **Event-driven updates** — size is reapplied on join, inventory change, armor slot click, item-held change, and offhand swap. No periodic polling, no lag.
   - **PvP cooldown** — when a player with a tracked helmet takes damage, their size resets to normal for a configurable duration, then reapplies.
   - **Per-trigger durations** — each damage type has its own cooldown length. PvP can be 15s, PvE can be 5s, natural damage can be 30s — you choose.
   - **Granular triggers** — you decide which damage types count, separately for the victim and the attacker:
     - **Victim** — `player` (PvP), `entity` (mobs and mob projectiles), `natural` (fall, lava, fire, drowning, etc.)
     - **Attacker** — `attacker-pvp` (hitting another player), `attacker-mob` (hitting a mob)
   - **No-downgrade cooldown rule** — a shorter cooldown will not overwrite a longer one already running. A longer or equal cooldown refreshes the timer.
   - **Projectile unwrapping** — arrows, tridents, and other projectiles are attributed to their shooter, so a skeleton's arrow counts as "entity" damage, not "natural."
   - **Fully editable messages** — every player-facing string and every console line lives in `messages.yml`. Edit and run `/hsize reload` — no rebuild.
   - **Multi-line messages** — any message can be a single string or a list of strings. Lists are sent line by line.
   - **Per-helmet list expansion** — inside `hsize.list`, lines containing `%name%` or `%scale%` are emitted once per configured helmet. Static lines are sent once.
   - **Permission-aware tab completion** — `/hsize ` suggests only subcommands the sender has permission to use.
   - **Debug command** — `/hsize debug` dumps a full diagnostic to `plugins/SmallSzyszka157/debug.log`. Optionally echoes to console if `debug-mode: true`.
   - **Attribute auto-detection** — uses `GENERIC_SCALE` on 1.20.5+, falls back to reflection if the server exposes it under a different name.
   - **Zero external dependencies** — single class, no libraries, no NMS.

   ---

   ## Requirements

   | Requirement | Version |
   |---|---|
   | Server | Paper or Spigot 1.20.5+ (Purpur, etc. all work) |
   | Java | 17 or newer |
   | Player client | 1.20.5+ (scale attribute is server-side, so any client that can join the server works) |

   The plugin targets `api-version: '1.21'` in `plugin.yml`.

   ---

   ## Installation

   1. Download or build `SmallSzyszka157.jar`.
   2. Drop it into your server's `plugins/` folder.
   3. Start the server once. It generates:
      - `plugins/SmallSzyszka157/config.yml`
      - `plugins/SmallSzyszka157/messages.yml`
   4. Edit both files as needed.
   5. Run `/hsize reload` or restart the server.

   To remove: stop the server, delete the jar and the `plugins/SmallSzyszka157/` folder, start the server.

   ---

   ## Commands

   All commands are under the `/hsize` root. Tab completion works on every subcommand and filters by permission.

   | Command | Description | Permission |
   |---|---|---|
   | `/hsize` | Show the help message | `sizeplugin.use` |
   | `/hsize help` | Same as `/hsize` | `sizeplugin.use` |
   | `/hsize reload` | Reload `config.yml` and `messages.yml` | `sizeplugin.reload` |
   | `/hsize status` | Show the current config state | `sizeplugin.status` |
   | `/hsize list` | List every configured helmet and its scale | `sizeplugin.list` (only if `list-requires-permission: true`) |
   | `/hsize debug` | Write a full diagnostic to `debug.log` | `sizeplugin.debug` |

   ---

   ## Permissions

   | Permission | Default | Description |
   |---|---|---|
   | `sizeplugin.use` | `true` | Allows the `/hsize` command itself |
   | `sizeplugin.reload` | `op` | Allows `/hsize reload` |
   | `sizeplugin.status` | `op` | Allows `/hsize status` |
   | `sizeplugin.list` | `true` | Allows `/hsize list` (only enforced when `list-requires-permission: true`) |
   | `sizeplugin.debug` | `op` | Allows `/hsize debug` |

   ---

   ## Configuration

   File: `plugins/SmallSzyszka157/config.yml`

   ```yaml
   # SmallSzyszka157 Configuration
   # Debug mode: Set to true to see detailed logs in console
   debug-mode: false

   # ============================================
   # PVP COOLDOWN FEATURE
   # Master switch. When false, no damage type triggers
   # a size cooldown, regardless of the settings below.
   # ============================================
   pvp-cooldown-enabled: true

   # ============================================
   # DAMAGE TRIGGERS - Choose what resets size
   # Each trigger is true/false
   # ============================================
   damage-triggers:
     # ---- VICTIM (the player who GETS hit) ----
     # Player damage (PvP) - another player hits you
     player: true
     # Mob/Entity damage (PvE) - a mob or mob projectile hits you
     entity: false
     # Natural damage (lava, fire, fall, drowning, etc.)
     natural: false

     # ---- ATTACKER (the player who DEALS the hit) ----
     # Attacker gets a cooldown when they hit another PLAYER
     attacker-pvp: false
     # Attacker gets a cooldown when they hit a MOB
     attacker-mob: false

   # ============================================
   # COOLDOWN SECONDS
   # How long each trigger disables the player's size.
   # Only used when the matching trigger above is true.
   # ============================================
   cooldown-seconds:
     player: 15
     entity: 15
     natural: 15
     attacker-pvp: 15
     attacker-mob: 15

   # ============================================
   # LIST COMMAND PERMISSION
   # ============================================
   list-requires-permission: false

   # ============================================
   # NAMED HELMETS
   # ============================================
   helmets:
     "szyszka157": 0.7
     "tiny": 0.5
     "giant": 1.5
     "mini": 0.5
     "normal": 1.0

   # ============================================
   # COLORED HELMETS
   # ============================================
   colors:
     # "255,0,0": 0.7

   # Scale attribute - LEAVE BLANK FOR AUTO-DETECTION
   scale-attribute: ""
   ```

   ### Options reference

   | Key | Type | Default | Description |
   |---|---|---|---|
   | `debug-mode` | boolean | `false` | When true, `/hsize debug` also prints its output to console and to the sender |
   | `pvp-cooldown-enabled` | boolean | `true` | Master switch for the whole cooldown feature |
   | `damage-triggers.player` | boolean | `true` | Cooldown starts when a player hits you |
   | `damage-triggers.entity` | boolean | `false` | Cooldown starts when a mob or mob projectile hits you |
   | `damage-triggers.natural` | boolean | `false` | Cooldown starts on fall, lava, fire, drowning, etc. |
   | `damage-triggers.attacker-pvp` | boolean | `false` | Cooldown starts on **you** when you hit another player |
   | `damage-triggers.attacker-mob` | boolean | `false` | Cooldown starts on **you** when you hit a mob |
   | `cooldown-seconds.player` | integer | `15` | Duration of the PvP victim cooldown |
   | `cooldown-seconds.entity` | integer | `15` | Duration of the PvE victim cooldown |
   | `cooldown-seconds.natural` | integer | `15` | Duration of the natural-damage cooldown |
   | `cooldown-seconds.attacker-pvp` | integer | `15` | Duration of the attacker PvP cooldown |
   | `cooldown-seconds.attacker-mob` | integer | `15` | Duration of the attacker mob cooldown |
   | `list-requires-permission` | boolean | `false` | If true, `/hsize list` requires `sizeplugin.list` |
   | `helmets.<name>` | string→double | — | Helmet display name → scale |
   | `colors.<r,g,b>` | string→double | — | Leather color → scale (commented out by default) |
   | `scale-attribute` | string | `""` | Leave blank to auto-detect. Only set if your server exposes the attribute under a different name |

   ### Scale values

   - `1.0` = normal size
   - `0.5` = half size
   - `1.5` = 1.5× size
   - Valid range is roughly `0.0625` to `16.0` (Minecraft's own limits)

   Helmet matching is **exact**, **case-sensitive**, and **includes color codes** if the helmet's display name has any. A plain `"szyszka157"` key only matches a helmet whose display name is literally `szyszka157` with no formatting. If you use `&a`-style colors when naming the helmet in-game, the display name will contain the translated `§a` prefix, and the config key must include those characters.

   ---

   ## Messages

   File: `plugins/SmallSzyszka157/messages.yml`

   Every player-facing string is defined there. Two formats are supported:

   ```yaml
   # Single line
   generic:
     no-permission: "&cNo permission!"

   # Multiple lines (sent in order)
   hsize:
     help:
       - "&6=== SmallSzyszka157 Commands ==="
       - "&e/hsize reload &7- Reload config"
       - "&e/hsize status &7- Show status"
       - "&e/hsize list &7- List helmets"
   ```

   ### Important: quote `yes` and `no`

   YAML 1.1 treats unquoted `yes`, `no`, `on`, `off`, `true`, and `false` as **booleans**, not strings. Always quote them:

   ```yaml
   values:
     enabled: "&cENABLED"
     disabled: "&aDISABLED"
     "yes": "&aYES"
     "no": "&cNO"
   ```

   If you forget the quotes, the parser silently renames the keys to `values.true` / `values.false` and the plugin reports them as missing.

   ### Placeholders

   | Placeholder | Replaced with |
   |---|---|
   | `%prefix%` | The value of the top-level `prefix` key |
   | `%status%` | `values.enabled` or `values.disabled` |
   | `%seconds%` | The cooldown duration in seconds (context-dependent) |
   | `%count%` | Number of configured helmets |
   | `%name%` | A helmet name (inside `hsize.list` only) |
   | `%scale%` | A helmet's scale (inside `hsize.list` only) |
   | `%victim-player%` / `%victim-entity%` / `%victim-natural%` | YES/NO for each victim trigger |
   | `%attacker-pvp%` / `%attacker-mob%` | YES/NO for each attacker trigger |
   | `%victim-player-seconds%`, etc. | The per-trigger cooldown duration |

   ### Missing keys

   If a key is missing, the plugin logs a one-time warning to console and sends nothing to the player. The four `values.*` keys have built-in fallbacks so status output never appears blank. To reset the warnings list, run `/hsize reload`.

   ---

   ## The debug command

   `/hsize debug` writes a full diagnostic report to `plugins/SmallSzyszka157/debug.log`. It includes:

   - Absolute paths to `config.yml`, `messages.yml`, and `debug.log`, plus whether each file exists.
   - Raw YAML read from disk — checks whether specific keys are actually present after parsing. This is what you want if you ever see "Missing messages.yml key" warnings.
   - The plugin's in-memory state — every trigger boolean, every cooldown duration, the loaded helmets, and the loaded colors.
   - A probe of every message key the plugin uses, showing the raw value or `NULL`.
   - Runtime state — online players, players currently in cooldown, and the list of keys that have triggered missing-key warnings.

   If `debug-mode: true` in `config.yml`, the same report is also printed to console and to whoever ran the command. If `false`, only the file is written.

   ---

   ## How it works

   - On every relevant event (join, inventory change, armor slot click, item swap, item-held change), the plugin reads the player's helmet display name and looks it up in the `helmets` map.
   - If the name matches, the configured scale is applied via the `GENERIC_SCALE` attribute.
   - If the player is in a cooldown, the scale is forced to `1.0` and reapplied after the cooldown expires.
   - Damage handling is entirely event-driven. There is no periodic task.

   When a damage event fires:

   1. The plugin resolves the damager, unwrapping projectiles to their shooter.
   2. If the victim is a player with a tracked helmet, the trigger is chosen from `player` / `entity` / `natural` and `startCooldown` is called.
   3. If the damager is a player with a tracked helmet, the trigger is chosen from `attacker-pvp` / `attacker-mob` and `startCooldown` is called on the attacker.
   4. `startCooldown` looks up the duration from `cooldown-seconds.<trigger>`. If the player is already in cooldown and the new duration is shorter, the hit is ignored. Otherwise the cooldown end time is updated.

   ---

   ## Building from source

   ### Requirements

   - JDK 17 to 23. **Do not build with JDK 24+** — Gradle 8.x will refuse to run on newer JDKs. The project pins Gradle 9.1.0 in the wrapper, which supports JDK 17–25, but JDK 17 or 21 is the safest choice.
   - Git.

   ### Steps

   1. Clone the repo:
      ```
      git clone https://github.com/YOURNAME/SmallSzyszka157.git
      cd SmallSzyszka157
      ```
   2. Create `gradle.properties` in the project root pointing at your local JDK:
      ```
      org.gradle.java.home=C:/path/to/your/jdk-21
      ```
      This file is in `.gitignore` and must be created per machine.
   3. Build:
      ```
      ./gradlew clean build          # Linux / macOS
      .\gradlew.bat clean build      # Windows
      ```
   4. Output:
      ```
      build/libs/SmallSzyszka157.jar
      ```

   ### Project structure

   ```
   SmallSzyszka157/
   ├── build.gradle
   ├── settings.gradle
   ├── gradlew, gradlew.bat
   ├── gradle/wrapper/
   ├── README.md
   └── src/main/
       ├── java/ca/small_szyszka157/sizeplugin/SmallSzyszka157.java
       └── resources/
           ├── config.yml
           ├── messages.yml
           └── plugin.yml
   ```

   The plugin is a single class. There are no sub-packages, no libraries, and no NMS.

   ---

   ## Credits

   Idea from a player request. Built by **szyszka157**.

   ---

   ## License

   No license specified yet. Add one if you plan to distribute or accept contributions.
