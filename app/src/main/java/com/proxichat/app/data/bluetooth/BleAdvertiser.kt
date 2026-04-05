package com.proxichat.app.data.bluetooth

import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.os.ParcelUuid
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer

/**
 * Manages BLE advertising so other devices can discover us.
 * Advertises our custom service UUID along with encoded username in manufacturer data.
 */
class BleAdvertiser(private val bluetoothAdapter: BluetoothAdapter) {

    companion object {
        private const val TAG = "BleAdvertiser"
    }

    private var advertiser: BluetoothLeAdvertiser? = null
    private var callback: AdvertiseCallback? = null

    private val _isAdvertising = MutableStateFlow(false)
    val isAdvertising: StateFlow<Boolean> = _isAdvertising.asStateFlow()

    fun startAdvertising(displayName: String) {
        if (_isAdvertising.value) {
            Log.d(TAG, "Already advertising")
            return
        }

        advertiser = bluetoothAdapter.bluetoothLeAdvertiser
        if (advertiser == null) {
            Log.e(TAG, "BLE advertising not supported on this device")
            return
        }

        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
            .setConnectable(true)
            .setTimeout(0) // Advertise indefinitely
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .build()

        val serviceData = encodeDisplayName(displayName)

        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false) // We use custom service data for the name
            .setIncludeTxPowerLevel(false)
            .addServiceUuid(ParcelUuid(BluetoothConstants.SERVICE_UUID))
            .addServiceData(ParcelUuid(BluetoothConstants.SERVICE_UUID), serviceData)
            .build()

        val scanResponse = AdvertiseData.Builder()
            .setIncludeDeviceName(true)
            .setIncludeTxPowerLevel(true)
            .build()

        callback = object : AdvertiseCallback() {
            override fun onStartSuccess(settingsInEffect: AdvertiseSettings?) {
                Log.d(TAG, "Advertising started successfully")
                _isAdvertising.value = true
            }

            override fun onStartFailure(errorCode: Int) {
                Log.e(TAG, "Advertising failed with error code: $errorCode")
                _isAdvertising.value = false
            }
        }

        try {
            advertiser?.startAdvertising(settings, data, scanResponse, callback)
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing Bluetooth advertise permission", e)
        }
    }

    fun stopAdvertising() {
        try {
            callback?.let { advertiser?.stopAdvertising(it) }
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing Bluetooth advertise permission", e)
        }
        callback = null
        _isAdvertising.value = false
        Log.d(TAG, "Advertising stopped")
    }

    fun updateDisplayName(displayName: String) {
        if (_isAdvertising.value) {
            stopAdvertising()
            startAdvertising(displayName)
        }
    }

    private fun encodeDisplayName(name: String): ByteArray {
        val nameBytes = name.toByteArray(Charsets.UTF_8)
        val truncated = if (nameBytes.size > 20) nameBytes.copyOf(20) else nameBytes
        return ByteBuffer.allocate(1 + truncated.size)
            .put(BluetoothConstants.PROTOCOL_VERSION)
            .put(truncated)
            .array()
    }
}
