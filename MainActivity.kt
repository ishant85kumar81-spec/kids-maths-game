package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.Image
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.material.icons.Icons
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import android.app.Application
import android.content.Context
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.random.Random
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.sin

object AudioSynth {
    private const val SAMPLE_RATE = 44100
    private val scope = kotlinx.coroutines.CoroutineScope(Dispatchers.IO)
    private val playMutex = Mutex()

    private suspend fun playTone(freq: Double, durationMs: Int, type: String = "sine") {
        playMutex.withLock {
            try {
                val numSamples = (durationMs * SAMPLE_RATE) / 1000
                val sample = ShortArray(numSamples)
                val bufferSize = AudioTrack.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                if (bufferSize <= 0) return
                
                val audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(SAMPLE_RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferSize.coerceAtLeast(numSamples * 2))
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
                
                for (i in 0 until numSamples) {
                    val t = i.toDouble() / SAMPLE_RATE
                    val envelope = 1.0 - (i.toDouble() / numSamples)
                    val value = when(type) {
                        "square" -> if (sin(2.0 * Math.PI * freq * t) > 0) 1.0 else -1.0
                        else -> sin(2.0 * Math.PI * freq * t)
                    }
                    val volume = 16384 * envelope
                    sample[i] = (value * volume).toInt().toShort()
                }

                audioTrack.play()
                audioTrack.write(sample, 0, numSamples)
                audioTrack.stop()
                audioTrack.release()
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }

    fun playSuccess() {
        scope.launch {
            try {
                playTone(880.0, 100)
                delay(100)
                playTone(1108.0, 100)
                delay(100)
                playTone(1318.5, 300)
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }

    fun playError() {
        scope.launch {
            try {
                playTone(150.0, 400, "square")
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }

    fun playCoin() {
        scope.launch {
            try {
                playTone(1046.5, 100)
                delay(100)
                playTone(1318.5, 200)
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }
}

// --- Game Logic ---
enum class GameMode { MAIN_MENU, OPERATION_SELECT, LEVEL_SELECT, PLAYING_LEVEL, PLAYING_ENDLESS, GAME_OVER, VICTORY }

data class MathQuestion(
    val a: Int,
    val b: Int,
    val operator: String,
    val options: List<Int>,
    val correctAnswer: Int
)

class GameViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("KidsMathGame", Context.MODE_PRIVATE)

    private val _gameState = MutableStateFlow(GameMode.MAIN_MENU)
    val gameState: StateFlow<GameMode> = _gameState.asStateFlow()

    private val _coins = MutableStateFlow(prefs.getInt("coins", 0))
    val coins: StateFlow<Int> = _coins.asStateFlow()

    private val _currentLevel = MutableStateFlow(1)
    val currentLevel: StateFlow<Int> = _currentLevel.asStateFlow()

    private val _maxLevelUnlocked = MutableStateFlow(mapOf(
        "+" to prefs.getInt("unlocked_+", 1),
        "-" to prefs.getInt("unlocked_-", 1),
        "*" to prefs.getInt("unlocked_*", 1),
        "/" to prefs.getInt("unlocked_/", 1)
    ))
    val maxLevelUnlocked: StateFlow<Map<String, Int>> = _maxLevelUnlocked.asStateFlow()
    
    private val _selectedOperation = MutableStateFlow<String?>(null)
    val selectedOperation: StateFlow<String?> = _selectedOperation.asStateFlow()

    private val _score = MutableStateFlow(0)
    val score: StateFlow<Int> = _score.asStateFlow()

    private val _lives = MutableStateFlow(3)
    val lives: StateFlow<Int> = _lives.asStateFlow()

    private val _currentQuestion = MutableStateFlow<MathQuestion?>(null)
    val currentQuestion: StateFlow<MathQuestion?> = _currentQuestion.asStateFlow()

    private val _feedback = MutableStateFlow<Boolean?>(null)
    val feedback: StateFlow<Boolean?> = _feedback.asStateFlow()
    
    private val _wrongAnswers = MutableStateFlow<Set<Int>>(emptySet())
    val wrongAnswers: StateFlow<Set<Int>> = _wrongAnswers.asStateFlow()

    private val _levelProgress = MutableStateFlow(0)
    val levelProgress: StateFlow<Int> = _levelProgress.asStateFlow()
    private val maxQuestionsPerLevel = 5

    fun startGame(mode: GameMode, level: Int = 1) {
        _gameState.value = mode
        if (mode == GameMode.PLAYING_LEVEL) {
            _currentLevel.value = level
            _levelProgress.value = 0
        } else if (mode == GameMode.PLAYING_ENDLESS) {
            _score.value = 0
            _lives.value = 3
        }
        generateQuestion()
    }

    fun goToMenu() {
        _gameState.value = GameMode.MAIN_MENU
        _feedback.value = null
    }

    fun goToLevelSelect() {
        _gameState.value = GameMode.LEVEL_SELECT
    }

    fun goToOperationSelect() {
        _gameState.value = GameMode.OPERATION_SELECT
    }

    fun selectOperationAndGoToLevels(op: String) {
        _selectedOperation.value = op
        _gameState.value = GameMode.LEVEL_SELECT
    }

    fun goBack() {
        when (_gameState.value) {
            GameMode.LEVEL_SELECT -> _gameState.value = GameMode.OPERATION_SELECT
            GameMode.PLAYING_LEVEL -> _gameState.value = GameMode.LEVEL_SELECT
            GameMode.OPERATION_SELECT -> _gameState.value = GameMode.MAIN_MENU
            else -> goToMenu()
        }
    }
    
    fun clearFeedback() {
        if (_feedback.value == false) {
            _feedback.value = null
        }
    }

    private fun generateQuestion() {
        _feedback.value = null
        _wrongAnswers.value = emptySet()
        val levelOrScore = if (_gameState.value == GameMode.PLAYING_LEVEL) _currentLevel.value else (_score.value / 3) + 1
        
        val op = if (_gameState.value == GameMode.PLAYING_ENDLESS) {
            val ops = mutableListOf("+", "-")
            if (levelOrScore >= 3) ops.add("*")
            if (levelOrScore >= 5) ops.add("/")
            ops.random()
        } else {
            _selectedOperation.value ?: "+"
        }

        var a = 0
        var b = 0
        var answer = 0

        if (op == "*") {
            a = Random.nextInt(1, 10 + (levelOrScore / 2))
            b = Random.nextInt(1, 10 + (levelOrScore / 2))
            answer = a * b
        } else if (op == "/") {
            b = Random.nextInt(1, 10 + (levelOrScore / 2))
            answer = Random.nextInt(1, 10 + (levelOrScore / 2))
            a = b * answer
        } else {
            val maxA = 5 + levelOrScore * 3
            val maxB = 5 + levelOrScore * 3
            a = Random.nextInt(1, maxA)
            b = Random.nextInt(1, maxB)

            if (op == "-" && b > a) {
                val temp = a
                a = b
                b = temp
            }
            answer = if (op == "+") a + b else a - b
        }

        val options = mutableSetOf<Int>()
        options.add(answer)
        while(options.size < 4) {
            val offset = Random.nextInt(-10, 11)
            if (offset != 0 && answer + offset >= 0) {
                options.add(answer + offset)
            }
        }

        _currentQuestion.value = MathQuestion(a, b, op, options.toList().shuffled(), answer)
    }

    fun answerQuestion(answer: Int) {
        if (_feedback.value == true) return
        if (_wrongAnswers.value.contains(answer)) return
        
        val correct = answer == _currentQuestion.value?.correctAnswer
        _feedback.value = correct

        if (correct) {
            AudioSynth.playSuccess()
            _coins.value += 10
            prefs.edit().putInt("coins", _coins.value).apply()
            
            if (_gameState.value == GameMode.PLAYING_ENDLESS) {
                _score.value += 1
            } else if (_gameState.value == GameMode.PLAYING_LEVEL) {
                _levelProgress.value += 1
            }
        } else {
            AudioSynth.playError()
            _wrongAnswers.value = _wrongAnswers.value + answer
            if (_gameState.value == GameMode.PLAYING_ENDLESS) {
                _lives.value -= 1
                if (_lives.value <= 0) {
                    _gameState.value = GameMode.GAME_OVER
                }
            }
        }
    }

    fun nextQuestionOrLevel() {
        if (_gameState.value == GameMode.PLAYING_LEVEL) {
            if (_levelProgress.value >= maxQuestionsPerLevel) {
                val op = _selectedOperation.value ?: "+"
                val currentMax = _maxLevelUnlocked.value[op] ?: 1
                if (_currentLevel.value >= currentMax) {
                    _maxLevelUnlocked.value = _maxLevelUnlocked.value.toMutableMap().apply { this[op] = _currentLevel.value + 1 }
                    prefs.edit().putInt("unlocked_$op", _currentLevel.value + 1).apply()
                }
                _gameState.value = GameMode.VICTORY
            } else {
                generateQuestion()
            }
        } else {
            if (_lives.value <= 0) {
                _gameState.value = GameMode.GAME_OVER
            } else {
                generateQuestion()
            }
        }
    }
}

// --- Main UI ---
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                App()
            }
        }
    }
}

@Composable
fun App() {
    val viewModel: GameViewModel = viewModel()
    val gameState by viewModel.gameState.collectAsState()
    
    Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            Image(
                painter = painterResource(id = R.drawable.img_background_pattern_1780114039076),
                contentDescription = "Background",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                alpha = 0.5f // Fade slightly so text remains readable
            )
            Crossfade(targetState = gameState, label = "Screen Transition") { state ->
                when (state) {
                    GameMode.MAIN_MENU -> MainMenuScreen(viewModel)
                    GameMode.OPERATION_SELECT -> OperationSelectScreen(viewModel)
                    GameMode.LEVEL_SELECT -> LevelSelectScreen(viewModel)
                    GameMode.PLAYING_LEVEL, GameMode.PLAYING_ENDLESS -> GameScreen(viewModel)
                    GameMode.GAME_OVER -> GameOverScreen(viewModel)
                    GameMode.VICTORY -> VictoryScreen(viewModel)
                }
            }
        }
    }
}

@Composable
fun MainMenuScreen(viewModel: GameViewModel) {
    val coins by viewModel.coins.collectAsState()
    
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Face, contentDescription = "Face", tint = Color.White, modifier = Modifier.size(120.dp))
        Spacer(modifier = Modifier.height(16.dp))
        Text("KIDS MATH", fontSize = 56.sp, fontWeight = FontWeight.Black, color = ImmersiveTextDark)
        Spacer(modifier = Modifier.height(8.dp))
        CoinDisplay(coins)
        
        Spacer(modifier = Modifier.height(64.dp))
        
        BigPlayButton("MATHS MENU", KidsGreenBtn, KidsGreenBtnDark) { viewModel.goToOperationSelect() }
        Spacer(modifier = Modifier.height(24.dp))
        BigPlayButton("ENDLESS MODE", KidsBlueBtn, KidsBlueBtnDark) { viewModel.startGame(GameMode.PLAYING_ENDLESS) }
    }
}

@Composable
fun BigPlayButton(text: String, containerColor: Color, shadowColor: Color, onClick: () -> Unit) {
    Immersive3DButton(
        text = text,
        containerColor = containerColor,
        shadowColor = shadowColor,
        modifier = Modifier.fillMaxWidth(0.8f).height(80.dp),
        fontSize = 28.sp,
        onClick = onClick
    )
}

@Composable
fun CoinDisplay(coins: Int) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(ImmersiveWhiteAlpha, RoundedCornerShape(20.dp))
            .border(2.dp, Color.White, RoundedCornerShape(20.dp))
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Icon(Icons.Default.Star, contentDescription = "Coins", tint = Color(0xFFF59E0B), modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text("$coins", color = ImmersiveTextDark, fontWeight = FontWeight.Black, fontSize = 20.sp)
    }
}

@Composable
fun LevelSelectScreen(viewModel: GameViewModel) {
    val unlockedMap by viewModel.maxLevelUnlocked.collectAsState()
    val selectedOp by viewModel.selectedOperation.collectAsState()
    val unlocked = selectedOp?.let { unlockedMap[it] } ?: 1
    
    Column(modifier = Modifier.fillMaxSize()) {
        TopBar(viewModel, title = "Select Level")
        
        Spacer(modifier = Modifier.height(24.dp))
        
        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
            columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(4),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).weight(1f),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            items(50) { index ->
                val level = index + 1
                val isUnlocked = level <= unlocked
                
                Immersive3DButton(
                    text = level.toString(),
                    containerColor = if (isUnlocked) KidsBlueBtn else Color.LightGray,
                    shadowColor = if (isUnlocked) KidsBlueBtnDark else Color.Gray,
                    modifier = Modifier.aspectRatio(1f),
                    fontSize = 24.sp,
                    onClick = { if (isUnlocked) viewModel.startGame(GameMode.PLAYING_LEVEL, level) }
                )
            }
        }
    }
}

@Composable
fun TopBar(viewModel: GameViewModel, title: String? = null) {
    val coins by viewModel.coins.collectAsState()
    val mode by viewModel.gameState.collectAsState()
    val lives by viewModel.lives.collectAsState()
    val score by viewModel.score.collectAsState()
    val level by viewModel.currentLevel.collectAsState()
    val progress by viewModel.levelProgress.collectAsState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (mode != GameMode.MAIN_MENU) {
                IconButton(
                    onClick = { viewModel.goBack() },
                    modifier = Modifier
                        .size(48.dp)
                        .background(ImmersiveWhiteAlpha, CircleShape)
                        .border(2.dp, Color.White, CircleShape)
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = ImmersiveTextDark)
                }
                Spacer(modifier = Modifier.width(8.dp))
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .background(ImmersiveWhiteAlpha, RoundedCornerShape(20.dp))
                    .border(2.dp, Color.White, RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Default.Star, contentDescription = "Level", tint = Color(0xFFF97316), modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(8.dp))
                
                if (title != null) {
                    Text(title.uppercase(), fontSize = 16.sp, fontWeight = FontWeight.Black, color = ImmersiveTextDark)
                } else if (mode == GameMode.PLAYING_ENDLESS) {
                    Text("SCORE: $score", fontSize = 16.sp, fontWeight = FontWeight.Black, color = ImmersiveTextDark)
                } else if (mode == GameMode.PLAYING_LEVEL) {
                    Text("LEVEL ${level.toString().padStart(2, '0')}", fontSize = 16.sp, fontWeight = FontWeight.Black, color = ImmersiveTextDark)
                }
            }
        }

        CoinDisplay(coins)
    }
    
    if (mode == GameMode.PLAYING_LEVEL) {
        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(16.dp)
                    .background(Color(0x331E3A8A), RoundedCornerShape(8.dp))
                    .border(2.dp, Color.White, RoundedCornerShape(8.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress / 5f)
                        .fillMaxHeight()
                        .background(KidsGreenBtn, RoundedCornerShape(8.dp))
                )
            }
        }
    } else if (mode == GameMode.PLAYING_ENDLESS) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.Center) {
            repeat(3) { index ->
                Icon(
                    Icons.Default.Favorite,
                    contentDescription = "Life",
                    tint = if (index < lives) KidsPink else Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(24.dp).padding(horizontal = 4.dp)
                )
            }
        }
    }
}

@Composable
fun GameScreen(viewModel: GameViewModel) {
    val question by viewModel.currentQuestion.collectAsState()
    val feedback by viewModel.feedback.collectAsState()
    val wrongAnswers by viewModel.wrongAnswers.collectAsState()

    LaunchedEffect(feedback) {
        if (feedback == true) {
            delay(1200)
            viewModel.nextQuestionOrLevel()
        } else if (feedback == false) {
            delay(1000)
            viewModel.clearFeedback()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopBar(viewModel)
        
        Spacer(modifier = Modifier.weight(0.5f))

        question?.let { q ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .aspectRatio(1f)
                    .background(Color.White, RoundedCornerShape(40.dp))
                    .padding(bottom = 8.dp) // Space for bottom border area visually
                    .background(Color.White, RoundedCornerShape(40.dp)) // inner draw to clip properly
                    .border(BorderStroke(8.dp, Color(0xFFE2E8F0)), RoundedCornerShape(40.dp)), // slate-200 border
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    val operatorName = when(q.operator) {
                        "+" -> "ADD (+)"
                        "-" -> "SUBSTRACT (-)"
                        "*" -> "MULTIPLICATION (×)"
                        "/" -> "DIVISION (/)"
                        else -> "Math"
                    }
                    Text(operatorName.uppercase(), color = ImmersiveGrayText, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 2.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val displayOp = when(q.operator) {
                            "*" -> "×"
                            "/" -> "÷"
                            else -> q.operator
                        }
                        Text(q.a.toString(), fontSize = 80.sp, fontWeight = FontWeight.Black, color = ImmersiveTextDark)
                        Text(" $displayOp ", fontSize = 80.sp, fontWeight = FontWeight.Black, color = ImmersiveBlueText)
                        Text(q.b.toString(), fontSize = 80.sp, fontWeight = FontWeight.Black, color = ImmersiveTextDark)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("= ?", fontSize = 32.sp, fontWeight = FontWeight.Black, color = ImmersiveBlueText)
                }
            }
        }

        Spacer(modifier = Modifier.weight(0.5f))

        Column(modifier = Modifier.fillMaxWidth().height(60.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            AnimatedVisibility(
                visible = feedback != null,
                enter = scaleIn(spring(stiffness = Spring.StiffnessLow)),
                exit = fadeOut()
            ) {
                Text(
                    if (feedback == true) "Awesome! +10" else "Oops!",
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Black,
                    color = if (feedback == true) KidsGreenBtn else KidsPink
                )
            }
        }

        Spacer(modifier = Modifier.weight(0.5f))

        question?.let { q ->
            Column(modifier = Modifier.padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OptionButton(q.options[0], feedback, q.correctAnswer, DefaultButtonColors.Red, wrongAnswers.contains(q.options[0]), modifier = Modifier.weight(1f)) { viewModel.answerQuestion(q.options[0]) }
                    OptionButton(q.options[1], feedback, q.correctAnswer, DefaultButtonColors.Blue, wrongAnswers.contains(q.options[1]), modifier = Modifier.weight(1f)) { viewModel.answerQuestion(q.options[1]) }
                }
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OptionButton(q.options[2], feedback, q.correctAnswer, DefaultButtonColors.Green, wrongAnswers.contains(q.options[2]), modifier = Modifier.weight(1f)) { viewModel.answerQuestion(q.options[2]) }
                    OptionButton(q.options[3], feedback, q.correctAnswer, DefaultButtonColors.Purple, wrongAnswers.contains(q.options[3]), modifier = Modifier.weight(1f)) { viewModel.answerQuestion(q.options[3]) }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BottomBarIcon(Icons.Default.Star) // Reusing Lightbulb/star conceptually
            BottomBarIcon(Icons.Default.Refresh)
            BottomBarIcon(Icons.Default.Pause)
        }
    }
}

@Composable
fun BottomBarIcon(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Box(
        modifier = Modifier
            .size(48.dp)
            .background(ImmersiveWhiteAlpha30, CircleShape)
            .border(2.dp, Color.White, CircleShape),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = Color.White)
    }
}

data class OptionColors(val container: Color, val shadow: Color)
object DefaultButtonColors {
    val Red = OptionColors(KidsPink, KidsPinkDark)
    val Blue = OptionColors(KidsBlueBtn, KidsBlueBtnDark)
    val Green = OptionColors(KidsGreenBtn, KidsGreenBtnDark)
    val Purple = OptionColors(KidsPurple, KidsPurpleDark)
}

@Composable
fun OptionButton(option: Int, feedback: Boolean?, correctAnswer: Int, colors: OptionColors, isWrong: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val isCorrectOption = option == correctAnswer
    val (bgColor, shadowColor) = if (feedback == true) {
        if (isCorrectOption) {
            Pair(KidsGreenBtn, KidsGreenBtnDark)
        } else {
            Pair(Color.LightGray, Color.Gray)
        }
    } else if (isWrong) {
        Pair(KidsPink, KidsPinkDark)
    } else {
        Pair(colors.container, colors.shadow)
    }

    Immersive3DButton(
        text = option.toString(),
        containerColor = bgColor,
        shadowColor = shadowColor,
        modifier = modifier.height(80.dp),
        fontSize = 32.sp,
        enabled = (!isWrong && feedback != true),
        onClick = onClick
    )
}

@Composable
fun Immersive3DButton(
    text: String,
    containerColor: Color,
    shadowColor: Color,
    modifier: Modifier = Modifier,
    fontSize: androidx.compose.ui.unit.TextUnit = 32.sp,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val paddingBottom = if (isPressed && enabled) 0.dp else 8.dp
    val paddingTop = if (isPressed && enabled) 8.dp else 0.dp

    Box(
        modifier = modifier
            .padding(top = paddingTop)
            .background(shadowColor, RoundedCornerShape(24.dp))
            .clickable(interactionSource = interactionSource, indication = null, enabled = enabled, onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = paddingBottom)
                .background(containerColor, RoundedCornerShape(24.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(text, fontSize = fontSize, fontWeight = FontWeight.Black, color = Color.White)
        }
    }
}

@Composable
fun GameOverScreen(viewModel: GameViewModel) {
    val score = viewModel.score.collectAsState().value
    
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("GAME OVER!", fontSize = 60.sp, fontWeight = FontWeight.Black, color = KidsPink)
        Spacer(modifier = Modifier.height(16.dp))
        Text("You scored: $score", fontSize = 32.sp, color = ImmersiveTextDark, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(48.dp))
        BigPlayButton("PLAY AGAIN", KidsGreenBtn, KidsGreenBtnDark) { viewModel.startGame(GameMode.PLAYING_ENDLESS) }
        Spacer(modifier = Modifier.height(24.dp))
        BigPlayButton("MAIN MENU", KidsBlueBtn, KidsBlueBtnDark) { viewModel.goToMenu() }
    }
}

@Composable
fun VictoryScreen(viewModel: GameViewModel) {
    val level = viewModel.currentLevel.collectAsState().value
    
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.Star, contentDescription = "Star", tint = Color(0xFFF59E0B), modifier = Modifier.size(160.dp))
        Spacer(modifier = Modifier.height(16.dp))
        Text("LEVEL $level", fontSize = 40.sp, fontWeight = FontWeight.Bold, color = ImmersiveBlueText)
        Text("COMPLETED!", fontSize = 60.sp, fontWeight = FontWeight.Black, color = KidsGreenBtn)
        Spacer(modifier = Modifier.height(48.dp))
        BigPlayButton("NEXT LEVEL", KidsGreenBtn, KidsGreenBtnDark) { viewModel.startGame(GameMode.PLAYING_LEVEL, level + 1) }
        Spacer(modifier = Modifier.height(24.dp))
        BigPlayButton("MAIN MENU", KidsBlueBtn, KidsBlueBtnDark) { viewModel.goToMenu() }
    }
}

@Composable
fun OperationSelectScreen(viewModel: GameViewModel) {
    Column(modifier = Modifier.fillMaxSize()) {
        TopBar(viewModel, title = "SELECT OPERATION")
        
        Spacer(modifier = Modifier.weight(1f))
        
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                OperationCard(
                    title = "ADD (+)",
                    symbol = "+",
                    containerColor = KidsGreenBtn,
                    shadowColor = KidsGreenBtnDark,
                    modifier = Modifier.weight(1f)
                ) { viewModel.selectOperationAndGoToLevels("+") }
                
                OperationCard(
                    title = "SUBSTRACT (-)",
                    symbol = "-",
                    containerColor = Color(0xFFF6D060), 
                    shadowColor = Color(0xFFD6A030),
                    modifier = Modifier.weight(1f)
                ) { viewModel.selectOperationAndGoToLevels("-") }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                OperationCard(
                    title = "MULTIPLICATION (×)",
                    symbol = "×",
                    containerColor = Color(0xFFF4A261), 
                    shadowColor = Color(0xFFD48241),
                    modifier = Modifier.weight(1f)
                ) { viewModel.selectOperationAndGoToLevels("*") }
                
                OperationCard(
                    title = "DIVISION (/)",
                    symbol = "÷",
                    containerColor = KidsPurple,
                    shadowColor = KidsPurpleDark,
                    modifier = Modifier.weight(1f)
                ) { viewModel.selectOperationAndGoToLevels("/") }
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
fun OperationCard(
    title: String,
    symbol: String,
    containerColor: Color,
    shadowColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val paddingBottom = if (isPressed) 0.dp else 8.dp
    val paddingTop = if (isPressed) 8.dp else 0.dp

    Box(
        modifier = modifier
            .aspectRatio(0.85f)
            .padding(top = paddingTop)
            .background(shadowColor, RoundedCornerShape(32.dp))
            .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = paddingBottom)
                .background(containerColor, RoundedCornerShape(32.dp))
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(symbol, fontSize = 72.sp, fontWeight = FontWeight.Black, color = Color.White)
            Spacer(modifier = Modifier.height(8.dp))
            Text(title, fontSize = 16.sp, fontWeight = FontWeight.Black, color = Color.White, textAlign = TextAlign.Center)
        }
    }
}

