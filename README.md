# ⚔ NationsSMP Plugin

The most epic Minecraft SMP plugin ever built. One `.jar` file. Everything included.

---

## 📦 What's Inside

| Feature | Status |
|---|---|
| 30 NPC bots per player with 12 roles | ✅ |
| Nation name, title, motto, animal, block | ✅ |
| Nation animal companion (fights with you) | ✅ |
| Oath / alliance system | ✅ |
| Betrayal detection + Oathbreaker punishment | ✅ |
| Martyr graves (indestructible) | ✅ |
| Trophy animal heads (wall-mounted) | ✅ |
| Land claiming on kill | ✅ |
| Bot inheritance (`/givebots`) | ✅ |
| OATH KEEPER sword (Overworld) | ✅ |
| OATH BREAKER mace (Nether) | ✅ |
| Full Netherite Kit (split worlds) | ✅ |
| Book of Command (sky) | ✅ |
| Obsidian Sword (Ancient City) | ✅ |
| GLITCH NPC (50hr countdown) | ✅ |
| Iron Throne (The End castle) | ✅ |
| Ender Dragon allegiance system | ✅ |

---

## 🛠 How to Build the JAR

### Option A — GitHub Actions (easiest, no install needed)

1. Push this folder to a GitHub repo
2. Go to **Actions** tab → **Build NationsSMP Plugin** → **Run workflow**
3. When it finishes, download `NationsSMP-plugin` from the **Artifacts** section
4. Inside that zip is `NationsSMP.jar` — that's your plugin!

### Option B — Build locally

Requirements: Java 17+, Maven 3.8+

```bash
cd NationsSMP
mvn clean package
# Output: target/NationsSMP.jar
```

---

## 🚀 Installation on Aternos

### Step 1 — Required dependency
Download **Citizens2** from: https://ci.citizensnpcs.co/job/Citizens2/
Upload it to your Aternos server under **Plugins**.

### Step 2 — Upload NationsSMP
Upload `NationsSMP.jar` to your Aternos **Plugins** folder.

### Step 3 — Server type
Make sure your Aternos server is set to **Paper** (not Vanilla or Spigot).
Recommended version: **1.20.4**

### Step 4 — Start the server
Boot it up. Both plugins should load. Check console for:
```
[NationsSMP] NationsSMP enabled — the nations rise!
```

---

## 🎮 Player Guide

### First Login
When you join for the first time, you'll be walked through 5 steps in chat:
1. **Nation Name** — no spaces, 2-24 characters
2. **Personal Title** — e.g. "The Unconquered"
3. **Nation Motto** — GOT-style words, e.g. "From Ash We Rise"
4. **Nation Animal** — click in the GUI (first come, first served)
5. **Building Block** — click in the GUI (first come, first served, get 64 of it)

Once done, 30 bots spawn around you and your animal companion arrives.

### Army Commands
```
/army list                  — list your bots + roles
/army assign <#> <role>     — assign a role to a bot
/army follow                — bots follow you
/army stay                  — bots hold position
/army attack                — bots attack all nearby enemies
/army defend                — bots protect you
/army mine                  — miner bots go dig
/army status                — army overview
/army spam                  — bots shout your title and motto
```

**Bot Roles:** soldier, archer, mage, miner, farmer, lumberjack, guard, assassin, alchemist, scout, trader, builder

### Alliance / Oath System
To form an oath with another player:
```
/I BEAR OATH TO <playerName> <their exact title> OF <their nation name>
```
Example:
```
/I BEAR OATH TO Zaid The Unconquered OF Khalifate
```
**Warning:** Breaking the oath will cost you 15 bots, half your resources, and your title becomes "Oathbreaker" permanently.

**Martyrs:** If you die defending an ally, you become a Martyr. An indestructible grave with a ceremonial crown is built at your death location. Your title gains "Martyr" forever.

### Death & Inheritance
- When you die: all 30 bots die, your animal companion dies, your land goes to your killer.
- Before dying you can type `/givebots <nationName>` to pass everything to a chosen nation instead.

### Legendary Items
| Item | Where | Who |
|---|---|---|
| ⚔ OATH KEEPER | Deep Overworld chest | Non-oathbreakers only |
| 🔨 OATH BREAKER | Deep Nether chest | Oathbreakers only |
| 🛡 Netherite Kit (4 pieces) | Half Overworld, half Nether | Anyone |
| 📖 Book of Command | High sky chest | Anyone — one use kill |
| 🗡 Obsidian Sword | Ancient City chest | Anyone — kills Dragon, Glitch hunts you |

### Iron Throne
- Located in a castle in **The End** (auto-built when first player enters)
- Right-click the throne → get Dragon coordinates + summon it with `/summondragon`
- Throne vanishes and reappears somewhere new in the castle each time someone sits
- Dragon fights for current throne owner

### Glitch & Obsidian Sword
- Obsidian Sword is hidden in an **Ancient City** chest
- The moment someone picks it up → **50-hour countdown begins**, GLITCH spawns
- GLITCH follows whoever has the sword. At Hour 50 → holder is killed, sword vanishes
- The sword can **only hurt the Dragon** (one shots it). It cannot damage players or mobs.
- Pass it to an enemy to let Glitch hunt them instead

---

## ⚙ Admin Commands
```
/nsadmin spawnitems      — re-trigger legendary item spawning
/nsadmin resetglitch     — reset glitch/obsidian sword state
/nsadmin resetthrone     — reset throne/castle state
/nsadmin listlands       — list all nation land holdings
/nsadmin givesword       — give yourself the Obsidian Sword (testing)
/nsadmin giveoathkeeper  — give yourself the Oath Keeper (testing)
```

---

## 📋 Bot Roles Reference
| Role | Health | Damage | Speed | Purpose |
|---|---|---|---|---|
| ⚔ Soldier | 30 | 5 | Normal | Heavy melee |
| 🏹 Archer | 16 | 4 | Fast | Ranged |
| 🧙 Mage | 18 | 6 | Normal | AoE splash |
| ⛏ Miner | 20 | 3 | Normal | Gathers ore |
| 🌾 Farmer | 14 | 2 | Normal | Grows food |
| 🪓 Lumberjack | 20 | 4 | Normal | Chops trees |
| 🛡 Guard | 40 | 4 | Slow | Tank / protector |
| 🗡 Assassin | 14 | 9 | Very fast | High damage |
| 🧪 Alchemist | 16 | 2 | Normal | Heals allies |
| 🐴 Scout | 16 | 3 | Fastest | Explores |
| 💰 Trader | 16 | 2 | Normal | Carries loot |
| 🏗 Builder | 20 | 2 | Normal | Collects materials |

---

## ❓ Troubleshooting

**Bots aren't spawning**
→ Make sure Citizens2 is also installed and loaded.

**Plugin not loading**
→ Check you're on Paper 1.20.4 and Java 17+.

**Commands not working**
→ Check `plugins/NationsSMP/` folder was created. Try `/nsadmin` to verify permissions.

**Glitch not appearing**
→ Citizens2 must be loaded. Check console for Citizens errors.

---

*Built for NationsSMP — the greatest Minecraft SMP concept ever made.*
