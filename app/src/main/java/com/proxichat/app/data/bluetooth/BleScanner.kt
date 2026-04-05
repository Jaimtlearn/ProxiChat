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

/**
 * Scans for nearby BLE devices advertising our ProxiChat service UUID.
 *
 * Note: Many Android devices have buggy 128-bit UUID scan filters, so we scan
 * WITHOUT filters and check for our service UUID in the callback instead.
 */
class BleScanner(private val bluetoothAdapter: BluetoothAdapter) {

    companion object {
        private const val TAG = "BleScanner"
        private const val RSSI_SMOOTHING = 0.3 // Lower = smoother, higher = more responsive
    }

    private var scanner: BluetoothLeScanner? = null
    private var scanCallback: ScanCallback? = null
    private var scanJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _discoveredDevices = MutableStateFlow<Map<String, ChatDevice>>(emptyMap())
    val discoveredDevices: StateFlow<Map<String, ChatDevice>> = _discoveredDevices.asStateFlow()

    private val ourServiceUuid = ParcelUuid(BluetoothConstants.SERVICE_UUID)

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
            // Scan WITHOUT filters — filter in callback instead.
            // Many Android devices fail to match 128-bit service UUIDs in hardware filters.
            scanner?.startScan(null, settings, scanCallback)
            _isScanning.value = true
            Log.d(TAG, "BLE scan started (no filter, checking UUID in callback)")

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
        // Check if this device is advertising our ProxiChat service UUID
        val serviceUuids = result.scanRecord?.serviceUuids
        if (serviceUuids == null || !serviceUuids.contains(ourServiceUuid)) {
            return // Not a ProxiChat device, ignore
        }

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

        // Smooth RSSI to prevent jittering
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
