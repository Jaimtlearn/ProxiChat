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
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Scans for nearby BLE devices advertising our ProxiChat service UUID.
 *
 * Scans WITHOUT hardware filters (buggy on many Android devices for 128-bit UUIDs)
 * and checks for our UUID in the callback using multiple detection methods.
 */
class BleScanner(private val bluetoothAdapter: BluetoothAdapter) {

    companion object {
        private const val TAG = "BleScanner"
        private const val RSSI_SMOOTHING = 0.3

        // Our UUID bytes in little-endian (BLE wire format) for raw byte matching
        private val SERVICE_UUID_BYTES_LE: ByteArray by lazy {
            val uuid = BluetoothConstants.SERVICE_UUID
            val bb = ByteBuffer.allocate(16).order(ByteOrder.BIG_ENDIAN)
            bb.putLong(uuid.mostSignificantBits)
            bb.putLong(uuid.leastSignificantBits)
            bb.array().reversedArray() // BLE transmits UUIDs in little-endian
        }
    }

    private var scanner: BluetoothLeScanner? = null
    private var scanCallback: ScanCallback? = null
    private var scanJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)
    private val ourServiceUuid = ParcelUuid(BluetoothConstants.SERVICE_UUID)

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<Map<String, ChatDevice>>(emptyMap())
    val discoveredDevices: StateFlow<Map<String, ChatDevice>> = _discoveredDevices.asStateFlow()

    fun startScanning() {
        if (_isScanning.value) return

        scanner = bluetoothAdapter.bluetoothLeScanner
        if (scanner == null) {
            Log.e(TAG, "BLE scanner not available")
            return
        }

        val settings = ScanSettings.Builder()
            .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
            .setReportDelay(0)
            .build()

        scanCallback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                processResult(result)
            }

            override fun onBatchScanResults(results: List<ScanResult>) {
                results.forEach { processResult(it) }
            }

            override fun onScanFailed(errorCode: Int) {
                Log.e(TAG, "Scan failed with error code: $errorCode")
                _isScanning.value = false
            }
        }

        try {
            // Scan WITHOUT hardware filter — check UUID in callback instead
            scanner?.startScan(null, settings, scanCallback)
            _isScanning.value = true
            Log.d(TAG, "BLE scan started")

            scanJob = scope.launch {
                while (isActive) {
                    delay(5_000)
                    pruneStaleDevices()
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing Bluetooth scan permission", e)
        }
    }

    fun stopScanning() {
        try {
            scanCallback?.let { scanner?.stopScan(it) }
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing Bluetooth scan permission", e)
        }
        scanCallback = null
        scanJob?.cancel()
        _isScanning.value = false
    }

    fun clearDevices() {
        _discoveredDevices.value = emptyMap()
    }

    private fun processResult(result: ScanResult) {
        // Check if this is a ProxiChat device using multiple methods
        if (!isProxiChatDevice(result)) return

        val device = result.device ?: return
        val address = try {
            device.address
        } catch (e: SecurityException) {
            return
        }

        val deviceName = try {
            result.scanRecord?.deviceName ?: device.name ?: "ProxiChat User"
        } catch (e: SecurityException) {
            "ProxiChat User"
        }

        val current = _discoveredDevices.value[address]

        val smoothedRssi = if (current != null) {
            ((1 - RSSI_SMOOTHING) * current.rssi + RSSI_SMOOTHING * result.rssi).toInt()
        } else {
            result.rssi
        }

        val chatDevice = ChatDevice(
            address = address,
            name = deviceName,
            displayName = current?.displayName ?: deviceName,
            rssi = smoothedRssi,
            connectionState = current?.connectionState ?: ConnectionState.DISCONNECTED,
            lastSeen = System.currentTimeMillis(),
            avatarColorIndex = address.hashCode().and(0x7)
        )

        _discoveredDevices.value = _discoveredDevices.value.toMutableMap().apply {
            put(address, chatDevice)
        }
    }

    /**
     * Checks if a scan result belongs to a ProxiChat device.
     * Uses 3 methods since Android's scanRecord.serviceUuids is null on many devices.
     */
    private fun isProxiChatDevice(result: ScanResult): Boolean {
        val scanRecord = result.scanRecord ?: return false

        // Method 1: Parsed service UUIDs (works on most devices)
        if (scanRecord.serviceUuids?.contains(ourServiceUuid) == true) return true

        // Method 2: Service data keys (if UUID is in service data)
        if (scanRecord.serviceData?.containsKey(ourServiceUuid) == true) return true

        // Method 3: Search raw advertisement bytes for our UUID (handles buggy parsers)
        val rawBytes = scanRecord.bytes ?: return false
        return containsUuidInRawBytes(rawBytes)
    }

    /**
     * Parses raw BLE advertisement bytes looking for our 128-bit service UUID.
     * BLE AD structure: [length][type][data...] repeated.
     * Type 0x06 = Incomplete 128-bit UUID list, 0x07 = Complete 128-bit UUID list.
     */
    private fun containsUuidInRawBytes(bytes: ByteArray): Boolean {
        var i = 0
        while (i < bytes.size) {
            val length = bytes[i].toInt() and 0xFF
            if (length == 0 || i + length >= bytes.size) break

            val type = bytes[i + 1].toInt() and 0xFF

            // 0x06 = Incomplete List of 128-bit UUIDs
            // 0x07 = Complete List of 128-bit UUIDs
            if (type == 0x06 || type == 0x07) {
                var offset = i + 2
                val end = i + 1 + length
                while (offset + 16 <= end) {
                    var match = true
                    for (j in 0 until 16) {
                        if (bytes[offset + j] != SERVICE_UUID_BYTES_LE[j]) {
                            match = false
                            break
                        }
                    }
                    if (match) return true
                    offset += 16
                }
            }
            i += length + 1
        }
        return false
    }

    private fun pruneStaleDevices() {
        val now = System.currentTimeMillis()
        val updated = _discoveredDevices.value.filter { (_, device) ->
            device.connectionState == ConnectionState.CONNECTED ||
                    (now - device.lastSeen) < BluetoothConstants.DEVICE_STALE_TIMEOUT_MS
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
