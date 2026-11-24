package com.example.data

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.nio.charset.StandardCharsets
import java.util.UUID

data class DiscoveredBlePeer(
    val name: String,
    val address: String,
    val rssi: Int,
    val isDenizCraftPeer: Boolean,
    val isBonded: Boolean
)

data class BluetoothUiState(
    val hasBluetoothFeature: Boolean = false,
    val hasBleFeature: Boolean = false,
    val adapterAvailable: Boolean = false,
    val isBluetoothEnabled: Boolean = false,
    val hasRequiredPermissions: Boolean = false,
    val isScanning: Boolean = false,
    val isAdvertising: Boolean = false,
    val connectedPeerName: String? = null,
    val connectedPeerAddress: String? = null,
    val bondedDevices: List<DiscoveredBlePeer> = emptyList(),
    val scannedDevices: List<DiscoveredBlePeer> = emptyList(),
    val lastGattMessage: String? = null,
    val statusLog: String = "Bluetooth durumu kontrol ediliyor..."
)

class BluetoothMultiplayerManager(context: Context) {
    private val appContext = context.applicationContext
    private val bluetoothManager: BluetoothManager? =
        appContext.getSystemService(BluetoothManager::class.java)
    private val bluetoothAdapter: BluetoothAdapter?
        get() = bluetoothManager?.adapter

    private val _state = MutableStateFlow(BluetoothUiState())
    val state: StateFlow<BluetoothUiState> = _state.asStateFlow()

    private var scanner: BluetoothLeScanner? = null
    private var advertiser: BluetoothLeAdvertiser? = null
    private var gattServer: BluetoothGattServer? = null
    private var activeGatt: BluetoothGatt? = null
    private var receiverRegistered = false
    private var currentBroadcastPayload: String = "DenizCraft|Lobi"

    companion object {
        private const val TAG = "DenizCraftBT"
        val SERVICE_UUID: UUID = UUID.fromString("0000DC01-0000-1000-8000-00805F9B34FB")
        val CHAR_INVITE_UUID: UUID = UUID.fromString("0000DC02-0000-1000-8000-00805F9B34FB")

        fun requiredRuntimePermissions(): Array<String> =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                arrayOf(
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_ADVERTISE
                )
            } else {
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
            }
    }

    private val stateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                refreshHardwareAndPermissions()
            }
        }
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            val device = result.device ?: return
            val addr = device.address ?: return
            val devName = if (hasConnectPermission()) {
                device.name ?: result.scanRecord?.deviceName ?: "Bilinmeyen Cihaz"
            } else {
                result.scanRecord?.deviceName ?: "BLE Cihaz"
            }
            val isDc = result.scanRecord?.serviceUuids?.any { it.uuid == SERVICE_UUID } == true ||
                devName.contains("DenizCraft", ignoreCase = true)
            val peer = DiscoveredBlePeer(
                name = devName,
                address = addr,
                rssi = result.rssi,
                isDenizCraftPeer = isDc,
                isBonded = false
            )
            _state.update { cur ->
                val updated = cur.scannedDevices.filterNot { it.address == addr } + peer
                cur.copy(scannedDevices = updated.sortedByDescending { it.rssi })
            }
        }

        override fun onScanFailed(errorCode: Int) {
            _state.update {
                it.copy(
                    isScanning = false,
                    statusLog = "BLE taraması başlatılamadı (Hata kodu: $errorCode)"
                )
            }
        }
    }

    private val advertiseCallback = object : AdvertiseCallback() {
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
            _state.update {
                it.copy(
                    isAdvertising = true,
                    statusLog = "BLE yayını aktif! Yakındaki cihazlar seni görebilir."
                )
            }
        }

        override fun onStartFailure(errorCode: Int) {
            _state.update {
                it.copy(
                    isAdvertising = false,
                    statusLog = "BLE yayını başlatılamadı (Hata kodu: $errorCode)"
                )
            }
        }
    }

    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice?, status: Int, newState: Int) {
            val addr = device?.address ?: "Cihaz"
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                _state.update { it.copy(statusLog = "GATT Sunucusuna bağlandı: $addr") }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicReadRequest(
            device: BluetoothDevice?,
            requestId: Int,
            offset: Int,
            characteristic: BluetoothGattCharacteristic?
        ) {
            if (!hasConnectPermission()) return
            val bytes = currentBroadcastPayload.toByteArray(StandardCharsets.UTF_8)
            gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, bytes)
        }

        @SuppressLint("MissingPermission")
        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice?,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic?,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            val msg = value?.toString(StandardCharsets.UTF_8).orEmpty()
            _state.update { it.copy(lastGattMessage = "Gelen BLE Mesajı: $msg") }
            if (responseNeeded && hasConnectPermission()) {
                gattServer?.sendResponse(device, requestId, BluetoothGatt.GATT_SUCCESS, offset, value)
            }
        }
    }

    private val gattClientCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                val devName = if (hasConnectPermission()) gatt.device?.name ?: gatt.device?.address else gatt.device?.address
                _state.update {
                    it.copy(
                        connectedPeerName = devName,
                        connectedPeerAddress = gatt.device?.address,
                        statusLog = "Bağlandı: $devName (Servisler keşfediliyor...)"
                    )
                }
                if (hasConnectPermission()) {
                    gatt.discoverServices()
                }
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                _state.update {
                    it.copy(
                        connectedPeerName = null,
                        connectedPeerAddress = null,
                        statusLog = "Bluetooth cihaz bağlantısı kesildi."
                    )
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            val service = gatt.getService(SERVICE_UUID)
            if (service != null) {
                val char = service.getCharacteristic(CHAR_INVITE_UUID)
                if (char != null && hasConnectPermission()) {
                    @Suppress("DEPRECATION")
                    gatt.readCharacteristic(char)
                }
                _state.update { it.copy(statusLog = "DenizCraft BLE servisi doğrulandı!") }
            } else {
                _state.update { it.copy(statusLog = "Cihaza bağlandı (Genel GATT profili).") }
            }
        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                handleGattInviteRead(value)
            }
        }

        @Deprecated("Deprecated in API 33")
        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS && Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                @Suppress("DEPRECATION")
                handleGattInviteRead(characteristic.value)
            }
        }
    }

    private fun handleGattInviteRead(value: ByteArray?) {
        val txt = value?.toString(StandardCharsets.UTF_8).orEmpty()
        if (txt.isNotBlank()) {
            _state.update { it.copy(lastGattMessage = "Eşleşen Oda: $txt") }
        }
    }

    fun registerReceiver() {
        if (!receiverRegistered) {
            ContextCompat.registerReceiver(
                appContext,
                stateReceiver,
                IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
                ContextCompat.RECEIVER_EXPORTED
            )
            receiverRegistered = true
        }
        refreshHardwareAndPermissions()
    }

    fun refreshHardwareAndPermissions() {
        val pm = appContext.packageManager
        val hasBt = pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH)
        val hasBle = pm.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
        val adapter = bluetoothAdapter
        val permsOk = hasAllPermissions()
        val enabled = adapter?.isEnabled == true

        val bonded = if (adapter != null && enabled && hasConnectPermission()) {
            loadBondedDevices(adapter)
        } else {
            emptyList()
        }

        val statusMsg = when {
            adapter == null -> "Bu sanal cihazda fiziksel Bluetooth adaptörü yok (Sanal ortam kısıtlaması)."
            !permsOk -> "Bluetooth tarama ve bağlantı izinleri gerekiyor."
            !enabled -> "Bluetooth şu an kapalı. Devam etmek için etkinleştirin."
            else -> "Bluetooth hazır (${bonded.size} eşleşmiş cihaz)."
        }

        _state.update {
            it.copy(
                hasBluetoothFeature = hasBt,
                hasBleFeature = hasBle,
                adapterAvailable = adapter != null,
                isBluetoothEnabled = enabled,
                hasRequiredPermissions = permsOk,
                bondedDevices = bonded,
                statusLog = statusMsg
            )
        }
    }

    private fun hasAllPermissions(): Boolean =
        requiredRuntimePermissions().all { perm ->
            ContextCompat.checkSelfPermission(appContext, perm) == PackageManager.PERMISSION_GRANTED
        }

    private fun hasConnectPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else true

    @SuppressLint("MissingPermission")
    private fun loadBondedDevices(adapter: BluetoothAdapter): List<DiscoveredBlePeer> {
        return try {
            adapter.bondedDevices.orEmpty().map { dev ->
                DiscoveredBlePeer(
                    name = dev.name ?: "Eşleşmiş Cihaz",
                    address = dev.address ?: "",
                    rssi = 0,
                    isDenizCraftPeer = false,
                    isBonded = true
                )
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "Cannot read bonded devices: ${e.message}")
            emptyList()
        }
    }

    @SuppressLint("MissingPermission")
    fun startBleScan() {
        refreshHardwareAndPermissions()
        val adapter = bluetoothAdapter
        if (adapter == null || !adapter.isEnabled || !hasAllPermissions()) return
        val leScanner = adapter.bluetoothLeScanner
        if (leScanner == null) {
            _state.update { it.copy(statusLog = "Bu cihazda BLE tarayıcı desteklenmiyor.") }
            return
        }
        scanner = leScanner
        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .build()
        _state.update { it.copy(isScanning = true, scannedDevices = emptyList(), statusLog = "Yakındaki BLE cihazları taranıyor...") }
        leScanner.startScan(null, settings, scanCallback)
    }

    @SuppressLint("MissingPermission")
    fun stopBleScan() {
        if (!_state.value.isScanning) return
        try {
            if (hasAllPermissions()) {
                scanner?.stopScan(scanCallback)
            }
        } catch (e: Exception) {
            Log.w(TAG, "stopScan error: ${e.message}")
        }
        _state.update { it.copy(isScanning = false, statusLog = "Tarama durduruldu.") }
    }

    @SuppressLint("MissingPermission")
    fun toggleBleAdvertising(playerName: String, serverName: String) {
        refreshHardwareAndPermissions()
        val adapter = bluetoothAdapter ?: return
        if (!adapter.isEnabled || !hasAllPermissions()) return

        if (_state.value.isAdvertising) {
            stopBleAdvertising()
            return
        }

        currentBroadcastPayload = "DenizCraft|$playerName|$serverName"
        startGattServer()

        val leAdv = adapter.bluetoothLeAdvertiser
        if (leAdv == null) {
            _state.update { it.copy(statusLog = "Bu sanal/fiziksel cihazda BLE yayını (Peripheral) desteklenmiyor.") }
            return
        }
        advertiser = leAdv
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
            .build()
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .addServiceUuid(ParcelUuid(SERVICE_UUID))
            .build()
        leAdv.startAdvertising(settings, data, advertiseCallback)
    }

    @SuppressLint("MissingPermission")
    private fun startGattServer() {
        if (!hasConnectPermission() || gattServer != null) return
        val server = bluetoothManager?.openGattServer(appContext, gattServerCallback) ?: return
        val service = BluetoothGattService(SERVICE_UUID, BluetoothGattService.SERVICE_TYPE_PRIMARY)
        val char = BluetoothGattCharacteristic(
            CHAR_INVITE_UUID,
            BluetoothGattCharacteristic.PROPERTY_READ or BluetoothGattCharacteristic.PROPERTY_WRITE,
            BluetoothGattCharacteristic.PERMISSION_READ or BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        service.addCharacteristic(char)
        server.addService(service)
        gattServer = server
    }

    @SuppressLint("MissingPermission")
    fun stopBleAdvertising() {
        try {
            if (hasAllPermissions()) {
                advertiser?.stopAdvertising(advertiseCallback)
            }
            gattServer?.close()
            gattServer = null
        } catch (e: Exception) {
            Log.w(TAG, "stopAdvertising error: ${e.message}")
        }
        _state.update { it.copy(isAdvertising = false, statusLog = "BLE yayını durduruldu.") }
    }

    @SuppressLint("MissingPermission")
    fun connectToPeer(address: String) {
        val adapter = bluetoothAdapter ?: return
        if (!adapter.isEnabled || !hasConnectPermission()) return
        stopBleScan()
        activeGatt?.close()
        val device = adapter.getRemoteDevice(address) ?: return
        _state.update { it.copy(statusLog = "$address cihazına bağlanılıyor...") }
        activeGatt = device.connectGatt(appContext, false, gattClientCallback, BluetoothDevice.TRANSPORT_LE)
    }

    @SuppressLint("MissingPermission")
    fun sendRoomInviteOverGatt(payload: String) {
        val gatt = activeGatt ?: return
        if (!hasConnectPermission()) return
        val char = gatt.getService(SERVICE_UUID)?.getCharacteristic(CHAR_INVITE_UUID) ?: return
        val bytes = payload.take(64).toByteArray(StandardCharsets.UTF_8)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            gatt.writeCharacteristic(char, bytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT)
        } else {
            @Suppress("DEPRECATION")
            char.value = bytes
            @Suppress("DEPRECATION")
            gatt.writeCharacteristic(char)
        }
        _state.update { it.copy(statusLog = "Oda daveti BLE ile gönderildi: $payload") }
    }

    @SuppressLint("MissingPermission")
    fun release() {
        stopBleScan()
        stopBleAdvertising()
        try {
            if (hasConnectPermission()) {
                activeGatt?.disconnect()
                activeGatt?.close()
            }
        } catch (_: Exception) {
        }
        activeGatt = null
        if (receiverRegistered) {
            try {
                appContext.unregisterReceiver(stateReceiver)
            } catch (_: Exception) {
            }
            receiverRegistered = false
        }
    }
}
