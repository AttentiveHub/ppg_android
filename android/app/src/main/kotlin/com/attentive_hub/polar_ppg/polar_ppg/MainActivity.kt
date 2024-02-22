package com.attentive_hub.polar_ppg.polar_ppg

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.PolarBleApiCallback
import com.polar.sdk.api.PolarBleApiDefaultImpl
import com.polar.sdk.api.errors.PolarInvalidArgument
import com.polar.sdk.api.model.PolarDeviceInfo
import com.polar.sdk.api.model.PolarPpgData
import io.flutter.embedding.android.FlutterActivity
import io.flutter.plugin.common.MethodChannel
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.Disposable
import java.util.*

class MainActivity: FlutterActivity() {
    private lateinit var api: PolarBleApi
    private val REQUEST_CODE_PERMISSIONS = 101
    private var deviceId: String? = null
    private lateinit var methodChannel: MethodChannel
    private var scanDisposable: Disposable? = null
    private var connectionStatus = "Disconnected"

    private fun startDeviceScan() {
        if (scanDisposable != null && !scanDisposable!!.isDisposed) {
            scanDisposable!!.dispose(); // This will stop the current scan
        }

        // Start scanning
        scanDisposable = api.searchForDevice().observeOn(AndroidSchedulers.mainThread()).subscribe(
            { polarDeviceInfo ->
                // Device found, send information to Flutter
                methodChannel.invokeMethod("onDeviceFound", mapOf(
                    "deviceId" to polarDeviceInfo.deviceId,
                    "name" to polarDeviceInfo.name
                ))
                Log.d("PPG LEGGO", polarDeviceInfo.deviceId)
            },
            { throwable ->
                Log.e("MainActivity", "Device scan error: $throwable")
            }
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        methodChannel = flutterEngine?.dartExecutor?.binaryMessenger?.let { MethodChannel(it, "com.attentive_hub.polar_ppg") }!!

        flutterEngine?.dartExecutor?.let { it ->
            MethodChannel(it.binaryMessenger, "com.attentive_hub.polar_ppg").setMethodCallHandler { call, result ->
                when (call.method) {
                    "startScan" -> {
                        startDeviceScan()
                        result.success(null)
                    }
                    "connectToDevice" -> {
                        val deviceId: String? = call.argument("deviceId")
                        deviceId?.let {
                            connectToDevice(it) // Assuming you have a connectToDevice function that takes a device ID
                            result.success(null)
                        } ?: result.error("INVALID_ID", "Device ID is null or invalid.", null)
                    }

                    else -> result.notImplemented()
                }
            }
        }

        checkPermissions()
    }

    private fun checkPermissions() {
        val requiredPermissions = mutableListOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            requiredPermissions.add(Manifest.permission.BLUETOOTH_SCAN)
            requiredPermissions.add(Manifest.permission.BLUETOOTH_CONNECT)
        }

        val allPermissionsGranted = requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        if (!allPermissionsGranted) {
            ActivityCompat.requestPermissions(
                this,
                requiredPermissions.toTypedArray(),
                REQUEST_CODE_PERMISSIONS
            )
        } else {
            // Initialize here if permissions are already granted at startup
            initializePolarSDK()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            initializePolarSDK()
        } else {
            Log.d("MyApp", "Permission not granted")
        }
    }

    private fun connectToDevice(deviceId: String) {
        try {
            api.connectToDevice(deviceId)
        } catch (e: PolarInvalidArgument) {
            Log.e("MainActivity", "Error connecting to device: ${e.message}")
        }
    }

    private fun initializePolarSDK() {
        api = PolarBleApiDefaultImpl.defaultImplementation(applicationContext, EnumSet.of(
            PolarBleApi.PolarBleSdkFeature.FEATURE_HR,
            PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_SDK_MODE,
            PolarBleApi.PolarBleSdkFeature.FEATURE_BATTERY_INFO,
            PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_H10_EXERCISE_RECORDING,
            PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_OFFLINE_RECORDING,
            PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING,
            PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_DEVICE_TIME_SETUP,
            PolarBleApi.PolarBleSdkFeature.FEATURE_DEVICE_INFO))
        api.setApiCallback(object : PolarBleApiCallback() {

            override fun blePowerStateChanged(powered: Boolean) {
                Log.d("MyApp", "BLE power: $powered")
            }

            override fun deviceConnected(polarDeviceInfo: PolarDeviceInfo) {
                Log.d("MyApp", "CONNECTED: ${polarDeviceInfo.deviceId}")
                deviceId = polarDeviceInfo.deviceId

                connectionStatus = "Connected"
                sendConnectionStatusToFlutter(connectionStatus)
            }

            override fun deviceConnecting(polarDeviceInfo: PolarDeviceInfo) {
                Log.d("MyApp", "CONNECTING: ${polarDeviceInfo.deviceId}")

                connectionStatus = "Connecting"
                sendConnectionStatusToFlutter(connectionStatus)
            }

            override fun deviceDisconnected(polarDeviceInfo: PolarDeviceInfo) {
                Log.d("MyApp", "DISCONNECTED: ${polarDeviceInfo.deviceId}")

                connectionStatus = "Disconnected"
                sendConnectionStatusToFlutter(connectionStatus)
            }

            override fun bleSdkFeatureReady(identifier: String, feature: PolarBleApi.PolarBleSdkFeature) {
                Log.d("MyApp", "Polar BLE SDK feature $feature is ready")

                if (feature == PolarBleApi.PolarBleSdkFeature.FEATURE_POLAR_ONLINE_STREAMING) {
                    // Start PPG streaming
                    enableSDKMode(identifier)
//                    startPPGStreaming(identifier)
                }
            }

            override fun disInformationReceived(identifier: String, uuid: UUID, value: String) {
                Log.d("MyApp", "DIS INFO uuid: $uuid value: $value")
            }

            override fun batteryLevelReceived(identifier: String, level: Int) {
                Log.d("MyApp", "BATTERY LEVEL: $level")
            }
        })
    }

    private fun sendConnectionStatusToFlutter(status: String) {
        runOnUiThread {
            methodChannel.invokeMethod(
                "updateConnectionStatus",
                status
            )
        }
    }

    @SuppressLint("CheckResult")
    private fun startPPGStreaming(deviceId: String) {
        api.requestStreamSettings(deviceId, PolarBleApi.PolarDeviceDataType.PPG)
            .toFlowable()
            .flatMap { settings ->
                api.startPpgStreaming(deviceId, settings.maxSettings())
            }
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { polarPpgData: PolarPpgData ->
                    if (polarPpgData.type == PolarPpgData.PpgDataType.PPG3_AMBIENT1) {
                        for (data in polarPpgData.samples) {
//                            Log.d("PPG LEGGO", "PPG ppg0: ${data.channelSamples[0]} ppg1: ${data.channelSamples[1]} ppg2: ${data.channelSamples[2]} ambient: ${data.channelSamples[3]} timeStamp: ${data.timeStamp}")
                            // Send data to Flutter
                            runOnUiThread {
                                methodChannel.invokeMethod("onPPGDataReceived", mapOf(
                                    "ppg0" to data.channelSamples[0].toString(),
                                    "ppg1" to data.channelSamples[1].toString(),
                                    "ppg2" to data.channelSamples[2].toString(),
                                    "ambient" to data.channelSamples[3].toString(),
                                    "timeStamp" to data.timeStamp.toString()
                                ))
                            }
                        }
                    }
                },
                { error: Throwable ->
                    Log.e("PPG LEGGO", "PPG stream failed. Reason $error")
                },
                { Log.d("PPG LEGGO", "PPG stream complete") }
            )
    }

    @SuppressLint("CheckResult")
    private fun enableSDKMode(deviceId: String){
        api.enableSDKMode(deviceId)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                {
                    Log.d("PPG LEGGO", "SDK mode enabled")
                    // at this point dispose all existing streams. SDK mode enable command
                    // stops all the streams but client is not informed. This is workaround
                    // for the bug.
//                    disposeAllStreams()
                },
                { error ->
                    Log.e("PPG LEGGO", "sdkmode err")
                }
            )
    }

    override fun onDestroy() {
        super.onDestroy()
        scanDisposable?.dispose()
        // Disconnect from the Polar device and perform cleanup
        api.shutDown()
    }
}
