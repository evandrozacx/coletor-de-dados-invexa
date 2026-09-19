package com.example

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.db.SectorEntity
import com.example.db.SectionEntity
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun SectionSelectionScreen(
    viewModel: CollectorViewModel,
    onSectionSelected: (sectorId: String, sectionCode: String) -> Unit,
    onBack: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var sectors by remember { mutableStateOf<List<SectorEntity>>(emptyList()) }
    var sections by remember { mutableStateOf<List<SectionEntity>>(emptyList()) }
    var inventoryName by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current

    val isOnline by viewModel.isOnline.collectAsState()

    // Reopen dialog states
    var showReopenDialog by remember { mutableStateOf(false) }
    var selectedSectionToReopen by remember { mutableStateOf<SectionEntity?>(null) }
    var passwordInput by remember { mutableStateOf("") }
    var passwordError by remember { mutableStateOf<String?>(null) }

    // Coordinator pending sync alert state
    var showPendingCoordinatorAlert by remember { mutableStateOf(false) }
    var selectedPendingSectionForDetail by remember { mutableStateOf<SectionEntity?>(null) }

    val isSyncing by viewModel.isSyncing.collectAsState()
    val syncMessage by viewModel.syncMessage.collectAsState()

    LaunchedEffect(isSyncing) {
        if (!isSyncing) {
            sectors = viewModel.getSectorsForCurrentInventory()
            sections = viewModel.getSectionsForCurrentInventory()
        }
    }

    LaunchedEffect(Unit) {
        isLoading = true
        // Load Inventory details
        val inventories = viewModel.getActiveInventories()
        val selectedInv = inventories.find { it.id == viewModel.inventoryId }
        inventoryName = selectedInv?.nome ?: selectedInv?.id ?: viewModel.inventoryId

        sectors = viewModel.getSectorsForCurrentInventory()
        sections = viewModel.getSectionsForCurrentInventory()
        if (sections.any { it.isPendingSync }) {
            showPendingCoordinatorAlert = true
        }
        isLoading = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.DarkBg)
            .windowInsetsPadding(WindowInsets.systemBars)
    ) {
        TopAppBar(
            title = {
                Column {
                    Text("Selecione a Seção", color = AppColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("Operador: ${viewModel.operatorName}", color = AppColors.CyanAccent, fontSize = 12.sp)
                    if (inventoryName.isNotEmpty()) {
                        Text(inventoryName, color = AppColors.TextLight, fontSize = 12.sp)
                    }
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Voltar",
                        tint = AppColors.TextPrimary
                    )
                }
            },
            actions = {
                if (!isOnline) {
                    // MODO OFFLINE: When disconnected, hide refresh button and show offline badge
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF37474F),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF78909C)),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudOff,
                                contentDescription = "Modo Offline",
                                tint = Color(0xFFFFB74D),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Modo Offline",
                                color = Color(0xFFFFB74D),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                } else {
                    // MODO ONLINE: Show refresh/sync button
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                viewModel.syncDataSuspend()
                                sectors = viewModel.getSectorsForCurrentInventory()
                                sections = viewModel.getSectionsForCurrentInventory()
                                if (sections.any { it.isPendingSync }) {
                                    showPendingCoordinatorAlert = true
                                }
                            }
                        },
                        enabled = !isSyncing
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                color = AppColors.CyanAccent,
                                strokeWidth = 2.5.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Sincronizar",
                                tint = AppColors.TextPrimary
                            )
                        }
                    }
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = AppColors.DarkBg
            )
        )

        // Progress indicator banner when sync is running
        if (isSyncing) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                shape = RoundedCornerShape(10.dp),
                color = AppColors.CyanAccent.copy(alpha = 0.15f),
                border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.CyanAccent)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = AppColors.CyanAccent,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = syncMessage ?: "Atualizando seções e setores...",
                        color = AppColors.CyanAccent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Persistent Warning Banner if any section is pending sync
        val pendingSectionsList = sections.filter { it.isPendingSync }
        if (pendingSectionsList.isNotEmpty()) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clickable { showPendingCoordinatorAlert = true },
                shape = RoundedCornerShape(12.dp),
                color = Color(0x28E91E63),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE91E63))
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFE91E63),
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "⚠️ ${pendingSectionsList.size} SEÇÃO(ÕES) PENDENTE(S) (${pendingSectionsList.joinToString(", ") { it.code }})",
                            color = Color(0xFFE91E63),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "CHAME O COORDENADOR IMEDIATAMENTE!",
                            color = Color(0xFFFFC107),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Search Field at the top
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Filtrar por código de seção...", color = AppColors.TextLight) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = AppColors.TextLight
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            shape = RoundedCornerShape(12.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = AppColors.PanelBg,
                unfocusedContainerColor = AppColors.PanelBg,
                focusedBorderColor = AppColors.CyanAccent,
                unfocusedBorderColor = AppColors.BorderDark,
                focusedTextColor = AppColors.TextPrimary,
                unfocusedTextColor = AppColors.TextPrimary
            ),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = { focusManager.clearFocus() }
            )
        )

        // Status Legend (Idêntica ao Painel Web)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            LegendItem(label = "Não Contado", color = AppColors.TextLight)
            LegendItem(label = "Contado", color = AppColors.GreenSuccess)
            LegendItem(label = "Compara OK", color = Color(0xFFFFC107))
            LegendItem(label = "Divergente", color = Color(0xFFE91E63))
        }

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = AppColors.CyanAccent)
            }
        } else if (sectors.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = AppColors.TextLight,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "Nenhum setor ou seção encontrado para este inventário.",
                        color = AppColors.TextLight,
                        textAlign = TextAlign.Center,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                viewModel.syncDataSuspend()
                                sectors = viewModel.getSectorsForCurrentInventory()
                                sections = viewModel.getSectionsForCurrentInventory()
                            }
                        },
                        enabled = !isSyncing,
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.CyanAccent)
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                color = AppColors.TextDark,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sincronizando...", color = AppColors.TextDark, fontWeight = FontWeight.Bold)
                        } else {
                            Text("Sincronizar Dados", color = AppColors.TextDark, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                sectors.forEach { sector ->
                    val filteredSections = sections.filter {
                        it.sectorId == sector.id && (searchQuery.isEmpty() || it.code.contains(searchQuery))
                    }

                    if (filteredSections.isNotEmpty()) {
                        // Section Header "barra fixa com o nome do setor"
                        stickyHeader {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(AppColors.PanelBg)
                                    .border(width = 1.dp, color = AppColors.BorderDark)
                                    .padding(horizontal = 16.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    text = sector.name.uppercase(),
                                    color = AppColors.CyanAccent,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                )
                            }
                        }

                        // Grid items chunks of 3 columns
                        val chunked = filteredSections.chunked(3)
                        chunked.forEach { rowSections ->
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    rowSections.forEach { section ->
                                        SectionCard(
                                            section = section,
                                            modifier = Modifier.weight(1f),
                                            onClick = {
                                                if (section.isPendingSync) {
                                                    selectedPendingSectionForDetail = section
                                                    return@SectionCard
                                                }
                                                val cleanStatus = section.status.trim().uppercase()
                                                val isComparaOk = cleanStatus in listOf("COMPARA_OK", "COMPARA OK", "COMPARA_VALIDO", "COMPARA", "BATIDO", "IDENTICO", "OK", "CONCLUIDO", "FINALIZADO", "VALIDO", "APROVADO", "IGUAL", "CONFERIDO", "CONFERIDO_OK", "FECHADO", "VERIFICADO", "AUDITADO", "MATCH", "2", "3")
                                                val isContado = cleanStatus in listOf("CONTADO", "CONTADA", "1_CONTAGEM", "PRIMEIRA_CONTAGEM", "CONTAGEM_1", "EM_ANDAMENTO", "1")
                                                
                                                if (isContado || isComparaOk) {
                                                    selectedSectionToReopen = section
                                                    passwordInput = ""
                                                    passwordError = null
                                                    showReopenDialog = true
                                                } else {
                                                    onSectionSelected(section.sectorId, section.code)
                                                }
                                            }
                                        )
                                    }
                                    // Fill the rest of the 3 columns with empty spacers
                                    repeat(3 - rowSections.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // --- REOPEN PASSWORD DIALOG ---
    if (showReopenDialog && selectedSectionToReopen != null) {
        val section = selectedSectionToReopen!!
        val opName = section.countedByOperatorName ?: "Desconhecido"
        AlertDialog(
            onDismissRequest = { showReopenDialog = false },
            title = {
                Text(
                    text = "Reabrir Seção ${section.code}",
                    color = AppColors.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val promptText = if (opName == "Desconhecido") {
                        "Esta seção já foi contada. Insira a sua senha de operador para reabri-la:"
                    } else {
                        "Esta seção já foi contada por $opName. Insira a senha deste operador para reabri-la:"
                    }
                    Text(
                        text = promptText,
                        color = AppColors.TextLight,
                        fontSize = 14.sp
                    )
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        label = { Text("Senha do Operador") },
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        singleLine = true,
                        isError = passwordError != null,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = AppColors.TextPrimary,
                            unfocusedTextColor = AppColors.TextPrimary,
                            focusedBorderColor = AppColors.CyanAccent,
                            unfocusedBorderColor = AppColors.BorderDark,
                            errorBorderColor = Color.Red,
                            focusedLabelColor = AppColors.CyanAccent,
                            unfocusedLabelColor = AppColors.TextLight
                        )
                    )
                    if (passwordError != null) {
                        Text(
                            text = passwordError!!,
                            color = Color.Red,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        coroutineScope.launch {
                            val success = viewModel.reopenSection(section.code, section.sectorId, passwordInput)
                            if (success) {
                                showReopenDialog = false
                                // Refresh sections list
                                sections = viewModel.getSectionsForCurrentInventory()
                                // Navigate to the section collector
                                onSectionSelected(section.sectorId, section.code)
                            } else {
                                passwordError = "Senha inválida ou operador incorreto"
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.CyanAccent)
                ) {
                    Text("REABRIR", color = AppColors.TextDark, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showReopenDialog = false },
                    colors = ButtonDefaults.textButtonColors(contentColor = AppColors.TextLight)
                ) {
                    Text("CANCELAR")
                }
            },
            containerColor = AppColors.PanelBg,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // --- INDIVIDUAL PENDING SECTION DETAIL DIALOG ---
    if (selectedPendingSectionForDetail != null) {
        val pendingSec = selectedPendingSectionForDetail!!
        AlertDialog(
            onDismissRequest = { selectedPendingSectionForDetail = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFFF9800),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Seção ${pendingSec.code} Pendente",
                        color = AppColors.TextPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Esta seção foi contada e os dados estão salvos com segurança neste aparelho, porém a transmissão para o servidor não foi concluída.",
                        color = AppColors.TextPrimary,
                        fontSize = 14.sp
                    )

                    val errorMsg = pendingSec.syncErrorMessage
                    if (!errorMsg.isNullOrBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0x33FF9800),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF9800)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "Status / Motivo:",
                                    color = Color(0xFFFF9800),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = errorMsg,
                                    color = AppColors.TextPrimary,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    } else {
                        Text(
                            text = "Aguardando conexão com o servidor para sincronização.",
                            color = Color(0xFFFFC107),
                            fontSize = 13.sp
                        )
                    }

                    Text(
                        text = "Assim que houver conexão com a internet e a seção estiver liberada no painel, toque no botão de sincronização no topo da tela para finalizar o envio.",
                        color = AppColors.TextLight,
                        fontSize = 12.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { selectedPendingSectionForDetail = null },
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.CyanAccent)
                ) {
                    Text("ENTENDI", color = AppColors.TextDark, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = AppColors.PanelBg,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // --- PENDING SYNC / COORDINATOR ALERT DIALOG ---
    if (showPendingCoordinatorAlert) {
        val pendingSections = sections.filter { it.isPendingSync }
        if (pendingSections.isNotEmpty()) {
            AlertDialog(
                onDismissRequest = { showPendingCoordinatorAlert = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color(0xFFE91E63),
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "CHAME O COORDENADOR!",
                            color = Color(0xFFE91E63),
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            text = "Você possui ${pendingSections.size} seção(ões) com transmissão pendente neste aparelho:\n${pendingSections.joinToString(", ") { it.code }}",
                            color = AppColors.TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "⚠️ Se outra pessoa já enviou esta seção anteriormente, o coordenador precisa excluir a contagem anterior no painel web para que a sua contagem correta seja aceita ao sincronizar.",
                            color = Color(0xFFFFC107),
                            fontSize = 13.sp
                        )
                        Text(
                            text = "Por favor, procure o coordenador imediatamente!",
                            color = AppColors.TextLight,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { showPendingCoordinatorAlert = false },
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.CyanAccent)
                    ) {
                        Text("OK, VOU AVISAR O COORDENADOR", color = AppColors.TextDark, fontWeight = FontWeight.Bold)
                    }
                },
                containerColor = AppColors.PanelBg,
                shape = RoundedCornerShape(16.dp)
            )
        }
    }
}

@Composable
fun LegendItem(label: String, color: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .background(color, RoundedCornerShape(50))
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = label,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun SectionCard(
    section: SectionEntity,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val cleanStatus = section.status.trim().uppercase()
    val isComparaOk = cleanStatus in listOf("COMPARA_OK", "COMPARA OK", "COMPARA_VALIDO", "COMPARA", "BATIDO", "IDENTICO", "OK", "CONCLUIDO", "FINALIZADO", "VALIDO", "APROVADO", "IGUAL", "CONFERIDO", "CONFERIDO_OK", "FECHADO", "VERIFICADO", "AUDITADO", "MATCH", "2", "3")
    val isDivergente = cleanStatus in listOf("DIVERGENTE", "DIVERGENCIA", "DIVERGENTE_RECONTAGEM", "RECONTAGEM", "DIVERGENTES")
    val isContado = cleanStatus in listOf("CONTADO", "CONTADA", "1_CONTAGEM", "PRIMEIRA_CONTAGEM", "CONTAGEM_1", "EM_ANDAMENTO", "1")

    val statusColor = when {
        isComparaOk -> Color(0xFFFFC107) // Amarelo (Compara OK)
        isDivergente -> Color(0xFFE91E63) // Vermelho / Pink (Divergente)
        isContado -> AppColors.GreenSuccess // Verde (Contado / 1ª contagem)
        else -> AppColors.TextLight // Não Contado / Pendente / Desconhecido
    }

    val statusLabel = when {
        isComparaOk -> "COMPARA OK"
        isDivergente -> "DIVERGENTE"
        isContado -> "CONTADO"
        else -> {
            val statusTrimmed = section.status.trim().uppercase()
            if (statusTrimmed == "PENDENTE" || statusTrimmed == "NAO_INICIADO") "NÃO CONTADO" else statusTrimmed.take(12).replace("_", " ")
        }
    }

    val borderColor = when {
        isComparaOk -> Color(0xFFFFC107).copy(alpha = 0.9f)
        isDivergente -> Color(0xFFE91E63).copy(alpha = 0.9f)
        isContado -> AppColors.GreenSuccess.copy(alpha = 0.85f)
        else -> AppColors.BorderDark
    }

    Card(
        modifier = modifier
            .height(85.dp)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = AppColors.PanelBg
        )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (section.isPendingSync) {
                Icon(
                    imageVector = Icons.Default.CloudUpload,
                    contentDescription = "Pendente de transmissão",
                    tint = Color(0xFFFF9800),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                        .size(16.dp)
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(8.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = section.code,
                    color = AppColors.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(statusColor, RoundedCornerShape(50))
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = statusLabel,
                        color = statusColor,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
