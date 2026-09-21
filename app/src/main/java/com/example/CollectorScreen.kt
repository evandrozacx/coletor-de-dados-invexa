package com.example

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import android.media.ToneGenerator
import android.media.AudioManager
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun CollectorScreen(
    viewModel: CollectorViewModel,
    onBack: () -> Unit
) {
    // Colors from HTML design
    val darkBg = AppColors.DarkBg
    val panelBg = AppColors.PanelBg
    val panelBgTranslucent = AppColors.PanelBgTranslucent
    val cyanAccent = AppColors.CyanAccent
    val textLight = AppColors.TextLight
    val textCyan = AppColors.TextCyan
    val textPrimary = AppColors.TextPrimary
    val greenSuccess = AppColors.GreenSuccess
    val borderDark = AppColors.BorderDark
    val textDark = AppColors.TextDark
    val footerText = AppColors.FooterText

    var barcodeInput by remember { mutableStateOf("") }
    var quantityInput by remember { mutableStateOf("1") }
    
    // Typing speed detection
    var lastKeystrokeTime by remember { mutableStateOf(0L) }
    var isManualTyping by remember { mutableStateOf(false) }

    var loteInput by remember { mutableStateOf("") }
    var validadeInput by remember { mutableStateOf("") }
    var palletInput by remember { mutableStateOf("") }

    val controleLote by viewModel.controleLote.collectAsState()
    val controleValidade by viewModel.controleValidade.collectAsState()
    val controlePalete by viewModel.controlePalete.collectAsState()
    val isTransmitting by viewModel.isTransmitting.collectAsState()

    var scanDebounceJob by remember { mutableStateOf<Job?>(null) }
    val scannerTimeoutMs = remember { viewModel.getScannerTimeoutMs() }

    fun processBarcodeSubmit(code: String) {
        val cleanCode = code.replace("\n", "").replace("\r", "").trim()
        if (cleanCode.isNotBlank()) {
            viewModel.addBarcode(
                ean = cleanCode,
                qty = if (viewModel.getAllowMultiplication()) (quantityInput.toDoubleOrNull() ?: 1.0) else 1.0,
                lote = if (controleLote) loteInput.trim().ifBlank { null } else null,
                validade = if (controleValidade) validadeInput.trim().ifBlank { null } else null,
                pallet = if (controlePalete) palletInput.trim().ifBlank { null } else null
            )
            barcodeInput = ""
            quantityInput = "1"
            loteInput = ""
            validadeInput = ""
            palletInput = ""
        }
    }

    // Dialog and Menu States
    var showConsultDialog by remember { mutableStateOf(false) }
    var consultQuery by remember { mutableStateOf("") }
    val consultFocusRequester = remember { FocusRequester() }
    var showDeleteAllConfirmDialog by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    
    var showExportSuccessDialog by remember { mutableStateOf(false) }
    var exportMessage by remember { mutableStateOf("") }

    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val exportSectionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                val res = viewModel.exportSectionToUri(context, uri)
                exportMessage = res.second
                showExportSuccessDialog = true
            }
        }
    }
    
    // Transmit validation states for confirm count with partial option
    var showTransmitValidationDialog by remember { mutableStateOf(false) }
    var showTransmitErrorDialog by remember { mutableStateOf(false) }
    var transmitErrorMessage by remember { mutableStateOf<String?>(null) }
    var enteredQuantityText by remember { mutableStateOf("") }
    var attemptCount by remember { mutableStateOf(0) }
    var validationErrorMessage by remember { mutableStateOf<String?>(null) }

    val focusRequester = remember { FocusRequester() }

    val toneGenerator = remember {
        try {
            ToneGenerator(AudioManager.STREAM_MUSIC, 100)
        } catch (e: Exception) {
            null
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            toneGenerator?.release()
        }
    }

    val isMultiplicationAllowed = viewModel.getAllowMultiplication()

    LaunchedEffect(Unit) {
        viewModel.scanEvent.collect { success ->
            if (success) {
                try {
                    toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            } else {
                Toast.makeText(context, "Produto não cadastrado!", Toast.LENGTH_SHORT).show()
                try {
                    toneGenerator?.startTone(ToneGenerator.TONE_SUP_ERROR, 350)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    LaunchedEffect(viewModel.currentSectionCode, viewModel.sectorId, viewModel.inventoryId, viewModel.operatorId) {
        viewModel.loadScannedItems()
    }

    // Pulse animation for the green dot
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(darkBg)
            .padding(16.dp)
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        // App Bar / Header with Back, Title, Transmit and Three-dots Menu
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                // Back Button
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Voltar",
                        tint = textPrimary
                    )
                }

                Column {
                    Text(
                        text = "COLETOR ATIVO",
                        color = textLight,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                    Text(
                        text = "OPERADOR: ${viewModel.operatorName.uppercase()}",
                        color = cyanAccent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "SEÇÃO: ${viewModel.currentSectionCode}",
                            color = textPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold,
                            letterSpacing = (-0.5).sp
                        )

                        // Transmit Button next to Section Number
                        Button(
                            onClick = {
                                if (isTransmitting) return@Button
                                if (viewModel.getConfirmCountWithPartial()) {
                                    attemptCount = 0
                                    enteredQuantityText = ""
                                    validationErrorMessage = null
                                    showTransmitValidationDialog = true
                                } else {
                                    coroutineScope.launch {
                                        val result = viewModel.transmitSection()
                                        if (result.first) {
                                            Toast.makeText(context, "✅ Contagem transmitida com SUCESSO!", Toast.LENGTH_LONG).show()
                                            onBack()
                                        } else {
                                            transmitErrorMessage = result.second ?: "Erro ao transmitir contagem para o servidor."
                                            showTransmitErrorDialog = true
                                        }
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = greenSuccess,
                                disabledContainerColor = greenSuccess.copy(alpha = 0.3f),
                                contentColor = textDark,
                                disabledContentColor = textDark.copy(alpha = 0.5f)
                            ),
                            enabled = viewModel.scannedItems.isNotEmpty() && !isTransmitting,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(30.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            if (isTransmitting) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(12.dp),
                                    strokeWidth = 2.dp,
                                    color = textDark
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "ENVIANDO...",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Transmitir",
                                    tint = textDark,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "TRANSMITIR",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Menu of options (Three Dots)
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Menu",
                            tint = textPrimary
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        modifier = Modifier.background(panelBg)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Consultar Código", color = textPrimary) },
                            onClick = {
                                menuExpanded = false
                                consultQuery = ""
                                showConsultDialog = true
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Baixar Arquivo da Seção (.json)", color = textPrimary) },
                            onClick = {
                                menuExpanded = false
                                exportSectionLauncher.launch("secao_${viewModel.currentSectionCode}.json")
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Apagar Toda a Coleta", color = Color(0xFFEF5350)) },
                            onClick = {
                                menuExpanded = false
                                showDeleteAllConfirmDialog = true
                            }
                        )
                    }
                }

                // Operator Avatar / Initials
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(panelBg, CircleShape)
                        .border(1.dp, borderDark, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    val initials = viewModel.operatorName.split(" ")
                        .take(2).joinToString("") { it.take(1) }.uppercase()
                    Text(text = initials, color = cyanAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Active Operator Row
        Row(
            modifier = Modifier.padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(greenSuccess.copy(alpha = alpha), CircleShape)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Operador: ", color = textLight, fontSize = 13.sp)
            Text(viewModel.operatorName, color = textPrimary, fontSize = 13.sp)
        }

        // Input Section
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            OutlinedTextField(
                value = barcodeInput,
                onValueChange = { newValue ->
                    val currentTime = System.currentTimeMillis()
                    if (newValue.contains("\n") || newValue.contains("\r")) {
                        scanDebounceJob?.cancel()
                        processBarcodeSubmit(newValue)
                    } else {
                        if (newValue.isEmpty()) {
                            isManualTyping = true
                        } else if (barcodeInput.isEmpty() && newValue.length == 1) {
                            isManualTyping = true
                        } else if (newValue.length - barcodeInput.length > 1) {
                            isManualTyping = false
                        } else {
                            val timeDiff = currentTime - lastKeystrokeTime
                            if (timeDiff > 80) {
                                isManualTyping = true
                            } else {
                                isManualTyping = false
                            }
                        }
                        lastKeystrokeTime = currentTime
                        barcodeInput = newValue
                        
                        scanDebounceJob?.cancel()
                        
                        // Auto-submit only if not typing manually (e.g. from scanner)
                        if (newValue.isNotBlank() && scannerTimeoutMs > 0 && !isManualTyping) {
                            scanDebounceJob = coroutineScope.launch {
                                delay(scannerTimeoutMs)
                                if (barcodeInput.isNotBlank()) {
                                    processBarcodeSubmit(barcodeInput)
                                }
                            }
                        }
                    }
                },
                label = { 
                    Text(
                        "Bipe Código (EAN ou SAP)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    ) 
                },
                textStyle = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 18.sp,
                    letterSpacing = 1.sp,
                    color = textCyan
                ),
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (viewModel.getKeyboardIsAlphanumeric()) KeyboardType.Text else KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        scanDebounceJob?.cancel()
                        processBarcodeSubmit(barcodeInput)
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = panelBg,
                    unfocusedContainerColor = panelBg,
                    focusedBorderColor = cyanAccent,
                    unfocusedBorderColor = cyanAccent.copy(alpha = 0.5f),
                    focusedLabelColor = cyanAccent,
                    unfocusedLabelColor = cyanAccent
                ),
                singleLine = true
            )

            if (controleLote) {
                OutlinedTextField(
                    value = loteInput,
                    onValueChange = { loteInput = it },
                    label = { Text("Lote", fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                    textStyle = TextStyle(color = textPrimary, fontSize = 16.sp),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = panelBg,
                        unfocusedContainerColor = panelBg,
                        focusedBorderColor = cyanAccent,
                        unfocusedBorderColor = cyanAccent.copy(alpha = 0.5f),
                        focusedLabelColor = cyanAccent,
                        unfocusedLabelColor = cyanAccent
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
            }

            if (controleValidade) {
                OutlinedTextField(
                    value = validadeInput,
                    onValueChange = { validadeInput = it },
                    label = { Text("Validade (ex: DD/MM/AAAA ou MM/AAAA)", fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                    textStyle = TextStyle(color = textPrimary, fontSize = 16.sp),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = panelBg,
                        unfocusedContainerColor = panelBg,
                        focusedBorderColor = cyanAccent,
                        unfocusedBorderColor = cyanAccent.copy(alpha = 0.5f),
                        focusedLabelColor = cyanAccent,
                        unfocusedLabelColor = cyanAccent
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
            }

            if (controlePalete) {
                OutlinedTextField(
                    value = palletInput,
                    onValueChange = { palletInput = it },
                    label = { Text("Palete / Pallet", fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                    textStyle = TextStyle(color = textPrimary, fontSize = 16.sp),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = panelBg,
                        unfocusedContainerColor = panelBg,
                        focusedBorderColor = cyanAccent,
                        unfocusedBorderColor = cyanAccent.copy(alpha = 0.5f),
                        focusedLabelColor = cyanAccent,
                        unfocusedLabelColor = cyanAccent
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                )
            }

            if (isMultiplicationAllowed) {
                OutlinedTextField(
                    value = quantityInput,
                    onValueChange = { quantityInput = it },
                    label = { Text("Quantidade (Multiplicador)", fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                    textStyle = TextStyle(
                        color = textPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = panelBg,
                        unfocusedContainerColor = panelBg,
                        focusedBorderColor = cyanAccent,
                        unfocusedBorderColor = cyanAccent.copy(alpha = 0.5f),
                        focusedLabelColor = cyanAccent,
                        unfocusedLabelColor = cyanAccent
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            focusRequester.requestFocus()
                        }
                    )
                )
            }
        }

        // Scanned Items List Container
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(top = 24.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = if (viewModel.getConfirmCountWithPartial()) "ÚLTIMOS 5 BIPES (${viewModel.scannedItems.size} total)" else "BIPES NESTA SEÇÃO (${viewModel.scannedItems.size})",
                    color = textLight,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "REVER TODOS",
                    color = textCyan,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(panelBgTranslucent, RoundedCornerShape(16.dp))
                    .border(1.dp, borderDark, RoundedCornerShape(16.dp))
                    .clip(RoundedCornerShape(16.dp))
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize()
                ) {
                    val displayedItems = if (viewModel.getConfirmCountWithPartial()) {
                        viewModel.scannedItems.take(5)
                    } else {
                        viewModel.scannedItems.toList()
                    }
                    items(items = displayedItems, key = { item -> item.ean + item.timestamp }) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(panelBg.copy(alpha = 0.8f))
                                .padding(vertical = 12.dp, horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = item.ean,
                                        color = textCyan,
                                        fontSize = 14.sp,
                                        fontFamily = FontFamily.Monospace,
                                        letterSpacing = (-0.5).sp
                                    )
                                    if (item.isTransmitted) {
                                        Box(
                                            modifier = Modifier
                                                .background(
                                                    color = Color(0xFF2E7D32).copy(alpha = 0.25f),
                                                    shape = RoundedCornerShape(4.dp)
                                                )
                                                .padding(horizontal = 5.dp, vertical = 1.dp)
                                        ) {
                                            Text(
                                                text = "ENVIADO",
                                                color = Color(0xFF81C784),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    } else {
                                        Box(
                                            modifier = Modifier
                                                .background(
                                                    color = Color(0xFF0288D1).copy(alpha = 0.25f),
                                                    shape = RoundedCornerShape(4.dp)
                                                )
                                                .padding(horizontal = 5.dp, vertical = 1.dp)
                                        ) {
                                            Text(
                                                text = "NOVO",
                                                color = Color(0xFF4FC3F7),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = item.descricao,
                                    color = textLight,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                                val meta = listOfNotNull(
                                    item.lote?.let { "Lote: $it" },
                                    item.validade?.let { "Val: $it" },
                                    item.pallet?.let { "Palete: $it" }
                                ).joinToString(" • ")
                                if (meta.isNotEmpty()) {
                                    Text(
                                        text = meta,
                                        color = cyanAccent.copy(alpha = 0.8f),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Normal,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.width(8.dp))
                            
                            Text(
                                text = "x${item.quantidade}",
                                color = greenSuccess,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            
                            Spacer(modifier = Modifier.width(12.dp))
                            
                            // Trash delete button next to item
                            IconButton(
                                onClick = {
                                    viewModel.removeItem(item)
                                },
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Excluir item",
                                    tint = Color(0xFFEF5350),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        HorizontalDivider(color = borderDark, thickness = 1.dp)
                    }
                }
            }
        }

        // Footer version string
        Text(
            text = "VERSION 2.4.0 • STANDBY",
            color = footerText,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }

    // --- DIALOGS ---

    // 1. Consult/Summary Dialog
    if (showConsultDialog) {
        LaunchedEffect(Unit) {
            consultFocusRequester.requestFocus()
        }

        AlertDialog(
            onDismissRequest = { 
                showConsultDialog = false 
                focusRequester.requestFocus()
            },
            title = {
                Text(
                    text = "Consultar Código - Seção ${viewModel.currentSectionCode}",
                    color = textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedTextField(
                        value = consultQuery,
                        onValueChange = { consultQuery = it },
                        label = { Text("Escanear ou Digitar Código", fontSize = 11.sp, fontWeight = FontWeight.Medium) },
                        textStyle = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = 16.sp,
                            letterSpacing = 1.sp,
                            color = textCyan
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                // Close keyboard or keep focus
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(consultFocusRequester),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = darkBg,
                            unfocusedContainerColor = darkBg,
                            focusedBorderColor = cyanAccent,
                            unfocusedBorderColor = cyanAccent.copy(alpha = 0.5f),
                            focusedLabelColor = cyanAccent,
                            unfocusedLabelColor = cyanAccent
                        ),
                        singleLine = true
                    )

                    val query = consultQuery.trim()
                    if (query.isEmpty()) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Aguardando leitura de código...",
                                color = textLight,
                                fontSize = 13.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    } else {
                        val matchedItems = viewModel.scannedItems.filter {
                            it.ean.equals(query, ignoreCase = true) || (it.sap != null && it.sap.equals(query, ignoreCase = true))
                        }
                        if (matchedItems.isEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Nenhuma contagem encontrada",
                                    color = Color(0xFFEF5350),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Código: $query não foi coletado nesta seção.",
                                    color = textLight,
                                    fontSize = 12.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                            }
                        } else {
                            val totalQty = matchedItems.sumOf { it.quantidade }
                            val firstItem = matchedItems.first()
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Product Info Box
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(darkBg, RoundedCornerShape(12.dp))
                                        .border(1.dp, borderDark, RoundedCornerShape(12.dp))
                                        .padding(12.dp)
                                ) {
                                    Text(
                                        text = firstItem.descricao,
                                        color = textPrimary,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "EAN: ${firstItem.ean}" + if (!firstItem.sap.isNullOrEmpty()) " | SAP: ${firstItem.sap}" else "",
                                        color = textLight,
                                        fontSize = 11.sp
                                    )
                                }

                                // Total Quantity Badge
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(cyanAccent.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                                        .border(1.dp, cyanAccent.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Total Contado:",
                                        color = textPrimary,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "$totalQty un",
                                        color = cyanAccent,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                // Individual entries list
                                Text(
                                    text = "Histórico nesta Seção (${matchedItems.size}):",
                                    color = textLight,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 4.dp)
                                )

                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 140.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(matchedItems) { item ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(darkBg.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                                                .padding(10.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column {
                                                val loteStr = if (!item.lote.isNullOrEmpty()) "Lote: ${item.lote}" else ""
                                                val validadeStr = if (!item.validade.isNullOrEmpty()) "Val: ${item.validade}" else ""
                                                val detailsList = listOf(loteStr, validadeStr).filter { it.isNotEmpty() }
                                                
                                                Text(
                                                    text = "Qtd: ${item.quantidade}",
                                                    color = textPrimary,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                if (detailsList.isNotEmpty()) {
                                                    Text(
                                                        text = detailsList.joinToString(" | "),
                                                        color = textLight,
                                                        fontSize = 11.sp
                                                    )
                                                }
                                            }
                                            Text(
                                                text = item.timestamp.split(" ").lastOrNull() ?: item.timestamp,
                                                color = textLight,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { 
                        showConsultDialog = false 
                        focusRequester.requestFocus()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = cyanAccent)
                ) {
                    Text("FECHAR")
                }
            },
            containerColor = panelBg,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // 2. Delete All Confirmation Dialog
    if (showDeleteAllConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllConfirmDialog = false },
            title = {
                Text(
                    text = "Apagar Toda a Coleta",
                    color = textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "Deseja realmente apagar toda a coleta desta seção para recomeçar? Esta ação não pode ser desfeita.",
                    color = textLight,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.clearScannedItems()
                        showDeleteAllConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF5350))
                ) {
                    Text("Zerar Seção", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showDeleteAllConfirmDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = textLight)
                ) {
                    Text("Cancelar")
                }
            },
            containerColor = panelBg,
            shape = RoundedCornerShape(16.dp)
        )
    }

    if (showTransmitErrorDialog) {
        AlertDialog(
            onDismissRequest = { 
                showTransmitErrorDialog = false
                onBack()
            },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFE91E63),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Aviso de Transmissão", color = Color(0xFFE91E63), fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
            },
            text = {
                Column {
                    Text(
                        text = transmitErrorMessage ?: "A seção já foi transmitida por outro operador.",
                        color = textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "• Sua contagem foi salva OFFLINE com segurança neste aparelho.",
                        color = AppColors.CyanAccent,
                        fontSize = 13.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "⚠️ CHAME O COORDENADOR IMEDIATAMENTE para verificar no painel se a primeira contagem está incorreta e deve ser apagada.",
                        color = Color(0xFFFFC107),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { 
                        showTransmitErrorDialog = false
                        onBack()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.CyanAccent)
                ) {
                    Text("OK, ENTENDI", color = textDark, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = AppColors.PanelBg,
            shape = RoundedCornerShape(16.dp)
        )
    }

    if (showTransmitValidationDialog) {
        AlertDialog(
            onDismissRequest = { 
                showTransmitValidationDialog = false 
                enteredQuantityText = ""
                validationErrorMessage = null
                focusRequester.requestFocus()
            },
            title = {
                Text(
                    text = "Confirmar Quantidade Contada",
                    color = textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "A função 'Confirmar contagem com parcial' está ativa. Por favor, insira o total de itens contados nesta seção para confirmar a transmissão:",
                        color = textLight,
                        fontSize = 14.sp
                    )
                    
                    OutlinedTextField(
                        value = enteredQuantityText,
                        onValueChange = { input ->
                            if (input.all { it.isDigit() || it == '.' || it == ',' }) {
                                enteredQuantityText = input
                            }
                        },
                        label = { Text("Quantidade Total Contada", fontSize = 12.sp) },
                        placeholder = { Text("Ex: 15") },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = darkBg,
                            unfocusedContainerColor = darkBg,
                            focusedBorderColor = cyanAccent,
                            unfocusedBorderColor = borderDark,
                            focusedLabelColor = cyanAccent,
                            unfocusedLabelColor = textLight,
                            focusedTextColor = textPrimary,
                            unfocusedTextColor = textPrimary
                        ),
                        singleLine = true
                    )
                    
                    if (validationErrorMessage != null) {
                        Text(
                            text = validationErrorMessage!!,
                            color = Color(0xFFEF5350),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Text(
                            text = "Tentativas restantes: ${2 - attemptCount}",
                            color = textLight,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (isTransmitting) return@Button
                        val actualQtySum = viewModel.scannedItems.sumOf { it.quantidade }
                        val enteredQty = enteredQuantityText.replace(",", ".").toDoubleOrNull()
                        
                        if (enteredQty == null) {
                            validationErrorMessage = "Por favor, digite um número válido."
                            return@Button
                        }
                        
                        if (Math.abs(enteredQty - actualQtySum) < 0.001) {
                            showTransmitValidationDialog = false
                            enteredQuantityText = ""
                            attemptCount = 0
                            validationErrorMessage = null
                            
                            coroutineScope.launch {
                                val result = viewModel.transmitSection()
                                if (result.first) {
                                    Toast.makeText(context, "✅ Contagem transmitida com SUCESSO!", Toast.LENGTH_LONG).show()
                                    onBack()
                                } else {
                                    transmitErrorMessage = result.second ?: "Erro ao transmitir contagem para o servidor."
                                    showTransmitErrorDialog = true
                                }
                            }
                        } else {
                            val nextAttempts = attemptCount + 1
                            attemptCount = nextAttempts
                            if (nextAttempts >= 2) {
                                viewModel.clearScannedItems()
                                showTransmitValidationDialog = false
                                enteredQuantityText = ""
                                attemptCount = 0
                                validationErrorMessage = null
                                
                                try {
                                    toneGenerator?.startTone(ToneGenerator.TONE_SUP_ERROR, 500)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                                
                                Toast.makeText(context, "Seção apagada! Quantidade incorreta pela 2ª vez.", Toast.LENGTH_LONG).show()
                            } else {
                                validationErrorMessage = "Quantidade incorreta! Mais uma tentativa incorreta e a seção será zerada."
                                try {
                                    toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP2, 300)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        }
                    },
                    enabled = !isTransmitting,
                    colors = ButtonDefaults.buttonColors(containerColor = cyanAccent)
                ) {
                    Text("Confirmar", color = textDark, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { 
                        showTransmitValidationDialog = false 
                        enteredQuantityText = ""
                        validationErrorMessage = null
                        focusRequester.requestFocus()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = textLight)
                ) {
                    Text("Cancelar")
                }
            },
            containerColor = panelBg,
            shape = RoundedCornerShape(16.dp)
        )
    }

    if (showExportSuccessDialog) {
        AlertDialog(
            onDismissRequest = { showExportSuccessDialog = false },
            containerColor = panelBg,
            title = {
                Text(
                    "Exportação da Seção",
                    color = textPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
            },
            text = {
                Text(
                    exportMessage,
                    color = textPrimary,
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = { showExportSuccessDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = cyanAccent)
                ) {
                    Text("OK", color = textDark, fontWeight = FontWeight.Bold)
                }
            },
            shape = RoundedCornerShape(16.dp)
        )
    }
}
