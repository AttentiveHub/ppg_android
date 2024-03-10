package com.attentive_hub.polar_ppg.polar_ppg

import android.util.Log
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.errors.PolarInvalidArgument
import io.flutter.plugin.common.MethodChannel
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable

class BluetoothManager(private val api: PolarBleApi, private val mainMethodChannel:MethodChannel) {
    var scanDisposable: Disposable? = null

    private val tag = "PPG LEGO"

    fun startDeviceScan() {
        if (scanDisposable != null && !scanDisposable!!.isDisposed) {
            scanDisposable!!.dispose() // This will stop the current scan
        }

        // Start scanning
        scanDisposable = api.searchForDevice().observeOn(AndroidSchedulers.mainThread()).subscribe(
            { polarDeviceInfo ->
                // Device found, send information to Flutter
                mainMethodChannel.invokeMethod("onDeviceFound", mapOf(
                    "deviceId" to polarDeviceInfo.deviceId,
                    "name" to polarDeviceInfo.name
                ))
                Log.d(tag, polarDeviceInfo.deviceId)
            },
            { throwable ->
                Log.e("MainActivity", "Device scan error: $throwable")
            }
        )
    }

    fun connectToDevice(deviceId: String) {
        try {
            api.connectToDevice(deviceId)
        } catch (e: PolarInvalidArgument) {
            Log.e("MainActivity", "Error connecting to device: ${e.message}")
        }
    }

    fun disconnectFromDevice(deviceId: String) {
        try {
            api.disconnectFromDevice(deviceId)
        } catch (e: PolarInvalidArgument) {
            Log.e("MainActivity", "Error disconnecting from device: ${e.message}")
        }
    }
}
