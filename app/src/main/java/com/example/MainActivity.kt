package com.example

import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.OtpAccount
import com.example.ui.OtpViewModel
import com.example.ui.theme.*
import com.example.util.Base32
import com.example.util.TotpGenerator
import com.example.util.QrCodeDecoder
import java.util.Locale
import android.net.Uri
import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.compose.ui.viewinterop.AndroidView
import java.io.InputStream
import java.util.concurrent.Executors

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val viewModel: OtpViewModel by viewModels {
                    OtpViewModel.Factory(application)
                }
                NoppoAuthenticatorApp(viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NoppoAuthenticatorApp(viewModel: OtpViewModel) {
    val filteredAccounts by viewModel.filteredAccounts.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val currentTimeMs by viewModel.currentTimeMs.collectAsStateWithLifecycle()

    var showAddDialog by remember { mutableStateOf(false) }
    var accountToEdit by remember { mutableStateOf<OtpAccount?>(null) }
    var showBackupPanel by remember { mutableStateOf(false) }
    var toastMessage by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val keyboardController = LocalSoftwareKeyboardController.current

    // Trigger local standard toasts for copy feedback
    LaunchedEffect(toastMessage) {
        toastMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            toastMessage = null
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .navigationBarsPadding()
                    .testTag("add_account_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "認証プロファイルの追加", modifier = Modifier.size(28.dp))
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .statusBarsPadding()
        ) {
            // Header Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        text = "Noppo",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = "Authenticator",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { showBackupPanel = !showBackupPanel },
                        modifier = Modifier.testTag("backup_settings_button")
                    ) {
                        Icon(
                            imageVector = if (showBackupPanel) Icons.Default.Close else Icons.Default.Settings,
                            contentDescription = "バックアップと復元設定",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Global Rotation Status Panel
            GlobalSyncDashboardCard(currentTimeMs = currentTimeMs)

            // Search Filter
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { viewModel.searchQuery.value = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .testTag("search_field"),
                placeholder = { Text("認証キーを検索...", color = MaterialTheme.colorScheme.outline) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "検索アイコン") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.searchQuery.value = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "検索条件をクリア")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface
                )
            )

            // Dynamic Panels: Backup & Restore, or OTP List
            AnimatedVisibility(
                visible = showBackupPanel,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                BackupRestorePanel(
                    viewModel = viewModel,
                    onToastMessage = { toastMessage = it },
                    onClose = { showBackupPanel = false }
                )
            }

            if (!showBackupPanel) {
                if (filteredAccounts.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = "アカウントなし",
                                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                                modifier = Modifier.size(96.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = if (searchQuery.isBlank()) "登録済みのキーがありません" else "該当するキーが見つかりません",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (searchQuery.isBlank()) {
                                    "右下の「+」ボタンからシークレットキーを追加するか、設定よりバックアップの復元を行ってください。"
                                } else {
                                    "スペルを確認するか、別の発行者名で検索し直してください。"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.outline,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 24.dp)
                            )
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .testTag("accounts_list"),
                        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(filteredAccounts, key = { it.id }) { item ->
                            OtpAccountCard(
                                account = item,
                                currentTimeMs = currentTimeMs,
                                onCopyClick = { pin ->
                                    try {
                                        clipboardManager.setText(AnnotatedString(pin))
                                        toastMessage = "コードをクリップボードにコピーしました: $pin"
                                    } catch (e: Exception) {
                                        Log.e("Noppo", "Failed to write clipboard", e)
                                        toastMessage = "コピーに失敗しました"
                                    }
                                },
                                onToggleFavorite = { viewModel.toggleFavorite(item) },
                                onEditClick = { accountToEdit = item },
                                onDeleteClick = { viewModel.deleteAccount(item) },
                                onIncrementCounter = { viewModel.incrementHotpCounter(item) }
                            )
                        }
                    }
                }
            }
        }
    }

    // Modal adding form Dialog
    if (showAddDialog) {
        AddAccountDialog(
            viewModel = viewModel,
            onDismiss = { showAddDialog = false },
            onSave = { record ->
                viewModel.addAccount(
                    accountName = record.accountName,
                    secretKey = record.secretKey,
                    issuer = record.issuer,
                    algo = record.algo,
                    digits = record.digits,
                    period = record.period,
                    customColorIndex = record.customColorIndex,
                    type = record.type,
                    counter = record.counter
                )
                showAddDialog = false
                toastMessage = "${record.issuer} のセキュリティプロファイルを追加しました"
            }
        )
    }

    // Modal editing form Dialog
    accountToEdit?.let { account ->
        EditAccountDialog(
            account = account,
            onDismiss = { accountToEdit = null },
            onSave = { updated ->
                viewModel.updateAccount(updated)
                accountToEdit = null
                toastMessage = "プロファイルを更新しました"
            }
        )
    }
}

/**
 * Global time synchronization panel showing the remaining 30-sec countdown in high elegance.
 */
@Composable
fun GlobalSyncDashboardCard(currentTimeMs: Long) {
    val period = 30
    val elapsedSeconds = (currentTimeMs / 1000) % period
    val secondsLeft = period - elapsedSeconds
    val progress = secondsLeft.toFloat() / period

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "キー同期ステータス",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "自動時刻同期が有効",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "二段階認証用のワンタイムパスワード（TOTP）は、30秒ごとに自動的に再生成され、最新のセキュリティを維持します。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    lineHeight = 16.sp
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Beautiful Liquid countdown circle
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(72.dp)
            ) {
                val animatedProgress by animateFloatAsState(
                    targetValue = progress,
                    label = "circularProgress"
                )

                Canvas(modifier = Modifier.fillMaxSize()) {
                    // Gray trace track
                    drawCircle(
                        color = Color(0x1F0040E0),
                        style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                    )
                    // Cobalt leading sweep
                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = listOf(LuminousPrimary, LuminousSecondaryContainer, LuminousPrimary)
                        ),
                        startAngle = -90f,
                        sweepAngle = 360f * animatedProgress,
                        useCenter = false,
                        style = Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$secondsLeft",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = if (secondsLeft <= 5) LuminousError else MaterialTheme.colorScheme.onBackground
                    )
                    Text(
                        text = "秒(残)",
                        style = MaterialTheme.typography.labelSmall,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }
    }
}

/**
 * Fully functional design of individual TOTP authentication factor cards.
 */
@Composable
fun OtpAccountCard(
    account: OtpAccount,
    currentTimeMs: Long,
    onCopyClick: (String) -> Unit,
    onToggleFavorite: () -> Unit,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onIncrementCounter: () -> Unit = {}
) {
    val isHotp = account.type.equals("HOTP", ignoreCase = true)

    // Dynamic key regenerator calculated locally based on time vs manual counter trigger
    val code = if (isHotp) {
        remember(account.secretKey, account.counter) {
            TotpGenerator.generateOtpWithCounter(
                secret = account.secretKey,
                counter = account.counter,
                digits = account.digits,
                algorithm = account.algo
            )
        }
    } else {
        remember(account.secretKey, currentTimeMs / 1000 / account.period) {
            TotpGenerator.generateTotp(
                secret = account.secretKey,
                timeMs = currentTimeMs,
                periodSec = account.period,
                digits = account.digits,
                algorithm = account.algo
            )
        }
    }

    val formattedCode = remember(code) {
        if (code.length == 6) {
            "${code.take(3)}   ${code.drop(3)}"
        } else if (code.length == 8) {
            "${code.take(4)}   ${code.drop(4)}"
        } else {
            code
        }
    }

    val glowColor = remember(account.customLabelColorIndex) {
        when (account.customLabelColorIndex) {
            1 -> LuminousSecondaryContainer
            2 -> LuminousTertiary
            3 -> LuminousError
            else -> LuminousPrimary
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("account_card_${account.id}")
            .clickable { onCopyClick(code) },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.5.dp, glowColor.copy(alpha = 0.35f)), // glow beveled highlight
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Interactive Brand Identifier Icon Circle
                PublisherBadge(issuer = account.issuer, colorIndex = account.customLabelColorIndex)

                Spacer(modifier = Modifier.width(16.dp))

                // Metadata Core Values (Issuer, account reference)
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = account.issuer,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = glowColor.copy(alpha = 0.15f),
                            modifier = Modifier.padding(2.dp)
                        ) {
                            Text(
                                text = if (isHotp) "HOTP-${account.counter}" else "TOTP",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = glowColor,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                        if (account.isFavorite) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = "Favorite Active",
                                tint = LuminousPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                    Text(
                        text = account.accountName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Interactive Tiny Toolbar Panel
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onToggleFavorite) {
                        Icon(
                            imageVector = if (account.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "お気に入りの切り替え",
                            tint = if (account.isFavorite) LuminousPrimary else MaterialTheme.colorScheme.outline
                        )
                    }

                    IconButton(onClick = onEditClick) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = "プロファイルの編集",
                            tint = MaterialTheme.colorScheme.outline
                        )
                    }

                    IconButton(onClick = onDeleteClick) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "プロファイルの削除",
                            tint = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            // Beautiful numeric rendering
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formattedCode,
                    fontSize = 34.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp,
                    modifier = Modifier.testTag("otp_code_${account.id}")
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isHotp) {
                        IconButton(onClick = onIncrementCounter) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "カウンター値を更新",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    IconButton(onClick = { onCopyClick(code) }) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "クリップボードにコピー",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (!isHotp) {
                // Shrinking Linear liquid bar indicators at the card bottom for TOTP
                val secondsLeft = account.period - ((currentTimeMs / 1000) % account.period)
                val progress = secondsLeft.toFloat() / account.period
                val animatedHorizontalProgress by animateFloatAsState(
                    targetValue = progress,
                    label = "linearState"
                )

                LinearProgressIndicator(
                    progress = { animatedHorizontalProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)),
                    color = if (secondsLeft <= 5) LuminousError else glowColor,
                    trackColor = Color(0x0C000000)
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(glowColor.copy(alpha = 0.5f))
                )
            }
        }
    }
}

/**
 * Beautiful circle brand insignia generator. Built to identify main players cleanly.
 */
@Composable
fun PublisherBadge(issuer: String, colorIndex: Int) {
    val cleanedIssuer = issuer.lowercase().trim()
    val (backColor, labelLetter) = when {
        cleanedIssuer.contains("google") -> Color(0xFFEA4335) to "G"
        cleanedIssuer.contains("github") -> Color(0xFF24292E) to "GH"
        cleanedIssuer.contains("microsoft") -> Color(0xFF00A4EF) to "MS"
        cleanedIssuer.contains("aws") -> Color(0xFFFF9900) to "AWS"
        cleanedIssuer.contains("slack") -> Color(0xFF4A154B) to "SL"
        cleanedIssuer.contains("discord") -> Color(0xFF5865F2) to "DS"
        cleanedIssuer.contains("facebook") -> Color(0xFF1877F2) to "FB"
        cleanedIssuer.contains("noppo") -> Color(0xFF00CCF9) to "N"
        else -> {
            val defaultColorOptions = listOf(
                LuminousPrimary,
                LuminousSecondary,
                LuminousTertiary,
                Color(0xFFE06600)
            )
            defaultColorOptions.getOrElse(colorIndex % defaultColorOptions.size) { LuminousPrimary } to issuer.take(2).uppercase()
        }
    }

    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(backColor),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = labelLetter,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
    }
}

/**
 * Advanced core Backup & Local Sync management pane.
 */
@Composable
fun BackupRestorePanel(
    viewModel: OtpViewModel,
    onToastMessage: (String) -> Unit,
    onClose: () -> Unit
) {
    var exportOutputText by remember { mutableStateOf("") }
    var importInputText by remember { mutableStateOf("") }

    val clipboardManager = LocalClipboardManager.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .testTag("backup_restore_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "ローカル電子データのバックアップ & 復元",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.sp
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Backup Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        val payload = viewModel.exportBackup()
                        exportOutputText = payload
                        try {
                            clipboardManager.setText(AnnotatedString(payload))
                            onToastMessage("バックアップデータをクリップボードにコピーしました！安全な場所に保存してください。")
                        } catch (e: Exception) {
                            Log.e("BackupRestorePanel", "Clipboard write failed safely", e)
                            onToastMessage("バックアップ出力に成功しました。手動で枠内をコピーしてください。")
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("export_backup_button"),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Share, contentDescription = "エクスポート")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("エクスポート", style = MaterialTheme.typography.labelSmall)
                }

                Button(
                    onClick = {
                        if (importInputText.isNotBlank()) {
                            val result = viewModel.importBackup(importInputText)
                            result.fold(
                                onSuccess = { count ->
                                    onToastMessage("インポート完了: $count 件のプロファイルを正常に復元しました！")
                                    importInputText = ""
                                    onClose()
                                },
                                onFailure = { error ->
                                    onToastMessage("バックアップ解析エラー: ${error.localizedMessage}")
                                }
                            )
                        } else {
                            onToastMessage("下のフォームに有効なバックアップ用JSONテキストを入力してください。")
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("import_backup_button"),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.ContentPaste, contentDescription = "インポート")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("インポート", style = MaterialTheme.typography.labelSmall)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Output generated box
            if (exportOutputText.isNotEmpty()) {
                Text(
                    text = "出力されたバックアップデータ (これをコピーして保存します):",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
                OutlinedTextField(
                    value = exportOutputText,
                    onValueChange = {},
                    readOnly = true,
                    maxLines = 4,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                        .padding(top = 4.dp),
                    shape = RoundedCornerShape(8.dp),
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // Paste Input area
            Text(
                text = "復元するバックアップ用JSONデータを貼り付けてください:",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = importInputText,
                onValueChange = { importInputText = it },
                placeholder = { Text("[{\"issuer\":\"...\",\"accountName\":\"...\",\"secretKey\":\"...\"}]", fontSize = 11.sp) },
                maxLines = 4,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
                    .padding(top = 4.dp)
                    .testTag("import_textarea_field"),
                shape = RoundedCornerShape(8.dp),
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
            )
        }
    }
}

data class AddRecordDetails(
    val accountName: String,
    val secretKey: String,
    val issuer: String,
    val algo: String,
    val digits: Int,
    val period: Int,
    val customColorIndex: Int,
    val type: String = "TOTP",
    val counter: Long = 0L
)

/**
 * Elegant Real-Time Camera scanner with CameraX frame parsing overlay.
 */
@Composable
fun CameraScanDialog(
    onDismiss: () -> Unit,
    onQrDetected: (String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val cameraExecutor = remember { java.util.concurrent.Executors.newSingleThreadExecutor() }

    var activeCameraProvider by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    DisposableEffect(activeCameraProvider) {
        onDispose {
            try {
                activeCameraProvider?.unbindAll()
            } catch (t: Throwable) {
                Log.e("CameraScan", "Fail unbind on dispose", t)
            }
        }
    }
    
    // Check permission
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, android.Manifest.permission.CAMERA) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
    }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasCameraPermission = isGranted
    }
    
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(android.Manifest.permission.CAMERA)
        }
    }
    
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .height(450.dp)
                .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), RoundedCornerShape(16.dp)),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "QRコードスキャナー",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "閉じる")
                    }
                }
                
                if (hasCameraPermission) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.Black)
                    ) {
                        AndroidView(
                            factory = { ctx ->
                                val previewView = PreviewView(ctx).apply {
                                    scaleType = PreviewView.ScaleType.FILL_CENTER
                                }
                                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                                cameraProviderFuture.addListener({
                                    try {
                                        val cameraProvider = cameraProviderFuture.get()
                                        activeCameraProvider = cameraProvider
                                        
                                        val preview = Preview.Builder().build().also {
                                            it.setSurfaceProvider(previewView.surfaceProvider)
                                        }
                                        
                                        val imageAnalysis = ImageAnalysis.Builder()
                                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                            .build()
                                            
                                        imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                                            try {
                                                val image = imageProxy.image
                                                val planes = image?.planes
                                                if (planes != null && planes.isNotEmpty()) {
                                                    val yPlane = planes[0]
                                                    val yBuffer = yPlane.buffer
                                                    val width = imageProxy.width
                                                    val height = imageProxy.height
                                                    val rowStride = yPlane.rowStride
                                                    
                                                    val yArray = ByteArray(width * height)
                                                    yBuffer.rewind()
                                                    for (row in 0 until height) {
                                                        yBuffer.position(row * rowStride)
                                                        val bytesToRead = minOf(width, yBuffer.remaining())
                                                        if (bytesToRead > 0) {
                                                            yBuffer.get(yArray, row * width, bytesToRead)
                                                        }
                                                    }
                                                    
                                                    val decoded = QrCodeDecoder.decodeYuv(yArray, width, height)
                                                    if (decoded != null) {
                                                        ContextCompat.getMainExecutor(ctx).execute {
                                                            onQrDetected(decoded)
                                                            onDismiss()
                                                        }
                                                    }
                                                }
                                            } catch (t: Throwable) {
                                                Log.e("CameraScan", "YUV decoding error", t)
                                            } finally {
                                                imageProxy.close()
                                            }
                                        }
                                        
                                        cameraProvider.unbindAll()
                                        cameraProvider.bindToLifecycle(
                                            lifecycleOwner,
                                            CameraSelector.DEFAULT_BACK_CAMERA,
                                            preview,
                                            imageAnalysis
                                        )
                                    } catch (t: Throwable) {
                                        Log.e("CameraScan", "Usecase initialization or binding error", t)
                                    }
                                }, ContextCompat.getMainExecutor(ctx))
                                previewView
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Text(
                        text = "QRコードをカメラにかざしてください。自動的にスキャンされます。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline,
                        textAlign = TextAlign.Center
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "カメラの使用許可が必要です。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/**
 * Elegant Add Profile dialogue overlay. Supports paste link autofill, camera scanning, and local imports.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAccountDialog(
    viewModel: OtpViewModel,
    onDismiss: () -> Unit,
    onSave: (AddRecordDetails) -> Unit
) {
    var uriLinkText by remember { mutableStateOf("") }
    var accountName by remember { mutableStateOf("") }
    var secretKey by remember { mutableStateOf("") }
    var issuerName by remember { mutableStateOf("") }
    var selectedAlgo by remember { mutableStateOf("SHA1") }
    var selectedDigits by remember { mutableStateOf(6) }
    var selectedPeriod by remember { mutableStateOf(30) }
    var colorIndex by remember { mutableStateOf(0) }
    
    var selectedType by remember { mutableStateOf("TOTP") }
    var initialCounterText by remember { mutableStateOf("0") }
    var showCameraScan by remember { mutableStateOf(false) }

    var feedbackString by remember { mutableStateOf<String?>(null) }
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val inputStream: InputStream? = context.contentResolver.openInputStream(it)
                val bitmap = BitmapFactory.decodeStream(inputStream)
                inputStream?.close()
                if (bitmap != null) {
                    val decoded = QrCodeDecoder.decodeQrCode(bitmap)
                    if (decoded != null) {
                        uriLinkText = decoded
                        val parsed = viewModel.parseOtpUri(decoded)
                        if (parsed != null) {
                            issuerName = parsed["issuer"] ?: ""
                            accountName = parsed["accountName"] ?: ""
                            secretKey = parsed["secret"] ?: ""
                            selectedAlgo = parsed["algo"] ?: "SHA1"
                            selectedDigits = parsed["digits"]?.toIntOrNull() ?: 6
                            selectedPeriod = parsed["period"]?.toIntOrNull() ?: 30
                            selectedType = parsed["type"] ?: "TOTP"
                            val parsedCounter = parsed["counter"]?.toLongOrNull() ?: 0L
                            initialCounterText = parsedCounter.toString()
                            feedbackString = "正常にQRコード画像を読み込み、内容を自動反映しました！"
                        } else {
                            feedbackString = "QR画像からデコードされましたが、無効なOTPリンクです: $decoded"
                        }
                    } else {
                        feedbackString = "画像からQRコードを分析できませんでした。別の画像をお試しください。"
                    }
                } else {
                    feedbackString = "画像をデコードできませんでした。"
                }
            } catch (e: Exception) {
                feedbackString = "画像取得での予期せぬエラー: ${e.localizedMessage}"
            }
        }
    }

    if (showCameraScan) {
        CameraScanDialog(
            onDismiss = { showCameraScan = false },
            onQrDetected = { decoded ->
                uriLinkText = decoded
                val parsed = viewModel.parseOtpUri(decoded)
                if (parsed != null) {
                    issuerName = parsed["issuer"] ?: ""
                    accountName = parsed["accountName"] ?: ""
                    secretKey = parsed["secret"] ?: ""
                    selectedAlgo = parsed["algo"] ?: "SHA1"
                    selectedDigits = parsed["digits"]?.toIntOrNull() ?: 6
                    selectedPeriod = parsed["period"]?.toIntOrNull() ?: 30
                    selectedType = parsed["type"] ?: "TOTP"
                    initialCounterText = (parsed["counter"] ?: "0")
                    feedbackString = "正常にQRコードをスキャンし、自動反映しました！"
                } else {
                    feedbackString = "スキャン完了：認証URIの形式が正しくありません: $decoded"
                }
            }
        )
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        val scrollState = rememberScrollState()
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(20.dp))
                .testTag("add_account_dialog"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "新規プロファイルの追加",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "閉じる")
                    }
                }
                Text(
                    text = "手動入力による追加、またはQRコード/URI読み込みによって、シークレットキーと設定値を即時に自動抽出して登録できます。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 4.dp)
                )

                // Autofill link section
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "QRコードスキャン & 形式自動読込",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = uriLinkText,
                                onValueChange = { uriLinkText = it },
                                placeholder = { Text("otpauth:// リンクを貼付、または下部からスキャン", fontSize = 10.sp, maxLines = 1) },
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("dialog_uri_field"),
                                shape = RoundedCornerShape(8.dp),
                                colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Color.White)
                            )

                            Button(
                                onClick = {
                                    val contentToParse = try {
                                        uriLinkText.ifBlank {
                                            clipboardManager.getText()?.text ?: ""
                                        }
                                    } catch (e: Exception) {
                                        Log.e("AddAccountDialog", "Clipboard text retrieval failed safely", e)
                                        ""
                                    }
                                    if (contentToParse.isNotBlank()) {
                                        val parsed = viewModel.parseOtpUri(contentToParse)
                                        if (parsed != null) {
                                            issuerName = parsed["issuer"] ?: ""
                                            accountName = parsed["accountName"] ?: ""
                                            secretKey = parsed["secret"] ?: ""
                                            selectedAlgo = parsed["algo"] ?: "SHA1"
                                            selectedDigits = parsed["digits"]?.toIntOrNull() ?: 6
                                            selectedPeriod = parsed["period"]?.toIntOrNull() ?: 30
                                            selectedType = parsed["type"] ?: "TOTP"
                                            initialCounterText = (parsed["counter"] ?: "0")
                                            feedbackString = "リンクを正常に検出し自動入力しました！"
                                            uriLinkText = contentToParse
                                        } else {
                                            feedbackString = "無効な認証URLフォーマットです。"
                                        }
                                    } else {
                                        feedbackString = "クリップボードに対象コードが見つかりません。下部スキャンもお試しください。"
                                    }
                                },
                                modifier = Modifier.testTag("autofill_button"),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp)
                            ) {
                                Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("自動読込", style = MaterialTheme.typography.labelSmall)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showCameraScan = true },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(vertical = 8.dp)
                            ) {
                                Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("カメラを起動", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }

                            OutlinedButton(
                                onClick = { galleryLauncher.launch("image/*") },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(vertical = 8.dp)
                            ) {
                                Icon(Icons.Default.PhotoLibrary, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("画像から読取", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            }
                        }

                        feedbackString?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (it.contains("正常に")) LuminousPrimary else LuminousError,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }

                // OTP Type Selection Fields
                Text("認証方式 (ワンタイムパスワードタイプ)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    FilterChip(
                        selected = selectedType == "TOTP",
                        onClick = { selectedType = "TOTP" },
                        label = { Text("時間ベース (TOTP)", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    )
                    FilterChip(
                        selected = selectedType == "HOTP",
                        onClick = { selectedType = "HOTP" },
                        label = { Text("カウンターベース (HOTP)", fontSize = 11.sp, fontWeight = FontWeight.SemiBold) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    )
                }

                if (selectedType == "HOTP") {
                    Text("初期カウンター値", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = initialCounterText,
                        onValueChange = { initialCounterText = it.filter { c -> c.isDigit() } },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        placeholder = { Text("例: 0") },
                        shape = RoundedCornerShape(10.dp)
                    )
                }

                // Manual fields form
                Text("発行者 (例: GitHub, Google)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = issuerName,
                    onValueChange = { issuerName = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_issuer_field"),
                    singleLine = true,
                    placeholder = { Text("例: GitHub") },
                    shape = RoundedCornerShape(10.dp)
                )

                Text("アカウント名 (メールアドレス、ユーザー名など)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = accountName,
                    onValueChange = { accountName = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_account_name_field"),
                    singleLine = true,
                    placeholder = { Text("例: email@example.com") },
                    shape = RoundedCornerShape(10.dp)
                )

                Text("シークレットキー (Base32 キーコード)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                OutlinedTextField(
                    value = secretKey,
                    onValueChange = { secretKey = it.uppercase(Locale.US) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_secret_field"),
                    singleLine = true,
                    placeholder = { Text("例: JBSWY3DPEHPK3PXP") },
                    shape = RoundedCornerShape(10.dp)
                )

                // Preset selector color tag points
                Text("表示テーマカラーの選択", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    val colors = listOf(LuminousPrimary, LuminousSecondaryContainer, LuminousTertiary, LuminousError)
                    colors.forEachIndexed { idx, col ->
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(col)
                                .border(
                                    width = if (colorIndex == idx) 3.dp else 0.dp,
                                    color = if (colorIndex == idx) MaterialTheme.colorScheme.onBackground else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { colorIndex = idx }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = {
                        if (secretKey.trim().isBlank()) {
                            feedbackString = "シークレットキーの入力は必須です。"
                        } else {
                            onSave(
                                AddRecordDetails(
                                    accountName = accountName.trim().ifEmpty { "認証プロファイル" },
                                    secretKey = secretKey.trim().replace(" ", "").replace("-", ""),
                                    issuer = issuerName.trim().ifEmpty { "その他" },
                                    algo = selectedAlgo,
                                    digits = selectedDigits,
                                    period = selectedPeriod,
                                    customColorIndex = colorIndex,
                                    type = selectedType,
                                    counter = initialCounterText.toLongOrNull() ?: 0L
                                )
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("dialog_save_button"),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("認証キーを登録する", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }
        }
    }
}

/**
 * Standard dialogue for updating existing Profiles metadata.
 */
@Composable
fun EditAccountDialog(
    account: OtpAccount,
    onDismiss: () -> Unit,
    onSave: (OtpAccount) -> Unit
) {
    var issuerName by remember { mutableStateOf(account.issuer) }
    var accountName by remember { mutableStateOf(account.accountName) }
    var colorIndex by remember { mutableStateOf(account.customLabelColorIndex) }

    Dialog(onDismissRequest = onDismiss) {
        val scrollState = rememberScrollState()
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(18.dp))
                .testTag("edit_account_dialog"),
            shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "登録プロファイルの編集",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "閉じる")
                    }
                }

                Column {
                    Text("発行者", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = issuerName,
                        onValueChange = { issuerName = it },
                        modifier = Modifier.fillMaxWidth().testTag("edit_dialog_issuer"),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )
                }

                Column {
                    Text("アカウント名 (識別子)", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = accountName,
                        onValueChange = { accountName = it },
                        modifier = Modifier.fillMaxWidth().testTag("edit_dialog_account"),
                        singleLine = true,
                        shape = RoundedCornerShape(8.dp)
                    )
                }

                Column {
                    Text("テーマアクセントカラーの選択", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        val colors = listOf(LuminousPrimary, LuminousSecondaryContainer, LuminousTertiary, LuminousError)
                        colors.forEachIndexed { idx, col ->
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(CircleShape)
                                    .background(col)
                                    .border(
                                        width = if (colorIndex == idx) 3.dp else 0.dp,
                                        color = if (colorIndex == idx) MaterialTheme.colorScheme.onBackground else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable { colorIndex = idx }
                            )
                        }
                    }
                }

                Button(
                    onClick = {
                        onSave(
                            account.copy(
                                issuer = issuerName.trim().ifEmpty { "その他" },
                                accountName = accountName.trim().ifEmpty { "認証プロファイル" },
                                customLabelColorIndex = colorIndex
                            )
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("edit_dialog_save_button"),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text("変更内容を保存する", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(text = "Hello $name!", modifier = modifier)
}
