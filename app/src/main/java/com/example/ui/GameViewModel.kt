package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.random.Random

class GameViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = GameRepository(application)

    private val _gameState = MutableStateFlow<GameState?>(null)
    val gameState: StateFlow<GameState?> = _gameState.asStateFlow()

    private val _activeEvent = MutableStateFlow<GameEvent?>(null)
    val activeEvent: StateFlow<GameEvent?> = _activeEvent.asStateFlow()

    private val _toastMessage = MutableStateFlow<String?>(null)
    val toastMessage: StateFlow<String?> = _toastMessage.asStateFlow()

    // Screen state: "title", "char_select", "game", "death", "victory"
    private val _screenState = MutableStateFlow("title")
    val screenState: StateFlow<String> = _screenState.asStateFlow()

    private val _hasActiveSave = MutableStateFlow(false)
    val hasActiveSave: StateFlow<Boolean> = _hasActiveSave.asStateFlow()

    private val _metaProgression = MutableStateFlow(MetaProgression())
    val metaProgression: StateFlow<MetaProgression> = _metaProgression.asStateFlow()

    init {
        checkSavedGame()
        loadMetaProgression()
    }

    fun loadMetaProgression() {
        viewModelScope.launch {
            _metaProgression.value = repository.getMetaProgression()
        }
    }

    fun saveMetaProgression(meta: MetaProgression) {
        viewModelScope.launch {
            _metaProgression.value = meta
            repository.saveMetaProgression(meta)
        }
    }

    // --- Login Rewards ---
    fun claimDailyLoginReward() {
        val meta = _metaProgression.value
        val now = System.currentTimeMillis()
        if (!isDailyLoginReady()) {
            showToast("Your daily login reward is not ready yet today!")
            return
        }

        // Cycle next streak
        val nextStreak = if (meta.lastLoginClaim == 0L) 0 else (meta.loginStreak + 1) % 7

        // Cycle rewards: Day 1-7
        val goldReward = when(nextStreak) {
            0 -> 100
            1 -> 200
            2 -> 300
            3 -> 400
            4 -> 500
            5 -> 600
            6 -> 1000
            else -> 100
        }
        val shardsReward = when(nextStreak) {
            0 -> 10
            1 -> 15
            2 -> 20
            3 -> 25
            4 -> 35
            5 -> 50
            6 -> 100
            else -> 10
        }

        val updatedMeta = meta.copy(
            lastLoginClaim = now,
            loginStreak = nextStreak,
            emberShards = meta.emberShards + shardsReward,
            goldReserves = meta.goldReserves + goldReward
        )
        saveMetaProgression(updatedMeta)
        AudioSynth.play("pickup")
        showToast("Claimed Day ${nextStreak + 1} reward: +$goldReward Gold, +$shardsReward Ember Shards!")

        // Inject gold directly to active game if any
        _gameState.value?.let { gs ->
            gs.player.gold += goldReward
            addMsg("🎁 Daily Reward Bonus: Added +$goldReward Gold coins directly to your pouch!", "mp")
            updateGoldAndStatsProgression()
            saveGame()
        }
    }

    fun isDailyLoginReady(): Boolean {
        val meta = _metaProgression.value
        if (meta.lastLoginClaim == 0L) return true
        val elapsed = System.currentTimeMillis() - meta.lastLoginClaim
        return elapsed >= 24 * 60 * 60 * 1000L
    }

    // --- Daily Quests ---
    fun claimQuestReward(questIndex: Int) {
        val meta = _metaProgression.value
        when (questIndex) {
            1 -> {
                if (meta.questSlayerProgress >= 8 && !meta.questSlayerClaimed) {
                    val updated = meta.copy(
                        questSlayerClaimed = true,
                        emberShards = meta.emberShards + 15,
                        goldReserves = meta.goldReserves + 80
                    )
                    saveMetaProgression(updated)
                    grantQuestPayout(80, "Minion Slasher goals met!")
                }
            }
            2 -> {
                if (meta.questExplorerProgress >= 4 && !meta.questExplorerClaimed) {
                    val updated = meta.copy(
                        questExplorerClaimed = true,
                        emberShards = meta.emberShards + 25,
                        goldReserves = meta.goldReserves + 150
                    )
                    saveMetaProgression(updated)
                    grantQuestPayout(150, "Abyss Explorer goals met!")
                }
            }
            3 -> {
                if (meta.questGoldProgress >= 300 && !meta.questGoldClaimed) {
                    val updated = meta.copy(
                        questGoldClaimed = true,
                        emberShards = meta.emberShards + 35,
                        goldReserves = meta.goldReserves + 200
                    )
                    saveMetaProgression(updated)
                    grantQuestPayout(200, "Deep Wealth goals met!")
                }
            }
        }
    }

    private fun grantQuestPayout(gold: Int, reason: String) {
        showToast("Claimed Goal! Obtained +$gold Gold, Shards increased!")
        AudioSynth.play("levelup")
        _gameState.value?.let { gs ->
            gs.player.gold += gold
            addMsg("🎁 Daily Quest [$reason]: Claimed +$gold Gold coins!", "mp")
            updateGoldAndStatsProgression()
            saveGame()
        }
    }

    fun reRollQuests() {
        val meta = _metaProgression.value
        val updated = meta.copy(
            lastQuestReset = System.currentTimeMillis(),
            questSlayerProgress = 0,
            questSlayerClaimed = false,
            questExplorerProgress = _gameState.value?.currentFloor ?: 1,
            questExplorerClaimed = false,
            questGoldProgress = 0,
            questGoldClaimed = false
        )
        saveMetaProgression(updated)
        showToast("Generated a new set of 3 active daily quests!")
    }

    fun simulateDailyReset() {
        val meta = _metaProgression.value
        val dayInMs = 24 * 60 * 60 * 1000L
        val updated = meta.copy(
            lastLoginClaim = meta.lastLoginClaim - dayInMs,
            lastQuestReset = meta.lastQuestReset - dayInMs
        )
        saveMetaProgression(updated)
        showToast("Simulated 24-hour advance! Complete reset ready.")
    }

    fun updateGoldAndStatsProgression() {
        val gs = _gameState.value ?: return
        val currentMeta = _metaProgression.value
        // Max gold held in current run tracks our quest gold progress
        val maxGold = Math.max(currentMeta.questGoldProgress, gs.player.gold)
        val maxFloor = Math.max(currentMeta.questExplorerProgress, gs.currentFloor)
        if (maxGold != currentMeta.questGoldProgress || maxFloor != currentMeta.questExplorerProgress) {
            saveMetaProgression(currentMeta.copy(questGoldProgress = maxGold, questExplorerProgress = maxFloor))
        }
    }

    // --- Meta Upgrades Panel ---
    fun buyPermanentUpgrade(upgradeKey: String) {
        val meta = _metaProgression.value
        when (upgradeKey) {
            "hp" -> {
                val nextLvl = meta.hpLvl + 1
                if (nextLvl > 5) { showToast("HP Upgrade is already fully maximized!"); return }
                val cost = nextLvl * 15
                if (meta.emberShards < cost) { showToast("Missing ${cost - meta.emberShards} Ember Shards!"); return }
                saveMetaProgression(meta.copy(hpLvl = nextLvl, emberShards = meta.emberShards - cost))
                AudioSynth.play("levelup")
                showToast("Purchased: Starting HP level $nextLvl!")
            }
            "mp" -> {
                val nextLvl = meta.mpLvl + 1
                if (nextLvl > 5) { showToast("MP Upgrade is already fully maximized!"); return }
                val cost = nextLvl * 15
                if (meta.emberShards < cost) { showToast("Missing ${cost - meta.emberShards} Ember Shards!"); return }
                saveMetaProgression(meta.copy(mpLvl = nextLvl, emberShards = meta.emberShards - cost))
                AudioSynth.play("levelup")
                showToast("Purchased: Starting Mana level $nextLvl!")
            }
            "atk" -> {
                val nextLvl = meta.atkLvl + 1
                if (nextLvl > 5) { showToast("Attack is already fully maximized!"); return }
                val cost = nextLvl * 25
                if (meta.emberShards < cost) { showToast("Missing ${cost - meta.emberShards} Ember Shards!"); return }
                saveMetaProgression(meta.copy(atkLvl = nextLvl, emberShards = meta.emberShards - cost))
                AudioSynth.play("levelup")
                showToast("Purchased: Starting Attack level $nextLvl!")
            }
            "def" -> {
                val nextLvl = meta.defLvl + 1
                if (nextLvl > 5) { showToast("Defense is already fully maximized!"); return }
                val cost = nextLvl * 25
                if (meta.emberShards < cost) { showToast("Missing ${cost - meta.emberShards} Ember Shards!"); return }
                saveMetaProgression(meta.copy(defLvl = nextLvl, emberShards = meta.emberShards - cost))
                AudioSynth.play("levelup")
                showToast("Purchased: Starting Defense level $nextLvl!")
            }
            "dodge" -> {
                val nextLvl = meta.dodgeLvl + 1
                if (nextLvl > 5) { showToast("Evasion is already fully maximized!"); return }
                val cost = nextLvl * 30
                if (meta.emberShards < cost) { showToast("Missing ${cost - meta.emberShards} Ember Shards!"); return }
                saveMetaProgression(meta.copy(dodgeLvl = nextLvl, emberShards = meta.emberShards - cost))
                AudioSynth.play("levelup")
                showToast("Purchased: Starting Evasion level $nextLvl!")
            }
            "perk_lifesteal" -> {
                if (meta.perkLifesteal) return
                val cost = 45
                if (meta.emberShards < cost) { showToast("Missing ${cost - meta.emberShards} Ember Shards!"); return }
                saveMetaProgression(meta.copy(perkLifesteal = true, emberShards = meta.emberShards - cost))
                AudioSynth.play("pickup")
                showToast("Unlocked: Harvest of Soul passive perk!")
            }
            "perk_spellboost" -> {
                if (meta.perkSpellboost) return
                val cost = 60
                if (meta.emberShards < cost) { showToast("Missing ${cost - meta.emberShards} Ember Shards!"); return }
                saveMetaProgression(meta.copy(perkSpellboost = true, emberShards = meta.emberShards - cost))
                AudioSynth.play("pickup")
                showToast("Unlocked: Spell Weaver passive perk!")
            }
            "perk_shield" -> {
                if (meta.perkShieldEmber) return
                val cost = 80
                if (meta.emberShards < cost) { showToast("Missing ${cost - meta.emberShards} Ember Shards!"); return }
                saveMetaProgression(meta.copy(perkShieldEmber = true, emberShards = meta.emberShards - cost))
                AudioSynth.play("pickup")
                showToast("Unlocked: Ember's Protection starting shield perk!")
            }
        }
    }

    fun checkSavedGame() {
        viewModelScope.launch {
            val saved = repository.getActiveGame()
            _hasActiveSave.value = saved != null
        }
    }

    fun setScreen(screen: String) {
        _screenState.value = screen
    }

    fun showToast(msg: String) {
        _toastMessage.value = msg
    }

    fun clearToast() {
        _toastMessage.value = null
    }

    fun loadSavedGame() {
        viewModelScope.launch {
            val saved = repository.getActiveGame()
            if (saved != null) {
                // Restore game model from full state save
                val gs = rebuildGameState(saved)
                _gameState.value = gs
                AudioSynth.play("pickup")
                _screenState.value = "game"
            } else {
                showToast("No saved game found!")
            }
        }
    }

    fun startNewGame(raceIndex: Int, classIndex: Int, charName: String) {
        viewModelScope.launch {
            repository.deleteGame()
            val initial = createInitialGameState(raceIndex, classIndex, charName)
            _gameState.value = initial
            _screenState.value = "game"
            AudioSynth.play("levelup")
            saveGame()
        }
    }

    fun saveGame() {
        val gs = _gameState.value ?: return
        viewModelScope.launch {
            updateGoldAndStatsProgression()
            repository.saveGame(gs.toFullGameState())
            _hasActiveSave.value = true
        }
    }

    fun deleteSaveAndReset() {
        viewModelScope.launch {
            repository.deleteGame()
            _hasActiveSave.value = false
            _gameState.value = null
            _screenState.value = "title"
        }
    }

    // Move player in direction dx, dy
    fun movePlayer(dx: Int, dy: Int) {
        val gs = _gameState.value ?: return
        if (gs.gameOver) return

        // Entangled check (Spider Queen Web debuff)
        val hasWeb = gs.player.buffs.any { it.type == "web" }
        if (hasWeb && Random.nextFloat() < 0.5f) {
            addMsg("The web holds you! Cannot move!", "mt")
            endTurn()
            return
        }

        val px = gs.player.currentX
        val py = gs.player.currentY
        val nx = px + dx
        val ny = py + dy

        if (nx !in 0 until 70 || ny !in 0 until 45) return
        val tile = gs.map.grid[ny][nx]
        if (tile == DungeonGenerator.TL_WALL || tile == DungeonGenerator.TL_VOID) return

        // Combat check
        val enemy = gs.enemies.find { it.x == nx && it.y == ny && !it.isAlly }
        if (enemy != null) {
            performAttack(gs, enemy)
            endTurn()
            return
        }

        gs.player.currentX = nx
        gs.player.currentY = ny

        // Pickup walkover logic
        val itemsHere = gs.items.filter { it.x == nx && it.y == ny }
        if (itemsHere.isNotEmpty()) {
            val remainItems = gs.items.toMutableList()
            for (item in itemsHere) {
                remainItems.remove(item)
                if (item.type == "gold") {
                    gs.player.gold += item.value
                    addMsg("Picked up ${item.value} gold.", "mp")
                    AudioSynth.play("pickup")
                } else {
                    addItemToInventory(gs, item)
                }
            }
            gs.items = remainItems
        }

        checkTrapsAndTiles(gs)
    }

    fun skipTurn() {
        addMsg("You wait...", "mi")
        endTurn()
    }

    fun pickupFloorItem() {
        val gs = _gameState.value ?: return
        val px = gs.player.currentX
        val py = gs.player.currentY
        val itemsHere = gs.items.filter { it.x == px && it.y == py && it.type != "gold" }
        if (itemsHere.isEmpty()) {
            addMsg("Nothing to pick up here.", "mi")
            return
        }
        val remainItems = gs.items.toMutableList()
        for (item in itemsHere) {
            remainItems.remove(item)
            addItemToInventory(gs, item)
        }
        gs.items = remainItems
        endTurn()
    }

    fun descendStairs() {
        val gs = _gameState.value ?: return
        val px = gs.player.currentX
        val py = gs.player.currentY
        if (gs.map.grid[py][px] != DungeonGenerator.TL_STAIR) {
            addMsg("There are no stairs here.", "mi")
            return
        }

        val nextFloor = gs.currentFloor + 1
        gs.player.deepestFloor = Math.max(gs.player.deepestFloor, nextFloor)
        enterFloor(gs, nextFloor)
        endTurn()
    }

    fun quickHealOrMana() {
        val gs = _gameState.value ?: return
        val idx = gs.player.inv.indexOfFirst { it.type == "potion" && (it.ef == "heal" || it.ef == "mana") }
        if (idx == -1) {
            addMsg("No health/mana potions in inventory!", "mi")
            return
        }
        useInventoryItem(idx)
    }

    fun quickReadScroll() {
        val gs = _gameState.value ?: return
        val idx = gs.player.inv.indexOfFirst { it.type == "scroll" }
        if (idx == -1) {
            addMsg("No scrolls in inventory!", "mi")
            return
        }
        useInventoryItem(idx)
    }

    // Active skill casting
    fun castClassSkill() {
        val gs = _gameState.value ?: return
        if (gs.player.skillCd > 0) {
            addMsg("Skill is on cooldown! (${gs.player.skillCd} turns left)", "mi")
            return
        }
        val cost = getClassSkillCost(gs.player.classIndex)
        if (gs.player.mp < cost) {
            addMsg("Not enough MP! (Need $cost MP)", "mi")
            return
        }

        gs.player.mp -= cost
        gs.player.skillCd = getClassSkillMaxCd(gs.player.classIndex)
        AudioSynth.play("spell")

        when (gs.player.classIndex) {
            0 -> { // Warrior: Shield Bash (Stuns closest enemy in distance 6)
                val target = findNearestEnemyInView(gs, 6.0)
                if (target != null) {
                    val dmg = (gs.player.atk * 1.5f).toInt()
                    target.hp -= dmg
                    target.stunned = 2
                    addMsg("Shield Bash! $dmg damage to ${target.name}, stunned for 2 turns!", "msk")
                    if (target.hp <= 0) {
                        killEnemy(gs, target)
                    }
                } else {
                    addMsg("Shield Bash missed! No target in range.", "mi")
                }
            }
            1 -> { // Rogue: Shadow Strike (deals massive single target damage)
                val target = findNearestEnemyInView(gs, 6.0)
                if (target != null) {
                    val dmg = (gs.player.atk * 2.5f).toInt()
                    target.hp -= dmg
                    addMsg("Shadow Strike! Dealt $dmg piercing damage to ${target.name}!", "msk")
                    if (target.hp <= 0) {
                        killEnemy(gs, target)
                    }
                } else {
                    addMsg("Shadow Strike missed! No target in range.", "mi")
                }
            }
            2 -> { // Mage: Arcane Blast (AoE magic blast to all within 5 tiles)
                val targets = gs.enemies.filter { !it.isAlly && distance(gs.player.currentX, gs.player.currentY, it.x, it.y) <= 5.0 }
                var slainCount = 0
                val dmgMultiplier = gs.player.spellPower
                val baseDmg = gs.player.atk + gs.player.level * 3
                val finalDmg = (baseDmg * dmgMultiplier).toInt()

                for (target in targets) {
                    target.hp -= finalDmg
                    if (target.hp <= 0) slainCount++
                }

                gs.enemies = gs.enemies.filter { it.hp > 0 || it.isAlly }
                for (i in 0 until slainCount) {
                    val expVal = 10 + gs.currentFloor * 2 // approximation for bulk kills
                    gs.player.exp += expVal
                    gs.player.kills++
                }

                addMsg("Arcane Blast! Devastated ${targets.size} enemies for $finalDmg damage!", "msk")
                checkLevelUp(gs)
            }
            3 -> { // Paladin: Holy Light (Heal 40% max HP + clear poison dot)
                val healVal = (gs.player.maxHp * 0.4f).toInt()
                gs.player.hp = Math.min(gs.player.maxHp, gs.player.hp + healVal)
                gs.player.poisonTurns = 0
                gs.player.poisonDmg = 0
                addMsg("Holy Light! Restored $healVal HP. Cleansed all poison from veins!", "mh")
                AudioSynth.play("heal")
            }
        }
        endTurn()
    }

    fun useInventoryItem(index: Int) {
        val gs = _gameState.value ?: return
        if (index !in gs.player.inv.indices) return
        val item = gs.player.inv[index]

        if (item.type == "food") {
            gs.player.inv = gs.player.inv.filterIndexed { idx, _ -> idx != index }
            gs.player.hunger = Math.min(gs.player.maxHunger, gs.player.hunger + 30)
            addMsg("You ate Dried Meat. Hunger restored.", "mh")
            AudioSynth.play("heal")
            endTurn()
            return
        }

        if (item.type == "potion") {
            when (item.ef) {
                "heal" -> {
                    val power = item.value
                    gs.player.hp = Math.min(gs.player.maxHp, gs.player.hp + power)
                    addMsg("Drank ${item.name}! HP restored by $power.", "mh")
                    AudioSynth.play("heal")
                }
                "mana" -> {
                    val power = item.value
                    gs.player.mp = Math.min(gs.player.maxMp, gs.player.mp + power)
                    addMsg("Drank ${item.name}! MP restored by $power.", "mh")
                    AudioSynth.play("heal")
                }
                "str_buff" -> {
                    val duration = item.dur
                    gs.player.buffs = gs.player.buffs + BuffState("Strength", "str_buff", item.valPower, duration)
                    addMsg("Drank ${item.name}! ATK gained +${item.valPower} for $duration turns.", "mi")
                    AudioSynth.play("pickup")
                }
                "def_buff" -> {
                    val duration = item.dur
                    gs.player.buffs = gs.player.buffs + BuffState("Iron Skin", "def_buff", item.valPower, duration)
                    addMsg("Drank ${item.name}! DEF gained +${item.valPower} for $duration turns.", "mi")
                    AudioSynth.play("pickup")
                }
                "restore" -> {
                    gs.player.hp = gs.player.maxHp
                    gs.player.mp = gs.player.maxMp
                    addMsg("Drank ${item.name}! Fully restored HP and MP!", "mh")
                    AudioSynth.play("heal")
                }
                "poison" -> {
                    val power = item.value
                    gs.player.hp -= power
                    addMsg("Accidentally drank Poison! Took $power damage!", "mc")
                    AudioSynth.play("trap")
                    if (gs.player.hp <= 0) {
                        triggerDeath(gs, "Poison Potion")
                        return
                    }
                }
            }
            gs.player.inv = gs.player.inv.filterIndexed { idx, _ -> idx != index }
            syncQuickSlots(gs)
            recalculateStats(gs)
            endTurn()
            return
        }

        if (item.type == "scroll") {
            // spell scrolls cost 3 MP
            if (gs.player.mp < 3) {
                addMsg("Not enough MP! (Need 3 MP to cast scrolls)", "mi")
                return
            }
            gs.player.mp -= 3
            AudioSynth.play("spell")

            when (item.ef) {
                "fireball" -> {
                    val targets = gs.enemies.filter { !it.isAlly && distance(gs.player.currentX, gs.player.currentY, it.x, it.y) <= 4.0 }
                    var kills = 0
                    val power = (item.value * gs.player.spellPower).toInt()
                    for (t in targets) {
                        t.hp -= power
                        if (t.hp <= 0) {
                            kills++
                            killEnemy(t_gs = gs, target = t, isSlainDirectly = false)
                        }
                    }
                    gs.enemies = gs.enemies.filter { it.hp > 0 || it.isAlly }
                    addMsg("Casted Fireball! Blasted ${targets.size} enemies for $power damage. Slain $kills!", "mc")
                    checkLevelUp(gs)
                }
                "lightning" -> {
                    val targets = gs.enemies.filter { !it.isAlly && gs.map.visibleGrid[it.y][it.x] }
                    var kills = 0
                    val power = (item.value * gs.player.spellPower).toInt()
                    for (t in targets) {
                        t.hp -= power
                        if (t.hp <= 0) {
                            kills++
                            killEnemy(t_gs = gs, target = t, isSlainDirectly = false)
                        }
                    }
                    gs.enemies = gs.enemies.filter { it.hp > 0 || it.isAlly }
                    addMsg("Casted Lightning Storm! Zapped ${targets.size} visible enemies for $power damage. Slain $kills!", "mc")
                    checkLevelUp(gs)
                }
                "teleport" -> {
                    val centerRoom = gs.rooms.random()
                    gs.player.currentX = centerRoom.cx
                    gs.player.currentY = centerRoom.cy
                    addMsg("Scroll of Teleportation activated! Relocated safely.", "mi")
                }
                "mapping" -> {
                    gs.map.exploredGrid = List(DungeonGenerator.MH) { List(DungeonGenerator.MW) { true } }
                    addMsg("Magic Scroll mapping reveals the full floor shape!", "mi")
                }
                "shield" -> {
                    val duration = item.dur
                    gs.player.buffs = gs.player.buffs + BuffState("Magic Shield", "shield", item.valPower, duration)
                    addMsg("Shield scroll casted! Gain +${item.valPower} DEF for $duration turns.", "mi")
                }
                "fear" -> {
                    val affected = gs.enemies.filter { !it.isAlly && distance(gs.player.currentX, gs.player.currentY, it.x, it.y) <= 5.0 }
                    for (t in affected) {
                        t.feared = Random.nextInt(5, 11)
                    }
                    addMsg("Scroll of Terror! Frightened ${affected.size} nearby monsters!", "mi")
                }
            }
            gs.player.inv = gs.player.inv.filterIndexed { idx, _ -> idx != index }
            syncQuickSlots(gs)
            recalculateStats(gs)
            endTurn()
            return
        }

        if (item.type == "consumable") {
            AudioSynth.play("spell")
            when (item.ef) {
                "bomb" -> {
                    val targets = gs.enemies.filter { !it.isAlly && distance(gs.player.currentX, gs.player.currentY, it.x, it.y) <= 3.0 }
                    var slain = 0
                    val power = (item.value * gs.player.spellPower).toInt()
                    for (t in targets) {
                        t.hp -= power
                        if (t.hp <= 0) {
                            slain++
                            killEnemy(t_gs = gs, target = t, isSlainDirectly = false)
                        }
                    }
                    gs.enemies = gs.enemies.filter { it.hp > 0 || it.isAlly }
                    addMsg("💣 Chucked Bomb! Direct blast hit ${targets.size} enemies for $power damage. Slain: $slain!", "mc")
                    checkLevelUp(gs)
                }
                "throw_knife" -> {
                    val target = findNearestEnemyInView(gs, 6.0)
                    if (target != null) {
                        val power = (item.value * gs.player.spellPower).toInt()
                        target.hp -= power
                        addMsg("Threw Throwing Knife! Dealt $power damage to ${target.name}!", "mc")
                        AudioSynth.play("hit")
                        if (target.hp <= 0) {
                            killEnemy(gs, target)
                        }
                    } else {
                        addMsg("Threw knife... but there are no targets in sight.", "mi")
                    }
                }
                "torch" -> {
                    val duration = item.dur
                    gs.player.buffs = gs.player.buffs + BuffState("Lighting Torch", "torch", item.valPower, duration)
                    addMsg("🔥 Lit Torch! FOV range expanded +${item.valPower} for $duration turns.", "mi")
                }
                "bear_trap" -> {
                    val trap = TrapState(
                        name = "Bear Trap",
                        dmg = item.value,
                        color = "#a0522d",
                        ds = "The bear trap snaps!",
                        x = gs.player.currentX,
                        y = gs.player.currentY,
                        triggered = false,
                        hidden = false,
                        playerTrap = true
                    )
                    gs.traps = gs.traps + trap
                    addMsg("Placed Bear Trap directly under your feet.", "mi")
                }
                "smoke_bomb" -> {
                    val affected = gs.enemies.filter { !it.isAlly && distance(gs.player.currentX, gs.player.currentY, it.x, it.y) <= 5.0 }
                    for (t in affected) {
                        t.feared = Random.nextInt(5, 11)
                    }
                    // Generate smoke clouds
                    val px = gs.player.currentX
                    val py = gs.player.currentY
                    val smokePoints = mutableListOf<SmokeState>()
                    for (dy in -3..3) {
                        for (dx in -3..3) {
                            if (dx * dx + dy * dy <= 9 && Random.nextFloat() < 0.7f) {
                                smokePoints.add(SmokeState(px + dx, py + dy, 4, 4))
                            }
                        }
                    }
                    gs.smokeEffects = gs.smokeEffects + smokePoints
                    addMsg("💨 Exploded Smoke Bomb! Confused and blinded ${affected.size} enemies!", "mi")
                }
                "ward" -> {
                    gs.player.warded = true
                    addMsg("🛡 Triggered Ward Stone! Next incoming hit will be entirely absorbed.", "mi")
                }
                "haste" -> {
                    gs.player.freeTurn = true
                    addMsg("⚡ Drank Haste Flask! You feel super speed, granting a free extra action!", "mi")
                }
                "antidote" -> {
                    gs.player.poisonTurns = 0
                    gs.player.poisonDmg = 0
                    gs.player.buffs = gs.player.buffs + BuffState("Poison Immunity", "antidote", 0, 15)
                    addMsg("✨ Swallowed Antidote! poison fully removed. Conferred antidote block for 15 turns.", "mi")
                    AudioSynth.play("heal")
                }
            }
            gs.player.inv = gs.player.inv.filterIndexed { idx, _ -> idx != index }
            syncQuickSlots(gs)
            recalculateStats(gs)
            endTurn()
            return
        }

        // Equipment items
        if (item.type == "weapon" || item.type == "armor" || item.type == "accessory") {
            equipItemDirectly(gs, index)
            endTurn()
        }
    }

    fun equipItemDirectly(gs: GameState, index: Int) {
        val item = gs.player.inv[index]
        val slot = item.type

        gs.player.inv = gs.player.inv.filterIndexed { idx, _ -> idx != index }

        when (slot) {
            "weapon" -> {
                val old = gs.player.eqWeapon
                gs.player.eqWeapon = item
                if (old != null) gs.player.inv = gs.player.inv + old
            }
            "armor" -> {
                val old = gs.player.eqArmor
                gs.player.eqArmor = item
                if (old != null) gs.player.inv = gs.player.inv + old
            }
            "accessory" -> {
                val old = gs.player.eqAccessory
                gs.player.eqAccessory = item
                if (old != null) gs.player.inv = gs.player.inv + old
            }
        }

        addMsg("Equipped ${item.name}.", "mi")
        AudioSynth.play("pickup")
        recalculateStats(gs)
    }

    // Assigning to quickbar slot
    fun toggleQuickslotAssign(item: ItemState, slotIndex: Int) {
        val gs = _gameState.value ?: return
        val invIndex = gs.player.inv.indexOf(item)
        if (invIndex == -1) return

        val slots = gs.player.quickSlots.toMutableList()
        if (slots[slotIndex] == invIndex) {
            slots[slotIndex] = -1
        } else {
            slots[slotIndex] = invIndex
        }
        gs.player.quickSlots = slots
        // Trigger recomposition
        _gameState.value = gs.copy()
    }

    fun useQuickSlot(slotIndex: Int) {
        val gs = _gameState.value ?: return
        if (slotIndex !in gs.player.quickSlots.indices) return
        val invIndex = gs.player.quickSlots[slotIndex]
        if (invIndex == -1 || invIndex >= gs.player.inv.size) {
            addMsg("Quick slot space is empty.", "mi")
            return
        }
        useInventoryItem(invIndex)
    }

    fun triggerEventChoice(choiceIndex: Int) {
        val gs = _gameState.value ?: return
        val ev = _activeEvent.value ?: return

        _activeEvent.value = null

        when (ev.type) {
            "merchant" -> {
                if (choiceIndex == 1) {
                    if (gs.player.gold < 30) {
                        addMsg("The merchant sighs: 'Not enough gold, beggar!'", "mi")
                        return
                    }
                    gs.player.gold -= 30
                    val mysteryItem = genRandomItem(gs.currentFloor + 3)
                    addMsg("The Wandering Merchant hands you a mysterious high quality item!", "me")
                    addItemToInventory(gs, mysteryItem)
                    AudioSynth.play("pickup")
                } else {
                    addMsg("You bid farewell to the mysterious merchant.", "mi")
                }
            }
            "chest" -> {
                if (choiceIndex == 1) {
                    if (Random.nextFloat() < 0.3f) {
                        val trapDmg = Random.nextInt(10, 26)
                        gs.player.hp -= trapDmg
                        addMsg("It was a mimic trap! It bites you for $trapDmg raw damage!", "mt")
                        AudioSynth.play("trap")
                        if (gs.player.hp <= 0) {
                            triggerDeath(gs, "Mimic Box")
                            return
                        }
                    } else {
                        val cnt = Random.nextInt(2, 5)
                        addMsg("The ornate chest cracks open, filled with shining gear!", "me")
                        AudioSynth.play("pickup")
                        for (i in 0 until cnt) {
                            val dropping = genRandomItem(gs.currentFloor)
                            dropping.x = gs.player.currentX
                            dropping.y = gs.player.currentY
                            gs.items = gs.items + dropping
                        }
                    }
                } else {
                    addMsg("You decide to match on, leaving the chest block intact.", "mi")
                }
            }
            "fountain_event" -> {
                if (choiceIndex == 1) {
                    val heal = (gs.player.maxHp * 0.35f).toInt()
                    gs.player.hp = Math.min(gs.player.maxHp, gs.player.hp + heal)
                    gs.player.mp = Math.min(gs.player.maxMp, gs.player.mp + (gs.player.maxMp * 0.3f).toInt())
                    addMsg("The magical fountain waters heal your core, restoring $heal HP!", "mh")
                    AudioSynth.play("heal")
                } else {
                    addMsg("You leave the glowing pond intact.", "mi")
                }
            }
            "shrine_event" -> {
                if (choiceIndex == 1) {
                    AudioSynth.play("levelup")
                    when (Random.nextInt(1, 4)) {
                        1 -> {
                            gs.player.baseAtk += 2
                            addMsg("Shrine blessings flow into you! ATK raised permanently (+2 ATK)!", "ml")
                        }
                        2 -> {
                            gs.player.baseDef += 2
                            addMsg("Shrine blessings flow into you! DEF raised permanently (+2 DEF)!", "ml")
                        }
                        3 -> {
                            gs.player.baseMaxHp += 10
                            gs.player.maxHp += 10
                            gs.player.hp += 10
                            addMsg("Shrine blessings flow into you! Vitality raised (+10 maxHP)!", "ml")
                        }
                    }
                    recalculateStats(gs)
                } else {
                    addMsg("You bypass the divine monolith.", "mi")
                }
            }
        }
        recalculateStats(gs)
        _gameState.value = gs.copy()
        renderVisuals(gs)
    }

    private fun addMsg(txt: String, type: String = "") {
        val gs = _gameState.value ?: return
        gs.msgs = gs.msgs + LogMessage(txt, type)
        if (gs.msgs.size > 80) {
            gs.msgs = gs.msgs.drop(20)
        }
    }

    // Perform turn evaluation
    private fun endTurn() {
        val gs = _gameState.value ?: return
        if (gs.gameOver) return

        gs.player.turns++

        if (gs.player.freeTurn) {
            gs.player.freeTurn = false
            recalculateStats(gs)
            renderVisuals(gs)
            return
        }

        // Skill cooldown ticks
        if (gs.player.skillCd > 0) {
            gs.player.skillCd--
        }

        // Decay buffs
        val finalBuffs = mutableListOf<BuffState>()
        for (b in gs.player.buffs) {
            val remain = b.turns - 1
            if (remain <= 0) {
                addMsg("${b.displayName} effect wore off.", "mi")
            } else {
                finalBuffs.add(b.copy(turns = remain))
            }
        }
        gs.player.buffs = finalBuffs

        recalculateStats(gs)

        // Hunger tick every 20 turns
        if (gs.player.turns % 20 == 0) {
            gs.player.hunger = Math.max(0, gs.player.hunger - 1)
            if (gs.player.hunger <= 20 && gs.player.hunger % 5 == 0) {
                addMsg("You are getting hungry...", "mt")
            }
            if (gs.player.hunger <= 0) {
                val starvDmg = Random.nextInt(2, 6)
                gs.player.hp -= starvDmg
                addMsg("Starvation deals $starvDmg damage!", "mt")
                if (gs.player.hp <= 0) {
                    triggerDeath(gs, "Starvation")
                    return
                }
            }
        }

        // Poison Dot
        if (gs.player.poisonTurns > 0) {
            gs.player.poisonTurns--
            gs.player.hp -= gs.player.poisonDmg
            addMsg("Poison deals ${gs.player.poisonDmg} damage!", "mc")
            AudioSynth.play("trap")
            if (gs.player.hp <= 0) {
                triggerDeath(gs, "Poison")
                return
            }
        }

        // Enemy AI Turn
        evaluateEnemiesAI(gs)
        if (gs.gameOver) return

        // Evaluation player trap triggers
        evaluatePlayerTraps(gs)

        // Smoke decay
        val remainSmoke = mutableListOf<SmokeState>()
        for (s in gs.smokeEffects) {
            val left = s.turns - 1
            if (left > 0) {
                remainSmoke.add(s.copy(turns = left))
            }
        }
        gs.smokeEffects = remainSmoke

        // Streak decay
        if (gs.player.streak > 0 && gs.player.turns % 8 == 0) {
            gs.player.streak = Math.max(0, gs.player.streak - 1)
        }

        // Passive HP/MP regeneration
        if (gs.player.turns % 5 == 0 && gs.player.hp < gs.player.maxHp && gs.player.poisonTurns <= 0 && gs.player.hunger > 20) {
            gs.player.hp = Math.min(gs.player.maxHp, gs.player.hp + 1)
        }
        if (gs.player.turns % 8 == 0 && gs.player.mp < gs.player.maxMp) {
            gs.player.mp = Math.min(gs.player.maxMp, gs.player.mp + 1)
        }

        // Random check event occurrences (3% on normal turns)
        if (Random.nextFloat() < 0.03f) {
            triggerRandomEventChance()
        }

        checkAchievementsProgress(gs)
        renderVisuals(gs)
    }

    private fun checkTrapsAndTiles(gs: GameState) {
        val px = gs.player.currentX
        val py = gs.player.currentY

        // Traps evaluation
        val trapsCopy = gs.traps.toMutableList()
        for (trap in trapsCopy) {
            if (trap.triggered || trap.playerTrap || trap.x != px || trap.y != py) continue
            trap.triggered = true
            trap.hidden = false
            AudioSynth.play("trap")
            addMsg(trap.ds, "mt")

            if (trap.dmg > 0) {
                gs.player.hp -= trap.dmg
                addMsg("${trap.name} deals ${trap.dmg} damage!", "mt")
                if (gs.player.hp <= 0) {
                    triggerDeath(gs, trap.name)
                    return
                }
            }

            if (trap.ef == "poison_dot") {
                val hasAntidoteImm = gs.player.buffs.any { it.type == "antidote" }
                if (!hasAntidoteImm) {
                    gs.player.poisonTurns = trap.dur
                    gs.player.poisonDmg = (trap.dmg / trap.dur) + 1
                }
            }
            if (trap.ef == "teleport") {
                val r = gs.rooms.random()
                gs.player.currentX = r.cx
                gs.player.currentY = r.cy
                addMsg("Space warped! Teleponed!", "mi")
            }
        }
        gs.traps = trapsCopy

        // Fountain/Shrine/Chest/Merchant Tile Evaluation on land
        val tile = gs.map.grid[py][px]
        if (tile == DungeonGenerator.TL_FOUNTAIN) {
            val heal = (gs.player.maxHp * 0.3f).toInt()
            gs.player.hp = Math.min(gs.player.maxHp, gs.player.hp + heal)
            gs.player.mp = Math.min(gs.player.maxMp, gs.player.mp + (gs.player.maxMp * 0.2f).toInt())
            addMsg("The magical fountain waters heal you. HP +$heal!", "mh")
            AudioSynth.play("heal")
            // Dry it into standard water tile
            val gridCopy = gs.map.grid.map { it.toMutableList() }
            gridCopy[py][px] = DungeonGenerator.TL_WATER
            gs.map.grid = gridCopy
        }

        if (tile == DungeonGenerator.TL_SHRINE) {
            AudioSynth.play("levelup")
            when (Random.nextInt(1, 4)) {
                1 -> {
                    gs.player.baseAtk += 1
                    addMsg("The ancient shrine glows! Permanent ATK increased!", "ml")
                }
                2 -> {
                    gs.player.baseDef += 1
                    addMsg("The ancient shrine glows! Permanent DEF increased!", "ml")
                }
                3 -> {
                    gs.player.baseMaxHp += 6
                    gs.player.maxHp += 6
                    gs.player.hp += 6
                    addMsg("The ancient shrine glows! Max HP increased!", "ml")
                }
            }
            val gridCopy = gs.map.grid.map { it.toMutableList() }
            gridCopy[py][px] = DungeonGenerator.TL_FLOOR
            gs.map.grid = gridCopy
            recalculateStats(gs)
        }

        if (tile == DungeonGenerator.TL_CHEST) {
            val gridCopy = gs.map.grid.map { it.toMutableList() }
            gridCopy[py][px] = DungeonGenerator.TL_FLOOR
            gs.map.grid = gridCopy
            triggerInstantEvent("chest")
        }

        if (tile == DungeonGenerator.TL_MERCHANT) {
            val gridCopy = gs.map.grid.map { it.toMutableList() }
            gridCopy[py][px] = DungeonGenerator.TL_FLOOR
            gs.map.grid = gridCopy
            triggerInstantEvent("merchant")
        }

        endTurn()
    }

    private fun triggerRandomEventChance() {
        val types = listOf("merchant", "chest", "fountain_event", "shrine_event")
        triggerInstantEvent(types.random())
    }

    private fun triggerInstantEvent(type: String) {
        val zh = getApplication<Application>().resources.configuration.locales[0].language == "zh"
        val ev = when (type) {
            "merchant" -> GameEvent(
                type = "merchant",
                title = if (zh) "🧙 流浪商人" else "🧙 Wandering Merchant",
                desc = if (zh) "一个神秘的商人出现了！ '我有货，如果你有钱的话……'" else "A mysterious merchant appears! 'I have wares, if you have coin...'",
                choice1 = if (zh) "购买神秘物品 (-30 金币)" else "Buy Mystery Item (-30 Gold)",
                choice2 = if (zh) "离开" else "Leave"
            )
            "chest" -> GameEvent(
                type = "chest",
                title = if (zh) "📦 宝箱" else "📦 Treasure Chest",
                desc = if (zh) "你发现了一个华丽的宝箱！ 里面可能有宝藏……也可能是陷阱。" else "You found an ornate chest! It could contain great treasure... or a trap.",
                choice1 = if (zh) "打开它" else "Open It",
                choice2 = if (zh) "不管它" else "Leave It"
            )
            "fountain_event" -> GameEvent(
                type = "fountain_event",
                title = if (zh) "⛲ 魔法喷泉" else "⛲ Enchanted Fountain",
                desc = if (zh) "一个发光的喷泉涌出魔法之水。要喝吗？" else "A glowing fountain bubbles with magical water. Drink?",
                choice1 = if (zh) "喝下" else "Drink",
                choice2 = if (zh) "跳过" else "Skip"
            )
            "shrine_event" -> GameEvent(
                type = "shrine_event",
                title = if (zh) "⛩ 古代神殿" else "⛩ Ancient Shrine",
                desc = if (zh) "一座神殿散发着力量。祈求祝福？" else "A shrine pulses with power. Pray for a blessing?",
                choice1 = if (zh) "祈祷" else "Pray",
                choice2 = if (zh) "跳过" else "Skip"
            )
            else -> return
        }
        _activeEvent.value = ev
    }

    fun dismissEvent() {
        _activeEvent.value = null
    }

    private fun performAttack(gs: GameState, e: EnemyState) {
        var dmg = Math.max(1, gs.player.atk - e.def + Random.nextInt(-2, 3))

        // Ward stone check
        if (gs.player.warded) {
            gs.player.warded = false
            addMsg("🛡 Ward blocks the incoming attack completely!", "mi")
            return
        }

        // Dodge check
        if (Random.nextFloat() < gs.player.dodgeChance) {
            addMsg("You dodged ${e.name}'s attack!", "mi")
            AudioSynth.play("dodge")
            return
        }

        // Crit check
        var isCrit = false
        if (Random.nextFloat() < gs.player.critChance) {
            dmg = (dmg * 1.8f).toInt()
            isCrit = true
        }

        e.hp -= dmg
        if (isCrit) {
            addMsg("CRIT! You hit ${e.name} for $dmg damage!", "mc")
            AudioSynth.play("crit")
        } else {
            addMsg("You hit ${e.name} for $dmg damage.", "mc")
            AudioSynth.play("hit")
        }

        // Vampirism AI heal check
        if (e.ai == "lifesteal" && dmg > 0) {
            val drain = (dmg * 0.35f).toInt()
            e.hp = Math.min(e.maxHp, e.hp + drain)
            addMsg("${e.name} drains $drain HP back!", "mc")
        }

        if (e.hp <= 0) {
            killEnemy(gs, e)
        }
    }

    private fun killEnemy(t_gs: GameState, target: EnemyState, isSlainDirectly: Boolean = true) {
        if (isSlainDirectly) {
            t_gs.enemies = t_gs.enemies.filter { it != target }
        }
        t_gs.player.exp += target.exp
        t_gs.player.gold += target.goldDrop
        t_gs.player.kills++

        addMsg("Slain ${target.name}! Gained +${target.exp} XP.", "mc")
        addMsg("Found +${target.goldDrop} gold coins.", "mp")
        AudioSynth.play("pickup")

        // Trigger passive "Harvest of Soul" if unlocked
        if (_metaProgression.value.perkLifesteal) {
            val h = 2
            t_gs.player.hp = Math.min(t_gs.player.maxHp, t_gs.player.hp + h)
            addMsg("🌟 Harvest of Soul: Slaying healed +$h HP!", "mh")
        }

        // Advance Slaying Daily Quest progress
        val currentMeta = _metaProgression.value
        if (currentMeta.questSlayerProgress < 8) {
            val newSlayerProgress = currentMeta.questSlayerProgress + 1
            saveMetaProgression(currentMeta.copy(questSlayerProgress = newSlayerProgress))
        }

        // Streak evaluations
        t_gs.player.streak++
        if (t_gs.player.streak > t_gs.player.bestStreak) {
            t_gs.player.bestStreak = t_gs.player.streak
        }
        if (t_gs.player.streak >= 3) {
            val bonus = (target.exp * 0.2f * t_gs.player.streak).toInt()
            t_gs.player.exp += bonus
            addMsg("🔥 Streak Combo ${t_gs.player.streak}x! Bonus +$bonus XP granted!", "ml")
        }

        if (target.isBoss) {
            addMsg("🏆 Defeated terrifying BOSS ${target.name}!", "mach")
            if (t_gs.currentFloor == 25) { // Vanquished Dragon Emperor
                triggerVictory(t_gs)
                return
            }
        }

        // Spawn dropping loot (30% chance)
        if (Random.nextFloat() < 0.3f) {
            val droppingItem = genRandomItem(t_gs.currentFloor)
            droppingItem.x = target.x
            droppingItem.y = target.y
            t_gs.items = t_gs.items + droppingItem
            addMsg("${target.name} dropped ${droppingItem.name}!", "mp")
        }

        checkLevelUp(t_gs)
        checkAchievementsProgress(t_gs)
    }

    private fun evaluateEnemiesAI(gs: GameState) {
        val finalEnemies = mutableListOf<EnemyState>()
        for (e in gs.enemies) {
            if (e.hp <= 0) continue

            if (e.isAlly) {
                evaluateAllyAI(gs, e)
                finalEnemies.add(e)
                continue
            }

            if (e.stunned > 0) {
                e.stunned--
                finalEnemies.add(e)
                continue
            }

            val d = distance(e.x, e.y, gs.player.currentX, gs.player.currentY)

            // Boss Rage Buff decaying
            if (e.rageDur > 0) {
                e.rageDur--
                if (e.rageDur == 0) {
                    addMsg("${e.name}'s inner rage subsides.", "mi")
                }
            }

            // Boss skill spells evaluation
            if (e.isBoss) {
                if (e.skillCd > 0) e.skillCd--
                if (d <= 10.0 && e.skillCd <= 0 && Random.nextFloat() < 0.35f) {
                    val skillCasted = castBossSpellSkill(gs, e)
                    if (skillCasted) {
                        finalEnemies.add(e)
                        continue
                    }
                }
            }

            if (e.feared > 0) {
                e.feared--
                // Move away from player
                val dx = if (e.x > gs.player.currentX) 1 else if (e.x < gs.player.currentX) -1 else 0
                val dy = if (e.y > gs.player.currentY) 1 else if (e.y < gs.player.currentY) -1 else 0
                tryEnemyMove(gs, e, dx, dy)
                finalEnemies.add(e)
                continue
            }

            // Attack player if adjacent
            if (d <= 1.5) {
                performEnemyAttack(gs, e)
                finalEnemies.add(e)
                continue
            }

            // Movement logic based on AI profiles
            when (e.ai) {
                "chase" -> {
                    if (d < 8.0 || gs.map.visibleGrid[e.y][e.x]) {
                        moveEnemyTowards(gs, e, gs.player.currentX, gs.player.currentY)
                    } else {
                        enemyRandomMove(gs, e)
                    }
                }
                "erratic" -> {
                    if (d < 6.0 && Random.nextFloat() < 0.6f) {
                        moveEnemyTowards(gs, e, gs.player.currentX, gs.player.currentY)
                    } else {
                        enemyRandomMove(gs, e)
                    }
                }
                "wander" -> {
                    if (d < 4.0) {
                        moveEnemyTowards(gs, e, gs.player.currentX, gs.player.currentY)
                    } else {
                        enemyRandomMove(gs, e)
                    }
                }
                "ambush" -> {
                    if (d < 5.0) {
                        moveEnemyTowards(gs, e, gs.player.currentX, gs.player.currentY)
                    } else {
                        enemyRandomMove(gs, e)
                    }
                }
                "phase" -> { // Can move into walls or voids
                    if (d < 8.0) {
                        val dx = Math.signum((gs.player.currentX - e.x).toFloat()).toInt()
                        val dy = Math.signum((gs.player.currentY - e.y).toFloat()).toInt()
                        val nx = e.x + dx
                        val ny = e.y + dy
                        if (nx in 0 until 70 && ny in 0 until 45 && gs.map.grid[ny][nx] != DungeonGenerator.TL_VOID) {
                            if (!gs.enemies.any { it != e && it.x == nx && it.y == ny }) {
                                e.x = nx
                                e.y = ny
                            }
                        }
                    } else {
                        enemyRandomMove(gs, e)
                    }
                }
                "ranged" -> {
                    if (d < 2) {
                        performEnemyAttack(gs, e)
                    } else if (d < 7.0 && gs.map.visibleGrid[e.y][e.x]) {
                        // Cast range projectile blast
                        val projDmg = Math.max(1, (e.atk * 0.7f).toInt() - gs.player.def + Random.nextInt(-1, 2))
                        gs.player.hp -= projDmg
                        addMsg("${e.name} casts spell projectile and zaps you for $projDmg damage!", "mc")
                        AudioSynth.play("hit")
                        if (gs.player.hp <= 0) {
                            triggerDeath(gs, e.name)
                            return
                        }
                    } else if (d < 8.0) {
                        moveEnemyTowards(gs, e, gs.player.currentX, gs.player.currentY)
                    } else {
                        enemyRandomMove(gs, e)
                    }
                }
                "lifesteal" -> {
                    if (d < 8.0) {
                        moveEnemyTowards(gs, e, gs.player.currentX, gs.player.currentY)
                    } else {
                        enemyRandomMove(gs, e)
                    }
                }
                else -> {
                    enemyRandomMove(gs, e)
                }
            }
            finalEnemies.add(e)
        }
        gs.enemies = finalEnemies
    }

    private fun castBossSpellSkill(gs: GameState, e: EnemyState): Boolean {
        val dist = distance(e.x, e.y, gs.player.currentX, gs.player.currentY)
        val floorNode = gs.currentFloor

        when (floorNode) {
            5 -> { // Goblin King (Summons Goblins)
                e.skillCd = 7
                var spawned = 0
                for (i in 0 until 2) {
                    val ox = Random.nextInt(-2, 3)
                    val oy = Random.nextInt(-2, 3)
                    val nx = e.x + ox
                    val ny = e.y + oy
                    if (nx in 1 until 69 && ny in 1 until 44 && gs.map.grid[ny][nx] == DungeonGenerator.TL_FLOOR) {
                        if (!gs.enemies.any { it.x == nx && it.y == ny } && (nx != gs.player.currentX || ny != gs.player.currentY)) {
                            gs.enemies = gs.enemies + EnemyState(
                                name = "Summoned Goblin", ch = "g", color = "#228b22", hp = 12 + gs.currentFloor,
                                maxHp = 12 + gs.currentFloor, atk = 4 + gs.currentFloor / 2, def = 1, exp = 4, goldDrop = 2,
                                ai = "chase", x = nx, y = ny
                            )
                            spawned++
                        }
                    }
                }
                if (spawned > 0) {
                    addMsg("${e.name} beats war drums and summons $spawned Goblin reinforcements!", "md")
                    AudioSynth.play("spell")
                    return true
                }
            }
            10 -> { // Spider Queen
                // skill: poison split or spin web
                e.skillCd = 5
                if (Random.nextFloat() < 0.5f) { // Poison saliva
                    gs.player.poisonTurns = 5
                    gs.player.poisonDmg = (e.atk * 0.4f).toInt() + 1
                    addMsg("${e.name} spits highly acidic venom! You are poisoned!", "md")
                } else { // Web snare
                    gs.player.buffs = gs.player.buffs + BuffState("Web Snare", "web", 0, 3)
                    addMsg("${e.name} spits thick webbing, entangling your limbs!", "md")
                }
                AudioSynth.play("spell")
                return true
            }
            15 -> { // Vampire Lord
                e.skillCd = 6
                if (Random.nextFloat() < 0.6f) { // Life siphon
                    val siphonVal = Math.max(5, (e.atk * 0.5f).toInt())
                    gs.player.hp -= siphonVal
                    e.hp = Math.min(e.maxHp, e.hp + (siphonVal * 1.5f).toInt())
                    addMsg("${e.name} launches Blood Siphon! HP drained by $siphonVal!", "md")
                    AudioSynth.play("spell")
                    if (gs.player.hp <= 0) {
                        triggerDeath(gs, e.name)
                    }
                } else { // Summon bats
                    gs.enemies = gs.enemies + EnemyState(
                        name = "Bat", ch = "b", color = "#696969", hp = 8, maxHp = 8, atk = 3, def = 0, exp = 2, goldDrop = 0,
                        ai = "chase", x = e.x + Random.nextInt(-1, 2), y = e.y + Random.nextInt(-1, 2)
                    )
                    addMsg("${e.name} dissolves into bats! A Bat appears!", "md")
                    AudioSynth.play("spell")
                }
                return true
            }
            20 -> { // Elder Lich
                e.skillCd = 6
                if (Random.nextFloat() < 0.5f) { // Dark explosion AoE
                    val aoeDmg = Math.max(10, e.atk + gs.currentFloor)
                    gs.player.hp -= aoeDmg
                    addMsg("${e.name} channels Arcane Dark Explosion! Took $aoeDmg points of shadow damage!", "md")
                    AudioSynth.play("spell")
                    if (gs.player.hp <= 0) {
                        triggerDeath(gs, e.name)
                    }
                } else { // Skeleton reinforcements
                    val nx = e.x + Random.nextInt(-2, 3)
                    val ny = e.y + Random.nextInt(-2, 3)
                    if (nx in 1 until 69 && ny in 1 until 44 && gs.map.grid[ny][nx] == DungeonGenerator.TL_FLOOR) {
                        gs.enemies = gs.enemies + EnemyState(
                            name = "Risen Skeleton", ch = "S", color = "#dcdcdc", hp = 15, maxHp = 15, atk = 5, def = 1, exp = 4, goldDrop = 2,
                            ai = "chase", x = nx, y = ny
                        )
                        addMsg("${e.name} summons an undead Skeleton from the graves!", "md")
                        AudioSynth.play("spell")
                    }
                }
                return true
            }
            25 -> { // Final Boss: Dragon Emperor
                e.skillCd = 5
                val opt = Random.nextInt(1, 4)
                when (opt) {
                    1 -> { // Flame Breath
                        val fDmg = 25
                        gs.player.hp -= fDmg
                        addMsg("🔥 ${e.name} breathes blazing flames! You took massive $fDmg damage!", "md")
                        AudioSynth.play("spell")
                        if (gs.player.hp <= 0) {
                            triggerDeath(gs, e.name)
                        }
                    }
                    2 -> { // Enrage
                        e.rageDur = 5
                        e.rageBuff = 5
                        addMsg("💢 ${e.name} goes ENRAGED! Strength temporarily raised!", "md")
                        AudioSynth.play("spell")
                    }
                    3 -> { // Summon Dragon Whelp
                        val nx = e.x + Random.nextInt(-2, 3)
                        val ny = e.y + Random.nextInt(-2, 3)
                        if (nx in 1 until 69 && ny in 1 until 44) {
                            gs.enemies = gs.enemies + EnemyState(
                                name = "Dragon Whelp", ch = "d", color = "#ff6347", hp = 30, maxHp = 30, atk = 10, def = 4, exp = 15, goldDrop = 10,
                                ai = "chase", x = nx, y = ny
                            )
                            addMsg("🐉 ${e.name} spawns a fresh Dragon Whelp adjacent!", "md")
                            AudioSynth.play("spell")
                        }
                    }
                }
                return true
            }
        }
        return false
    }

    private fun performEnemyAttack(gs: GameState, e: EnemyState) {
        var dmg = Math.max(1, e.atk + e.rageBuff - gs.player.def + Random.nextInt(-1, 2))

        if (gs.player.warded) {
            gs.player.warded = false
            addMsg("🛡 Ward blocks ${e.name}'s incoming damage totally!", "mi")
            return
        }

        if (Random.nextFloat() < gs.player.dodgeChance) {
            addMsg("You dodged ${e.name}'s slash!", "mi")
            AudioSynth.play("dodge")
            return
        }

        gs.player.hp -= dmg
        addMsg("${e.name} hits you for $dmg damage!", "mc")
        AudioSynth.play("hit")

        if (gs.player.hp <= 0) {
            triggerDeath(gs, e.name)
        }
    }

    private fun evaluateAllyAI(gs: GameState, ally: EnemyState) {
        // Find nearest hostile
        var nearest: EnemyState? = null
        var nd = 999.0
        for (e in gs.enemies) {
            if (e.isAlly) continue
            val d = distance(ally.x, ally.y, e.x, e.y)
            if (d < nd) {
                nd = d
                nearest = e
            }
        }

        if (nearest != null && nd <= 1.5) {
            val dmg = Math.max(1, ally.atk - nearest.def + Random.nextInt(-1, 2))
            nearest.hp -= dmg
            addMsg("Your Summoned Spirit swipes ${nearest.name} for $dmg damage!", "mc")
            if (nearest.hp <= 0) {
                killEnemy(gs, nearest)
            }
        } else if (nearest != null && nd < 8.0) {
            moveEnemyTowards(gs, ally, nearest.x, nearest.y)
        } else {
            moveEnemyTowards(gs, ally, gs.player.currentX, gs.player.currentY)
        }
    }

    private fun moveEnemyTowards(gs: GameState, e: EnemyState, tx: Int, ty: Int) {
        val dx = Math.signum((tx - e.x).toFloat()).toInt()
        val dy = Math.signum((ty - e.y).toFloat()).toInt()
        if (tryEnemyMove(gs, e, dx, 0)) return
        if (tryEnemyMove(gs, e, 0, dy)) return
        tryEnemyMove(gs, e, dx, dy)
    }

    private fun tryEnemyMove(gs: GameState, e: EnemyState, dx: Int, dy: Int): Boolean {
        val nx = e.x + dx
        val ny = e.y + dy
        if (nx !in 0 until 70 || ny !in 0 until 45) return false
        val tile = gs.map.grid[ny][nx]
        if (tile == DungeonGenerator.TL_WALL || tile == DungeonGenerator.TL_VOID) return false
        if (gs.enemies.any { it != e && it.x == nx && it.y == ny }) return false
        if (nx == gs.player.currentX && ny == gs.player.currentY) return false
        e.x = nx
        e.y = ny
        return true
    }

    private fun enemyRandomMove(gs: GameState, e: EnemyState) {
        val options = listOf(Pair(0, 1), Pair(0, -1), Pair(1, 0), Pair(-1, 0))
        val direction = options.random()
        tryEnemyMove(gs, e, direction.first, direction.second)
    }

    private fun evaluatePlayerTraps(gs: GameState) {
        val remainsTraps = mutableListOf<TrapState>()
        for (trap in gs.traps) {
            if (!trap.playerTrap || trap.triggered) {
                remainsTraps.add(trap)
                continue
            }
            val trappedEnemy = gs.enemies.find { !it.isAlly && it.x == trap.x && it.y == trap.y }
            if (trappedEnemy != null) {
                trap.triggered = true
                trappedEnemy.hp -= trap.dmg
                addMsg("🐾 Your placed Bear Trap snaps! Dealt ${trap.dmg} damage to ${trappedEnemy.name}!", "mc")
                AudioSynth.play("hit")
                if (trappedEnemy.hp <= 0) {
                    killEnemy(gs, trappedEnemy)
                }
            } else {
                remainsTraps.add(trap)
            }
        }
        gs.traps = remainsTraps
    }

    private fun checkLevelUp(gs: GameState) {
        while (gs.player.exp >= gs.player.expNext) {
            gs.player.exp -= gs.player.expNext
            gs.player.level++
            gs.player.expNext = (gs.player.expNext * 1.5f).toInt()

            val hpGain = Random.nextInt(5, 12) + (if (gs.player.classIndex == 0) 5 else if (gs.player.classIndex == 3) 3 else 0)
            val mpGain = Random.nextInt(2, 6) + (if (gs.player.classIndex == 2) 5 else 0)
            val atkGain = Random.nextInt(1, 4)
            val defGain = Random.nextInt(0, 2)

            gs.player.maxHp += hpGain
            gs.player.baseMaxHp += hpGain
            gs.player.hp = Math.min(gs.player.hp + hpGain, gs.player.maxHp)
            gs.player.maxMp += mpGain
            gs.player.mp = Math.min(gs.player.mp + mpGain, gs.player.maxMp)
            gs.player.baseAtk += atkGain
            gs.player.baseDef += defGain

            addMsg("LEVEL UP! You became Level ${gs.player.level}!", "ml")
            addMsg("属性提升: +${hpGain}HP +${mpGain}MP +${atkGain}ATK +${defGain}DEF", "ml")
            AudioSynth.play("levelup")
        }
        recalculateStats(gs)
    }

    private fun recalculateStats(gs: GameState) {
        val p = gs.player
        p.atk = p.baseAtk
        p.def = p.baseDef
        p.maxHp = p.baseMaxHp

        // Weapon
        p.eqWeapon?.let { p.atk += it.value }
        // Armor
        p.eqArmor?.let { p.def += it.value }
        // Accessory
        p.eqAccessory?.let {
            p.atk += it.attrAtk
            p.def += it.attrDef
            p.maxHp += it.attrHp
        }

        // Active potion buffs
        for (b in p.buffs) {
            if (b.type == "str_buff") p.atk += b.value
            if (b.type == "def_buff" || b.type == "shield") p.def += b.value
        }

        p.hp = Math.min(p.hp, p.maxHp)
        p.mp = Math.min(p.mp, p.maxMp)
    }

    private fun renderVisuals(gs: GameState) {
        // Recompute visible grid FOV
        var rad = 10
        for (b in gs.player.buffs) {
            if (b.type == "torch") rad += b.value
        }

        val visible = DungeonGenerator.computeFOV(gs.map.grid, gs.player.currentX, gs.player.currentY, rad)
        val expGrid = gs.map.exploredGrid.mapIndexed { y, row ->
            row.mapIndexed { x, explored ->
                explored || visible[y][x]
            }
        }
        gs.map.visibleGrid = visible.map { it.toList() }
        gs.map.exploredGrid = expGrid

        // Sweep out hidden traps inside FOV
        for (trap in gs.traps) {
            if (trap.hidden && visible[trap.y][trap.x]) {
                trap.hidden = false
            }
        }

        _gameState.value = gs.copy()
        // Save the game on crucial actions & turns!
        saveGame()
    }

    private fun triggerDeath(gs: GameState, killer: String) {
        gs.gameOver = true
        _gameState.value = gs
        _screenState.value = "death"
        AudioSynth.play("death")
        viewModelScope.launch {
            repository.deleteGame()
            _hasActiveSave.value = false
        }
    }

    private fun triggerVictory(gs: GameState) {
        gs.gameOver = true
        gs.won = true
        _gameState.value = gs
        _screenState.value = "victory"
        AudioSynth.play("victory")
        viewModelScope.launch {
            repository.deleteGame()
            _hasActiveSave.value = false
        }
    }

    private fun addItemToInventory(gs: GameState, item: ItemState) {
        if (item.type == "food") {
            gs.player.hunger = Math.min(gs.player.maxHunger, gs.player.hunger + 30)
            addMsg("Picked up Dried Meat! Swallowed immediately. Hunger level restored.", "mh")
            AudioSynth.play("heal")
            return
        }

        val currentBag = gs.player.inv.toMutableList()
        if (currentBag.size < 20) {
            currentBag.add(item)
            gs.player.inv = currentBag
            addMsg("Picked up ${item.name}.", "mp")
            AudioSynth.play("pickup")
            attemptAutoEquipOrAssign(gs, item)
            return
        }

        // Full bag. Check if upgrades
        if (item.type == "weapon" || item.type == "armor" || item.type == "accessory") {
            val equipped = when (item.type) {
                "weapon" -> gs.player.eqWeapon
                "armor" -> gs.player.eqArmor
                else -> gs.player.eqAccessory
            }
            if (equipped == null || evaluateCompareBetter(item, equipped)) {
                // Auto equip it directly, slide old into full bag by auto melting the worst bag item!
                melodyWorstBagItemToGold(gs, item)
                return
            }
        }

        // Fallback: Discard the worst item in bag to make space for the new item if the new one is better
        melodyWorstBagItemToGold(gs, item)
    }

    private fun attemptAutoEquipOrAssign(gs: GameState, item: ItemState) {
        if (item.type == "weapon" || item.type == "armor" || item.type == "accessory") {
            val equipped = when (item.type) {
                "weapon" -> gs.player.eqWeapon
                "armor" -> gs.player.eqArmor
                else -> gs.player.eqAccessory
            }
            if (equipped == null || evaluateCompareBetter(item, equipped)) {
                val invIdx = gs.player.inv.indexOf(item)
                if (invIdx >= 0) {
                    equipItemDirectly(gs, invIdx)
                    if (equipped != null) {
                        addMsg("Auto equipped ${item.name}, old gear replaced in bag.", "mi")
                    } else {
                        addMsg("Auto equipped ${item.name} into empty slot.", "mi")
                    }
                }
            }
        } else {
            // Put consumable into quickbar slots
            val emptyQuickIdx = gs.player.quickSlots.indexOf(-1)
            if (emptyQuickIdx >= 0) {
                val invIdx = gs.player.inv.indexOf(item)
                if (invIdx >= 0) {
                    val slots = gs.player.quickSlots.toMutableList()
                    slots[emptyQuickIdx] = invIdx
                    gs.player.quickSlots = slots
                }
            }
        }
    }

    private fun melodyWorstBagItemToGold(gs: GameState, newItem: ItemState) {
        val bag = gs.player.inv.toMutableList()
        var worstIndex = 0
        var worstScore = evaluateScore(bag[0])

        for (i in 1 until bag.size) {
            val sc = evaluateScore(bag[i])
            if (bag[i].rarity < bag[worstIndex].rarity || (bag[i].rarity == bag[worstIndex].rarity && sc < worstScore)) {
                worstIndex = i
                worstScore = sc
            }
        }

        val worstItem = bag[worstIndex]
        val newItemScore = evaluateScore(newItem)

        if (newItem.rarity > worstItem.rarity || (newItem.rarity == worstItem.rarity && newItemScore > worstScore)) {
            // Discard worst for cash
            val cashValue = getItemValueInGold(worstItem, gs.currentFloor)
            gs.player.gold += cashValue
            // Clear slot link quickbar
            val slots = gs.player.quickSlots.map { if (it == worstIndex) -1 else it }
            gs.player.quickSlots = slots

            bag.removeAt(worstIndex)
            bag.add(newItem)
            gs.player.inv = bag
            addMsg("📦 Backpack FULL! Recycled worst item ${worstItem.name} for +$cashValue💰.", "mp")
            addMsg("Picked up ${newItem.name}.", "mp")
            AudioSynth.play("pickup")
            attemptAutoEquipOrAssign(gs, newItem)
        } else {
            // Discard the new item directly to gold
            val cashValue = getItemValueInGold(newItem, gs.currentFloor)
            gs.player.gold += cashValue
            addMsg("📦 Backpack FULL! Automatically melted ${newItem.name} into +$cashValue💰 gold.", "mp")
            AudioSynth.play("pickup")
        }
        syncQuickSlots(gs)
    }

    private fun syncQuickSlots(gs: GameState) {
        val slots = gs.player.quickSlots.toMutableList()
        // Recalculate inv binds
        for (i in slots.indices) {
            val boundIdx = slots[i]
            if (boundIdx != -1 && boundIdx >= gs.player.inv.size) {
                slots[i] = -1
            }
        }
        gs.player.quickSlots = slots
    }

    private fun evaluateCompareBetter(a: ItemState, b: ItemState): Boolean {
        return evaluateScore(a) > evaluateScore(b)
    }

    private fun evaluateScore(item: ItemState): Int {
        return when (item.type) {
            "weapon" -> item.value
            "armor" -> item.value
            "accessory" -> item.attrAtk + item.attrDef + item.attrHp
            else -> item.value
        }
    }

    private fun getItemValueInGold(item: ItemState, floor: Int): Int {
        val base = when (item.rarity) {
            1 -> 12
            2 -> 25
            3 -> 50
            4 -> 100
            else -> 5
        }
        return base + Random.nextInt(1, Math.max(2, floor))
    }

    private fun checkAchievementsProgress(gs: GameState) {
        val achieved = gs.player.achievements.toMutableSet()

        fun award(id: String) {
            if (!achieved.contains(id)) {
                achieved.add(id)
                val def = ACH_DEFS.find { it.id == id }
                if (def != null) {
                    gs.player.achievements = achieved.toList()
                    addMsg("🏆 ACHIEVEMENT UNLOCKED! — ${def.name.en}", "mach")
                    AudioSynth.play("ach")
                }
            }
        }

        if (gs.player.kills >= 1) award("first_kill")
        if (gs.player.kills >= 10) award("kill_10")
        if (gs.player.kills >= 50) award("kill_50")
        if (gs.player.bestStreak >= 5) award("streak5")
        if (gs.player.gold >= 500) award("gold500")
        if (gs.player.level >= 10) award("lvl10")
        if (gs.currentFloor >= 5) award("floor5")
        if (gs.currentFloor >= 15) award("floor15")
        if (gs.currentFloor >= 25) award("floor25")
        if (gs.won) award("win")

        // check gear legendaries
        val isLegendMatch = listOf(gs.player.eqWeapon, gs.player.eqArmor, gs.player.eqAccessory).any { it?.rarity == 4 } || gs.player.inv.any { it.rarity == 4 }
        if (isLegendMatch) award("legendary")
    }

    // --- REBUILD & INITIAL SETUP UTILS ---
    private fun rebuildGameState(saved: FullGameState): GameState {
        // Map data rebuild
        val dMap = DungeonMap(
            floor = saved.map.floor,
            grid = saved.map.grid,
            exploredGrid = saved.map.exploredGrid,
            visibleGrid = List(DungeonGenerator.MH) { List(DungeonGenerator.MW) { false } },
            stairX = saved.map.stairX,
            stairY = saved.map.stairY
        )

        val player = PlayerState(
            name = saved.player.name,
            raceIndex = saved.player.raceIndex,
            classIndex = saved.player.classIndex,
            raceName = saved.player.raceName,
            clsName = saved.player.clsName,
            hp = saved.player.hp,
            maxHp = saved.player.maxHp,
            mp = saved.player.mp,
            maxMp = saved.player.maxMp,
            level = saved.player.level,
            exp = saved.player.exp,
            expNext = saved.player.expNext,
            gold = saved.player.gold,
            turns = saved.player.turns,
            streak = saved.player.streak,
            bestStreak = saved.player.bestStreak,
            baseAtk = saved.player.baseAtk,
            baseDef = saved.player.baseDef,
            atk = saved.player.atk,
            def = saved.player.def,
            baseMaxHp = saved.player.baseMaxHp,
            currentX = saved.player.currentX,
            currentY = saved.player.currentY,
            deepestFloor = saved.player.deepestFloor,
            critChance = saved.player.critChance,
            spellPower = saved.player.spellPower,
            dodgeChance = saved.player.dodgeChance,
            poisonTurns = saved.player.poisonTurns,
            poisonDmg = saved.player.poisonDmg,
            hunger = saved.player.hunger,
            maxHunger = saved.player.maxHunger,
            warded = saved.player.warded,
            freeTurn = saved.player.freeTurn,
            skillCd = saved.player.skillCd,
            achievements = saved.player.achievements,
            eqWeapon = saved.player.eqWeapon,
            eqArmor = saved.player.eqArmor,
            eqAccessory = saved.player.eqAccessory,
            inv = saved.player.inv,
            quickSlots = saved.player.quickSlots.ifEmpty { List(9) { -1 } }
        )

        val gs = GameState(
            player = player,
            currentFloor = saved.currentFloor,
            map = dMap,
            rooms = emptyList(), // generated rooms aren't vital for gameplay updates, but stair triggers are kept in map
            enemies = saved.enemies.toMutableList(),
            items = saved.items,
            traps = saved.traps,
            msgs = saved.msgs,
            smokeEffects = saved.smokeEffects,
            gameOver = saved.gameOver,
            won = saved.won
        )
        recalculateStats(gs)
        // Redraw FOV
        var rad = 10
        for (b in gs.player.buffs) {
            if (b.type == "torch") rad += b.value
        }
        val visible = DungeonGenerator.computeFOV(gs.map.grid, gs.player.currentX, gs.player.currentY, rad)
        gs.map.visibleGrid = visible.map { it.toList() }

        return gs
    }

    private fun createInitialGameState(raceIndex: Int, classIndex: Int, charName: String): GameState {
        val race = RACES[raceIndex]
        val cls = CLASSES[classIndex]

        val nameStr = charName.ifBlank { "Adventurer" }
        
        // Permanent meta-progression upgrades
        val meta = _metaProgression.value
        val hpBonus = meta.hpLvl * 4
        val mpBonus = meta.mpLvl * 4
        val atkBonus = meta.atkLvl * 1
        val defBonus = meta.defLvl * 1
        val dodgeBonus = meta.dodgeLvl * 0.02f
        val spellBonus = if (meta.perkSpellboost) 0.15f else 0.0f

        val hpInit = cls.hp + race.hpM + hpBonus
        val mpInit = cls.mp + race.mpM + mpBonus
        val atkInit = cls.atk + race.atkM + atkBonus
        val defInit = cls.def + race.defM + defBonus

        val player = PlayerState(
            name = nameStr,
            raceIndex = raceIndex,
            classIndex = classIndex,
            raceName = race.name.en,
            clsName = cls.name.en,
            hp = hpInit,
            maxHp = hpInit,
            mp = mpInit,
            maxMp = mpInit,
            level = 1,
            exp = 0,
            expNext = 20,
            gold = 0,
            turns = 0,
            streak = 0,
            bestStreak = 0,
            baseAtk = atkInit,
            baseDef = defInit,
            atk = atkInit,
            def = defInit,
            baseMaxHp = hpInit,
            currentX = 0,
            currentY = 0,
            deepestFloor = 1,
            critChance = if (classIndex == 1) 0.15f else 0.05f,
            spellPower = (if (classIndex == 2) 1.5f else if (classIndex == 3) 1.1f else 1.0f) + spellBonus,
            dodgeChance = (if (classIndex == 1) 0.12f else 0.05f) + dodgeBonus,
            poisonTurns = 0,
            poisonDmg = 0,
            hunger = 100,
            maxHunger = 100,
            warded = false,
            freeTurn = false,
            achievements = emptyList(),
            eqWeapon = null,
            eqArmor = null,
            eqAccessory = null,
            inv = emptyList(),
            quickSlots = List(9) { -1 }
        )

        val gs = GameState(
            player = player,
            currentFloor = 1,
            map = DungeonMap(1, emptyList(), emptyList(), emptyList(), 0, 0),
            rooms = emptyList(),
            enemies = mutableListOf(),
            items = emptyList(),
            traps = emptyList(),
            msgs = listOf(LogMessage("Welcome to the Depths of Darkhollow...", "mst"), LogMessage( "Find the stairs (>) to go deeper. Survive!","mi"), LogMessage("Beware of traps (^) and manage your hunger.", "mi")),
            smokeEffects = emptyList(),
            gameOver = false,
            won = false
        )

        enterFloor(gs, 1)
        return gs
    }

    private fun enterFloor(gs: GameState, floor: Int) {
        gs.currentFloor = floor
        val dData = DungeonGenerator.genDungeon(floor)
        gs.map = DungeonMap(
            floor = floor,
            grid = dData.mapState.grid,
            exploredGrid = dData.mapState.exploredGrid,
            visibleGrid = dData.mapState.visibleGrid,
            stairX = dData.mapState.stairX,
            stairY = dData.mapState.stairY
        )
        gs.rooms = dData.rooms
        gs.traps = dData.traps
        gs.smokeEffects = emptyList()

        // Apply starting Ember Shield perk buff if unlocked
        if (_metaProgression.value.perkShieldEmber) {
            val hasShield = gs.player.buffs.any { it.type == "shield" }
            if (!hasShield) {
                gs.player.buffs = gs.player.buffs + BuffState("Ember Shield", "shield", 10, 999)
                addMsg("🛡️ Ember's Protection creates a starting shield (+10 HP Protection)!", "mst")
            }
        }

        // Update Quest 2 Explorer Progress Tracker
        val currentMeta = _metaProgression.value
        if (floor > currentMeta.questExplorerProgress) {
            saveMetaProgression(currentMeta.copy(questExplorerProgress = floor))
        }

        // Set player coordinates to room 0 center
        gs.player.currentX = dData.startRoom.cx
        gs.player.currentY = dData.startRoom.cy

        // Spawn monsters
        gs.enemies = spawnEnemiesForFloor(floor, dData.rooms).toMutableList()

        // Spawn items
        val itemsCount = Random.nextInt(5, 11) + (floor / 3)
        val list = mutableListOf<ItemState>()
        for (i in 0 until itemsCount) {
            val rm = dData.rooms.random()
            val dropping = genRandomItem(floor)
            dropping.x = Random.nextInt(rm.x + 1, rm.x + rm.w - 1)
            dropping.y = Random.nextInt(rm.y + 1, rm.y + rm.h - 1)
            if (gs.map.grid[dropping.y][dropping.x] == DungeonGenerator.TL_FLOOR) {
                list.add(dropping)
            }
        }
        // Spawn gold piles
        val goldCount = Random.nextInt(3, 8)
        for (i in 0 until goldCount) {
            val rm = dData.rooms.random()
            val gx = Random.nextInt(rm.x + 1, rm.x + rm.w - 1)
            val gy = Random.nextInt(rm.y + 1, rm.y + rm.h - 1)
            if (gs.map.grid[gy][gx] == DungeonGenerator.TL_FLOOR) {
                list.add(ItemState(
                    type = "gold",
                    name = "Gold Pile",
                    description = "Shiny gold coins.",
                    ch = "$",
                    color = "#ffd700",
                    value = Random.nextInt(5, 16) + floor * 3,
                    x = gx, y = gy
                ))
            }
        }
        // Spawn food
        val foodCount = Random.nextInt(1, 4)
        for (i in 0 until foodCount) {
            val rm = dData.rooms.random()
            val fx = Random.nextInt(rm.x + 1, rm.x + rm.w - 1)
            val fy = Random.nextInt(rm.y + 1, rm.y + rm.h - 1)
            if (gs.map.grid[fy][fx] == DungeonGenerator.TL_FLOOR) {
                list.add(ItemState(
                    type = "food",
                    name = "Dried Meat",
                    description = "Tastes salty. Restores hunger when chewed.",
                    ch = "%",
                    color = "#f4845f",
                    x = fx, y = fy
                ))
            }
        }

        gs.items = list

        if (floor > 1) {
            addMsg("You descended to floor $floor...", "mi")
            AudioSynth.play("stairs")
        }

        // Ambient floor descriptions
        val loreIdx = Math.min(LORE_FLOOR_DESC.size - 1, (floor - 1) / 5)
        addMsg(LORE_FLOOR_DESC[loreIdx], "mst")

        if (floor % 5 == 0) {
            addMsg("⚠️ You sense a powerful, monstrous presence on this floor...", "md")
        }
        if (floor == 25) {
            addMsg("🐉 The Golden Dragon Emperor awaits in the depth...", "md")
        }

        renderVisuals(gs)
    }

    private fun spawnEnemiesForFloor(floor: Int, rooms: List<DungeonGenerator.Room>): List<EnemyState> {
        val list = mutableListOf<EnemyState>()
        val count = Random.nextInt(6, 11) + (floor / 2)
        val listEligible = ENEMIES_DEF.filter { it.minFloor <= floor }

        for (i in 0 until count) {
            val rm = rooms.random()
            if (rm == rooms[0]) continue // skip start room

            val ex = Random.nextInt(rm.x + 1, rm.x + rm.w - 1)
            val ey = Random.nextInt(rm.y + 1, rm.y + rm.h - 1)

            val selectionScope = listEligible.filter { it.minFloor >= Math.max(1, floor - 4) }
            val base = if (selectionScope.isNotEmpty()) selectionScope.random() else listEligible.random()

            val multiplier = 1f + (floor - 1) * 0.12f
            var nameStr = base.name
            var hpModVal = 1f
            var atkModVal = 1f
            var defAdd = 0
            var expModVal = 1f
            var goldModVal = 1f
            var isElite = false

            // Elite trigger (starting floor 3, max 25% chance)
            if (floor >= 3 && Random.nextFloat() < Math.min(0.25f, 0.05f + floor * 0.01f)) {
                val prefix = ELITE_PREFIX_DEF.random()
                nameStr = prefix.name + nameStr
                hpModVal = prefix.hpM
                atkModVal = prefix.atkM
                defAdd = prefix.defM
                expModVal = prefix.expM
                goldModVal = prefix.goldM
                isElite = true
            }

            val finalHp = (base.hp * multiplier * hpModVal).toInt()
            list.add(
                EnemyState(
                    name = nameStr,
                    ch = base.ch,
                    color = base.color,
                    hp = finalHp,
                    maxHp = finalHp,
                    atk = (base.atk * multiplier * atkModVal).toInt(),
                    def = ((base.def + defAdd) * multiplier).toInt(),
                    exp = (base.exp * multiplier * expModVal).toInt(),
                    goldDrop = (Random.nextInt(base.gMin, base.gMax + 1) * goldModVal).toInt(),
                    ai = base.ai,
                    isBoss = false,
                    isElite = isElite,
                    x = ex,
                    y = ey
                )
            )
        }

        // Boss spawn evaluation
        val bossDef = BOSSES_DEF.find { it.floor == floor }
        if (bossDef != null) {
            val rm = if (rooms.size > 2) rooms[rooms.size - 2] else rooms.last()
            val bs = 1f + (floor - 1) * 0.1f
            val bossHp = (bossDef.hp * bs).toInt()
            list.add(
                EnemyState(
                    name = bossDef.name,
                    ch = bossDef.ch,
                    color = bossDef.color,
                    hp = bossHp,
                    maxHp = bossHp,
                    atk = (bossDef.atk * bs).toInt(),
                    def = (bossDef.def * bs).toInt(),
                    exp = (bossDef.exp * bs).toInt(),
                    goldDrop = Random.nextInt(bossDef.gMin, bossDef.gMax + 1),
                    ai = bossDef.ai,
                    isBoss = true,
                    isElite = false,
                    x = rm.cx,
                    y = rm.cy
                )
            )
        }

        return list
    }

    private fun genRandomItem(floor: Int): ItemState {
        val p = Random.nextFloat()
        return when {
            p < 0.24f -> genWeapon(floor)
            p < 0.44f -> genArmor(floor)
            p < 0.56f -> genAccessory(floor)
            p < 0.69f -> genPotion(floor)
            p < 0.80f -> genScroll(floor)
            else -> genConsumable(floor)
        }
    }

    private fun genWeapon(floor: Int): ItemState {
        val maxRarity = Math.min(4, floor / 3)
        val eligible = WEAPONS_DEF.filter { it.rarity <= maxRarity }
        val base = eligible.random()
        val bonus = if (floor > 5) Random.nextInt(0, (floor / 5) + 1) else 0
        val finalAtk = base.atk + bonus
        val rareCol = RARITY_COLORS[base.rarity]
        return ItemState(
            type = "weapon",
            name = base.name,
            description = "Atk bonus +$finalAtk. Quality: ${RARITY_NAMES[base.rarity]}.",
            ch = "/",
            color = "#f4845f",
            rarity = base.rarity,
            value = finalAtk
        )
    }

    private fun genArmor(floor: Int): ItemState {
        val maxRarity = Math.min(4, floor / 3)
        val eligible = ARMORS_DEF.filter { it.rarity <= maxRarity }
        val base = eligible.random()
        val bonus = if (floor > 5) Random.nextInt(0, (floor / 5) + 1) else 0
        val finalDef = base.def + bonus
        return ItemState(
            type = "armor",
            name = base.name,
            description = "Def bonus +$finalDef. Quality: ${RARITY_NAMES[base.rarity]}.",
            ch = "]",
            color = "#7ec8e3",
            rarity = base.rarity,
            value = finalDef
        )
    }

    private fun genAccessory(floor: Int): ItemState {
        val maxRarity = Math.min(4, floor / 4)
        val eligible = ACCESSORIES_DEF.filter { it.rarity <= maxRarity }
        val base = eligible.random()
        return ItemState(
            type = "accessory",
            name = base.name,
            description = "Stat modifiers: Atk+${base.atk} Def+${base.def} HP+${base.hp}",
            ch = ")",
            color = "#06d6a0",
            rarity = base.rarity,
            attrAtk = base.atk,
            attrDef = base.def,
            attrHp = base.hp
        )
    }

    private fun genPotion(floor: Int): ItemState {
        val maxIdx = Math.min(POTIONS_DEF.size - 1, (floor / 2) + 2)
        val base = POTIONS_DEF[Random.nextInt(0, maxIdx + 1)]
        val valueScale = (base.value * (1f + floor * 0.1f)).toInt()
        return ItemState(
            type = "potion",
            name = base.name,
            description = when (base.ef) {
                "heal" -> "Restores $valueScale HP instantly."
                "mana" -> "Restores $valueScale MP instantly."
                "str_buff" -> "Grants +${base.value} bonus ATK power for ${base.dur} turns."
                "def_buff" -> "Grants +${base.value} bonus DEF power for ${base.dur} turns."
                "restore" -> "Unlocks ultimate power, restoring HP/MP fully."
                else -> "Damages you! Avoid drinking this."
            },
            ch = "!",
            color = base.color,
            rarity = 0,
            value = valueScale,
            ef = base.ef,
            valPower = base.value,
            dur = base.dur
        )
    }

    private fun genScroll(floor: Int): ItemState {
        val maxIdx = Math.min(SCROLLS_DEF.size - 1, (floor / 2) + 2)
        val base = SCROLLS_DEF[Random.nextInt(0, maxIdx + 1)]
        val valueScale = (base.value * (1f + floor * 0.15f)).toInt()
        return ItemState(
            type = "scroll",
            name = base.name,
            description = when (base.ef) {
                "fireball" -> "Launches explosive fireball. Dealt $valueScale AoE damage (dist 4)."
                "lightning" -> "Strikes all seen targets with zapping bolt for $valueScale damage."
                "teleport" -> "Blinks player to any random room location safely."
                "mapping" -> "Fully paints the explored map grid for this floor."
                "shield" -> "Encases in shield +${base.value} DEF for ${base.dur} turns."
                else -> "Induces terrifying scream frighting enemies within 5 tiles."
            },
            ch = "?",
            color = base.color,
            rarity = 1,
            value = valueScale,
            ef = base.ef,
            valPower = base.value,
            dur = base.dur
        )
    }

    private fun genConsumable(floor: Int): ItemState {
        val maxIdx = Math.min(CONSUMABLES_DEF.size - 1, (floor / 2) + 2)
        val base = CONSUMABLES_DEF[Random.nextInt(0, maxIdx + 1)]
        val valueScale = (base.value * (1f + floor * 0.12f)).toInt()
        return ItemState(
            type = "consumable",
            name = base.name,
            description = base.desc,
            ch = base.ch,
            color = base.color,
            rarity = base.rarity,
            value = valueScale,
            ef = base.ef,
            valPower = base.value,
            dur = base.dur
        )
    }

    private fun findNearestEnemyInView(gs: GameState, maxDist: Double): EnemyState? {
        var best: EnemyState? = null
        var bd = maxDist
        for (e in gs.enemies) {
            if (e.isAlly) continue
            val d = distance(gs.player.currentX, gs.player.currentY, e.x, e.y)
            if (d < bd && gs.map.visibleGrid[e.y][e.x]) {
                bd = d
                best = e
            }
        }
        return best
    }

    private fun distance(x1: Int, y1: Int, x2: Int, y2: Int): Double {
        return Math.sqrt(((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1)).toDouble())
    }
}

// Immutable template definitions
class Race(val name: I18N, val hpM: Int, val atkM: Int, val defM: Int, val mpM: Int)
class ClassDef(val name: I18N, val hp: Int, val mp: Int, val atk: Int, val def: Int, val desc: I18N)
class I18N(val en: String, val zh: String)

val RACES = listOf(
    Race(I18N("Human", "人类"), 0, 0, 0, 0),
    Race(I18N("Dwarf", "矮人"), 10, 0, 2, -5),
    Race(I18N("Elf", "精灵"), -5, 1, -1, 10),
    Race(I18N("Orc", "兽人"), 5, 2, 0, -5)
)

val CLASSES = listOf(
    ClassDef(I18N("Warrior", "战士"), 50, 10, 6, 3, I18N("High HP & DEF. Skill: Shield Bash (stuns, CD: 8, cost 5)", "高生命和防御。技能: 盾击")),
    ClassDef(I18N("Rogue", "盗贼"), 35, 15, 8, 1, I18N("High ATK & Crit rate. Skill: Shadow Strike (deals heavy 250% single dmg, CD: 6, cost 4)", "高爆发。技能: 暗影突袭")),
    ClassDef(I18N("Mage", "法师"), 30, 40, 3, 1, I18N("High MP & Spells. Skill: Arcane Blast (devastating bulk magic blast CD: 10, cost 8)", "高魔法能量。技能: 奥术爆破")),
    ClassDef(I18N("Paladin", "圣骑士"), 45, 20, 5, 4, I18N("Balanced tank. Skill: Holy Light (bulk 40% healing and poison wipe, CD: 9, cost 6)", "兼顾治愈。技能: 圣光术"))
)

fun getClassSkillCost(classIdx: Int) = when (classIdx) {
    0 -> 5
    1 -> 4
    2 -> 8
    3 -> 6
    else -> 0
}

fun getClassSkillMaxCd(classIdx: Int) = when (classIdx) {
    0 -> 8
    1 -> 6
    2 -> 10
    3 -> 9
    else -> 0
}

fun getClassSkillName(classIdx: Int) = when (classIdx) {
    0 -> "Shield Bash"
    1 -> "Shadow Strike"
    2 -> "Arcane Blast"
    3 -> "Holy Light"
    else -> "Skill"
}

fun getClassSkillDesc(classIdx: Int) = when (classIdx) {
    0 -> "Deal 150% ATK damage and stun target for 2 turns. (5 MP, CD: 8)"
    1 -> "Deal 250% piercing physical ATK to nearest target. (4 MP, CD: 6)"
    2 -> "Deal heavy spell blast to all visible nearby foes (range 5). (8 MP, CD: 10)"
    3 -> "Restore 40% max HP and purge all poison dots instantly. (6 MP, CD: 9)"
    else -> ""
}

class WeaponDef(val name: String, val rarity: Int, val atk: Int)
val WEAPONS_DEF = listOf(
    WeaponDef("Rusty Sword", 0, 2), WeaponDef("Iron Dagger", 0, 3), WeaponDef("Short Sword", 0, 4),
    WeaponDef("Longsword", 1, 6), WeaponDef("Battle Axe", 1, 7), WeaponDef("War Hammer", 1, 8),
    WeaponDef("Flamebrand", 2, 10), WeaponDef("Frost Edge", 2, 11), WeaponDef("Thunder Mace", 2, 12),
    WeaponDef("Shadow Blade", 3, 15), WeaponDef("Dragon's Fang", 3, 17),
    WeaponDef("Vorpal Sword", 4, 22), WeaponDef("Godslayer", 4, 25)
)

class ArmorDef(val name: String, val rarity: Int, val def: Int)
val ARMORS_DEF = listOf(
    ArmorDef("Leather Vest", 0, 1), ArmorDef("Chain Mail", 0, 2), ArmorDef("Iron Plate", 1, 4),
    ArmorDef("Steel Armor", 1, 5), ArmorDef("Mithril Mail", 2, 7), ArmorDef("Dragon Scale", 2, 8),
    ArmorDef("Shadow Cloak", 3, 10), ArmorDef("Celestial Plate", 4, 14)
)

class AccessoryDef(val name: String, val rarity: Int, val atk: Int, val def: Int, val hp: Int)
val ACCESSORIES_DEF = listOf(
    AccessoryDef("Copper Ring", 0, 1, 0, 0), AccessoryDef("Iron Amulet", 0, 0, 1, 5),
    AccessoryDef("Ruby Ring", 1, 2, 0, 0), AccessoryDef("Sapphire Pendant", 1, 0, 2, 10),
    AccessoryDef("Emerald Brooch", 2, 2, 2, 15), AccessoryDef("Crown of Flames", 3, 4, 2, 20),
    AccessoryDef("Ring of the Void", 4, 5, 5, 30)
)

class PotionDef(val name: String, val ef: String, val value: Int, val dur: Int, val color: String)
val POTIONS_DEF = listOf(
    PotionDef("Health Potion", "heal", 20, 0, "#e63946"),
    PotionDef("Greater Health Potion", "heal", 50, 0, "#ff6b6b"),
    PotionDef("Mana Potion", "mana", 15, 0, "#4895ef"),
    PotionDef("Greater Mana Potion", "mana", 35, 0, "#7ec8e3"),
    PotionDef("Strength Elixir", "str_buff", 3, 30, "#f4845f"),
    PotionDef("Iron Skin Potion", "def_buff", 3, 30, "#7ec8e3"),
    PotionDef("Potion of Restoration", "restore", 0, 0, "#ffd700"),
    PotionDef("Poison", "poison", 10, 0, "#32cd32")
)

class ScrollDef(val name: String, val ef: String, val value: Int, val dur: Int, val color: String)
val SCROLLS_DEF = listOf(
    ScrollDef("Scroll of Fireball", "fireball", 25, 0, "#f4845f"),
    ScrollDef("Scroll of Lightning", "lightning", 30, 0, "#ffd700"),
    ScrollDef("Scroll of Teleport", "teleport", 0, 0, "#9b5de5"),
    ScrollDef("Scroll of Mapping", "mapping", 0, 0, "#4895ef"),
    ScrollDef("Scroll of Shield", "shield", 5, 30, "#7ec8e3"),
    ScrollDef("Scroll of Fear", "fear", 0, 0, "#aaa")
)

class ConsumableDef(val name: String, val ef: String, val value: Int, val dur: Int, val color: String, val ch: String, val rarity: Int, val desc: String)
val CONSUMABLES_DEF = listOf(
    ConsumableDef("Bomb", "bomb", 30, 0, "#ff4500", "*", 1, "AoE fire damage mapping 3 radius."),
    ConsumableDef("Throwing Knife", "throw_knife", 20, 0, "#c0c0c0", "†", 0, "Dard throws at nearest visible enemy."),
    ConsumableDef("Torch", "torch", 5, 30, "#f4845f", "☀", 0, "FOV radius boosted +5 for 30 turns."),
    ConsumableDef("Bear Trap", "bear_trap", 20, 0, "#a0522d", "▲", 0, "Landed snare trap snapping walkers."),
    ConsumableDef("Smoke Bomb", "smoke_bomb", 0, 0, "#888", "○", 1, "Blind cloud trigger terror fleeing."),
    ConsumableDef("Ward Stone", "ward", 0, 0, "#4895ef", "◆", 1, "Perfect ward immunity blocking next spell hit."),
    ConsumableDef("Haste Potion", "haste", 0, 0, "#06d6a0", "»", 1, "Grants sudden burst to speed, double turns."),
    ConsumableDef("Antidote", "antidote", 0, 0, "#80ed99", "✦", 0, "Cure toxic poison, triggers immunization.")
)

class EnemyDef(val name: String, val ch: String, val color: String, val hp: Int, val atk: Int, val def: Int, val exp: Int, val gMin: Int, val gMax: Int, val ai: String, val minFloor: Int)
val ENEMIES_DEF = listOf(
    EnemyDef("Rat", "r", "#a0522d", 8, 2, 0, 5, 1, 3, "wander", 1),
    EnemyDef("Bat", "b", "#696969", 6, 3, 0, 5, 1, 2, "erratic", 1),
    EnemyDef("Goblin", "g", "#228b22", 12, 4, 1, 8, 2, 6, "chase", 1),
    EnemyDef("Slime", "s", "#32cd32", 15, 2, 3, 6, 1, 4, "wander", 1),
    EnemyDef("Skeleton", "S", "#dcdcdc", 18, 6, 2, 12, 3, 8, "chase", 2),
    EnemyDef("Spider", "s", "#4b0082", 14, 8, 1, 10, 2, 5, "ambush", 2),
    EnemyDef("Orc", "o", "#8b0000", 25, 7, 3, 15, 5, 12, "chase", 3),
    EnemyDef("Wraith", "W", "#9370db", 20, 10, 3, 20, 5, 15, "phase", 4),
    EnemyDef("Dark Mage", "M", "#800080", 22, 14, 2, 22, 10, 25, "ranged", 4),
    EnemyDef("Ogre", "O", "#daa520", 40, 12, 4, 25, 8, 20, "chase", 5),
    EnemyDef("Troll", "T", "#556b2f", 50, 14, 6, 35, 12, 30, "chase", 7),
    EnemyDef("Vampire", "V", "#8b0000", 35, 16, 5, 40, 15, 35, "lifesteal", 7),
    EnemyDef("Golem", "G", "#808080", 60, 12, 10, 38, 10, 25, "chase", 8),
    EnemyDef("Lich", "L", "#9400d3", 45, 20, 8, 55, 20, 50, "ranged", 10),
    EnemyDef("Demon", "D", "#ff4500", 55, 22, 7, 60, 25, 60, "chase", 10),
    EnemyDef("Dragon Whelp", "d", "#ff6347", 65, 18, 10, 50, 30, 70, "ranged", 11),
    EnemyDef("Ancient Dragon", "D", "#ff0000", 80, 25, 12, 80, 40, 100, "ranged", 14),
    EnemyDef("Death Knight", "K", "#191970", 70, 28, 14, 75, 35, 80, "chase", 14)
)

class ElitePrefixDef(val name: String, val hpM: Float, val atkM: Float, val defM: Int, val expM: Float, val goldM: Float)
val ELITE_PREFIX_DEF = listOf(
    ElitePrefixDef("Elite ", 1.5f, 1.3f, 0, 2f, 2f),
    ElitePrefixDef("Enraged ", 1.2f, 1.6f, 0, 1.8f, 1.5f),
    ElitePrefixDef("Armored ", 1.8f, 1.0f, 2, 1.5f, 1.5f),
    ElitePrefixDef("Swift ", 0.8f, 1.4f, 0, 1.3f, 1.2f)
)

class BossDef(val name: String, val ch: String, val color: String, val hp: Int, val atk: Int, val def: Int, val exp: Int, val gMin: Int, val gMax: Int, val floor: Int, val ai: String)
val BOSSES_DEF = listOf(
    BossDef("Goblin King", "G", "#ffd700", 60, 10, 4, 100, 50, 80, 5, "chase"),
    BossDef("Spider Queen", "Q", "#8a2be2", 90, 14, 6, 180, 70, 120, 10, "chase"),
    BossDef("Vampire Lord", "V", "#dc143c", 120, 18, 8, 280, 100, 180, 15, "chase"),
    BossDef("Elder Lich", "L", "#9932cc", 150, 22, 10, 400, 150, 250, 20, "ranged"),
    BossDef("Dragon Emperor", "D", "#ffd700", 200, 28, 14, 600, 250, 500, 25, "chase")
)

val RARITY_COLORS = listOf("#c0c0c0", "#06d6a0", "#4895ef", "#9b5de5", "#ffd700")
val RARITY_NAMES = listOf("Common", "Uncommon", "Rare", "Epic", "Legendary")

val LORE_FLOOR_DESC = listOf(
    "Damp cave walls drip with moisture.",
    "Ancient carvings line the corridors.",
    "The air smells of brimstone.",
    "Bones crunch under your feet.",
    "A distant roar echoes through the halls."
)

// Active state holder runtime mapping
data class GameState(
    var player: PlayerState,
    var currentFloor: Int,
    var map: DungeonMap,
    var rooms: List<DungeonGenerator.Room>,
    var enemies: List<EnemyState>,
    var items: List<ItemState>,
    var traps: List<TrapState>,
    var msgs: List<LogMessage>,
    var smokeEffects: List<SmokeState>,
    var gameOver: Boolean,
    var won: Boolean
) {
    fun toFullGameState(): FullGameState {
        return FullGameState(
            player = player,
            currentFloor = currentFloor,
            map = DungeonMapState(
                floor = map.floor, width = 70, height = 45,
                grid = map.grid,
                exploredGrid = map.exploredGrid,
                stairX = map.stairX, stairY = map.stairY
            ),
            enemies = enemies,
            items = items,
            traps = traps,
            msgs = msgs,
            smokeEffects = smokeEffects,
            gameOver = gameOver,
            won = won
        )
    }
}

data class DungeonMap(
    val floor: Int,
    var grid: List<List<Int>>,
    var exploredGrid: List<List<Boolean>>,
    var visibleGrid: List<List<Boolean>>,
    val stairX: Int,
    val stairY: Int
)

class AchvDef(val id: String, val icon: String, val name: I18N, val d: I18N)
val ACH_DEFS = listOf(
    AchvDef("first_kill", "⚔", I18N("First Blood", "初见血"), I18N("Kill your first enemy", "击杀第一个敌人")),
    AchvDef("kill_10", "💀", I18N("Monster Slayer", "怪物猎人"), I18N("Kill 10 enemies", "击杀10个敌人")),
    AchvDef("kill_50", "☠️", I18N("Massacre", "屠杀者"), I18N("Kill 50 enemies", "击杀50个敌人")),
    AchvDef("boss_kill", "👑", I18N("Boss Slayer", "Boss杀手"), I18N("Defeat a boss", "击败一个Boss")),
    AchvDef("floor5", "🗡️", I18N("Deep Explorer", "深层探索者"), I18N("Reach floor 5", "到达第5层")),
    AchvDef("floor15", "🕳️", I18N("Abyss Walker", "深渊行者"), I18N("Reach floor 15", "到达第15层")),
    AchvDef("floor25", "🐉", I18N("Dragon Slayer", "屠龙者"), I18N("Reach floor 25", "到达第25层")),
    AchvDef("legendary", "🌟", I18N("Legendary Find", "传说发现"), I18N("Find a legendary item", "找到一件传说装备")),
    AchvDef("streak5", "🔥", I18N("On Fire!", "火力全开！"), I18N("5 kill streak", "5连杀")),
    AchvDef("gold500", "💰", I18N("Rich", "富翁"), I18N("Accumulate 500 gold", "累积500金币")),
    AchvDef("lvl10", "⭐", I18N("Veteran", "老兵"), I18N("Reach level 10", "到达10级")),
    AchvDef("win", "🏆", I18N("Champion", "冠军"), I18N("Beat the Dragon Emperor", "击败龙皇"))
)
