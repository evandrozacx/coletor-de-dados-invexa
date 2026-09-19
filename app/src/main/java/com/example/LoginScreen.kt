package com.example

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.db.OperatorEntity
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    viewModel: CollectorViewModel,
    onLoginSuccess: (operatorName: String, operatorId: String) -> Unit
) {
    var operatorId by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    var showUrlDialog by remember { mutableStateOf(false) }
    var serverUrlInput by remember { mutableStateOf(viewModel.getServerUrl()) }
    var tenantIdInput by remember { mutableStateOf(viewModel.getTenantId()) }
    var collectorNumberInput by remember { mutableStateOf(viewModel.getCollectorNumber()) }
    var currentCollectorNumber by remember { mutableStateOf(viewModel.getCollectorNumber()) }
    var allowMultiplication by remember { mutableStateOf(viewModel.getAllowMultiplication()) }
    var onlyRegisteredProducts by remember { mutableStateOf(viewModel.getOnlyRegisteredProducts()) }
    var keyboardIsAlphanumeric by remember { mutableStateOf(viewModel.getKeyboardIsAlphanumeric()) }
    var confirmCountWithPartial by remember { mutableStateOf(viewModel.getConfirmCountWithPartial()) }
    var scannerTimeoutMsInput by remember { mutableStateOf(viewModel.getScannerTimeoutMs().toString()) }

    var showSettingsPasswordDialog by remember { mutableStateOf(false) }
    var settingsPasswordInput by remember { mutableStateOf("") }
    var settingsPasswordError by remember { mutableStateOf(false) }

    var availableOperators by remember { mutableStateOf<List<OperatorEntity>>(emptyList()) }
    var dropdownExpanded by remember { mutableStateOf(false) }

    val isSyncing by viewModel.isSyncing.collectAsState(initial = false)
    val syncState by viewModel.syncMessage.collectAsState(initial = null)

    var hasAttemptedInitialSync by remember { mutableStateOf(false) }

    // Recarrega operadores do banco local sempre que a tela inicia ou finaliza uma sincronização
    LaunchedEffect(isSyncing) {
        val list = viewModel.getOperatorsList()
        availableOperators = list
    }

    LaunchedEffect(Unit) {
        val list = viewModel.getOperatorsList()
        if (list.isEmpty() && !hasAttemptedInitialSync) {
            hasAttemptedInitialSync = true
            viewModel.syncData()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.DarkBg)
    ) {
        // Top right actions: Theme toggle and Settings
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .windowInsetsPadding(WindowInsets.systemBars),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { viewModel.toggleTheme() }
            ) {
                Icon(
                    imageVector = if (AppColors.isDark) Icons.Default.LightMode else Icons.Default.DarkMode,
                    contentDescription = if (AppColors.isDark) "Alternar para Modo Claro" else "Alternar para Modo Escuro",
                    tint = AppColors.CyanAccent
                )
            }
            IconButton(
                onClick = { 
                    settingsPasswordInput = ""
                    settingsPasswordError = false
                    showSettingsPasswordDialog = true 
                }
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Configurações do Coletor",
                    tint = AppColors.CyanAccent
                )
            }
        }

        if (showSettingsPasswordDialog) {
            AlertDialog(
                onDismissRequest = { showSettingsPasswordDialog = false },
                title = { Text("Acesso Restrito", color = AppColors.TextPrimary) },
                text = {
                    Column {
                        Text("Digite a senha de administrador para acessar as configurações.", color = AppColors.TextLight, fontSize = 14.sp, modifier = Modifier.padding(bottom = 16.dp))
                        OutlinedTextField(
                            value = settingsPasswordInput,
                            onValueChange = { 
                                settingsPasswordInput = it
                                settingsPasswordError = false
                            },
                            label = { Text("Senha") },
                            modifier = Modifier.fillMaxWidth(),
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            isError = settingsPasswordError,
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = AppColors.PanelBg,
                                unfocusedContainerColor = AppColors.PanelBg,
                                focusedBorderColor = AppColors.CyanAccent,
                                unfocusedBorderColor = AppColors.BorderDark,
                                focusedLabelColor = AppColors.CyanAccent,
                                unfocusedLabelColor = AppColors.TextLight,
                                focusedTextColor = AppColors.TextPrimary,
                                unfocusedTextColor = AppColors.TextPrimary,
                                errorTextColor = Color.Red,
                                errorBorderColor = Color.Red
                            )
                        )
                        if (settingsPasswordError) {
                            Text("Senha incorreta", color = Color.Red, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (settingsPasswordInput == "123456") {
                                showSettingsPasswordDialog = false
                                serverUrlInput = viewModel.getServerUrl()
                                tenantIdInput = viewModel.getTenantId()
                                collectorNumberInput = viewModel.getCollectorNumber()
                                allowMultiplication = viewModel.getAllowMultiplication()
                                onlyRegisteredProducts = viewModel.getOnlyRegisteredProducts()
                                keyboardIsAlphanumeric = viewModel.getKeyboardIsAlphanumeric()
                                confirmCountWithPartial = viewModel.getConfirmCountWithPartial()
                                scannerTimeoutMsInput = viewModel.getScannerTimeoutMs().toString()
                                showUrlDialog = true
                            } else {
                                settingsPasswordError = true
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.CyanAccent)
                    ) {
                        Text("Acessar", color = AppColors.TextDark, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSettingsPasswordDialog = false }) {
                        Text("Cancelar", color = AppColors.TextLight)
                    }
                },
                containerColor = AppColors.PanelBg,
                shape = RoundedCornerShape(16.dp)
            )
        }

        if (showUrlDialog) {
            AlertDialog(
                onDismissRequest = { showUrlDialog = false },
                title = { Text("Configurações do Coletor", color = AppColors.TextPrimary) },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            "Dados de conexão:",
                            color = AppColors.CyanAccent,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        OutlinedTextField(
                            value = serverUrlInput,
                            onValueChange = { serverUrlInput = it },
                            label = { Text("URL Base") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = AppColors.PanelBg,
                                unfocusedContainerColor = AppColors.PanelBg,
                                focusedBorderColor = AppColors.CyanAccent,
                                unfocusedBorderColor = AppColors.BorderDark,
                                focusedLabelColor = AppColors.CyanAccent,
                                unfocusedLabelColor = AppColors.TextLight,
                                focusedTextColor = AppColors.TextPrimary,
                                unfocusedTextColor = AppColors.TextPrimary
                            ),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = tenantIdInput,
                            onValueChange = { tenantIdInput = it },
                            label = { Text("ID da Empresa / Tenant ID") },
                            placeholder = { Text("Ex: ppbrasil") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = AppColors.PanelBg,
                                unfocusedContainerColor = AppColors.PanelBg,
                                focusedBorderColor = AppColors.CyanAccent,
                                unfocusedBorderColor = AppColors.BorderDark,
                                focusedLabelColor = AppColors.CyanAccent,
                                unfocusedLabelColor = AppColors.TextLight,
                                focusedTextColor = AppColors.TextPrimary,
                                unfocusedTextColor = AppColors.TextPrimary
                            ),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = collectorNumberInput,
                            onValueChange = { collectorNumberInput = it },
                            label = { Text("Número do Coletor") },
                            placeholder = { Text("Ex: 01, 02, COL-01") },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = AppColors.PanelBg,
                                unfocusedContainerColor = AppColors.PanelBg,
                                focusedBorderColor = AppColors.CyanAccent,
                                unfocusedBorderColor = AppColors.BorderDark,
                                focusedLabelColor = AppColors.CyanAccent,
                                unfocusedLabelColor = AppColors.TextLight,
                                focusedTextColor = AppColors.TextPrimary,
                                unfocusedTextColor = AppColors.TextPrimary
                            ),
                            singleLine = true
                        )
                        
                        Spacer(modifier = Modifier.height(20.dp))
                        Text(
                            "Preferências do Coletor:",
                            color = AppColors.CyanAccent,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        // Option: Visual Theme Mode (Dark / Light)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.toggleTheme() }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text("Tema Visual (Modo Escuro / Claro)", color = AppColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Text(if (AppColors.isDark) "Atualmente em Modo Escuro" else "Atualmente em Modo Claro", color = AppColors.TextLight, fontSize = 11.sp)
                            }
                            Switch(
                                checked = AppColors.isDark,
                                onCheckedChange = { viewModel.setDarkMode(it) },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = AppColors.CyanAccent,
                                    checkedTrackColor = AppColors.CyanAccent.copy(alpha = 0.5f),
                                    uncheckedThumbColor = Color.LightGray,
                                    uncheckedTrackColor = AppColors.BorderDark
                                )
                            )
                        }
                        
                        // Option 1: Product Multiplication Authorization
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { allowMultiplication = !allowMultiplication }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text("Permitir Multiplicação", color = AppColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Text("Autoriza o dispositivo a multiplicar produtos", color = AppColors.TextLight, fontSize = 11.sp)
                            }
                            Switch(
                                checked = allowMultiplication,
                                onCheckedChange = { allowMultiplication = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = AppColors.CyanAccent,
                                    checkedTrackColor = AppColors.CyanAccent.copy(alpha = 0.5f),
                                    uncheckedThumbColor = Color.LightGray,
                                    uncheckedTrackColor = AppColors.BorderDark
                                )
                            )
                        }

                        // Option 2: Only Read Registered Products vs Any Barcode
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onlyRegisteredProducts = !onlyRegisteredProducts }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text("Apenas Cadastrados", color = AppColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Text("Bloqueia códigos que não estão no banco local", color = AppColors.TextLight, fontSize = 11.sp)
                            }
                            Switch(
                                checked = onlyRegisteredProducts,
                                onCheckedChange = { onlyRegisteredProducts = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = AppColors.CyanAccent,
                                    checkedTrackColor = AppColors.CyanAccent.copy(alpha = 0.5f),
                                    uncheckedThumbColor = Color.LightGray,
                                    uncheckedTrackColor = AppColors.BorderDark
                                )
                            )
                        }

                        // Option 3: Keyboard Type (Numeric vs Alphanumeric)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { keyboardIsAlphanumeric = !keyboardIsAlphanumeric }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text("Teclado Alfanumérico", color = AppColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Text("Letras no teclado (desativado usa apenas números)", color = AppColors.TextLight, fontSize = 11.sp)
                            }
                            Switch(
                                checked = keyboardIsAlphanumeric,
                                onCheckedChange = { keyboardIsAlphanumeric = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = AppColors.CyanAccent,
                                    checkedTrackColor = AppColors.CyanAccent.copy(alpha = 0.5f),
                                    uncheckedThumbColor = Color.LightGray,
                                    uncheckedTrackColor = AppColors.BorderDark
                                )
                            )
                        }

                        // Option 4: Confirm count with partial
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { confirmCountWithPartial = !confirmCountWithPartial }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text("Confirmar contagem com parcial", color = AppColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Text("Pede a quantidade contada ao transmitir e limita lista a 5 itens", color = AppColors.TextLight, fontSize = 11.sp)
                            }
                            Switch(
                                checked = confirmCountWithPartial,
                                onCheckedChange = { confirmCountWithPartial = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = AppColors.CyanAccent,
                                    checkedTrackColor = AppColors.CyanAccent.copy(alpha = 0.5f),
                                    uncheckedThumbColor = Color.LightGray,
                                    uncheckedTrackColor = AppColors.BorderDark
                                )
                            )
                        }

                        // Option 5: Scanner Timeout (ms)
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                        ) {
                            Text("Timeout do Scanner / Intervalo entre bips (ms)", color = AppColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text("Ajuste em milissegundos para juntar o código em leituras rápidas (padrão: 200ms)", color = AppColors.TextLight, fontSize = 11.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            OutlinedTextField(
                                value = scannerTimeoutMsInput,
                                onValueChange = { input ->
                                    if (input.all { it.isDigit() }) {
                                        scannerTimeoutMsInput = input
                                    }
                                },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(8.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedContainerColor = AppColors.InputBg,
                                    unfocusedContainerColor = AppColors.InputBg,
                                    focusedBorderColor = AppColors.CyanAccent,
                                    unfocusedBorderColor = AppColors.BorderDark,
                                    focusedTextColor = AppColors.TextPrimary,
                                    unfocusedTextColor = AppColors.TextPrimary
                                )
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.setServerUrl(serverUrlInput)
                            viewModel.setTenantId(tenantIdInput)
                            viewModel.setCollectorNumber(collectorNumberInput)
                            currentCollectorNumber = viewModel.getCollectorNumber()
                            viewModel.setAllowMultiplication(allowMultiplication)
                            viewModel.setOnlyRegisteredProducts(onlyRegisteredProducts)
                            viewModel.setKeyboardIsAlphanumeric(keyboardIsAlphanumeric)
                            viewModel.setConfirmCountWithPartial(confirmCountWithPartial)
                            val timeoutVal = scannerTimeoutMsInput.toLongOrNull() ?: 200L
                            viewModel.setScannerTimeoutMs(timeoutVal)
                            showUrlDialog = false
                            viewModel.syncData()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AppColors.CyanAccent)
                    ) {
                        Text("Salvar", color = AppColors.TextDark, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showUrlDialog = false }) {
                        Text("Cancelar", color = AppColors.TextLight)
                    }
                },
                containerColor = AppColors.PanelBg,
                shape = RoundedCornerShape(16.dp)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
                .windowInsetsPadding(WindowInsets.systemBars),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Indicador visual do número do coletor
            Surface(
                color = AppColors.CyanAccent.copy(alpha = 0.15f),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, AppColors.CyanAccent.copy(alpha = 0.4f)),
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "COLETOR Nº: ",
                        color = AppColors.TextLight,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                    Text(
                        text = currentCollectorNumber.uppercase(),
                        color = AppColors.CyanAccent,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
            
            val lastOpName = remember { viewModel.getLastOperatorName() }
            if (lastOpName.isNotBlank()) {
                Text(
                    text = "ÚLTIMO USUÁRIO: ${lastOpName.uppercase()}",
                    color = AppColors.CyanAccent,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            Text(
                text = "LOGIN DO OPERADOR",
                color = AppColors.TextPrimary,
                fontSize = 28.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            // Se existirem operadores no banco, mostra um seletor dropdown com busca/digitacao
            ExposedDropdownMenuBox(
                expanded = dropdownExpanded && availableOperators.isNotEmpty(),
                onExpandedChange = { dropdownExpanded = it }
            ) {
                OutlinedTextField(
                    value = operatorId,
                    onValueChange = { 
                        operatorId = it 
                        dropdownExpanded = true
                    },
                    label = { Text("Nome do Operador ou CPF") },
                    placeholder = { Text("Selecione ou digite seu Nome/CPF") },
                    trailingIcon = {
                        if (availableOperators.isNotEmpty()) {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded)
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = AppColors.PanelBg,
                        unfocusedContainerColor = AppColors.PanelBg,
                        focusedBorderColor = AppColors.CyanAccent,
                        unfocusedBorderColor = AppColors.BorderDark,
                        focusedLabelColor = AppColors.CyanAccent,
                        unfocusedLabelColor = AppColors.TextLight,
                        focusedTextColor = AppColors.TextPrimary,
                        unfocusedTextColor = AppColors.TextPrimary
                    ),
                    singleLine = true
                )

                if (availableOperators.isNotEmpty()) {
                    ExposedDropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false },
                        modifier = Modifier.background(AppColors.PanelBg)
                    ) {
                        availableOperators
                            .filter { it.name.contains(operatorId, ignoreCase = true) || (it.cpf?.contains(operatorId) == true) }
                            .forEach { op ->
                                DropdownMenuItem(
                                    text = { 
                                        Column {
                                            Text(text = op.name, color = AppColors.TextPrimary, fontWeight = FontWeight.Bold)
                                            if (!op.cpf.isNullOrBlank()) {
                                                Text(text = "CPF: ${op.cpf}", color = AppColors.TextLight, fontSize = 12.sp)
                                            }
                                        }
                                    },
                                    onClick = {
                                        operatorId = op.name
                                        dropdownExpanded = false
                                    }
                                )
                            }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { input ->
                    if (input.length <= 6 && input.all { it.isDigit() }) {
                        password = input
                    }
                },
                label = { Text("Senha de 6 dígitos") },
                placeholder = { Text("Ex: 111111") },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = AppColors.PanelBg,
                    unfocusedContainerColor = AppColors.PanelBg,
                    focusedBorderColor = AppColors.CyanAccent,
                    unfocusedBorderColor = AppColors.BorderDark,
                    focusedLabelColor = AppColors.CyanAccent,
                    unfocusedLabelColor = AppColors.TextLight,
                    focusedTextColor = AppColors.TextPrimary,
                    unfocusedTextColor = AppColors.TextPrimary
                ),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(24.dp))

            if (errorMsg != null) {
                Text(
                    text = errorMsg ?: "",
                    color = Color.Red,
                    modifier = Modifier.padding(bottom = 16.dp),
                    fontSize = 14.sp
                )
            }

            if (syncState != null) {
                Text(
                    text = syncState ?: "",
                    color = AppColors.CyanAccent,
                    modifier = Modifier.padding(bottom = 16.dp),
                    fontSize = 13.sp
                )
            }

            Button(
                onClick = {
                    if (operatorId.isBlank()) {
                        errorMsg = "Selecione ou digite o Nome do Operador."
                        return@Button
                    }
                    if (password.length != 6) {
                        errorMsg = "Senha incorreta (deve conter 6 dígitos)."
                        return@Button
                    }
                    coroutineScope.launch {
                        val loginResult = viewModel.login(operatorId.trim(), password.trim())
                        when (loginResult) {
                            0 -> {
                                errorMsg = null
                                onLoginSuccess(viewModel.operatorName, viewModel.operatorId)
                            }
                            1 -> {
                                errorMsg = "Operador '${operatorId.trim()}' não encontrado. Sincronize os dados da nuvem ou verifique o nome cadastrado."
                            }
                            2 -> {
                                errorMsg = "Senha incorreta para o operador (deve ter 6 dígitos)."
                            }
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.GreenSuccess,
                    disabledContainerColor = AppColors.GreenSuccess.copy(alpha = 0.5f)
                ),
                enabled = operatorId.isNotBlank() && password.isNotBlank() && !isSyncing
            ) {
                Text(
                    text = if (isSyncing) "SINCRONIZANDO..." else "ENTRAR",
                    color = AppColors.TextDark,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    fontSize = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = { viewModel.syncData() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                enabled = !isSyncing
            ) {
                if (isSyncing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = AppColors.CyanAccent,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "SINCRONIZANDO COM A NUVEM...",
                        color = AppColors.CyanAccent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = AppColors.CyanAccent,
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text(
                        text = "SINCRONIZAR OPERADORES DA NUVEM",
                        color = AppColors.CyanAccent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
