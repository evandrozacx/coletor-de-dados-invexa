package com.example

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventorySelectionScreen(
    viewModel: CollectorViewModel,
    onInventorySelected: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var inventories by remember { mutableStateOf<List<com.example.db.InventoryEntity>>(emptyList()) }
    var productCounts by remember { mutableStateOf<Map<String, Int>>(emptyMap()) }
    val isSyncing by viewModel.isSyncing.collectAsState()
    val syncMessage by viewModel.syncMessage.collectAsState()

    val downloadProgress by viewModel.downloadProgress.collectAsState()
    val downloadItemCount by viewModel.downloadItemCount.collectAsState()

    var isDownloadingProducts by remember { mutableStateOf(false) }
    var downloadingInvId by remember { mutableStateOf<String?>(null) }
    var downloadResultMessage by remember { mutableStateOf<String?>(null) }
    var showDownloadDialog by remember { mutableStateOf<Boolean>(false) }
    var targetInvIdForFilePicker by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    suspend fun refreshCounts() {
        val map = mutableMapOf<String, Int>()
        for (inv in inventories) {
            map[inv.id] = viewModel.getProductCountForInventory(inv.id)
        }
        productCounts = map
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: android.net.Uri? ->
        val invId = targetInvIdForFilePicker
        if (uri != null && invId != null) {
            coroutineScope.launch {
                isDownloadingProducts = true
                downloadingInvId = invId
                val result = viewModel.importProductsFromUri(context, invId, uri)
                isDownloadingProducts = false
                downloadingInvId = null
                downloadResultMessage = result.second
                showDownloadDialog = true
                refreshCounts()
            }
        }
    }

    LaunchedEffect(isSyncing, showDownloadDialog) {
        if (!isSyncing) {
            inventories = viewModel.getActiveInventories()
            refreshCounts()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(AppColors.DarkBg)
                .windowInsetsPadding(WindowInsets.systemBars)
        ) {
            TopAppBar(
                title = {
                    Column {
                        Text("Selecione o Inventário", color = AppColors.TextPrimary, fontSize = 18.sp)
                        Text("Operador: ${viewModel.operatorName}", color = AppColors.CyanAccent, fontSize = 12.sp)
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
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                viewModel.syncDataSuspend()
                                inventories = viewModel.getActiveInventories()
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
                                contentDescription = "Atualizar",
                                tint = AppColors.TextPrimary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppColors.DarkBg
                )
            )

            // Progress banner when syncing
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
                            text = syncMessage ?: "Atualizando inventários...",
                            color = AppColors.CyanAccent,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
            
            if (!isSyncing && inventories.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Nenhum inventário em andamento encontrado.\nSincronize ou verifique o status no painel.",
                            color = AppColors.TextLight,
                            fontSize = 14.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    viewModel.syncDataSuspend()
                                    inventories = viewModel.getActiveInventories()
                                }
                            },
                            enabled = !isSyncing,
                            colors = ButtonDefaults.buttonColors(containerColor = AppColors.CyanAccent),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Sincronizar Inventários", color = AppColors.TextDark, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(inventories) { inventory ->
                    InventoryCard(
                        inventory = inventory,
                        productCount = productCounts[inventory.id] ?: 0,
                        onClick = { onInventorySelected(inventory.id) },
                        onDownloadProducts = { invId ->
                            coroutineScope.launch {
                                isDownloadingProducts = true
                                downloadingInvId = invId
                                val result = viewModel.downloadProductsForInventory(invId)
                                isDownloadingProducts = false
                                downloadingInvId = null
                                downloadResultMessage = result.second
                                showDownloadDialog = true
                                refreshCounts()
                            }
                        },
                        onImportLocalProducts = { invId ->
                            targetInvIdForFilePicker = invId
                            filePickerLauncher.launch("*/*")
                        },
                        onClearProducts = { invId ->
                            coroutineScope.launch {
                                viewModel.clearProductsForInventory(invId)
                                downloadResultMessage = "Produtos do inventário limpos com sucesso!"
                                showDownloadDialog = true
                                refreshCounts()
                            }
                        },
                        isDownloading = isDownloadingProducts,
                        downloadingInvId = downloadingInvId,
                        downloadProgress = downloadProgress,
                        downloadItemCount = downloadItemCount
                    )
                }
            }
        }

        if (showDownloadDialog) {
            AlertDialog(
                onDismissRequest = { showDownloadDialog = false },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = AppColors.CyanAccent,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Download de Produtos", color = AppColors.TextPrimary, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                },
                text = {
                    Text(
                        text = downloadResultMessage ?: "Operação concluída.",
                        color = AppColors.TextPrimary,
                        fontSize = 14.sp
                    )
                },
                confirmButton = {
                    Button(
                        onClick = { showDownloadDialog = false },
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.CyanAccent)
                    ) {
                        Text("OK", color = AppColors.TextDark, fontWeight = FontWeight.Bold)
                    }
                },
                containerColor = AppColors.PanelBg,
                shape = RoundedCornerShape(16.dp)
            )
        }
    }
}

@Composable
fun InventoryCard(
    inventory: com.example.db.InventoryEntity,
    productCount: Int,
    onClick: () -> Unit,
    onDownloadProducts: (String) -> Unit,
    onImportLocalProducts: (String) -> Unit,
    onClearProducts: (String) -> Unit,
    isDownloading: Boolean,
    downloadingInvId: String?,
    downloadProgress: Int,
    downloadItemCount: Int
) {
    var showClearDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppColors.PanelBg, RoundedCornerShape(12.dp))
            .border(1.dp, AppColors.BorderDark, RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (!inventory.nome.isNullOrBlank()) inventory.nome else "Inventário ${inventory.id}",
                    color = AppColors.TextPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold
                )
                val subText = inventory.filial ?: inventory.subNome
                if (!subText.isNullOrBlank()) {
                    val labelText = if (subText.startsWith("Filial", ignoreCase = true)) subText else "Filial: $subText"
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = labelText,
                        color = AppColors.TextLight,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Configuração Operacional Badges
                if (inventory.controleLote || inventory.controleValidade || inventory.controlePalete || inventory.onlyRegisteredProducts || inventory.confirmCountWithPartial) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (inventory.controleLote) {
                            ConfigBadge(text = "LOTE", color = Color(0xFFFFB74D))
                        }
                        if (inventory.controleValidade) {
                            ConfigBadge(text = "VALIDADE", color = Color(0xFF64B5F6))
                        }
                        if (inventory.controlePalete) {
                            ConfigBadge(text = "PALETE", color = Color(0xFFBA68C8))
                        }
                        if (inventory.onlyRegisteredProducts) {
                            ConfigBadge(text = "SÓ CADASTRADOS", color = Color(0xFFE57373))
                        }
                        if (inventory.confirmCountWithPartial) {
                            ConfigBadge(text = "CEGA / PARCIAL", color = Color(0xFF4DB6AC))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Botão Entrar
            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(containerColor = AppColors.CyanAccent),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.Login,
                    contentDescription = "Entrar no Inventário",
                    tint = AppColors.TextDark,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "ENTRAR",
                    color = AppColors.TextDark,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (productCount > 0) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AppColors.DarkBg, RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "📦 Produtos importados:",
                    color = AppColors.TextLight,
                    fontSize = 12.sp
                )
                Text(
                    text = "$productCount itens",
                    color = AppColors.CyanAccent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        val hasProducts = productCount > 0
        val isThisDownloading = isDownloading && downloadingInvId == inventory.id

        // Download and Import buttons are only shown when no products are loaded (or during download)
        if (!hasProducts || isThisDownloading) {
            // Download Button
            OutlinedButton(
                onClick = { onDownloadProducts(inventory.id) },
                enabled = !isThisDownloading,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = AppColors.CyanAccent
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.CyanAccent.copy(alpha = 0.5f)),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (isThisDownloading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = AppColors.CyanAccent,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    if (downloadProgress >= 0) {
                        Text("Baixando: $downloadProgress% ($downloadItemCount itens)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    } else if (downloadItemCount > 0) {
                        Text("Baixando: $downloadItemCount itens...", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Text("Baixando produtos...", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                } else {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = "Puxar Produtos",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Puxar Arquivo de Produtos",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (!hasProducts && !isThisDownloading) {
                Spacer(modifier = Modifier.height(6.dp))

                // Local File Import Button
                OutlinedButton(
                    onClick = { onImportLocalProducts(inventory.id) },
                    enabled = !isThisDownloading,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFFFFB74D)
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFB74D).copy(alpha = 0.6f)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = "Importar Arquivo Local",
                        tint = Color(0xFFFFB74D),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Importar Arquivo Local (.txt / .csv)",
                        color = Color(0xFFFFB74D),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Clear Products Button (Shown when products exist)
        if (hasProducts) {
            OutlinedButton(
                onClick = { showClearDialog = true },
                enabled = !isThisDownloading,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = Color(0xFFF44336)
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF44336).copy(alpha = 0.5f)),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Limpar Produtos",
                    tint = Color(0xFFF44336),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Limpar Arquivo de Produtos",
                    color = Color(0xFFF44336),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (isDownloading && downloadingInvId == inventory.id && downloadProgress >= 0) {
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { downloadProgress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                color = AppColors.CyanAccent,
                trackColor = AppColors.BorderDark
            )
        }

        if (showClearDialog) {
            AlertDialog(
                onDismissRequest = { showClearDialog = false },
                containerColor = AppColors.PanelBg,
                title = {
                    Text(
                        "Limpar Produtos",
                        color = AppColors.TextPrimary,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Text(
                        "Tem certeza que deseja apagar os produtos baixados deste inventário do seu aparelho? Você precisará baixá-los novamente para realizar a contagem.",
                        color = AppColors.TextPrimary
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showClearDialog = false
                            onClearProducts(inventory.id)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF44336))
                    ) {
                        Text("Sim, apagar", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    OutlinedButton(
                        onClick = { showClearDialog = false },
                        border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.TextLight)
                    ) {
                        Text("Cancelar", color = AppColors.TextPrimary)
                    }
                }
            )
        }
    }
}

@Composable
fun ConfigBadge(text: String, color: Color) {
    Box(
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(text = text, color = color, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}
