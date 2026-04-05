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

/**
 * Manages BLE advertising so other devices can discover us.
 *
 * BLE advertisement packets are limited to 31 bytes. A 128-bit service UUID
 * alone takes 18 bytes, so we keep the main packet minimal (just the UUID)
 * and put the device name in the scan response packet.
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
            .setTimeout(0)
            .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
            .build()

        // Main advertisement: ONLY the service UUID (18 bytes, fits in 31-byte limit)
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false)
            .setIncludeTxPowerLevel(false)
            .addServiceUuid(ParcelUuid(BluetoothConstants.SERVICE_UUID))
            .build()

        // Scan response: device name (sent when scanner requests more info)
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
}
