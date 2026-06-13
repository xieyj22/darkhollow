package com.example.data

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class PlayerState(
    var name: String = "Adventurer",
    var raceIndex: Int = 0,
    var classIndex: Int = 0,
    var raceName: String = "",
    var clsName: String = "",
    var hp: Int = 10,
    var maxHp: Int = 10,
    var mp: Int = 10,
    var maxMp: Int = 10,
    var level: Int = 1,
    var exp: Int = 0,
    var expNext: Int = 20,
    var gold: Int = 0,
    var turns: Int = 0,
    var streak: Int = 0,
    var bestStreak: Int = 0,
    var baseAtk: Int = 5,
    var baseDef: Int = 2,
    var atk: Int = 5,
    var def: Int = 2,
    var baseMaxHp: Int = 10,
    var currentX: Int = 0,
    var currentY: Int = 0,
    var deepestFloor: Int = 1,
    var critChance: Float = 0.05f,
    var spellPower: Float = 1.0f,
    var dodgeChance: Float = 0.05f,
    var poisonTurns: Int = 0,
    var poisonDmg: Int = 0,
    var hunger: Int = 100,
    var maxHunger: Int = 100,
    var warded: Boolean = false,
    var freeTurn: Boolean = false,
    var skillCd: var_skill_cd_class = 0,
    var achievements: List<String> = emptyList(),
    var eqWeapon: ItemState? = null,
    var eqArmor: ItemState? = null,
    var eqAccessory: ItemState? = null,
    var inv: List<ItemState> = emptyList(),
    var quickSlots: List<Int> = emptyList(), // Indexes in inv (-1 for null)
    var buffs: List<BuffState> = emptyList(),
    var kills: Int = 0
)

@JsonClass(generateAdapter = true)
data class MetaProgression(
    val lastLoginClaim: Long = 0,
    val loginStreak: Int = 0, // 0 to 6
    val lastQuestReset: Long = 0,
    val emberShards: Int = 0, // Meta currency used for upgrades
    val goldReserves: Int = 0, // Accumulative permanent gold

    // Permanent upgrades levels (max 5 each)
    val hpLvl: Int = 0,      // +4 HP per lvl
    val mpLvl: Int = 0,      // +4 MP per lvl
    val atkLvl: Int = 0,     // +1 ATK per lvl
    val defLvl: Int = 0,     // +1 DEF per lvl
    val dodgeLvl: Int = 0,   // +2% Dodge per lvl

    // Skill unlocks
    val perkLifesteal: Boolean = false,  // HP back on kills
    val perkSpellboost: Boolean = false, // Spellpower +15%
    val perkShieldEmber: Boolean = false, // Start floor with shield

    // 3 Daily Quests variables
    val questSlayerProgress: Int = 0,
    val questSlayerClaimed: Boolean = false,
    val questExplorerProgress: Int = 1,
    val questExplorerClaimed: Boolean = false,
    val questGoldProgress: Int = 0,
    val questGoldClaimed: Boolean = false
)

typealias var_skill_cd_class = Int

@JsonClass(generateAdapter = true)
data class BuffState(
    val displayName: String,
    val type: String, // "str_buff", "def_buff", "shield", "torch", "web", "antidote"
    val value: Int,
    val turns: Int
)

@JsonClass(generateAdapter = true)
data class GameEvent(
    val type: String,
    val title: String,
    val desc: String,
    val choice1: String,
    val choice2: String
)

@JsonClass(generateAdapter = true)
data class ItemState(
    val type: String, // "weapon", "armor", "accessory", "potion", "scroll", "consumable", "food", "gold"
    val name: String,
    val description: String,
    val ch: String,
    val color: String,
    val rarity: Int = 0, // 0 to 4
    val value: Int = 0, // generic value (armor def, weapon atk, gold amount, potion power, etc.)
    val attrAtk: Int = 0,
    val attrDef: Int = 0,
    val attrHp: Int = 0,
    val ef: String = "",
    val valPower: Int = 0,
    val dur: Int = 0,
    var x: Int = 0,
    var y: Int = 0,
    val id: Boolean = true
)

@JsonClass(generateAdapter = true)
data class EnemyState(
    val name: String,
    val ch: String,
    val color: String,
    var hp: Int,
    var maxHp: Int,
    val atk: Int,
    val def: Int,
    val exp: Int,
    val goldDrop: Int,
    val ai: String,
    val isBoss: Boolean = false,
    val isElite: Boolean = false,
    var x: Int,
    var y: Int,
    var stunned: Int = 0,
    var feared: Int = 0,
    var isAlly: Boolean = false,
    var skillCd: Int = 0,
    var rageBuff: Int = 0,
    var rageDur: Int = 0
)

@JsonClass(generateAdapter = true)
data class TrapState(
    val name: String,
    val dmg: Int,
    val color: String,
    val ds: String,
    val ef: String = "",
    val dur: Int = 0,
    var x: Int,
    var y: Int,
    var triggered: Boolean = false,
    var hidden: Boolean = true,
    val playerTrap: Boolean = false
)

@JsonClass(generateAdapter = true)
data class SmokeState(
    var x: Int,
    var y: Int,
    var turns: Int,
    val maxTurns: Int
)

@JsonClass(generateAdapter = true)
data class DungeonMapState(
    val floor: Int,
    val width: Int,
    val height: Int,
    val grid: List<List<Int>>, // MW x MH grid
    val exploredGrid: List<List<Boolean>>, // 1 if explored
    val visibleGrid: List<List<Boolean>> = emptyList(), // FoV visible
    val stairX: Int,
    val stairY: Int
)

@JsonClass(generateAdapter = true)
data class FullGameState(
    val player: PlayerState,
    val currentFloor: Int,
    val map: DungeonMapState,
    val enemies: List<EnemyState>,
    val items: List<ItemState>,
    val traps: List<TrapState>,
    val msgs: List<LogMessage>,
    val smokeEffects: List<SmokeState>,
    val gameOver: Boolean = false,
    val won: Boolean = false
)

@JsonClass(generateAdapter = true)
data class LogMessage(
    val text: String,
    val type: String // "mi", "mc", "mp", "ml", "mst", "md", "mt", "mh", "me", "mach", "msk" or empty
)
