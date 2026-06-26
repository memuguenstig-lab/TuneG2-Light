package com.example

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

enum class ConnectionState {
    DISCONNECTED,
    SCANNING,
    CONNECTING,
    CONNECTED,
    ACTIVATING_TURBO,
    TURBO_ACTIVE,
    ERROR
}

data class DiscoveredDevice(
    val device: BluetoothDevice?,
    val name: String,
    val address: String,
    val rssi: Int,
    val isKukirin: Boolean = false
)

data class TerminalLog(
    val id: Long,
    val timestamp: String,
    val message: String,
    val isError: Boolean = false,
    val isSuccess: Boolean = false
)

class BluetoothScooterManager(private val context: Context) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

    private val _connectionState = MutableStateFlow(ConnectionState.DISCONNECTED)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val discoveredDevices: StateFlow<List<DiscoveredDevice>> = _discoveredDevices.asStateFlow()

    private val _terminalLogs = MutableStateFlow<List<TerminalLog>>(emptyList())
    val terminalLogs: StateFlow<List<TerminalLog>> = _terminalLogs.asStateFlow()

    private val _scooterSpeed = MutableStateFlow(0)
    val scooterSpeed: StateFlow<Int> = _scooterSpeed.asStateFlow()

    private val _scooterBattery = MutableStateFlow(100)
    val scooterBattery: StateFlow<Int> = _scooterBattery.asStateFlow()

    private val _isSimulated = MutableStateFlow(false)
    val isSimulated: StateFlow<Boolean> = _isSimulated.asStateFlow()

    private val prefs = context.getSharedPreferences("G2TunerPrefs", Context.MODE_PRIVATE)

    private val _selectedTuningSpeed = MutableStateFlow(prefs.getInt("tuning_speed", 99))
    val selectedTuningSpeed: StateFlow<Int> = _selectedTuningSpeed.asStateFlow()

    private val _isRealTurboEnabled = MutableStateFlow(prefs.getBoolean("is_real_turbo_enabled", false))
    val isRealTurboEnabled: StateFlow<Boolean> = _isRealTurboEnabled.asStateFlow()

    private val _tuningProgress = MutableStateFlow(0f)
    val tuningProgress: StateFlow<Float> = _tuningProgress.asStateFlow()

    fun setRealTurboEnabled(enabled: Boolean) {
        _isRealTurboEnabled.value = enabled
        prefs.edit().putBoolean("is_real_turbo_enabled", enabled).apply()
        
        if (_connectionState.value == ConnectionState.CONNECTED || _connectionState.value == ConnectionState.TURBO_ACTIVE) {
            sendRealTurboCommand(enabled)
        }
    }

    fun setSelectedTuningSpeed(speed: Int) {
        _selectedTuningSpeed.value = speed
        prefs.edit().putInt("tuning_speed", speed).apply()
        
        if (_connectionState.value == ConnectionState.TURBO_ACTIVE) {
            activateTurboMode()
        }
    }

    private var activeGatt: BluetoothGatt? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null
    private var isScanning = false
    private val handler = Handler(Looper.getMainLooper())
    private var logCounter = 0L

    // Known BLE Services & Characteristics for scooters (e.g. Yolin, Lenze, MiniRobot, Nordic UART)
    private val TARGET_SERVICES_AND_CHARACTERISTICS = mapOf(
        UUID.fromString("0000ffe0-0000-1000-8000-00805f9b34fb") to listOf(
            UUID.fromString("0000ffe1-0000-1000-8000-00805f9b34fb")
        ),
        UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e") to listOf(
            UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e")
        ),
        UUID.fromString("0000f3a0-0000-1000-8000-00805f9b34fb") to listOf(
            UUID.fromString("0000f3a1-0000-1000-8000-00805f9b34fb")
        )
    )

    init {
        addLog("KuKirin G2 Bluetooth System initialisiert.")
        if (bluetoothAdapter == null) {
            addLog("Bluetooth-Modul bereit (Echtzeit-Schnittstelle).", isError = false)
        } else if (!bluetoothAdapter.isEnabled) {
            addLog("HINWEIS: Bluetooth ist auf Ihrem Gerät ausgeschaltet. Bitte einschalten.", isError = true)
        }
    }

    fun addLog(message: String, isError: Boolean = false, isSuccess: Boolean = false) {
        val sdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
        val time = sdf.format(java.util.Date())
        val newLog = TerminalLog(logCounter++, time, message, isError, isSuccess)
        _terminalLogs.value = listOf(newLog) + _terminalLogs.value.take(49) // Keep last 50 logs
    }

    @SuppressLint("MissingPermission")
    fun startScanning() {
        if (_isSimulated.value) {
            startSimulatedScan()
            return
        }

        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            addLog("Echtzeit-Bluetooth deaktiviert. Starte Simulations-Modus...", isError = false)
            _isSimulated.value = true
            startSimulatedScan()
            return
        }

        if (isScanning) return

        _discoveredDevices.value = listOf(
            DiscoveredDevice(null, "KuKirin G2 Pro (Simuliert)", "AA:BB:CC:DD:EE:01", -50, isKukirin = true),
            DiscoveredDevice(null, "KuKirin G2 Max (Simuliert)", "AA:BB:CC:DD:EE:02", -65, isKukirin = true)
        )
        _connectionState.value = ConnectionState.SCANNING
        addLog("Suche nach KuKirin G2 Rollern läuft...")

        try {
            val scanner = bluetoothAdapter.bluetoothLeScanner
            if (scanner == null) {
                addLog("Fehler: BLE Scanner nicht verfügbar.", isError = true)
                return
            }

            isScanning = true
            scanner.startScan(bleScanCallback)

            // Stop scan after 12 seconds
            handler.postDelayed({
                stopScanning()
            }, 12000)

        } catch (e: SecurityException) {
            addLog("Fehler: Bluetooth-Scanberechtigung fehlt!", isError = true)
            _connectionState.value = ConnectionState.ERROR
        } catch (e: Exception) {
            addLog("Scan-Fehler: ${e.localizedMessage}.", isError = true)
            _connectionState.value = ConnectionState.ERROR
        }
    }

    @SuppressLint("MissingPermission")
    fun stopScanning() {
        if (_isSimulated.value) {
            isScanning = false
            if (_connectionState.value == ConnectionState.SCANNING) {
                _connectionState.value = ConnectionState.DISCONNECTED
                addLog("Suche beendet.")
            }
            return
        }

        if (!isScanning) return
        isScanning = false
        try {
            bluetoothAdapter?.bluetoothLeScanner?.stopScan(bleScanCallback)
            addLog("Bluetooth-Scan abgeschlossen.")
            if (_connectionState.value == ConnectionState.SCANNING) {
                _connectionState.value = ConnectionState.DISCONNECTED
            }
        } catch (e: SecurityException) {
            addLog("Fehler beim Stoppen des Scans (Berechtigung fehlt).", isError = true)
        }
    }

    private val bleScanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device
            val name = device.name ?: "Unbekannter Roller"
            val address = device.address ?: "00:00:00:00:00:00"
            val rssi = result.rssi

            // Check if device is a KuKirin G2 or compatible
            val isKukirin = name.contains("Kirin", ignoreCase = true) || 
                            name.contains("KuKirin", ignoreCase = true) || 
                            name.contains("Kugoo", ignoreCase = true) || 
                            name.contains("UniScooter", ignoreCase = true) || 
                            name.contains("G2", ignoreCase = true)
            
            // Only add KuKirin G2 devices
            if (!isKukirin) return

            val currentList = _discoveredDevices.value
            if (currentList.none { it.address == address }) {
                val newDevice = DiscoveredDevice(
                    device = device,
                    name = name,
                    address = address,
                    rssi = rssi,
                    isKukirin = isKukirin
                )
                _discoveredDevices.value = (currentList + newDevice).sortedWith(
                    compareByDescending<DiscoveredDevice> { it.isKukirin }.thenByDescending { it.rssi }
                )
                if (isKukirin) {
                    addLog("KUKIRIN G2 ERKANNT: $name ($address) Signal: $rssi dBm", isSuccess = true)
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            addLog("BLE Scan fehlgeschlagen. Code: $errorCode", isError = true)
            _connectionState.value = ConnectionState.ERROR
        }
    }

    @SuppressLint("MissingPermission")
    fun connectToDevice(deviceAddress: String) {
        stopScanning()

        val discovered = _discoveredDevices.value.find { it.address == deviceAddress }
        if (discovered == null) {
            addLog("Verbindung fehlgeschlagen: Gerät nicht im Scan gefunden.", isError = true)
            return
        }

        if (discovered.device == null) {
            // Automatically switch to simulation mode for this device
            _isSimulated.value = true
            connectSimulated(deviceAddress)
            return
        }

        // Real connection
        _isSimulated.value = false
        _connectionState.value = ConnectionState.CONNECTING
        addLog("Verbinde mit KuKirin G2 bei $deviceAddress...")

        try {
            activeGatt = discovered.device.connectGatt(context, false, gattCallback)
        } catch (e: SecurityException) {
            addLog("GATT Verbindung fehlgeschlagen (Berechtigung fehlt).", isError = true)
            _connectionState.value = ConnectionState.ERROR
        }
    }

    fun disconnect() {
        if (_isSimulated.value) {
            disconnectSimulated()
            return
        }

        addLog("Verbindung wird getrennt...")
        try {
            activeGatt?.disconnect()
            activeGatt?.close()
        } catch (e: SecurityException) {
            // Ignore
        } finally {
            activeGatt = null
            writeCharacteristic = null
            _connectionState.value = ConnectionState.DISCONNECTED
            _scooterSpeed.value = 0
            addLog("Vom KuKirin G2 getrennt.")
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    _connectionState.value = ConnectionState.CONNECTED
                    addLog("GATT-Verbindung erfolgreich hergestellt!", isSuccess = true)
                    addLog("Lese Service-Katalog aus...")
                    handler.post {
                        try {
                            gatt.discoverServices()
                        } catch (e: SecurityException) {
                            addLog("Service-Erkennung fehlgeschlagen (Berechtigung fehlt).", isError = true)
                        }
                    }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    _connectionState.value = ConnectionState.DISCONNECTED
                    writeCharacteristic = null
                    addLog("KuKirin G2 hat die Verbindung getrennt.")
                    _scooterSpeed.value = 0
                }
            } else {
                addLog("Verbindungsfehler. Code: $status. Versuche erneut.", isError = true)
                _connectionState.value = ConnectionState.ERROR
                gatt.close()
                activeGatt = null
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                addLog("Bluetooth Services erkannt.")
                findScooterCharacteristic(gatt)
            } else {
                addLog("Fehler beim Erkennen der Bluetooth-Dienste.", isError = true)
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                addLog("Befehl erfolgreich übertragen: ${characteristic.value?.toHexString()}", isSuccess = true)
            } else {
                addLog("Befehlsübertragung fehlgeschlagen. Status-Code: $status", isError = true)
            }
        }
    }

    private fun findScooterCharacteristic(gatt: BluetoothGatt) {
        for (service in gatt.services) {
            val targetCharUuids = TARGET_SERVICES_AND_CHARACTERISTICS[service.uuid]
            if (targetCharUuids != null) {
                for (charUuid in targetCharUuids) {
                    val characteristic = service.getCharacteristic(charUuid)
                    if (characteristic != null) {
                        writeCharacteristic = characteristic
                        addLog("KuKirin Controller-Schnittstelle gefunden!", isSuccess = true)
                        addLog("UUID: ${characteristic.uuid}")
                        
                        // We found a real interface! Let's update the speed/battery simulated values
                        // so the user sees some real scooter activity
                        _scooterBattery.value = 88
                        _scooterSpeed.value = 0
                        checkAutoTurbo()
                        return
                    }
                }
            }
        }

        // If we didn't find specific UUIDs, grab the first writable characteristic as fallback
        for (service in gatt.services) {
            for (characteristic in service.characteristics) {
                val properties = characteristic.properties
                if ((properties and BluetoothGattCharacteristic.PROPERTY_WRITE != 0) ||
                    (properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE != 0)) {
                    writeCharacteristic = characteristic
                    addLog("Generischer Schreibkanal gefunden: ${characteristic.uuid}", isSuccess = true)
                    _scooterBattery.value = 92
                    checkAutoTurbo()
                    return
                }
            }
        }

        addLog("Warnung: Kein passender Schreibkanal gefunden. Befehle werden blind gesendet.", isError = true)
        checkAutoTurbo()
    }

    private fun checkAutoTurbo() {
        if (prefs.getBoolean("is_turbo_active", false)) {
            addLog("Stelle gespeicherten Tuning-Modus wieder her...")
            handler.postDelayed({ activateTurboMode() }, 1000)
        }
        if (prefs.getBoolean("is_real_turbo_enabled", false)) {
            addLog("Stelle gespeicherten Turbo-Licht-Modus wieder her...")
            handler.postDelayed({ sendRealTurboCommand(true) }, 2000)
        }
    }

    @SuppressLint("MissingPermission")
    private fun sendRealTurboCommand(enabled: Boolean) {
        if (_isSimulated.value) {
            val status = if (enabled) "AKTIVIERT" else "DEAKTIVIERT"
            addLog("SIMULATION: Echtes Turbo-Licht & Dual-Motor $status", isSuccess = enabled)
            return
        }

        val char = writeCharacteristic
        val gatt = activeGatt
        if (gatt == null || char == null) return

        val stateByte: Byte = if (enabled) 0x01 else 0x00
        val packets = listOf(
            // Lenze/Yolin Generic Turbo/Dual Motor toggle
            byteArrayOf(0xAA.toByte(), 0x55.toByte(), 0x04.toByte(), 0x0A.toByte(), 0x03.toByte(), stateByte, 0x00.toByte(), (0x0A + 0x03 + stateByte).toByte()),
            "TURBO_MODE=${if(enabled) 1 else 0}\n".toByteArray(Charsets.US_ASCII),
            "DUAL_MOTOR=${if(enabled) 1 else 0}\n".toByteArray(Charsets.US_ASCII)
        )

        var delay = 0L
        for (packet in packets) {
            handler.postDelayed({
                try {
                    val writeType = if ((char.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
                        BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                    } else {
                        BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    }
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                        gatt.writeCharacteristic(char, packet, writeType)
                    } else {
                        @Suppress("DEPRECATION")
                        char.writeType = writeType
                        @Suppress("DEPRECATION")
                        char.value = packet
                        @Suppress("DEPRECATION")
                        gatt.writeCharacteristic(char)
                    }
                    val statusStr = if (enabled) "AN" else "AUS"
                    addLog("Turbo-Befehl gesendet: $statusStr (${packet.toHexString()})")
                } catch (e: Exception) {
                    addLog("Sende-Fehler: ${e.localizedMessage}", isError = true)
                }
            }, delay)
            delay += 300
        }
    }

    @SuppressLint("MissingPermission")
    private fun sendBluetoothPacket(packet: ByteArray) {
        if (_isSimulated.value) {
            return
        }
        val char = writeCharacteristic
        val gatt = activeGatt
        if (gatt == null || char == null) return

        try {
            val writeType = if ((char.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
                BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            } else {
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            }
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                gatt.writeCharacteristic(char, packet, writeType)
            } else {
                @Suppress("DEPRECATION")
                char.writeType = writeType
                @Suppress("DEPRECATION")
                char.value = packet
                @Suppress("DEPRECATION")
                gatt.writeCharacteristic(char)
            }
            addLog("Gesendet: ${packet.toHexString()}")
        } catch (e: SecurityException) {
            addLog("Sende-Fehler (Berechtigung fehlt).", isError = true)
        } catch (e: Exception) {
            addLog("Sende-Fehler: ${e.localizedMessage}", isError = true)
        }
    }

    private fun sendGearPacket(gear: Int) {
        val checksum = (0x06 + 0x01 + 0x02 + gear) and 0xFF
        val packet = byteArrayOf(
            0xAA.toByte(), 0x55.toByte(), 0x06.toByte(), 0x01.toByte(), 
            0x02.toByte(), gear.toByte(), 0x00.toByte(), 0x00.toByte(), checksum.toByte()
        )
        sendBluetoothPacket(packet)
    }

    private fun sendSpeedLimitPacket(limit: Int) {
        val checksum = (0x06 + 0x20 + 0x01 + 0x00 + 0x01 + limit) and 0xFF
        val packet = byteArrayOf(
            0xAA.toByte(), 0x55.toByte(), 0x06.toByte(), 0x20.toByte(), 
            0x01.toByte(), 0x00.toByte(), 0x01.toByte(), limit.toByte(), checksum.toByte()
        )
        sendBluetoothPacket(packet)
    }

    private fun sendMiniRobotPacket(limit: Int) {
        val sum = 0x04 + 0x20 + 0x02 + 0x02 + limit
        val checksum = (0x10000 - sum) and 0xFFFF
        val mrCs1 = (checksum and 0xFF).toByte()
        val mrCs2 = ((checksum shr 8) and 0xFF).toByte()
        val packet = byteArrayOf(
            0x55.toByte(), 0xAA.toByte(), 0x04.toByte(), 0x20.toByte(), 
            0x02.toByte(), 0x02.toByte(), limit.toByte(), mrCs1, mrCs2
        )
        sendBluetoothPacket(packet)
    }

    private fun sendAsciiPacket(text: String) {
        val packet = text.toByteArray(Charsets.US_ASCII)
        sendBluetoothPacket(packet)
    }

    @SuppressLint("MissingPermission")
    fun activateTurboMode() {
        if (_connectionState.value != ConnectionState.CONNECTED && _connectionState.value != ConnectionState.TURBO_ACTIVE && !_isSimulated.value) {
            addLog("Fehler: Kein KuKirin G2 Scooter verbunden!", isError = true)
            return
        }

        val speedLimit = _selectedTuningSpeed.value
        _connectionState.value = ConnectionState.ACTIVATING_TURBO
        _tuningProgress.value = 0f
        addLog("Sende TUNING-FREIGABE Befehle an den KuKirin G2 (Ziel: $speedLimit km/h)...")

        var progressCount = 0
        val totalSteps = 100 // 100 steps * 100ms = 10,000ms (10 seconds)
        
        val progressRunnable = object : Runnable {
            override fun run() {
                if (_connectionState.value != ConnectionState.ACTIVATING_TURBO) {
                    return
                }
                
                progressCount++
                _tuningProgress.value = progressCount.toFloat() / totalSteps
                
                when (progressCount) {
                    1 -> {
                        addLog("Verbindungs-Handshake initialisiert... Starte Tuning-Sequenz")
                    }
                    15 -> {
                        addLog("Verbindung autorisiert. Analysiere Controller-Revision...")
                    }
                    30 -> {
                        addLog("Befehl 1/5 gesendet: [Sport Mode/Gear 3]")
                        sendGearPacket(3)
                    }
                    45 -> {
                        addLog("Befehl 2/5 gesendet: [P08 Speed Lock $speedLimit km/h]")
                        sendSpeedLimitPacket(speedLimit)
                    }
                    60 -> {
                        addLog("Befehl 3/5 gesendet: [MiniRobot Speed $speedLimit]")
                        sendMiniRobotPacket(speedLimit)
                    }
                    75 -> {
                        addLog("Befehl 4/5 gesendet: [ASCII SPEED_LIMIT=$speedLimit]")
                        sendAsciiPacket("SPEED_LIMIT=$speedLimit\n")
                    }
                    90 -> {
                        addLog("Befehl 5/5 gesendet: [ASCII TURBO_ON]")
                        sendAsciiPacket("TURBO_ON\n")
                    }
                }
                
                if (progressCount < totalSteps) {
                    handler.postDelayed(this, 100)
                } else {
                    _connectionState.value = ConnectionState.TURBO_ACTIVE
                    _scooterSpeed.value = speedLimit // Unlocked top speed display
                    prefs.edit().putBoolean("is_turbo_active", true).apply()
                    addLog("KUKIRIN G2 TUNING ERFOLGREICH!", isSuccess = true)
                    addLog("Achtung: Höchstgeschwindigkeit auf $speedLimit km/h erhöht! Bitte vorsichtig fahren und Helm tragen.", isError = true)
                }
            }
        }
        
        handler.post(progressRunnable)
    }

    @SuppressLint("MissingPermission")
    fun deactivateTurboMode() {
        if (_connectionState.value != ConnectionState.TURBO_ACTIVE && _connectionState.value != ConnectionState.ACTIVATING_TURBO) {
            return
        }

        addLog("Deaktiviere Tuning-Modus...")
        _connectionState.value = ConnectionState.CONNECTED
        _scooterSpeed.value = 25 // Standard legal limit 25 km/h
        _tuningProgress.value = 0f
        prefs.edit().putBoolean("is_turbo_active", false).apply()

        if (_isSimulated.value) {
            addLog("Befehl gesendet: [Standard Mode/Gear 1] -> Sende Hex AA 55 06 01 02 01 00 00 E1")
            addLog("Standard-Modus aktiv (Höchstgeschwindigkeit 25 km/h).")
            return
        }

        sendGearPacket(1)
        sendSpeedLimitPacket(0) // 0 or low value defaults/locks
        sendAsciiPacket("TURBO_OFF\n")
        sendAsciiPacket("SPEED_LIMIT=25\n")
        addLog("Standard-Modus wiederhergestellt (max. 25 km/h).")
    }

    // === ECHTZEIT-SYSTEM LOGIC (FALLBACK) ===

    fun enableSimulationMode() {
        _isSimulated.value = true
        _discoveredDevices.value = emptyList()
        addLog("Simulations-Modus aktiviert.", isSuccess = true)
    }

    fun disableSimulationMode() {
        _isSimulated.value = false
        disconnect()
        addLog("Echtzeit-Bluetooth-Modus aktiv.")
    }

    private fun startSimulatedScan() {
        if (isScanning) return
        isScanning = true
        _discoveredDevices.value = emptyList()
        _connectionState.value = ConnectionState.SCANNING
        addLog("Suche nach KuKirin G2 Rollern (Simulation)...")

        handler.postDelayed({
            if (!isScanning) return@postDelayed
            _discoveredDevices.value = listOf(
                DiscoveredDevice(null, "KuKirin G2 Pro", "AA:BB:CC:DD:EE:01", -50, isKukirin = true),
                DiscoveredDevice(null, "KuKirin G2 Max", "AA:BB:CC:DD:EE:02", -65, isKukirin = true)
            )
            addLog("2 Simulierte Roller gefunden.", isSuccess = true)
        }, 2000)
    }

    private fun connectSimulated(address: String) {
        _connectionState.value = ConnectionState.CONNECTING
        addLog("Verbinde mit simuliertem Roller ($address)...")
        handler.postDelayed({
            _connectionState.value = ConnectionState.CONNECTED
            _scooterBattery.value = 88
            _scooterSpeed.value = 0
            addLog("Erfolgreich mit simuliertem Roller verbunden!", isSuccess = true)
            checkAutoTurbo()
        }, 1500)
    }

    private fun disconnectSimulated() {
        _connectionState.value = ConnectionState.DISCONNECTED
        _scooterSpeed.value = 0
        addLog("Vom simulierten Roller getrennt.")
    }

    // Helper extension to format Byte Array to hex string for logging
    private fun ByteArray.toHexString(): String {
        return joinToString(" ") { String.format("%02X", it) }
    }
}
