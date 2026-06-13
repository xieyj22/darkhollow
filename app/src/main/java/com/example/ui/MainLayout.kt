package com.example.ui

import android.graphics.Paint
import androidx.compose.foundation.BorderStroke
import android.graphics.Typeface
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.*
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.random.Random

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun MainLayout(viewModel: GameViewModel) {
    val screenState by viewModel.screenState.collectAsStateWithLifecycle()
    val hasActiveSave by viewModel.hasActiveSave.collectAsStateWithLifecycle()
    val toastMessage by viewModel.toastMessage.collectAsStateWithLifecycle()

    var isChinese by remember { mutableStateOf(false) }

    LaunchedEffect(toastMessage) {
        toastMessage?.let {
            viewModel.showToast(it)
            delay(2000)
            viewModel.clearToast()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepBlack)
    ) {
        // Dynamic screen selector
        when (screenState) {
            "title" -> TitleScreen(
                hasActiveSave = hasActiveSave,
                isChinese = isChinese,
                onLanguageToggle = { isChinese = !isChinese },
                onContinue = { viewModel.loadSavedGame() },
                onNewGame = { viewModel.setScreen("char_select") },
                onDailyUpgrades = { viewModel.setScreen("daily_upgrades") }
            )
            "char_select" -> CharacterSelectionScreen(
                isChinese = isChinese,
                onBack = { viewModel.setScreen("title") },
                onStart = { ri, ci, name ->
                    viewModel.startNewGame(ri, ci, name)
                }
            )
            "game" -> GameScreen(
                viewModel = viewModel,
                isChinese = isChinese,
                onLanguageToggle = { isChinese = !isChinese },
                onExitGame = { viewModel.setScreen("title") }
            )
            "death" -> DeathScreen(
                viewModel = viewModel,
                isChinese = isChinese,
                onRestart = { viewModel.setScreen("char_select") },
                onBackToTitle = { viewModel.setScreen("title") }
            )
            "victory" -> VictoryScreen(
                viewModel = viewModel,
                isChinese = isChinese,
                onRestart = { viewModel.setScreen("char_select") },
                onBackToTitle = { viewModel.setScreen("title") }
            )
            "daily_upgrades" -> DailyGoalsAndUpgradesScreen(
                viewModel = viewModel,
                isChinese = isChinese,
                onBack = { viewModel.setScreen("title") }
            )
        }

        // Action popup toast overlay
        toastMessage?.let {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 90.dp)
                    .background(Color.Black.copy(alpha = 0.85f), RoundedCornerShape(8.dp))
                    .border(1.dp, CrimsonRed, RoundedCornerShape(8.dp))
                    .padding(horizontal = 20.dp, vertical = 10.dp)
            ) {
                Text(
                    text = it,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

// 1. --- TITLE SCREEN (Ambient Pulsing Particles) ---
@Composable
fun TitleScreen(
    hasActiveSave: Boolean,
    isChinese: Boolean,
    onLanguageToggle: () -> Unit,
    onContinue: () -> Unit,
    onNewGame: () -> Unit,
    onDailyUpgrades: () -> Unit
) {
    var showHelpDialog by remember { mutableStateOf(false) }

    // Particle effect states
    val particles = remember {
        List(40) {
            mutableStateOf(
                Particle(
                    x = Random.nextFloat(),
                    y = Random.nextFloat(),
                    speed = Random.nextFloat() * 0.002f + 0.0005f,
                    alpha = Random.nextFloat() * 0.6f + 0.2f,
                    color = if (Random.nextFloat() < 0.5f) CrimsonRed else PureGold
                )
            )
        }
    }

    // Animation ticker to update frames
    val infiniteTransition = rememberInfiniteTransition(label = "title_glow")
    val titleScale by infiniteTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = 1.02f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    LaunchedEffect(Unit) {
        while (true) {
            delay(16) // ~60fps
            for (pState in particles) {
                val p = pState.value
                var ny = p.y - p.speed
                var nAlpha = p.alpha - 0.0015f
                var nx = p.x + (Random.nextFloat() - 0.5f) * 0.001f
                if (ny < 0f || nAlpha <= 0f) {
                    ny = 1f
                    nx = Random.nextFloat()
                    nAlpha = Random.nextFloat() * 0.6f + 0.2f
                }
                pState.value = p.copy(x = nx, y = ny, alpha = nAlpha)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                // Background deep base
                drawRect(color = DeepBlack)
                // Center-top radial ambient glow
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(CrimsonRed.copy(alpha = 0.12f), Color.Transparent),
                        center = Offset(size.width * 0.5f, size.height * 0.4f),
                        radius = size.maxDimension * 0.75f
                    )
                )
            }
    ) {
        // Floating particles
        Canvas(modifier = Modifier.fillMaxSize()) {
            for (pState in particles) {
                val p = pState.value
                drawCircle(
                    color = p.color.copy(alpha = p.alpha),
                    radius = 3.dp.toPx(),
                    center = Offset(p.x * size.width, p.y * size.height)
                )
            }
        }

        // Flag Language Select
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(20.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(CardSlate)
                .border(1.dp, CrimsonRed.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                .clickable { onLanguageToggle() }
                .padding(horizontal = 14.dp, vertical = 8.dp)
        ) {
            Text(
                text = if (isChinese) "English" else "中文",
                color = CrimsonRed,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
        }

        // Brand + Menu
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = if (isChinese) "暗渊深处" else "DEPTHS OF DARKHOLLOW",
                color = CrimsonRed,
                fontSize = 28.sp,
                fontWeight = FontWeight.Black,
                letterSpacing = 4.sp,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .drawBehind {
                        // Soft logo drop shadow
                        drawCircle(
                            color = CrimsonRed.copy(alpha = 0.25f),
                            radius = titleScale * 60.dp.toPx(),
                            center = center
                        )
                    }
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = if (isChinese) "经典硬核肉鸽地牢探险" else "A Roguelike Dungeon Crawler",
                color = DarkText,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(44.dp))

            // Actions Block
            if (hasActiveSave) {
                Button(
                    onClick = onContinue,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CrimsonRed,
                        contentColor = DarkCrimson
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .width(220.dp)
                        .height(48.dp)
                        .testTag("btn_continue")
                ) {
                    Text(
                        text = if (isChinese) "继续游戏" else "Continue Adventure",
                        fontWeight = FontWeight.Black,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
            }

            Button(
                onClick = onNewGame,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    contentColor = CrimsonRed
                ),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(2.dp, CrimsonRed),
                modifier = Modifier
                    .width(220.dp)
                    .height(48.dp)
                    .testTag("btn_new_game")
            ) {
                Text(
                    text = if (isChinese) "新游戏" else "New Game",
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = { showHelpDialog = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    contentColor = SkyBlue
                ),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.5.dp, BorderDark),
                modifier = Modifier
                    .width(220.dp)
                    .height(48.dp)
                    .testTag("btn_help")
            ) {
                Text(
                    text = if (isChinese) "如何玩 / 说明" else "How to Play",
                    fontWeight = FontWeight.Black,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onDailyUpgrades,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    contentColor = PureGold
                ),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(2.dp, PureGold.copy(alpha = 0.8f)),
                modifier = Modifier
                    .width(220.dp)
                    .height(48.dp)
                    .testTag("btn_daily_upgrades")
            ) {
                Text(
                    text = if (isChinese) "🏆 每日任务 & 永久强化" else "🏆 Goals & Upgrades",
                    fontWeight = FontWeight.Black,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        // System hints footer
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 20.dp, start = 20.dp, end = 20.dp)
        ) {
            Text(
                text = if (isChinese) "触摸滑动移动 | 实时回合自动保存" else "Swipe/Tap to move | Real-time Auto Save persistence",
                color = DarkText.copy(alpha = 0.5f),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center
            )
        }
    }

    if (showHelpDialog) {
        HelpDialog(isChinese = isChinese, onDismiss = { showHelpDialog = false })
    }
}

data class Particle(val x: Float, val y: Float, val speed: Float, val alpha: Float, val color: Color)

// 2. --- CHARACTER SELECTION SCREEN ---
@Composable
fun CharacterSelectionScreen(
    isChinese: Boolean,
    onBack: () -> Unit,
    onStart: (raceIdx: Int, classIdx: Int, name: String) -> Unit
) {
    var selRace by remember { mutableStateOf(0) }
    var selCls by remember { mutableStateOf(0) }
    var inputName by remember { mutableStateOf("Adventurer") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .background(DeepBlack),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Nav Back
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Start
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = CrimsonRed)
            }
        }

        Text(
            text = if (isChinese) "创建你的英雄" else "Create Your Hero",
            color = CrimsonRed,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Name input
        OutlinedTextField(
            value = inputName,
            onValueChange = { inputName = it },
            label = { Text(if (isChinese) "角色名字" else "Hero Name", color = DarkText) },
            textStyle = LocalTextStyle.current.copy(color = Color.White, fontFamily = FontFamily.Monospace),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = CrimsonRed,
                unfocusedBorderColor = BorderSlate,
                focusedContainerColor = DarkSlate,
                unfocusedContainerColor = DeepBlack
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .testTag("name_field")
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Grid Split
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            // RACES COLUMN
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(4.dp)
            ) {
                Text(
                    text = if (isChinese) "👤 种族 (Race)" else "👤 Race",
                    color = SkyBlue,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                LazyColumn {
                    items(RACES.size) { idx ->
                        val race = RACES[idx]
                        val isSelected = selRace == idx
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) DarkCrimson.copy(alpha = 0.5f) else CardSlate)
                                .border(1.5.dp, if (isSelected) CrimsonRed else BorderDark, RoundedCornerShape(12.dp))
                                .clickable { selRace = idx }
                                .padding(10.dp)
                        ) {
                            Text(
                                text = if (isChinese) race.name.zh else race.name.en,
                                color = if (isSelected) CrimsonRed else SoftGrey,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            val desc = when (idx) {
                                1 -> "+10 HP, +2 DEF, -5 MP"
                                2 -> "-5 HP, +1 ATK, -1 DEF, +10 MP"
                                3 -> "+5 HP, +2 ATK, -5 MP"
                                else -> "Balanced stats"
                            }
                            Text(text = desc, color = DarkText, fontSize = 10.sp)
                        }
                    }
                }
            }

            // CLASSES COLUMN
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(4.dp)
            ) {
                Text(
                    text = if (isChinese) "⚔️ 职业 (Class)" else "⚔️ Class",
                    color = SkyBlue,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                LazyColumn {
                    items(CLASSES.size) { idx ->
                        val cls = CLASSES[idx]
                        val isSelected = selCls == idx
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) DarkCrimson.copy(alpha = 0.5f) else CardSlate)
                                .border(1.5.dp, if (isSelected) CrimsonRed else BorderDark, RoundedCornerShape(12.dp))
                                .clickable { selCls = idx }
                                .padding(10.dp)
                        ) {
                            Text(
                                text = if (isChinese) cls.name.zh else cls.name.en,
                                color = if (isSelected) CrimsonRed else SoftGrey,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = "HP:${cls.hp} MP:${cls.mp} ATK:${cls.atk}",
                                color = DarkText,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }

        // Description area for Class Skills
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .background(CardSlate, RoundedCornerShape(12.dp))
                .border(1.dp, BorderDark, RoundedCornerShape(12.dp))
                .padding(12.dp)
        ) {
            val cls = CLASSES[selCls]
            Column {
                Text(
                    text = if (isChinese) "✨ 职业固有技能" else "✨ Signature Class Skill:",
                    color = PureGold,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (isChinese) "【${getClassSkillName(selCls)}】: ${getClassSkillDesc(selCls)}" else "[${getClassSkillName(selCls)}]: ${getClassSkillDesc(selCls)}",
                    color = SoftGrey,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 15.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Start Button
        Button(
            onClick = { onStart(selRace, selCls, inputName) },
            colors = ButtonDefaults.buttonColors(
                containerColor = CrimsonRed,
                contentColor = DarkCrimson
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .testTag("btn_begin")
        ) {
            Text(
                text = if (isChinese) "开始冒险" else "Begin Adventure",
                fontWeight = FontWeight.Black,
                fontSize = 16.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

// 3. --- GAME SCREEN (Immersive HUD and Canvas Renderer) ---
@Composable
fun GameScreen(
    viewModel: GameViewModel,
    isChinese: Boolean,
    onLanguageToggle: () -> Unit,
    onExitGame: () -> Unit
) {
    val state by viewModel.gameState.collectAsStateWithLifecycle()
    val activeEvent by viewModel.activeEvent.collectAsStateWithLifecycle()

    var showInventory by remember { mutableStateOf(false) }
    var showAchievements by remember { mutableStateOf(false) }
    var showLegend by remember { mutableStateOf(false) }
    var tileScale by remember { mutableStateOf(26f) } // default tile cell pixels

    // Touch mapping drag state
    var dragAccumX by remember { mutableStateOf(0f) }
    var dragAccumY by remember { mutableStateOf(0f) }

    val gs = state ?: return

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepBlack)
            .drawBehind {
                // Center-top radial ambient glow mapping HTML bg-[radial-gradient]
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(CrimsonRed.copy(alpha = 0.08f), Color.Transparent),
                        center = Offset(size.width * 0.5f, size.height * 0.35f),
                        radius = size.maxDimension * 0.65f
                    )
                )
            }
    ) {
        // IMMERSIVE UNIFIED HUD HEADER CARD
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkSlate),
            shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            border = BorderStroke(1.dp, BorderSlate),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                // Row 1: Floor Indicator and Control/Zoom Action Toggles
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "DARKHOLLOW  F${gs.currentFloor}",
                        color = CrimsonRed,
                        fontWeight = FontWeight.Black,
                        fontSize = 12.sp,
                        letterSpacing = 2.sp,
                        fontFamily = FontFamily.Monospace
                    )

                    Row(
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Info Legend
                        IconButton(
                            onClick = { showLegend = !showLegend },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Text("🗺", color = SkyBlue, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                        // Achievements
                        IconButton(
                            onClick = { showAchievements = true },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Text("🏆", color = PureGold, fontSize = 15.sp)
                        }
                        // Zoom Out
                        IconButton(
                            onClick = { tileScale = Math.max(18f, tileScale - 2f) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Text("➖", color = DarkText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                        // Zoom In
                        IconButton(
                            onClick = { tileScale = Math.min(38f, tileScale + 2f) },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Text("➕", color = DarkText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        // Exit
                        IconButton(
                            onClick = onExitGame,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.ExitToApp, contentDescription = "Exit", tint = DangerRed, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Row 2: Level Badge, Player Names and Gold Counter
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Level badge circle (Matches HTML Lv.24 indicator)
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(
                                brush = Brush.linearGradient(
                                    colors = listOf(CrimsonRed, DarkCrimson)
                                )
                            )
                            .border(1.5.dp, BorderDark, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Lv.${gs.player.level}",
                            color = Color.White,
                            fontWeight = FontWeight.Black,
                            fontSize = 13.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    // Player titles & class subtitle
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = gs.player.name.uppercase(),
                            color = CrimsonRed,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 13.sp,
                            letterSpacing = 1.sp,
                            fontFamily = FontFamily.Monospace
                        )
                        Text(
                            text = "${gs.player.raceName} · ${gs.player.clsName}",
                            color = SoftGrey.copy(alpha = 0.85f),
                            fontWeight = FontWeight.Medium,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    // Gold & Turn Stats
                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "${gs.player.gold}",
                                color = SoftGrey,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(PureGold)
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "T: ${gs.player.turns}",
                            color = DarkText,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Medium,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Row 3: Cohesive Progress Grid Tracker
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // HP Progress Block
                    Column(modifier = Modifier.weight(1f)) {
                        LinearProgressIndicator(
                            progress = { gs.player.hp.toFloat() / gs.player.maxHp.toFloat() },
                            color = DangerRed,
                            trackColor = Color.Black.copy(alpha = 0.5f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "HP ${gs.player.hp}/${gs.player.maxHp}",
                            color = DangerRed,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    // MP Progress Block
                    Column(modifier = Modifier.weight(1f)) {
                        LinearProgressIndicator(
                            progress = { gs.player.mp.toFloat() / gs.player.maxMp.toFloat() },
                            color = SkyBlue,
                            trackColor = Color.Black.copy(alpha = 0.5f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "MP ${gs.player.mp}/${gs.player.maxMp}",
                            color = SkyBlue,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    // XP Progress Block
                    Column(modifier = Modifier.weight(1.2f)) {
                        LinearProgressIndicator(
                            progress = { gs.player.exp.toFloat() / gs.player.expNext.toFloat() },
                            color = CrimsonRed,
                            trackColor = Color.Black.copy(alpha = 0.5f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "XP ${gs.player.exp}/${gs.player.expNext}",
                            color = CrimsonRed,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    // Hunger Progress Block
                    Column(modifier = Modifier.weight(0.8f)) {
                        LinearProgressIndicator(
                            progress = { gs.player.hunger.toFloat() / gs.player.maxHunger.toFloat() },
                            color = PureGold,
                            trackColor = Color.Black.copy(alpha = 0.5f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp))
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "HGR ${gs.player.hunger}",
                            color = PureGold,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }

        // ROW WITH CANVAS AND LOGS (Landscape support if needed, otherwise stacked)
        val config = LocalConfiguration.current
        val isLandscape = config.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE

        if (isLandscape) {
            Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
                // Map Panel
                Box(
                    modifier = Modifier
                        .weight(1.5f)
                        .fillMaxHeight()
                ) {
                    RoguelikeMapCanvas(
                        gs = gs,
                        tileScale = tileScale,
                        onSwipeMove = { dx, dy -> viewModel.movePlayer(dx, dy) }
                    )
                }

                // Control panel Side Block
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(DeepBlack)
                ) {
                    BattleMsgConsoleList(gs, Modifier.weight(1f))
                    Spacer(modifier = Modifier.height(4.dp))
                    ControlsBlock(viewModel, gs, isChinese, onShowBag = { showInventory = true })
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxWidth().weight(1f)) {
                // Map Panel (Takes up 55% space)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1.3f)
                ) {
                    RoguelikeMapCanvas(
                        gs = gs,
                        tileScale = tileScale,
                        onSwipeMove = { dx, dy -> viewModel.movePlayer(dx, dy) }
                    )
                }

                // Quick slots bar directly on top of maps
                QuickSlotsBar(gs = gs, onSlotClick = { viewModel.useQuickSlot(it) })

                // Console / Logs (Takes 15% space)
                BattleMsgConsoleList(gs, Modifier.weight(0.4f))

                // D-Pad and Actions Menu Panel
                ControlsBlock(viewModel, gs, isChinese, onShowBag = { showInventory = true })
            }
        }
    }

    // Modal Overlays
    if (activeEvent != null) {
        EventDialog(ev = activeEvent!!, onChoose = { viewModel.triggerEventChoice(it) })
    }

    if (showInventory) {
        InventoryDialog(
            gs = gs,
            isChinese = isChinese,
            onDismiss = { showInventory = false },
            onItemClicked = { viewModel.useInventoryItem(it) },
            onItemEquip = { viewModel.equipItemDirectly(gs, it) }
        )
    }

    if (showLegend) {
        LegendDialog(isChinese = isChinese, onDismiss = { showLegend = false })
    }

    if (showAchievements) {
        AchievementsDialog(gs = gs, isChinese = isChinese, onDismiss = { showAchievements = false })
    }
}

// 4. --- CANVAS RENDERING (ASCII overlay) ---
@Composable
fun RoguelikeMapCanvas(
    gs: GameState,
    tileScale: Float,
    onSwipeMove: (dx: Int, dy: Int) -> Unit
) {
    // Swipe mapping trigger threshold
    var dragAccumX by remember { mutableStateOf(0f) }
    var dragAccumY by remember { mutableStateOf(0f) }

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragCancel = {
                        dragAccumX = 0f
                        dragAccumY = 0f
                    },
                    onDragEnd = {
                        val limit = 55f
                        if (abs(dragAccumX) > abs(dragAccumY)) {
                            if (dragAccumX > limit) onSwipeMove(1, 0)
                            else if (dragAccumX < -limit) onSwipeMove(-1, 0)
                        } else {
                            if (dragAccumY > limit) onSwipeMove(0, 1)
                            else if (dragAccumY < -limit) onSwipeMove(0, -1)
                        }
                        dragAccumX = 0f
                        dragAccumY = 0f
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragAccumX += dragAmount.x
                        dragAccumY += dragAmount.y
                    }
                )
            }
    ) {
        // Tile matrix rendering on canvas
        drawIntoCanvas { canvas ->
            val sizeX = size.width
            val sizeY = size.height

            // Target viewport centers on player (cx, cy)
            val px = gs.player.currentX
            val py = gs.player.currentY

            val tilesX = (sizeX / tileScale).toInt()
            val tilesY = (sizeY / tileScale).toInt()

            val viewportStartX = Math.max(0, px - tilesX / 2)
            val viewportStartY = Math.max(0, py - tilesY / 2)

            val paintMap = Paint().apply {
                textSize = tileScale * 0.9f
                textAlign = Paint.Align.CENTER
                typeface = Typeface.MONOSPACE
                isAntiAlias = true
            }

            // Draw tile grid loops
            for (vy in 0 until tilesY) {
                for (vx in 0 until tilesX) {
                    val mx = viewportStartX + vx
                    val my = viewportStartY + vy

                    if (mx !in 0 until 70 || my !in 0 until 45) continue

                    val itemX = vx * tileScale + tileScale / 2
                    val itemY = vy * tileScale + tileScale * 0.82f

                    val explored = gs.map.exploredGrid[my][mx]
                    val visible = gs.map.visibleGrid[my][mx]

                    if (!explored) continue

                    val tile = gs.map.grid[my][mx]

                    // Set character token
                    val ch = when (tile) {
                        DungeonGenerator.TL_WALL -> "#"
                        DungeonGenerator.TL_FLOOR -> "·"
                        DungeonGenerator.TL_CORR -> "·"
                        DungeonGenerator.TL_STAIR -> ">"
                        DungeonGenerator.TL_WATER -> "~"
                        DungeonGenerator.TL_FOUNTAIN -> "O"
                        DungeonGenerator.TL_SHRINE -> "T"
                        DungeonGenerator.TL_CHEST -> "="
                        DungeonGenerator.TL_MERCHANT -> "&"
                        else -> " "
                    }

                    // Setup appropriate color
                    val codeColor = when (tile) {
                        DungeonGenerator.TL_WALL -> android.graphics.Color.DKGRAY
                        DungeonGenerator.TL_FLOOR -> android.graphics.Color.GRAY
                        DungeonGenerator.TL_CORR -> android.graphics.Color.GRAY
                        DungeonGenerator.TL_STAIR -> android.graphics.Color.CYAN
                        DungeonGenerator.TL_WATER -> android.graphics.Color.BLUE
                        DungeonGenerator.TL_FOUNTAIN -> android.graphics.Color.BLUE
                        DungeonGenerator.TL_SHRINE -> android.graphics.Color.GREEN
                        DungeonGenerator.TL_CHEST -> android.graphics.Color.YELLOW
                        DungeonGenerator.TL_MERCHANT -> android.graphics.Color.MAGENTA
                        else -> android.graphics.Color.BLACK
                    }

                    // Apply half-dim shade to non-FOV areas mimicking fog of war
                    paintMap.color = if (visible) codeColor else getDimmed(codeColor)
                    paintMap.typeface = if (tile == DungeonGenerator.TL_WALL) Typeface.DEFAULT else Typeface.MONOSPACE

                    // Paint background on special rooms
                    if (tile == DungeonGenerator.TL_FOUNTAIN || tile == DungeonGenerator.TL_SHRINE || tile == DungeonGenerator.TL_MERCHANT) {
                        val bgPaint = Paint().apply {
                            color = if (visible) android.graphics.Color.parseColor("#121225") else android.graphics.Color.BLACK
                        }
                        canvas.nativeCanvas.drawRect(
                            vx * tileScale, vy * tileScale,
                            (vx + 1) * tileScale, (vy + 1) * tileScale, bgPaint
                        )
                    }

                    canvas.nativeCanvas.drawText(ch, itemX, itemY, paintMap)
                }
            }

            // Draw Traps
            for (trap in gs.traps) {
                if (trap.triggered || (trap.hidden)) continue
                val vx = trap.x - viewportStartX
                val vy = trap.y - viewportStartY
                if (vx in 0 until tilesX && vy in 0 until tilesY) {
                    val visible = gs.map.visibleGrid[trap.y][trap.x]
                    if (!visible) continue

                    val itemX = vx * tileScale + tileScale / 2
                    val itemY = vy * tileScale + tileScale * 0.82f

                    paintMap.color = android.graphics.Color.parseColor(trap.color)
                    paintMap.typeface = Typeface.DEFAULT_BOLD
                    canvas.nativeCanvas.drawText(if (trap.playerTrap) "▲" else "^", itemX, itemY, paintMap)
                }
            }

            // Draw items on top
            for (item in gs.items) {
                val vx = item.x - viewportStartX
                val vy = item.y - viewportStartY
                if (vx in 0 until tilesX && vy in 0 until tilesY) {
                    val visible = gs.map.visibleGrid[item.y][item.x]
                    if (!visible) continue

                    val itemX = vx * tileScale + tileScale / 2
                    val itemY = vy * tileScale + tileScale * 0.82f

                    paintMap.color = android.graphics.Color.parseColor(item.color)
                    paintMap.typeface = Typeface.DEFAULT_BOLD
                    canvas.nativeCanvas.drawText(item.ch, itemX, itemY, paintMap)
                }
            }

            // Draw enemies
            for (e in gs.enemies) {
                val vx = e.x - viewportStartX
                val vy = e.y - viewportStartY
                if (vx in 0 until tilesX && vy in 0 until tilesY) {
                    val visible = gs.map.visibleGrid[e.y][e.x]
                    if (!visible) continue

                    val itemX = vx * tileScale + tileScale / 2
                    val itemY = vy * tileScale + tileScale * 0.82f

                    paintMap.color = if (e.isAlly) android.graphics.Color.GREEN else android.graphics.Color.parseColor(e.color)
                    paintMap.typeface = Typeface.DEFAULT_BOLD
                    canvas.nativeCanvas.drawText(e.ch, itemX, itemY, paintMap)

                    // Mini Health Bars
                    if (e.hp < e.maxHp) {
                        val barHeight = 2.dp.toPx()
                        val barWidth = tileScale * 0.8f
                        val startX = vx * tileScale + (tileScale - barWidth) / 2
                        val startY = vy * tileScale + 1.dp.toPx()

                        val borderPaint = Paint().apply { color = android.graphics.Color.BLACK }
                        val emptyHpPaint = Paint().apply { color = android.graphics.Color.RED }
                        val currHpPaint = Paint().apply { color = android.graphics.Color.GREEN }

                        canvas.nativeCanvas.drawRect(startX, startY, startX + barWidth, startY + barHeight, emptyHpPaint)
                        val greenLen = barWidth * (e.hp.toFloat() / e.maxHp.toFloat())
                        canvas.nativeCanvas.drawRect(startX, startY, startX + greenLen, startY + barHeight, currHpPaint)
                    }
                }
            }

            // Draw Player centrally (always visible!)
            val playerVX = px - viewportStartX
            val playerVY = py - viewportStartY
            if (playerVX in 0 until tilesX && playerVY in 0 until tilesY) {
                val itemX = playerVX * tileScale + tileScale / 2
                val itemY = playerVY * tileScale + tileScale * 0.82f

                // Draw background card shadow highlighter
                val goldShine = Paint().apply {
                    color = android.graphics.Color.parseColor("#443300")
                }
                canvas.nativeCanvas.drawCircle(playerVX * tileScale + tileScale / 2, playerVY * tileScale + tileScale / 2, tileScale * 0.5f, goldShine)

                paintMap.color = android.graphics.Color.YELLOW
                paintMap.typeface = Typeface.DEFAULT_BOLD
                canvas.nativeCanvas.drawText("@", itemX, itemY, paintMap)
            }

            // Draw smokes
            for (s in gs.smokeEffects) {
                val vx = s.x - viewportStartX
                val vy = s.y - viewportStartY
                if (vx in 0 until tilesX && vy in 0 until tilesY) {
                    val visible = gs.map.visibleGrid[s.y][s.x]
                    if (!visible) continue

                    val itemX = vx * tileScale + tileScale / 2
                    val itemY = vy * tileScale + tileScale / 2

                    val smokePaint = Paint().apply {
                        color = android.graphics.Color.parseColor("#A08888AA")
                    }
                    canvas.nativeCanvas.drawCircle(itemX, itemY, tileScale * 0.55f, smokePaint)
                }
            }
        }
    }
}

private fun getDimmed(color: Int): Int {
    val r = (android.graphics.Color.red(color) * 0.35f).toInt()
    val g = (android.graphics.Color.green(color) * 0.35f).toInt()
    val b = (android.graphics.Color.blue(color) * 0.35f).toInt()
    return android.graphics.Color.rgb(r, g, b)
}

// 5. --- CONTROLS DPAD BLOCK AND ACTIONS ---
@Composable
fun ControlsBlock(
    viewModel: GameViewModel,
    gs: GameState,
    isChinese: Boolean,
    onShowBag: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DarkSlate)
            .padding(10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // DPAD LEFT SIDE
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(start = 12.dp)
        ) {
            Row {
                Spacer(modifier = Modifier.size(44.dp))
                DpadButton("▲") { viewModel.movePlayer(0, -1) }
                Spacer(modifier = Modifier.size(44.dp))
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row {
                DpadButton("◄") { viewModel.movePlayer(-1, 0) }
                DpadButton("●") { viewModel.skipTurn() } // Wait turn center
                DpadButton("►") { viewModel.movePlayer(1, 0) }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row {
                Spacer(modifier = Modifier.size(44.dp))
                DpadButton("▼") { viewModel.movePlayer(0, 1) }
                Spacer(modifier = Modifier.size(44.dp))
            }
        }

        // ACTIONS PANEL RIGHT SIDE
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(end = 12.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // Skills Button
                ActionButton(
                    icon = "⚡",
                    label = if (isChinese) "技能" else "Skill",
                    tag = "btn_skill",
                    onClick = { viewModel.castClassSkill() }
                )

                // Pick up Button
                ActionButton(
                    icon = "G",
                    label = if (isChinese) "捡拾" else "Pickup",
                    tag = "btn_pickup",
                    onClick = { viewModel.pickupFloorItem() }
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // Stairs descend
                ActionButton(
                    icon = ">",
                    label = if (isChinese) "下楼" else "Descend",
                    tag = "btn_descend",
                    onClick = { viewModel.descendStairs() }
                )

                // Inventory Bag
                ActionButton(
                    icon = "🎒",
                    label = if (isChinese) "背包" else "Inventory",
                    tag = "btn_inventory",
                    onClick = onShowBag
                )
            }
        }
    }
}

@Composable
fun DpadButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(CardSlate)
            .border(1.5.dp, BorderDark, RoundedCornerShape(12.dp))
            .clickable { onClick() }
    ) {
        Text(
            text = label,
            fontSize = 18.sp,
            color = CrimsonRed, // Lavender
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

@Composable
fun ActionButton(icon: String, label: String, tag: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(72.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(CardSlate)
            .border(1.5.dp, BorderDark, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(vertical = 8.dp)
            .testTag(tag)
    ) {
        Text(text = icon, fontSize = 18.sp, color = SkyBlue)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = label, fontSize = 9.sp, color = Color.White, fontFamily = FontFamily.Monospace)
    }
}

// 6. --- QUICK SLOTS HOTBAR ---
@Composable
fun QuickSlotsBar(
    gs: GameState,
    onSlotClick: (Int) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(DeepBlack)
            .padding(horizontal = 6.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceAround
    ) {
        for (i in 0 until 9) {
            val bindIdx = gs.player.quickSlots.getOrElse(i) { -1 }
            val item = if (bindIdx != -1 && bindIdx < gs.player.inv.size) gs.player.inv[bindIdx] else null

            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (item != null) CardSlate else Color.Transparent)
                    .border(
                        1.5.dp,
                        if (item != null) Color(android.graphics.Color.parseColor(item.color)).copy(alpha = 0.8f) else BorderDark.copy(alpha = 0.5f),
                        RoundedCornerShape(10.dp)
                    )
                    .clickable(enabled = item != null) { onSlotClick(i) }
            ) {
                // Index tag top-left
                Text(
                    text = "${i + 1}",
                    fontSize = 8.sp,
                    color = CrimsonRed,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(2.dp)
                )

                if (item != null) {
                    Text(
                        text = item.ch,
                        color = Color(android.graphics.Color.parseColor(item.color)),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    Text(
                        text = "·",
                        color = Color.DarkGray,
                        fontSize = 14.sp,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
    }
}

// 7. --- LOGS BOX ---
@Composable
fun BattleMsgConsoleList(gs: GameState, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()

    // Scroll to latest log whenever state updates
    LaunchedEffect(gs.msgs.size) {
        if (gs.msgs.isNotEmpty()) {
            listState.animateScrollToItem(gs.msgs.size - 1)
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xCC1B1B1F)) // Matches bg-[#1B1B1F]/80 backdrop translucency
            .border(1.5.dp, BorderSlate, RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize()
        ) {
            items(gs.msgs) { msg ->
                val txtColor = when (msg.type) {
                    "mi" -> SkyBlue // info
                    "mc" -> CrimsonRed // battle hit
                    "mp" -> PureGold // gold/loot
                    "ml" -> AcidGreen // levelup
                    "mst" -> SoftGrey // history lore
                    "md" -> CrimsonRed // danger boss
                    "mt" -> SkyBlue // trap
                    "mh" -> AcidGreen // healing
                    "me" -> NeonPurple // magic item
                    "mach" -> PureGold // achievement
                    "msk" -> SkyBlue // spell skill
                    else -> SoftGrey
                }
                Text(
                    text = msg.text,
                    color = txtColor,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 14.sp,
                    modifier = Modifier.padding(vertical = 1.dp)
                )
            }
        }
    }
}

// 8. --- FLOATING INVENTORY DIALOG ---
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InventoryDialog(
    gs: GameState,
    isChinese: Boolean,
    onDismiss: () -> Unit,
    onItemClicked: (Int) -> Unit,
    onItemEquip: (Int) -> Unit
) {
    var quickslotMode by remember { mutableStateOf(false) }
    var selectedQuickslotSlot by remember { mutableStateOf(-1) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(8.dp),
            color = DarkSlate,
            border = BorderStroke(2.dp, CrimsonRed)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isChinese) "🎒 背包 (Inventory)" else "🎒 Inventory",
                        color = CrimsonRed,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = SoftGrey)
                    }
                }

                // Equip gear summary
                Text(
                    text = if (isChinese) "🛡 主角装备" else "🛡 Character Equipment:",
                    color = SkyBlue,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 4.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    EquipGearCell("Weapon", gs.player.eqWeapon)
                    EquipGearCell("Armor", gs.player.eqArmor)
                    EquipGearCell("Ring", gs.player.eqAccessory)
                }

                Text(
                    text = if (isChinese) "📦 背包物品" else "📦 Stored Items:",
                    color = SkyBlue,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 6.dp)
                )

                if (gs.player.inv.isEmpty()) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(
                            text = if (isChinese) "背包目前空无一物。" else "Your inventory is empty.",
                            color = DarkText,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(gs.player.inv.size) { index ->
                            val item = gs.player.inv[index]
                            val isEquipItem = item.type == "weapon" || item.type == "armor" || item.type == "accessory"
                            val isEquipBind = gs.player.quickSlots.contains(index)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(CardSlate)
                                    .border(1.dp, Color(android.graphics.Color.parseColor(item.color)).copy(alpha = 0.25f), RoundedCornerShape(4.dp))
                                    .clickable {
                                        if (quickslotMode) {
                                            selectedQuickslotSlot = index
                                        } else {
                                            onItemClicked(index)
                                        }
                                    }
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = item.ch,
                                        color = Color(android.graphics.Color.parseColor(item.color)),
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                    Column {
                                        Text(
                                            text = item.name,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                        Text(text = item.description, color = DarkText, fontSize = 10.sp)
                                    }
                                }

                                if (isEquipItem) {
                                    Button(
                                        onClick = { onItemEquip(index) },
                                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = SkyBlue),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Text(
                                            text = if (isChinese) "装备" else "Equip",
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                } else {
                                    // Binds Slot Index Tag
                                    val qsIdx = gs.player.quickSlots.indexOf(index)
                                    if (qsIdx >= 0) {
                                        Text(
                                            text = "Quick Slot ${qsIdx + 1}",
                                            color = PureGold,
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EquipGearCell(slotName: String, item: ItemState?) {
    Row(
        modifier = Modifier
            .width(105.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(CardSlate)
            .border(
                1.dp,
                if (item != null) Color(android.graphics.Color.parseColor(item.color)).copy(alpha = 0.4f) else DarkText.copy(alpha = 0.2f),
                RoundedCornerShape(3.dp)
            )
            .padding(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = item?.ch ?: "·",
            color = if (item != null) Color(android.graphics.Color.parseColor(item.color)) else DarkText,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(end = 4.dp)
        )
        Column {
            Text(text = slotName, color = DarkText, fontSize = 8.sp)
            Text(
                text = item?.name ?: "-",
                color = if (item != null) Color.White else DarkText,
                fontSize = 9.sp,
                maxLines = 1,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

// 9. --- POPUP FLOATING EVENT ---
@Composable
fun EventDialog(ev: GameEvent, onChoose: (Int) -> Unit) {
    Dialog(onDismissRequest = {}) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = DarkSlate,
            border = BorderStroke(2.dp, NeonPurple),
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = ev.title,
                    color = NeonPurple,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = ev.desc,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp
                )
                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = { onChoose(1) },
                    colors = ButtonDefaults.buttonColors(containerColor = NeonPurple),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = ev.choice1, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace)
                }

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = { onChoose(2) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = SoftGrey),
                    shape = RoundedCornerShape(4.dp),
                    border = BorderStroke(1.dp, SoftGrey.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = ev.choice2, fontFamily = FontFamily.Monospace)
                }
            }
        }
    }
}

// 10. --- HOW TO PLAY TUTORIAL OVERLAY ---
@Composable
fun HelpDialog(isChinese: Boolean, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f),
            shape = RoundedCornerShape(8.dp),
            color = DarkSlate,
            border = BorderStroke(2.dp, SkyBlue)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isChinese) "📖 游戏玩法说明" else "📖 How to Play",
                        color = SkyBlue,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = SoftGrey)
                    }
                }

                LazyColumn(modifier = Modifier.weight(1f)) {
                    item {
                        Text(
                            text = if (isChinese) "🎯 目标" else "🎯 Objective:",
                            color = PureGold,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                        Text(
                            text = if (isChinese)
                                "向下探索 25 层楼，杀死最终 Boss 龙皇即可获胜！每增加 5 层就会遇到新的高能 Boss。"
                            else
                                "Venture down through 25 dangerous dungeon levels to vanquish the Golden Dragon Emperor and achieve ultimate victory! Be cautious as ferocious bosses guard the stairs on every 5th floor.",
                            color = SoftGrey,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = if (isChinese) "⚙️ 系统机制" else "⚙️ Core Mechanics:",
                            color = PureGold,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(vertical = 6.dp)
                        )
                        Text(
                            text = if (isChinese)
                                "1. 饥饿度 (Hunger): 回合行走消耗饥饿度。当饥饿度降为0时，开始挨饿掉血。搜刮食物(%)可以补充吃掉！\n" +
                                "2. 连杀强化 (Kill Streak): 短时间连斩奖励额外经验。时间过长未杀敌则计数会减少。\n" +
                                "3. 背包溢出: 当包裹装满遭遇新装备时，若更好则会自动穿上！否则也会将其直接熔炼成亮闪闪的金沙，并增加金币。\n" +
                                "4. 快捷槽 bind 机制: 背包获得的消耗性飞刀药水爆弹等可自动绑到快捷栏位按键，直接点底部的槽即可无耗调用。"
                            else "1. Hunger System: Lose hunger as you make actions. If hunger reaches 0, you begins starving and taking damage. Collect meat % to replenish!\n" +
                              "2. Kill Streak bonus: Defeating targets in brief turns grants high experience scaling. Idle turns decay count.\n" +
                              "3. Auto-recycle overflow: Full bags automatically process newly discovered upgrades, direct equipping them and recycling old components to gold! Basic items get melted automatically.\n" +
                              "4. Hotbar Binds: Potions, scrolls, throwables automatically bind to slot tags 1 to 9. Click on any slot at the bottom to use directly.",
                            color = SoftGrey,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }
}

// 11. --- MAP CONSOLE SYMBOLS LEGEND ---
@Composable
fun LegendDialog(isChinese: Boolean, onDismiss: () -> Unit) {
    val items = listOf(
        Pair("#", "Wall"), Pair("·", "Dungeon Floor"), Pair(">", "Stairs Down"),
        Pair("~", "Water Body"), Pair("O", "Magic Fountain"), Pair("T", "Ancient Shrine"),
        Pair("=", "Treasure Chest"), Pair("&", "Wandering Merchant"), Pair("^", "Hidden Trap"),
        Pair("@", "You (Hero)"), Pair("r/g/s/S", "Monsters"), Pair("$", "Gold Piles"),
        Pair("%", "Food Meat Pile"), Pair("!", "Potion flasks"), Pair("?", "Magic Scrolls")
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = DarkSlate,
            border = BorderStroke(1.dp, SkyBlue),
            modifier = Modifier.padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isChinese) "🗺 符号图例" else "🗺 Symbols Legend",
                        color = SkyBlue,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = SoftGrey)
                    }
                }

                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    items(items) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = item.first,
                                color = CrimsonRed,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.width(36.dp)
                            )
                            Text(
                                text = item.second,
                                color = Color.White,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                }
            }
        }
    }
}

// 12. --- ACHIEVEMENTS HUD ---
@Composable
fun AchievementsDialog(gs: GameState, isChinese: Boolean, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f),
            shape = RoundedCornerShape(8.dp),
            color = DarkSlate,
            border = BorderStroke(2.dp, PureGold)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isChinese) "🏆 成就殿堂 (Achievements)" else "🏆 Achievements Hall",
                        color = PureGold,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = SoftGrey)
                    }
                }

                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(ACH_DEFS) { ach ->
                        val unlocked = gs.player.achievements.contains(ach.id)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(if (unlocked) DarkCrimson.copy(alpha = 0.2f) else Color.Transparent)
                                .border(
                                    1.dp,
                                    if (unlocked) PureGold.copy(alpha = 0.4f) else CardSlate.copy(alpha = 0.2f),
                                    RoundedCornerShape(4.dp)
                                )
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = ach.icon,
                                fontSize = 24.sp,
                                modifier = Modifier.padding(end = 12.dp)
                            )
                            Column {
                                Text(
                                    text = if (isChinese) ach.name.zh else ach.name.en,
                                    color = if (unlocked) PureGold else DarkText,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                                Text(
                                    text = if (isChinese) ach.d.zh else ach.d.en,
                                    color = if (unlocked) SoftGrey else DarkText.copy(alpha = 0.6f),
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// 13. --- DEATH SCREEN ---
@Composable
fun DeathScreen(
    viewModel: GameViewModel,
    isChinese: Boolean,
    onRestart: () -> Unit,
    onBackToTitle: () -> Unit
) {
    val gs by viewModel.gameState.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF100000))
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "YOU HAVE PERISHED",
                color = CrimsonRed,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 2.sp
            )
            Text(
                text = if (isChinese) "你在地牢中陨落了..." else "Valiant hero, your bones remain in Darkhollow...",
                color = DarkText,
                fontSize = 12.sp,
                modifier = Modifier.padding(vertical = 6.dp)
            )

            Spacer(modifier = Modifier.height(30.dp))

            // Scoreboard Summary
            gs?.let {
                Column(
                    modifier = Modifier
                        .background(CardSlate, RoundedCornerShape(16.dp))
                        .border(1.dp, BorderDark, RoundedCornerShape(16.dp))
                        .padding(16.dp)
                        .width(260.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        valueStrTitleValue("Deepest Floor", "F${it.currentFloor}"),
                        color = Color.White, fontSize = 13.sp, fontFamily = FontFamily.Monospace
                    )
                    Text(
                        valueStrTitleValue("Lvl Reached", "${it.player.level}"),
                        color = Color.White, fontSize = 13.sp, fontFamily = FontFamily.Monospace
                    )
                    Text(
                        valueStrTitleValue("Enemies Slain", "${it.player.kills}"),
                        color = Color.White, fontSize = 13.sp, fontFamily = FontFamily.Monospace
                    )
                    Text(
                        valueStrTitleValue("Gold Saved", "💰${it.player.gold}"),
                        color = Color.White, fontSize = 13.sp, fontFamily = FontFamily.Monospace
                    )
                    Text(
                        valueStrTitleValue("Turns Taken", "${it.player.turns}"),
                        color = Color.White, fontSize = 13.sp, fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            Button(
                onClick = onRestart,
                colors = ButtonDefaults.buttonColors(containerColor = CrimsonRed, contentColor = DarkCrimson),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.width(200.dp).height(46.dp)
            ) {
                Text(
                    text = if (isChinese) "重试一次" else "Try Again",
                    fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onBackToTitle,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = SoftGrey),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, BorderDark),
                modifier = Modifier.width(200.dp).height(46.dp)
            ) {
                Text(text = if (isChinese) "返回主菜单" else "Title Screen", fontFamily = FontFamily.Monospace)
            }
        }
    }
}

// 14. --- VICTORY SCREEN ---
@Composable
fun VictoryScreen(
    viewModel: GameViewModel,
    isChinese: Boolean,
    onRestart: () -> Unit,
    onBackToTitle: () -> Unit
) {
    val gs by viewModel.gameState.collectAsStateWithLifecycle()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F1500))
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "🌟 VICTORY 🌟",
                color = PureGold,
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                letterSpacing = 4.sp
            )
            Text(
                text = if (isChinese) "你击败了龙皇，征服了暗渊！" else "The Dragon Emperor falls! You have conquered Darkhollow!",
                color = AcidGreen,
                fontSize = 13.sp,
                modifier = Modifier.padding(vertical = 6.dp),
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(30.dp))

            // Scoreboard Summary
            gs?.let {
                Column(
                    modifier = Modifier
                        .background(CardSlate, RoundedCornerShape(16.dp))
                        .border(1.dp, BorderDark, RoundedCornerShape(16.dp))
                        .padding(16.dp)
                        .width(260.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        valueStrTitleValue("Ascendant Lvl", "${it.player.level}"),
                        color = Color.White, fontSize = 13.sp, fontFamily = FontFamily.Monospace
                    )
                    Text(
                        valueStrTitleValue("Monsters Purged", "${it.player.kills}"),
                        color = Color.White, fontSize = 13.sp, fontFamily = FontFamily.Monospace
                    )
                    Text(
                        valueStrTitleValue("Gold Pile Saved", "💰${it.player.gold}"),
                        color = Color.White, fontSize = 13.sp, fontFamily = FontFamily.Monospace
                    )
                    Text(
                        valueStrTitleValue("Turns Taken", "${it.player.turns}"),
                        color = Color.White, fontSize = 13.sp, fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(40.dp))

            Button(
                onClick = onRestart,
                colors = ButtonDefaults.buttonColors(containerColor = AcidGreen),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.width(200.dp).height(46.dp)
            ) {
                Text(
                    text = if (isChinese) "再来一局" else "Play Again",
                    fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace,
                    color = Color.Black
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onBackToTitle,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = SoftGrey),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, BorderDark),
                modifier = Modifier.width(200.dp).height(46.dp)
            ) {
                Text(text = if (isChinese) "返回主菜单" else "Title Screen", fontFamily = FontFamily.Monospace)
            }
        }
    }
}

private fun valueStrTitleValue(title: String, v: String): String {
    return "$title: $v"
}

@Composable
fun DailyGoalsAndUpgradesScreen(
    viewModel: GameViewModel,
    isChinese: Boolean,
    onBack: () -> Unit
) {
    val metaState by viewModel.metaProgression.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Logins, 1: Quests, 2: Upgrades
    val isLoginReady = viewModel.isDailyLoginReady()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepBlack)
    ) {
        // --- 1. Premium Header (Title + Back Button + Resource summary) ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkSlate)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.testTag("btn_back_to_title")
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = SoftGrey
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (isChinese) "宿命祭坛" else "Ascension Altar",
                    color = PureGold,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = if (isChinese) "每日签到、挑战与宿命印记" else "Daily Destiny Hub",
                    color = DarkText,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Resources Pill
            Row(
                modifier = Modifier
                    .background(CardSlate, RoundedCornerShape(12.dp))
                    .border(1.dp, BorderDark, RoundedCornerShape(12.dp))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "✨ ${metaState.emberShards}",
                    color = PureGold,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.testTag("lbl_ember_shards")
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "💰 ${metaState.goldReserves}",
                    color = AcidGreen,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.testTag("lbl_reserve_gold")
                )
            }
        }
        HorizontalDivider(color = BorderDark, thickness = 1.dp)

        // --- 2. Custom Tabs (Segmented style) ---
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .background(DarkSlate, RoundedCornerShape(12.dp))
                .border(1.dp, BorderDark, RoundedCornerShape(12.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            val tabs = listOf(
                if (isChinese) "📅 连签奖励" else "📅 Logins",
                if (isChinese) "⚔️ 每日任务" else "⚔️ Quests",
                if (isChinese) "🔮 符文洗礼" else "🔮 Altar"
            )
            tabs.forEachIndexed { index, title ->
                val isSelected = selectedTab == index
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            if (isSelected) CardSlate else Color.Transparent,
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { selectedTab = index }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = title,
                        color = if (isSelected) SoftGrey else DarkText,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // --- 3. Tab Body Container ---
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            when (selectedTab) {
                0 -> LoginRewardsTab(metaState, isLoginReady, isChinese, viewModel)
                1 -> DailyQuestsTab(metaState, isChinese, viewModel)
                2 -> PermanentUpgradesTab(metaState, isChinese, viewModel)
            }

            Spacer(modifier = Modifier.height(24.dp))

            // --- 4. Beautiful Testing sandbox overlay for fast visual dungeoneering ---
            Text(
                text = if (isChinese) "关卡调试沙盒" else "AUDITOR TESTING TOOLBOX",
                color = DarkText,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CardSlate.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                    .border(1.dp, BorderDark.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { viewModel.simulateDailyReset() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = SkyBlue),
                    border = BorderStroke(1.dp, SkyBlue.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("btn_cheat_time_skip")
                ) {
                    Text(
                        text = if (isChinese) "⏳ 快进24小时" else "⏳ Skip 24h Cooldown",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }

                Button(
                    onClick = { viewModel.reRollQuests() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent, contentColor = PureGold),
                    border = BorderStroke(1.dp, PureGold.copy(alpha = 0.4f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("btn_cheat_reroll_quests")
                ) {
                    Text(
                        text = if (isChinese) "🆕 刷新每日任务" else "🆕 Re-roll Quests",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// 📅 LOGIN REWARDS CALENDAR
@Composable
fun LoginRewardsTab(
    meta: MetaProgression,
    isLoginReady: Boolean,
    isChinese: Boolean,
    viewModel: GameViewModel
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSlate),
        border = BorderStroke(1.dp, BorderDark),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = if (isChinese) "📅 守护连签 (7天连签 calendar)" else "📅 Cosmic Vault (7-Day Calendars)",
                color = SoftGrey,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (isChinese) "每日登陆领取金币与余烬结晶，连签奖励更加丰厚！" else "Log in daily to secure resources for your crawl. Keep the streak going for massive payouts!",
                color = DarkText,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }

    // 7 Days rewards list layout
    val rewardItems = listOf(
        Pair("100 💰", "10 ✨"),
        Pair("200 💰", "15 ✨"),
        Pair("300 💰", "20 ✨"),
        Pair("400 💰", "25 ✨"),
        Pair("500 💰", "35 ✨"),
        Pair("600 💰", "50 ✨"),
        Pair("1000 💰", "100 ✨")
    )

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        rewardItems.forEachIndexed { dayIdx, rewards ->
            val dayNumber = dayIdx + 1
            val isClaimed = dayIdx <= meta.loginStreak && (dayIdx < meta.loginStreak || !isLoginReady)
            val isCurrentActive = (isLoginReady && dayIdx == (meta.loginStreak + 1) % 7) || (isLoginReady && meta.lastLoginClaim == 0L && dayIdx == 0)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (isCurrentActive) CardSlate else DarkSlate,
                        RoundedCornerShape(16.dp)
                    )
                    .border(
                        BorderStroke(
                            1.5.dp,
                            if (isCurrentActive) PureGold else if (isClaimed) AcidGreen.copy(alpha = 0.4f) else BorderDark
                        ),
                        RoundedCornerShape(16.dp)
                    )
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Day Badge block
                Box(
                    modifier = Modifier
                        .size(46.dp)
                        .background(
                            if (isClaimed) AcidGreen.copy(alpha = 0.15f) else if (isCurrentActive) PureGold.copy(alpha = 0.15f) else CardSlate,
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "D$dayNumber",
                        color = if (isClaimed) AcidGreen else if (isCurrentActive) PureGold else SoftGrey,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isChinese) "第 $dayNumber 天奖励" else "Day $dayNumber Standard Gift",
                        color = if (isCurrentActive) PureGold else SoftGrey,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = "Rewards: ${rewards.first} + ${rewards.second}",
                        color = DarkText,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                if (isClaimed) {
                    Text(
                        text = if (isChinese) "已领取 ✅" else "CLAIMED ✅",
                        color = AcidGreen,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                } else if (isCurrentActive) {
                    Button(
                        onClick = { viewModel.claimDailyLoginReward() },
                        colors = ButtonDefaults.buttonColors(containerColor = PureGold, contentColor = Color.Black),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                        modifier = Modifier.testTag("btn_claim_daily_login")
                    ) {
                        Text(
                            text = if (isChinese) "领 取 🎁" else "CLAIM 🎁",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                } else {
                    Text(
                        text = if (isChinese) "未解锁 🔒" else "LOCKED 🔒",
                        color = DarkText.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

// ⚔️ DAILY QUESTS BOARD
@Composable
fun DailyQuestsTab(
    meta: MetaProgression,
    isChinese: Boolean,
    viewModel: GameViewModel
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSlate),
        border = BorderStroke(1.5.dp, BorderDark),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = if (isChinese) "⚔️ 宿命神谕 (每日洗礼任务)" else "⚔️ Oracle's Decrees (Dailies)",
                color = SoftGrey,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (isChinese) "在单次迷宫探索中达成这些目标！当进度满时即可领取丰厚秘宝奖励。" else "Formulate goals during active dungeon runs. Accomplish and reap rich persistent rewards instantly!",
                color = DarkText,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }

    // Quest 1: Slayer
    QuestItemCard(
        title = if (isChinese) "深渊斩杀者 (Minion Slasher)" else "Minion Slasher Challenge",
        desc = if (isChinese) "在任意战斗中累计击杀 8 只怪物。" else "Slay 8 monsters in your active dungeoneering run.",
        progress = meta.questSlayerProgress,
        goal = 8,
        rewardText = "💰 +80 Gold, ✨ +15 Shards",
        isClaimed = meta.questSlayerClaimed,
        onClaim = { viewModel.claimQuestReward(1) },
        progressColor = CrimsonRed,
        isChinese = isChinese,
        testTag = "quest_1"
    )

    Spacer(modifier = Modifier.height(12.dp))

    // Quest 2: Explorer
    QuestItemCard(
        title = if (isChinese) "深层探索 (Deeper Descent)" else "Deeper Descent Explorer",
        desc = if (isChinese) "在迷宫中成功下潜至第 4 层或更深。" else "Reach floor 4 or deeper on your active run.",
        progress = meta.questExplorerProgress,
        goal = 4,
        rewardText = "💰 +150 Gold, ✨ +25 Shards",
        isClaimed = meta.questExplorerClaimed,
        onClaim = { viewModel.claimQuestReward(2) },
        progressColor = SkyBlue,
        isChinese = isChinese,
        testTag = "quest_2"
    )

    Spacer(modifier = Modifier.height(12.dp))

    // Quest 3: Gold Gatherer
    QuestItemCard(
        title = if (isChinese) "黄金劫掠者 (Treasury Hoarder)" else "Treasury Hoarder Raid",
        desc = if (isChinese) "在单次探索中累积获得 300 枚金币。" else "Accumulate 300 gold coins in your coin pouch.",
        progress = meta.questGoldProgress,
        goal = 300,
        rewardText = "💰 +200 Gold, ✨ +35 Shards",
        isClaimed = meta.questGoldClaimed,
        onClaim = { viewModel.claimQuestReward(3) },
        progressColor = PureGold,
        isChinese = isChinese,
        testTag = "quest_3"
    )
}

@Composable
fun QuestItemCard(
    title: String,
    desc: String,
    progress: Int,
    goal: Int,
    rewardText: String,
    isClaimed: Boolean,
    onClaim: () -> Unit,
    progressColor: Color,
    isChinese: Boolean,
    testTag: String
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSlate),
        border = BorderStroke(1.dp, BorderDark),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().testTag(testTag)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    color = SoftGrey,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f)
                )

                Text(
                    text = "$progress / $goal",
                    color = progressColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = desc,
                color = DarkText,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Progress bar
            val ratio = (progress.toFloat() / goal.toFloat()).coerceIn(0f, 1f)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp)
                        .background(CardSlate, RoundedCornerShape(4.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(ratio)
                            .background(progressColor, RoundedCornerShape(4.dp))
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Claim Button or states
                if (isClaimed) {
                    Text(
                        text = if (isChinese) "已领取 ✅" else "CLAIMED ✅",
                        color = AcidGreen,
                        fontWeight = FontWeight.Black,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                } else if (progress >= goal) {
                    Button(
                        onClick = onClaim,
                        colors = ButtonDefaults.buttonColors(containerColor = progressColor, contentColor = Color.Black),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text(
                            text = if (isChinese) "领取 🎁" else "CLAIM 🎁",
                            fontWeight = FontWeight.Black,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                } else {
                    Text(
                        text = if (isChinese) "进行中" else "PROGRESSING",
                        color = DarkText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Bonus: $rewardText",
                color = PureGold.copy(alpha = 0.85f),
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

// 🔮 PERMANENT UPGRADES & PASSIVE SKILLS ALTAR
@Composable
fun PermanentUpgradesTab(
    meta: MetaProgression,
    isChinese: Boolean,
    viewModel: GameViewModel
) {
    // Stat upgrades section header
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSlate),
        border = BorderStroke(1.5.dp, BorderDark),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = if (isChinese) "🔮 符印觉醒 (永久初始属性神印)" else "🔮 Runic Baptism (Meta-Stats)",
                color = SoftGrey,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (isChinese) "强化你永久角色属性的最高限制(上限值5层解锁)，开局更具优势！" else "Infuse your starting characters with permanent runic essence! Increases stats level up to max 5.",
                color = DarkText,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }

    // Stats List
    val statUpgrades = listOf(
        Triple("hp", if (isChinese) "泰坦生命神印 (Starting HP)" else "Vital Vessel (HP Boost)", Pair("+4 Starting Max HP per level", meta.hpLvl)),
        Triple("mp", if (isChinese) "秘能法力神印 (Starting MP)" else "Mind Well (MP Boost)", Pair("+4 Starting Max MP per level", meta.mpLvl)),
        Triple("atk", if (isChinese) "龙息力量印记 (Starting ATK)" else "Titan Force (ATK Boost)", Pair("+1 Starting Attack per level", meta.atkLvl)),
        Triple("def", if (isChinese) "不灭壁垒印记 (Starting DEF)" else "Shield Aegis (DEF Boost)", Pair("+1 Starting Defense per level", meta.defLvl)),
        Triple("dodge", if (isChinese) "迷雾闪避印记 (Starting EVASION)" else "Evasion Drift (Dodge Boost)", Pair("+2% Starting Dodge Chance per level", meta.dodgeLvl))
    )

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        statUpgrades.forEach { upgrade ->
            val key = upgrade.first
            val title = upgrade.second
            val desc = upgrade.third.first
            val lvl = upgrade.third.second
            val isMax = lvl >= 5
            val cost = (lvl + 1) * if (key == "hp" || key == "mp") 15 else if (key == "atk" || key == "def") 25 else 30

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSlate, RoundedCornerShape(16.dp))
                    .border(BorderStroke(1.dp, BorderDark), RoundedCornerShape(16.dp))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = title,
                            color = SoftGrey,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Lvl $lvl/5",
                            color = if (isMax) PureGold else SkyBlue,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                    Text(
                        text = desc,
                        color = DarkText,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                if (isMax) {
                    Text(
                        text = "MAXED 👑",
                        color = PureGold,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                } else {
                    val canAfford = meta.emberShards >= cost
                    Button(
                        onClick = { viewModel.buyPermanentUpgrade(key) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (canAfford) SkyBlue else CardSlate,
                            contentColor = Color.Black
                        ),
                        enabled = canAfford,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp).testTag("btn_upgrade_${key}")
                    ) {
                        Text(
                            text = "✨ $cost",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }

    Spacer(modifier = Modifier.height(24.dp))

    // Passive skills/perks section header
    Card(
        colors = CardDefaults.cardColors(containerColor = DarkSlate),
        border = BorderStroke(1.5.dp, BorderDark),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = if (isChinese) "🔮 宿命传承 (解锁永久特殊被动)" else "🔮 Inherited Passives (Skill Unlocks)",
                color = SoftGrey,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (isChinese) "永久觉醒这些天赋流派！为后续进入地牢的所有冒险提供决定性的战略支持。" else "Unshackle forbidden dungeoneering passive skills! Activating these substantially enhances run mechanics.",
                color = DarkText,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }

    // Perks list
    val perks = listOf(
        Triple(
            "perk_lifesteal",
            if (isChinese) "摄魂收割 (Harvest of Soul)" else "Harvest of Soul",
            Pair(
                if (isChinese) "宿命被动: 击杀迷宫怪物时吸食其魂能，回复生命2点！" else "Slaying heals +2 HP instantly upon every monster defeated.",
                Pair(45, meta.perkLifesteal)
            )
        ),
        Triple(
            "perk_spellboost",
            if (isChinese) "奥法本领 (Spell Weaver)" else "Spell Weaver",
            Pair(
                if (isChinese) "宿命被动: 使你所有的咒法与法卷元素附加伤害 permanently 提升 15%！" else "Increases spells power and elemental scroll damage by +15% permanently.",
                Pair(60, meta.perkSpellboost)
            )
        ),
        Triple(
            "perk_shield",
            if (isChinese) "余烬护盾 (Ember's Protection)" else "Ember's Protection Aegis",
            Pair(
                if (isChinese) "宿命被动: 每一层地牢入场开局时吸收余烬，附带 10 HP 永久护盾护甲防身！" else "Each floor descent creates a protective starting shield (+10 HP).",
                Pair(80, meta.perkShieldEmber)
            )
        )
    )

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        perks.forEach { perk ->
            val key = perk.first
            val title = perk.second
            val desc = perk.third.first
            val cost = perk.third.second.first
            val isUnlocked = perk.third.second.second

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSlate, RoundedCornerShape(16.dp))
                    .border(
                        BorderStroke(
                            1.5.dp,
                            if (isUnlocked) PureGold.copy(alpha = 0.5f) else BorderDark
                        ),
                        RoundedCornerShape(16.dp)
                    )
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        color = if (isUnlocked) PureGold else SoftGrey,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Text(
                        text = desc,
                        color = DarkText,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }

                if (isUnlocked) {
                    Text(
                        text = if (isChinese) "已觉醒 ⚡" else "ACTIVE ⚡",
                        color = PureGold,
                        fontWeight = FontWeight.Black,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                } else {
                    val canAfford = meta.emberShards >= cost
                    Button(
                        onClick = { viewModel.buyPermanentUpgrade(key) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (canAfford) PureGold else CardSlate,
                            contentColor = Color.Black
                        ),
                        enabled = canAfford,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp).testTag("btn_unlock_${key}")
                    ) {
                        Text(
                            text = "✨ $cost",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        }
    }
}
