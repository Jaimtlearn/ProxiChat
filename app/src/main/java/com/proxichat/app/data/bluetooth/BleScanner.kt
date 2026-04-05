package com.proxichat.app.data.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.os.ParcelUuid
import android.util.Log
import com.proxichat.app.domain.model.ChatDevice
import com.proxichat.app.domain.model.ConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class BleScanner(private val bluetoothAdapter: BluetoothAdapter) {

    companion object {
        private const val TAG = "BleScanner"
        private const val RSSI_SMOOTHING = 0.7 // 70% new, 30% old — responsive to changes
    }

    private var scanner: BluetoothLeScanner? = null
    private var scanCallback: ScanCallback? = null
    private var pruneJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)
    private val ourServiceUuid = ParcelUuid(BluetoothConstants.SERVICE_UUID)

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<Map<String, ChatDevice>>(emptyMap())
    val discoveredDevices: StateFlow<Map<String, ChatDevice>> = _discoveredDevices.asStateFlow()

    // Pre-compute UUID bytes for raw scan record matching
    private val uuidBytesLE: ByteArray = run {
        val uuid = BluetoothConstants.SERVICE_UUID
        val msb = uuid.mostSignificantBits
        val lsb = uuid.leastSignificantBits
        val be = ByteArray(16)
        for (i in 0..7) {
            be[i] = (msb shr (56 - i * 8) and 0xFF).toByte()
            be[i + 8] = (lsb shr (56 - i * 8) and 0xFF).toByte()
        }
        be.reversedArray()
    }

    fun startScanning() {
        if (_isScanning.value) return

        scanner = bluetoothAdapter.bluetoothLeScanner
        if (scanner == null) {
            Log.e(TAG, "BLE scanner not available — is Bluetooth enabled?")
            return
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)
            .build()

        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                if (isOurDevice(result)) processResult(result)
            }

            override fun onBatchScanResults(results: List<ScanResult>) {
                results.forEach { if (isOurDevice(it)) processResult(it) }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "Scan failed, error code: $errorCode")
                _isScanning.value = false
            }
        }

        try {
            // Scan WITHOUT hardware filter — check UUID in callback instead.
            // Hardware UUID filters are broken on many Android devices for 128-bit UUIDs.
            scanner?.startScan(null, settings, scanCallback)
            _isScanning.value = true
            Log.d(TAG, "Scan started (no hardware filter)")

            pruneJob = scope.launch {
                while (isActive) {
                    delay(5_000)
                    pruneStaleDevices()
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing BLUETOOTH_SCAN permission", e)
        }
    }

    fun stopScanning() {
        try {
            scanCallback?.let { scanner?.stopScan(it) }
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing permission to stop scan", e)
        } catch (e: IllegalStateException) {
            Log.e(TAG, "Scanner in bad state", e)
        }
        scanCallback = null
        pruneJob?.cancel()
        _isScanning.value = false
    }

    fun clearDevices() {
        _discoveredDevices.value = emptyMap()
    }

    // Three-method UUID detection — handles all Android BLE stack quirks
    private fun isOurDevice(result: ScanResult): Boolean {
        val record = result.scanRecord ?: return false

        // Method 1: Parsed service UUIDs (works on most devices)
        val uuids = record.serviceUuids
        if (uuids != null && uuids.contains(ourServiceUuid)) return true

        // Method 2: Service data keyed by our UUID
        val svcData = record.serviceData
        if (svcData != null && svcData.containsKey(ourServiceUuid)) return true

        // Method 3: Parse raw advertisement bytes (handles buggy parsers)
        val raw = record.bytes
        if (raw != null && findUuidInAdBytes(raw)) return true

        return false
    }

    // Parse BLE AD structures: [length][type][data...] repeated
    // Type 0x06 = Incomplete 128-bit UUID list, 0x07 = Complete 128-bit UUID list
    private fun findUuidInAdBytes(bytes: ByteArray): Boolean {
        var i = 0
        while (i < bytes.size - 1) {
            val len = bytes[i].toInt() and 0xFF
            if (len == 0) break
            if (i + len >= bytes.size) break

            val type = bytes[i + 1].toInt() and 0xFF
            if (type == 0x06 || type == 0x07) {
                var off = i + 2
                val end = i + 1 + len
                while (off + 16 <= end) {
                    if (matchBytes(bytes, off, uuidBytesLE)) return true
                    off += 16
                }
            }
            i += len + 1
        }
        return false
    }

    private fun matchBytes(data: ByteArray, offset: Int, target: ByteArray): Boolean {
        for (j in target.indices) {
            if (data[offset + j] != target[j]) return false
        }
        return true
    }

    private fun processResult(result: ScanResult) {
        val device = result.device ?: return
        val address = try { device.address } catch (e: SecurityException) { return }

        val name = try {
            result.scanRecord?.deviceName ?: device.name ?: "ProxiChat User"
        } catch (e: SecurityException) { "ProxiChat User" }

        val current = _discoveredDevices.value[address]

        // RSSI smoothing: 70% new value + 30% old value (responsive but stable)
        val rssi = if (current != null) {
            (RSSI_SMOOTHING * result.rssi + (1 - RSSI_SMOOTHING) * current.rssi).toInt()
        } else {
            result.rssi
        }

        val chatDevice = ChatDevice(
            address = address,
            name = name,
            displayName = current?.displayName ?: name,
            rssi = rssi,
            connectionState = current?.connectionState ?: ConnectionState.DISCONNECTED,
            lastSeen = System.currentTimeMillis(),
            avatarColorIndex = address.hashCode().and(0x7)
        )

        _discoveredDevices.value = _discoveredDevices.value.toMutableMap().apply {
            put(address, chatDevice)
        }
    }

    private fun pruneStaleDevices() {
        val now = System.currentTimeMillis()
        val updated = _discoveredDevices.value.filter { (_, d) ->
            d.connectionState == ConnectionState.CONNECTED ||
                    (now - d.lastSeen) < BluetoothConstants.DEVICE_STALE_TIMEOUT_MS
        }
        if (updated.size != _discoveredDevices.value.size) {
            _discoveredDevices.value = updated
        }
    }

    fun updateDeviceConnectionState(address: String, state: ConnectionState) {
        val current = _discoveredDevices.value[address] ?: return
        _discoveredDevices.value = _discoveredDevices.value.toMutableMap().apply {
            put(address, current.copy(connectionState = state))
        }
    }
}
