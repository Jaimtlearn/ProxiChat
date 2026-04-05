package com.proxichat.app.data.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
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

/**
 * Scans for nearby BLE devices advertising our ProxiChat service UUID.
 * Maintains a list of discovered devices with RSSI signal strength.
 */
class BleScanner(private val bluetoothAdapter: BluetoothAdapter) {

    companion object {
        private const val TAG = "BleScanner"
    }

    private var scanner: BluetoothLeScanner? = null
    private var scanCallback: ScanCallback? = null
    private var scanJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

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

        val filters = listOf(
            ScanFilter.Builder()
                .setServiceUuid(ParcelUuid(BluetoothConstants.SERVICE_UUID))
                .build()
        )

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
            scanner?.startScan(filters, settings, scanCallback)
            _isScanning.value = true
            Log.d(TAG, "BLE scan started")

            // Periodically remove stale devices
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
        Log.d(TAG, "BLE scan stopped")
    }

    fun clearDevices() {
        _discoveredDevices.value = emptyMap()
    }

    private fun processResult(result: ScanResult) {
        val device = result.device ?: return
        val address = try {
            device.address
        } catch (e: SecurityException) {
            return
        }

        val displayName = extractDisplayName(result)
        val deviceName = try {
            device.name ?: "Unknown Device"
        } catch (e: SecurityException) {
            "Unknown Device"
        }

        val current = _discoveredDevices.value[address]
        val chatDevice = ChatDevice(
            address = address,
            name = deviceName,
            displayName = displayName ?: deviceName,
            rssi = result.rssi,
            connectionState = current?.connectionState ?: ConnectionState.DISCONNECTED,
            lastSeen = System.currentTimeMillis(),
            avatarColorIndex = address.hashCode().and(0x7) // 0-7 color index
        )

        _discoveredDevices.value = _discoveredDevices.value.toMutableMap().apply {
            put(address, chatDevice)
        }
    }

    private fun extractDisplayName(result: ScanResult): String? {
        val serviceData = result.scanRecord?.getServiceData(ParcelUuid(BluetoothConstants.SERVICE_UUID))
            ?: return null

        if (serviceData.size < 2) return null

        val buffer = ByteBuffer.wrap(serviceData)
        val version = buffer.get()
        if (version != BluetoothConstants.PROTOCOL_VERSION) return null

        val nameBytes = ByteArray(serviceData.size - 1)
        buffer.get(nameBytes)
        return String(nameBytes, Charsets.UTF_8).trim()
    }

    private fun pruneStaleDevices() {
        val now = System.currentTimeMillis()
        val updated = _discoveredDevices.value.filter { (_, device) ->
            // Keep connected devices and recently seen devices
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
