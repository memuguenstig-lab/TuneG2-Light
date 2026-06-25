package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.example.ui.theme.BorderGrey
import com.example.ui.theme.CarbonDark
import com.example.ui.theme.CustomGrey
import com.example.ui.theme.CustomWhite
import com.example.ui.theme.ErrorRed
import com.example.ui.theme.KuKirinOrange
import com.example.ui.theme.KuKirinOrangeSubtle
import com.example.ui.theme.LightGrey
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.SlateGrey
import com.example.ui.theme.SuccessGreen

class MainActivity : ComponentActivity() {
    private lateinit var scooterManager: BluetoothScooterManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        scooterManager = BluetoothScooterManager(applicationContext)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                MainScreen(scooterManager)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(manager: BluetoothScooterManager) {
    val context = LocalContext.current
    val connectionState by manager.connectionState.collectAsState()
    val discoveredDevices by manager.discoveredDevices.collectAsState()
    val terminalLogs by manager.terminalLogs.collectAsState()
    val speed by manager.scooterSpeed.collectAsState()
    val battery by manager.scooterBattery.collectAsState()
    val isSimulated by manager.isSimulated.collectAsState()

    // Determine permissions required
    val requiredPermissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            listOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            listOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        }
    }

    var hasPermissions by remember {
        mutableStateOf(
            requiredPermissions.all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
        )
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        hasPermissions = results.values.all { it }
        if (hasPermissions) {
            manager.addLog("Bluetooth-Berechtigungen erteilt!")
            manager.startScanning()
        } else {
            manager.addLog("Berechtigungen abgelehnt. Bluetooth-Schnittstelle läuft im Offline-Modus.", isError = true)
            manager.enableSimulationMode()
        }
    }

    // Auto-trigger scanning on startup if we have permissions
    LaunchedEffect(hasPermissions) {
        if (hasPermissions) {
            manager.startScanning()
        } else {
            // Ask for permissions
            permissionLauncher.launch(requiredPermissions.toTypedArray())
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Home,
                            contentDescription = null,
                            tint = KuKirinOrange,
                            modifier = Modifier.size(28.dp)
                        )
                        Column {
                            Text(
                                text = "Kukirint Tuner",
                                color = CustomWhite,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "LIGHT EDITION",
                                color = KuKirinOrange,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 1.sp
                            )
                        }
                    }
                },
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(KuKirinOrange.copy(alpha = 0.2f))
                            .border(1.dp, KuKirinOrange, RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(SuccessGreen)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "BLE-BEREIT",
                            color = CustomWhite,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = CarbonDark
                )
            )
        },
        containerColor = CarbonDark
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Connection Status Card
            StatusCard(
                connectionState = connectionState,
                isSimulated = isSimulated,
                battery = battery,
                speed = speed,
                onDisconnect = { manager.disconnect() }
            )

            // 2. Main Command Panel
            if (connectionState == ConnectionState.CONNECTED || connectionState == ConnectionState.TURBO_ACTIVE || connectionState == ConnectionState.ACTIVATING_TURBO) {
                val selectedTuningSpeed by manager.selectedTuningSpeed.collectAsState()
                TurboTriggerPanel(
                    connectionState = connectionState,
                    speed = speed,
                    selectedTuningSpeed = selectedTuningSpeed,
                    onSpeedChange = { manager.setSelectedTuningSpeed(it) },
                    onActivate = { manager.activateTurboMode() },
                    onDeactivate = { manager.deactivateTurboMode() }
                )
            } else {
                // Not connected -> Device Scanner / Discovery
                ScannerPanel(
                    connectionState = connectionState,
                    discoveredDevices = discoveredDevices,
                    hasPermissions = hasPermissions,
                    onRequestPermissions = { permissionLauncher.launch(requiredPermissions.toTypedArray()) },
                    onStartScan = { manager.startScanning() },
                    onConnect = { address -> manager.connectToDevice(address) }
                )
            }

            // 3. Real-time BLE Terminal Logger
            TerminalLogPanel(
                logs = terminalLogs,
                onClear = { manager.addLog("Protokoll gelöscht.") }
            )
        }
    }
}

@Composable
fun StatusCard(
    connectionState: ConnectionState,
    isSimulated: Boolean,
    battery: Int,
    speed: Int,
    onDisconnect: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SlateGrey),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, BorderGrey)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Status icon pulse animation
                val infiniteTransition = rememberInfiniteTransition(label = "pulse")
                val alphaAnimation by infiniteTransition.animateFloat(
                    initialValue = 0.4f,
                    targetValue = 1.0f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1000),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "pulseAlpha"
                )

                val modifier = if (connectionState == ConnectionState.SCANNING || connectionState == ConnectionState.CONNECTING) {
                    Modifier.alpha(alphaAnimation)
                } else Modifier

                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            when (connectionState) {
                                ConnectionState.DISCONNECTED -> LightGrey
                                ConnectionState.SCANNING -> KuKirinOrange.copy(alpha = 0.2f)
                                ConnectionState.CONNECTING -> KuKirinOrange.copy(alpha = 0.2f)
                                ConnectionState.CONNECTED -> SuccessGreen.copy(alpha = 0.2f)
                                ConnectionState.ACTIVATING_TURBO -> KuKirinOrange.copy(alpha = 0.3f)
                                ConnectionState.TURBO_ACTIVE -> KuKirinOrange.copy(alpha = 0.4f)
                                ConnectionState.ERROR -> ErrorRed.copy(alpha = 0.2f)
                            }
                        )
                        .then(modifier),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when (connectionState) {
                            ConnectionState.DISCONNECTED -> Icons.Default.Close
                            ConnectionState.SCANNING, ConnectionState.CONNECTING -> Icons.Default.Refresh
                            ConnectionState.CONNECTED -> Icons.Default.Check
                            ConnectionState.ACTIVATING_TURBO, ConnectionState.TURBO_ACTIVE -> Icons.Default.Star
                            ConnectionState.ERROR -> Icons.Default.Warning
                        },
                        contentDescription = null,
                        tint = when (connectionState) {
                            ConnectionState.DISCONNECTED -> CustomGrey
                            ConnectionState.SCANNING, ConnectionState.CONNECTING -> KuKirinOrange
                            ConnectionState.CONNECTED -> SuccessGreen
                            ConnectionState.ACTIVATING_TURBO, ConnectionState.TURBO_ACTIVE -> KuKirinOrange
                            ConnectionState.ERROR -> ErrorRed
                        },
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column {
                    Text(
                        text = when (connectionState) {
                            ConnectionState.DISCONNECTED -> "Nicht verbunden"
                            ConnectionState.SCANNING -> "Suche nach Roller..."
                            ConnectionState.CONNECTING -> "Verbindung wird aufgebaut..."
                            ConnectionState.CONNECTED -> "G2 Verbunden"
                            ConnectionState.ACTIVATING_TURBO -> "Zünde Turbo..."
                            ConnectionState.TURBO_ACTIVE -> "TURBO AKTIV 🔥"
                            ConnectionState.ERROR -> "Verbindungsfehler"
                        },
                        color = CustomWhite,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "KuKirin G2 Motor-Controller",
                        color = CustomGrey,
                        fontSize = 12.sp
                    )
                }
            }

            if (connectionState == ConnectionState.CONNECTED || connectionState == ConnectionState.TURBO_ACTIVE) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Battery indicator
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "$battery%",
                            color = if (battery > 20) SuccessGreen else ErrorRed,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(text = "Batterie", color = CustomGrey, fontSize = 10.sp)
                    }

                    // Disconnect button
                    Button(
                        onClick = onDisconnect,
                        colors = ButtonDefaults.buttonColors(containerColor = LightGrey),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text(text = "Trennen", color = CustomWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun ScannerPanel(
    connectionState: ConnectionState,
    discoveredDevices: List<DiscoveredDevice>,
    hasPermissions: Boolean,
    onRequestPermissions: () -> Unit,
    onStartScan: () -> Unit,
    onConnect: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp),
        colors = CardDefaults.cardColors(containerColor = SlateGrey),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, BorderGrey)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "ROLLER-SUCHE",
                    color = KuKirinOrange,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.sp
                )

                if (connectionState == ConnectionState.SCANNING) {
                    CircularProgressIndicator(
                        color = KuKirinOrange,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(16.dp)
                    )
                } else {
                    IconButton(
                        onClick = {
                            if (hasPermissions) onStartScan() else onRequestPermissions()
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Scan starten",
                            tint = CustomWhite
                        )
                    }
                }
            }

            if (!hasPermissions) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        tint = CustomGrey,
                        modifier = Modifier.size(44.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Bluetooth-Berechtigungen benötigt",
                        color = CustomWhite,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Bitte erteilen Sie der App die Berechtigungen zur BLE-Suche.",
                        color = CustomGrey,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = onRequestPermissions,
                        colors = ButtonDefaults.buttonColors(containerColor = KuKirinOrange)
                    ) {
                        Text(text = "Berechtigungen erlauben", color = CustomWhite)
                    }
                }
            } else if (discoveredDevices.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        tint = CustomGrey,
                        modifier = Modifier.size(36.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (connectionState == ConnectionState.SCANNING) "Suche nach Geräten..." else "Keine Geräte gefunden",
                        color = CustomWhite,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = if (connectionState == ConnectionState.SCANNING) "Schalte deinen KuKirin G2 Roller ein." else "Tippe auf Aktualisieren um neu zu scannen.",
                        color = CustomGrey,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center
                    )
                    if (connectionState != ConnectionState.SCANNING) {
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = onStartScan,
                            colors = ButtonDefaults.buttonColors(containerColor = LightGrey)
                        ) {
                            Text(text = "Scan starten", color = CustomWhite)
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(discoveredDevices) { item ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (item.isKukirin) LightGrey else LightGrey.copy(alpha = 0.5f))
                                .border(
                                    BorderStroke(
                                        1.dp,
                                        if (item.isKukirin) KuKirinOrange else Color.Transparent
                                    ),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .clickable { onConnect(item.address) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (item.isKukirin) Icons.Default.Home else Icons.Default.Info,
                                    contentDescription = null,
                                    tint = if (item.isKukirin) KuKirinOrange else CustomGrey,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = item.name,
                                            color = CustomWhite,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (item.isKukirin) {
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "KOMPATIBEL",
                                                color = KuKirinOrange,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Black,
                                                modifier = Modifier
                                                    .background(
                                                        KuKirinOrange.copy(alpha = 0.15f),
                                                        RoundedCornerShape(4.dp)
                                                    )
                                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = item.address,
                                        color = CustomGrey,
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = "${item.rssi} dBm",
                                    color = CustomGrey,
                                    fontSize = 11.sp
                                )
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = "Verbinden",
                                    tint = KuKirinOrange,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TurboTriggerPanel(
    connectionState: ConnectionState,
    speed: Int,
    selectedTuningSpeed: Int,
    onSpeedChange: (Int) -> Unit,
    onActivate: () -> Unit,
    onDeactivate: () -> Unit
) {
    val isTurboActive = connectionState == ConnectionState.TURBO_ACTIVE

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = SlateGrey),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, BorderGrey)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "TUNING & TEMPOLIMIT EINSTELLUNGEN",
                color = KuKirinOrange,
                fontSize = 12.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.sp,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Start
            )

            // Speed gauge visualization
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$selectedTuningSpeed",
                        color = KuKirinOrange,
                        fontSize = 56.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 56.sp
                    )
                    Text(
                        text = "KM/H GEWÄHLTES TEMPOLIMIT",
                        color = CustomGrey,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }

            // Presets row
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "SCHNELL-PRESETS:",
                    color = CustomWhite,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )

                val presets = listOf(
                    Triple(25, "Standard", "Legal"),
                    Triple(35, "Stage 1", "Dynamic"),
                    Triple(45, "Stage 2", "G2 Stock"),
                    Triple(65, "Stage 3", "Extreme"),
                    Triple(99, "VMax", "Limitlos"),
                    Triple(120, "Hyper", "Verrückt")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    presets.chunked(3).forEach { rowPresets ->
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            rowPresets.forEach { (presetSpeed, label, sub) ->
                                val isSelected = selectedTuningSpeed == presetSpeed
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) KuKirinOrange else LightGrey)
                                        .border(
                                            1.dp,
                                            if (isSelected) KuKirinOrangeSubtle else BorderGrey,
                                            RoundedCornerShape(8.dp)
                                        )
                                        .clickable { onSpeedChange(presetSpeed) }
                                        .padding(vertical = 8.dp, horizontal = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            text = "$presetSpeed km/h",
                                            color = CustomWhite,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "$label ($sub)",
                                            color = if (isSelected) CustomWhite.copy(alpha = 0.8f) else CustomGrey,
                                            fontSize = 9.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Slider for manual custom adjustment
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "MANUELLE HÖCHSTGESCHWINDIGKEIT:",
                        color = CustomWhite,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "$selectedTuningSpeed km/h",
                        color = KuKirinOrange,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Slider(
                    value = selectedTuningSpeed.toFloat(),
                    onValueChange = { onSpeedChange(it.toInt()) },
                    valueRange = 20f..120f,
                    colors = SliderDefaults.colors(
                        activeTrackColor = KuKirinOrange,
                        thumbColor = KuKirinOrange,
                        inactiveTrackColor = LightGrey,
                        activeTickColor = Color.Transparent,
                        inactiveTickColor = Color.Transparent
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Action Buttons
            if (connectionState == ConnectionState.ACTIVATING_TURBO) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(LightGrey),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(color = KuKirinOrange, strokeWidth = 3.dp, modifier = Modifier.size(24.dp))
                        Text(
                            text = "Befehle werden übertragen...",
                            color = CustomWhite,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Activate or Update Tuning Button
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(64.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(KuKirinOrange)
                            .border(BorderStroke(1.dp, KuKirinOrangeSubtle), shape = RoundedCornerShape(12.dp))
                            .clickable { onActivate() },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Star,
                                contentDescription = null,
                                tint = CustomWhite,
                                modifier = Modifier.size(20.dp)
                            )
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = if (isTurboActive) "TUNING AKTUALISIEREN" else "TUNING AKTIVIEREN",
                                    color = CustomWhite,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Black
                                )
                                Text(
                                    text = "Limit: $selectedTuningSpeed km/h",
                                    color = CustomWhite.copy(alpha = 0.8f),
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }

                    // Reset Button (Only active if currently in turbo mode)
                    if (isTurboActive) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(64.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(LightGrey)
                                .border(BorderStroke(1.dp, BorderGrey), shape = RoundedCornerShape(12.dp))
                                .clickable { onDeactivate() },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = null,
                                    tint = ErrorRed,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = "DEAKTIVIEREN",
                                        color = CustomWhite,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                    Text(
                                        text = "Limit: 25 km/h",
                                        color = CustomGrey,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Legal Disclaimer & Safety Info
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(LightGrey, RoundedCornerShape(8.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = if (isTurboActive) KuKirinOrange else CustomGrey,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "HINWEIS: Das Entsperren des Tempolimits auf über 25 km/h ist nur für den Betrieb auf Privatgelände zulässig. Das Fahren mit offenem Limit im öffentlichen Straßenverkehr kann strafbar sein. Bitte tragen Sie Schutzkleidung und Helm!",
                    color = CustomGrey,
                    fontSize = 10.sp,
                    lineHeight = 13.sp
                )
            }
        }
    }
}

@Composable
fun TerminalLogPanel(
    logs: List<TerminalLog>,
    onClear: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(240.dp),
        colors = CardDefaults.cardColors(containerColor = SlateGrey),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, BorderGrey)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(KuKirinOrange)
                    )
                    Text(
                        text = "BLUETOOTH-KOMMUNIKATIONSPROTOKOLL",
                        color = CustomWhite,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                }

                Text(
                    text = "Löschen",
                    color = CustomGrey,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable { onClear() }
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .background(CarbonDark, RoundedCornerShape(6.dp))
                    .padding(8.dp)
            ) {
                if (logs.isEmpty()) {
                    Text(
                        text = "Keine Aktivitäten aufgezeichnet.",
                        color = CustomGrey,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        reverseLayout = false
                    ) {
                        items(logs, key = { it.id }) { log ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    text = "[${log.timestamp}] ",
                                    color = CustomGrey,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                                Text(
                                    text = log.message,
                                    color = when {
                                        log.isError -> ErrorRed
                                        log.isSuccess -> SuccessGreen
                                        else -> CustomWhite
                                    },
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    lineHeight = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
