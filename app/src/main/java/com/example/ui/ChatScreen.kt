package com.example.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.SettingsVoice
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.data.local.ChatMessage
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    // Read state from ViewModel
    val messages by viewModel.messages.collectAsState()
    val inputText by viewModel.inputText.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val isContinuousVoiceMode by viewModel.isContinuousVoiceMode.collectAsState()
    val speechText by viewModel.speechText.collectAsState()
    val isApiKeyMissing by viewModel.isApiKeyMissing.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val customApiKey by viewModel.customApiKey.collectAsState()
    
    var showSettingsDialog by remember { mutableStateOf(false) }
    var tempApiKey by remember(customApiKey) { mutableStateOf(customApiKey) }
    
    val isListening = isContinuousVoiceMode

    val listState = rememberLazyListState()

    // Auto-scroll to latest message when history changes
    LaunchedEffect(messages.size, isLoading) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Record audio permission contract
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                viewModel.toggleContinuousVoiceMode(true)
            } else {
                viewModel.toggleContinuousVoiceMode(false)
            }
        }
    )

    // Force RTL local layout direction for Arabic
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Start
                        ) {
                            Box(contentAlignment = Alignment.BottomEnd) {
                                Image(
                                    painter = painterResource(id = R.drawable.img_ahmed_avatar),
                                    contentDescription = "صورة أحمد",
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                                // Active/Pulsing online indicator
                                val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                                val scale by infiniteTransition.animateFloat(
                                    initialValue = 0.8f,
                                    targetValue = 1.3f,
                                    animationSpec = infiniteRepeatable(
                                        animation = tween(1000, easing = FastOutSlowInEasing),
                                        repeatMode = RepeatMode.Reverse
                                    ),
                                    label = "scale"
                                )
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .scale(if (isListening) scale else 1f)
                                        .clip(CircleShape)
                                        .background(if (isListening) Color(0xFF4CAF50) else Color(0xFF8BC34A))
                                        .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "أحمد",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )
                                )
                                Text(
                                    text = when {
                                        isLoading -> "يكتب الآن..."
                                        isListening -> "يستمع إليك..."
                                        isContinuousVoiceMode -> "متصل صوتياً"
                                        else -> "نشط الآن"
                                    },
                                    style = MaterialTheme.typography.bodySmall.copy(
                                        color = if (isListening) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        fontSize = 12.sp
                                    )
                                )
                            }
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { showSettingsDialog = true },
                            modifier = Modifier.testTag("settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "إعدادات مفتاح API",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = { viewModel.clearChat() },
                            modifier = Modifier.testTag("clear_chat_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = "مسح المحادثة",
                                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp)
                    ),
                    modifier = Modifier.shadow(4.dp)
                )
            },
            containerColor = MaterialTheme.colorScheme.background,
            modifier = modifier.fillMaxSize()
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.surface,
                                MaterialTheme.colorScheme.background
                            )
                        )
                    )
            ) {
                // Warning if API Key is missing
                if (isApiKeyMissing) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "⚠️ إعداد مطلوب لمفتاح الـ API",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "يرجى إضافة مفتاح الـ GEMINI_API_KEY لتشغيل المساعد أحمد بشكل سليم. يمكنك كتابته أو لصقه هنا مباشرة:",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            
                            var inlineKeyByState by remember { mutableStateOf("") }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = inlineKeyByState,
                                    onValueChange = { inlineKeyByState = it },
                                    placeholder = { Text("لصق مفتاح الـ API هنا...", style = MaterialTheme.typography.bodyMedium) },
                                    singleLine = true,
                                    modifier = Modifier
                                        .weight(1f)
                                        .testTag("inline_api_key_input"),
                                    colors = OutlinedTextFieldDefaults.colors(
                                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Button(
                                    onClick = {
                                        if (inlineKeyByState.trim().isNotEmpty()) {
                                            viewModel.saveCustomApiKey(inlineKeyByState.trim())
                                        }
                                    },
                                    enabled = inlineKeyByState.trim().isNotEmpty(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("حفظ")
                                }
                            }
                        }
                    }
                }

                if (errorMessage != null) {
                    Text(
                        text = errorMessage ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        textAlign = TextAlign.Center
                    )
                }

                // Chat Messages Area
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (messages.isEmpty() && !isLoading) {
                        // Custom empty state
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(100.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SettingsVoice,
                                    contentDescription = "مساعد صوتي",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "أهلاً بك! أنا أحمد مساعدك الشخصي.",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "أنا ذكي، سريع الاستجابة وأعمل بشكل مستمر دون الحاجة للضغط المستمر بفضل ميزة التحدث الصوتي الذكي. جرب التحدث معي الآن!",
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                ),
                                modifier = Modifier.padding(horizontal = 16.dp)
                            )
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            contentPadding = PaddingValues(top = 16.dp, bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(messages) { message ->
                                MessageBubble(message = message)
                            }
                            if (isLoading) {
                                item {
                                    AhmedTypingIndicator()
                                }
                            }
                        }
                    }
                }

                // Bottom Panel (Text Input or Continuous Voice Visualizer)
                Surface(
                    tonalElevation = 8.dp,
                    shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AnimatedVisibility(
                        visible = isContinuousVoiceMode,
                        enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
                        exit = fadeOut()
                    ) {
                        // Beautiful Continuous Voice Panel
                        ContinuousVoicePanel(
                            isListening = isListening,
                            speechText = speechText,
                            onToggleOff = { viewModel.toggleContinuousVoiceMode(false) }
                        )
                    }

                    AnimatedVisibility(
                        visible = !isContinuousVoiceMode,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        // Traditional Chat Bar
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Voice Activation Button
                            IconButton(
                                onClick = {
                                    val hasPermission = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.RECORD_AUDIO
                                    ) == PackageManager.PERMISSION_GRANTED

                                    if (hasPermission) {
                                        viewModel.toggleContinuousVoiceMode(true)
                                    } else {
                                        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .testTag("activate_voice_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Mic,
                                    contentDescription = "تشغيل التحدث المستمر",
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // Text input field
                            TextField(
                                value = inputText,
                                onValueChange = { viewModel.setInputText(it) },
                                placeholder = { Text("اكتب رسالة لأحمد...") },
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(24.dp))
                                    .testTag("text_input_field"),
                                colors = TextFieldDefaults.colors(
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent,
                                    disabledIndicatorColor = Color.Transparent
                                ),
                                keyboardOptions = KeyboardOptions(
                                    imeAction = ImeAction.Send
                                ),
                                keyboardActions = KeyboardActions(
                                    onSend = { viewModel.sendMessage() }
                                )
                            )

                            Spacer(modifier = Modifier.width(8.dp))

                            // Send Button
                            IconButton(
                                onClick = { viewModel.sendMessage() },
                                enabled = inputText.isNotBlank(),
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (inputText.isNotBlank()) MaterialTheme.colorScheme.primary 
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                                    .testTag("send_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "إرسال",
                                    tint = if (inputText.isNotBlank()) MaterialTheme.colorScheme.onPrimary 
                                           else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSettingsDialog) {
        AlertDialog(
            onDismissRequest = { showSettingsDialog = false },
            title = {
                Text(
                    text = "إعدادات مفتاح الـ API",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Right
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "قم بإدخال مفتاح الـ Gemini API لتفعيل محادثات المساعد أحمد النصية والصوتية الحية:",
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = tempApiKey,
                        onValueChange = { tempApiKey = it },
                        placeholder = { Text("AIzaSy...", style = MaterialTheme.typography.bodyMedium) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("api_key_settings_input"),
                        shape = RoundedCornerShape(8.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "إذا تركت هذا الحقل فارغاً، سيتم محاولة استخدام المفتاح الافتراضي للمشروع.",
                        style = MaterialTheme.typography.bodySmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                        textAlign = TextAlign.Right,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.saveCustomApiKey(tempApiKey.trim())
                        showSettingsDialog = false
                    }
                ) {
                    Text("حفظ")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showSettingsDialog = false }
                ) {
                    Text("إلغاء")
                }
            }
        )
    }
}

@Composable
fun MessageBubble(message: ChatMessage) {
    val bubbleColor = if (message.isUser) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }

    val textColor = if (message.isUser) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    val bubbleShape = if (message.isUser) {
        RoundedCornerShape(topStart = 16.dp, topEnd = 4.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
    } else {
        RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp)
    }

    val alignment = if (message.isUser) Alignment.End else Alignment.Start

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = alignment
    ) {
        Row(
            verticalAlignment = Alignment.Top,
            horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start,
            modifier = Modifier.fillMaxWidth(0.85f)
        ) {
            if (!message.isUser) {
                Image(
                    painter = painterResource(id = R.drawable.img_ahmed_avatar),
                    contentDescription = "صورة أحمد",
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(28.dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            }

            Surface(
                color = bubbleColor,
                shape = bubbleShape,
                shadowElevation = 1.dp
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Text(
                        text = message.text,
                        color = textColor,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            lineHeight = 22.sp,
                            fontSize = 15.sp
                        )
                    )
                }
            }
        }
    }
}

@Composable
fun AhmedTypingIndicator() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Image(
            painter = painterResource(id = R.drawable.img_ahmed_avatar),
            contentDescription = "صورة أحمد",
            modifier = Modifier
                .padding(end = 8.dp)
                .size(28.dp)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )

        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 16.dp),
            modifier = Modifier.width(80.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.padding(vertical = 12.dp, horizontal = 16.dp)
            ) {
                val infiniteTransition = rememberInfiniteTransition(label = "dots")
                val dots = listOf(0, 1, 2)
                
                dots.forEach { index ->
                    val delay = index * 150
                    val scale by infiniteTransition.animateFloat(
                        initialValue = 0.4f,
                        targetValue = 1f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(450, delayMillis = delay, easing = FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "dot_scale_$index"
                    )

                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .scale(scale)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                    )
                }
            }
        }
    }
}

@Composable
fun ContinuousVoicePanel(
    isListening: Boolean,
    speechText: String,
    onToggleOff: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(top = 24.dp, bottom = 32.dp, start = 24.dp, end = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "الوضع الصوتي المستمر نشط",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                fontSize = 18.sp
            ),
            textAlign = TextAlign.Center
        )
        
        Text(
            text = "أحمد يستمع ويتحدث معك دون انقطاع ودون الحاجة لأي ضغط",
            style = MaterialTheme.typography.bodySmall.copy(
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            ),
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
        )

        // Pulsing radar / voice visualizer
        Box(
            modifier = Modifier
                .size(140.dp)
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            val infiniteTransition = rememberInfiniteTransition(label = "pulse_radar")
            
            val wave1 by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1800, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "wave1"
            )
            
            val wave2 by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1800, delayMillis = 600, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "wave2"
            )

            val wave3 by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1800, delayMillis = 1200, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "wave3"
            )

            val primaryColor = MaterialTheme.colorScheme.primary

            Canvas(modifier = Modifier.fillMaxSize()) {
                if (isListening) {
                    drawCircle(
                        color = primaryColor.copy(alpha = 1f - wave1),
                        radius = size.minDimension / 2 * wave1,
                        style = Stroke(width = 3.dp.toPx())
                    )
                    drawCircle(
                        color = primaryColor.copy(alpha = 1f - wave2),
                        radius = size.minDimension / 2 * wave2,
                        style = Stroke(width = 3.dp.toPx())
                    )
                    drawCircle(
                        color = primaryColor.copy(alpha = 1f - wave3),
                        radius = size.minDimension / 2 * wave3,
                        style = Stroke(width = 3.dp.toPx())
                    )
                }
            }

            IconButton(
                onClick = onToggleOff,
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.error)
                    .shadow(4.dp, CircleShape)
                    .testTag("deactivate_voice_button")
            ) {
                Icon(
                    imageVector = Icons.Default.MicOff,
                    contentDescription = "تعطيل الوضع الصوتي",
                    tint = MaterialTheme.colorScheme.onError,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Live recognized speech card
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
            ),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(16.dp)
            ) {
                Icon(
                    imageVector = if (isListening) Icons.Default.VolumeUp else Icons.Default.SettingsVoice,
                    contentDescription = "حالة الاستماع",
                    tint = if (isListening) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = speechText.ifBlank { "أصغي إليك، تفضل بالحديث..." },
                    style = MaterialTheme.typography.bodyMedium.copy(
                        color = if (speechText.isNotBlank()) MaterialTheme.colorScheme.onSurface 
                               else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        fontSize = 15.sp,
                        fontWeight = if (speechText.isNotBlank()) FontWeight.Medium else FontWeight.Normal
                    ),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
