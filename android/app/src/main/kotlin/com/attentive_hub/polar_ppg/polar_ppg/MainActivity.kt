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
import com.polar.sdk.api.model.PolarAccelerometerData
import com.polar.sdk.api.model.PolarDeviceInfo
import com.polar.sdk.api.model.PolarEcgData
import com.polar.sdk.api.model.PolarGyroData
import com.polar.sdk.api.model.PolarHrData
import com.polar.sdk.api.model.PolarMagnetometerData
import com.polar.sdk.api.model.PolarPpgData
import com.polar.sdk.api.model.PolarPpiData
import com.polar.sdk.api.model.PolarSensorSetting
import io.flutter.embedding.android.FlutterActivity
import io.flutter.plugin.common.MethodChannel
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import java.util.*

class MainActivity: FlutterActivity() {
    private lateinit var api: PolarBleApi
    private val requestCodePermissions = 101
    private lateinit var methodChannel: MethodChannel
    private val tag = "PPG LEGO"

    private var scanDisposable: Disposable? = null
    private var connectionStatus = "Disconnected"
    private var connectedDevice: String? = null

    private var SDKMode: Boolean = false
    private var record: Boolean = false

    private var hrDisposable: Disposable? = null
    private var ecgDisposable: Disposable? = null
    private var accDisposable: Disposable? = null
    private var ppgDisposable: Disposable? = null
    private var ppiDisposable: Disposable? = null
    private var gyrDisposable: Disposable? = null
    private var magDisposable: Disposable? = null
    private var streamingDisposables = CompositeDisposable()

    private fun startDeviceScan() {
        if (scanDisposable != null && !scanDisposable!!.isDisposed) {
            scanDisposable!!.dispose() // This will stop the current scan
        }

        // Start scanning
        scanDisposable = api.searchForDevice().observeOn(AndroidSchedulers.mainThread()).subscribe(
            { polarDeviceInfo ->
                // Device found, send information to Flutter
                methodChannel.invokeMethod("onDeviceFound", mapOf(
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
                        val deviceId : String ? = call.argument("deviceId")
                        deviceId?.let {
                            connectToDevice(it) // Assuming you have a connectToDevice function that takes a device ID
                            result.success(null)
                        } ?: result.error("INVALID_ID", "Device ID is null or invalid.", null)
                    }
                    "disconnectFromDevice" -> {
                        val deviceId : String ? = call.argument("deviceId")
                        deviceId?.let {
                            disconnectFromDevice(it) // Assuming you have a connectToDevice function that takes a device ID
                            result.success(null)
                        } ?: result.error("INVALID_ID", "Device ID is null or invalid.", null)
                    }
                    "toggleSDKMode" -> {
                        toggleSDKMode()
                        result.success(null)
                    }
                    "toggleRecord" -> {
                        toggleRecord()
                        result.success(null)
                    }
                    "startListeningToChannels" -> {
                        val channels: List<String>? = call.arguments()
                        startListeningToChannels(channels)
                        result.success(null)
                    }
                    "stopListeningToChannels" -> {
                        stopListeningToChannels()
                        result.success(null)
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
                requestCodePermissions
            )
        } else {
            // Initialize here if permissions are already granted at startup
            initializePolarSDK()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == requestCodePermissions && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
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

    private fun disconnectFromDevice(deviceId: String) {
        try {
            api.disconnectFromDevice(deviceId)
        } catch (e: PolarInvalidArgument) {
            Log.e("MainActivity", "Error disconnecting from device: ${e.message}")
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
                connectedDevice = polarDeviceInfo.deviceId

                connectionStatus = "Connected"
                sendConnectionStatusToFlutter(connectionStatus, polarDeviceInfo.deviceId)
            }

            override fun deviceConnecting(polarDeviceInfo: PolarDeviceInfo) {
                Log.d("MyApp", "CONNECTING: ${polarDeviceInfo.deviceId}")

                connectionStatus = "Connecting"
                sendConnectionStatusToFlutter(connectionStatus, polarDeviceInfo.deviceId)
            }

            override fun deviceDisconnected(polarDeviceInfo: PolarDeviceInfo) {
                Log.d("MyApp", "DISCONNECTED: ${polarDeviceInfo.deviceId}")

                connectionStatus = "Disconnected"
                sendConnectionStatusToFlutter(connectionStatus, polarDeviceInfo.deviceId)
            }

            override fun bleSdkFeatureReady(identifier: String, feature: PolarBleApi.PolarBleSdkFeature) {
                Log.d("MyApp", "Polar BLE SDK feature $feature is ready")
            }

            override fun disInformationReceived(identifier: String, uuid: UUID, value: String) {
                Log.d("MyApp", "DIS INFO uuid: $uuid value: $value")
            }

            override fun batteryLevelReceived(identifier: String, level: Int) {
                Log.d("MyApp", "BATTERY LEVEL: $level")
            }
        })
    }

    private fun sendConnectionStatusToFlutter(status: String, deviceId: String) {
        runOnUiThread {
            methodChannel.invokeMethod("updateConnectionStatus", mapOf(
                    "status" to status,
                    "deviceId" to deviceId
            ))
        }
    }

    private fun toggleSDKMode() {
        if(SDKMode) {
            connectedDevice?.let { disableSDKMode(it) }

        } else {
            connectedDevice?.let { enableSDKMode(it) }
        }
    }

    private fun toggleRecord() {
//        TODO
    }

    @SuppressLint("CheckResult")
    private fun disableSDKMode(deviceId: String) {
        api.enableSDKMode(deviceId)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                {
                    Log.d(tag, "SDK mode disabled")
                },
                {
                    Log.e(tag, "SDKMode err")
                }
            )
    }

    @SuppressLint("CheckResult")
    private fun enableSDKMode(deviceId: String) {
        api.enableSDKMode(deviceId)
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                {
                    Log.d(tag, "SDK mode enabled")
                },
                {
                    Log.e(tag, "SDKMode err")
                }
            )
    }

    private fun startListeningToChannels(channels: List<String>?) {
        channels?.forEach { channel ->
            when (channel) {
                "HR " -> startHRStreaming()
                "ECG" -> startECGStreaming()
                "ACC" -> startACCStreaming()
                "PPG" -> startPPGStreaming()
                "PPI" -> startPPIStreaming()
                "Gyro" -> startGyroStreaming()
                "Magnetometer" -> startMagStreaming()
            }
        }
    }

    private fun stopListeningToChannels() {
        streamingDisposables.clear() // Stop all streams
    }

    private fun startHRStreaming() {
        hrDisposable?.dispose() // Ensure any existing stream is disposed
        hrDisposable = connectedDevice?.let {
            api.startHrStreaming(it)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { hrData: PolarHrData ->
                        var log : String? = null
                        for (sample in hrData.samples) {
                            log = "HR     bpm: ${sample.hr} rrs: ${sample.rrsMs} rrAvailable: ${sample.rrAvailable} contactStatus: ${sample.contactStatus} contactStatusSupported: ${sample.contactStatusSupported}"
                            Log.d(tag, log)
                        }
                        methodChannel.invokeMethod("onHRDataReceived", log)
                    },
                    { error ->
                        Log.e(tag, "HR stream failed: $error")
                    },
                    { Log.d(tag, "HR stream complete")
                    }
                )
        }
        hrDisposable?.let { streamingDisposables.add(it) }
    }

    private fun startECGStreaming() {
        ecgDisposable?.dispose() // Ensure any existing stream is disposed
        ecgDisposable = connectedDevice?.let {
            api.requestFullStreamSettings(it, PolarBleApi.PolarDeviceDataType.ECG)
                .toFlowable()
                .flatMap { settings: PolarSensorSetting ->
                    api.startEcgStreaming(it, settings)
                }
                .subscribe(
                    { polarEcgData: PolarEcgData ->
                        var log : String? = null
                        for (data in polarEcgData.samples) {
                            log = "    yV: ${data.voltage} timeStamp: ${data.timeStamp}"
                            Log.d(tag, log)
                        }
                        methodChannel.invokeMethod("onECGDataReceived", log)
                    },
                    { error: Throwable ->
                        Log.e(tag, "ECG stream failed. Reason $error")
                    },
                    { Log.d(tag, "ECG stream complete") }
                )
        }
        ecgDisposable?.let { streamingDisposables.add(it) }
    }

    private fun startACCStreaming() {
        accDisposable?.dispose() // Ensure any existing stream is disposed
        accDisposable = connectedDevice?.let {
            api.requestFullStreamSettings(it, PolarBleApi.PolarDeviceDataType.ACC)
                .toFlowable()
                .flatMap { settings: PolarSensorSetting ->
                    api.startAccStreaming(it, settings)
                }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { polarAccelerometerData: PolarAccelerometerData ->
                        var log : String? = null
                        for (data in polarAccelerometerData.samples) {
                            log = "ACC    x: ${data.x} y: ${data.y} z: ${data.z} timeStamp: ${data.timeStamp}"
                            Log.d(tag, log)
                        }
                        methodChannel.invokeMethod("onACCDataReceived", log)
                    },
                    { error: Throwable ->
                        Log.e(tag, "ACC stream failed. Reason $error")
                    },
                    { Log.d(tag, "ACC stream complete") }
                )
        }
        accDisposable?.let { streamingDisposables.add(it) }
    }

    private fun startGyroStreaming() {
        gyrDisposable?.dispose() // Ensure any existing stream is disposed
        gyrDisposable = connectedDevice?.let {
            api.requestFullStreamSettings(it, PolarBleApi.PolarDeviceDataType.GYRO)
                .toFlowable()
                .flatMap { settings: PolarSensorSetting ->
                    api.startGyroStreaming(it, settings)
                }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { polarGyroData: PolarGyroData ->
                        var log : String? = null
                        for (data in polarGyroData.samples) {
                            log = "GYR    x: ${data.x} y: ${data.y} z: ${data.z} timeStamp: ${data.timeStamp}"
                            Log.d(tag, log)
                        }
                        methodChannel.invokeMethod("onGyrDataReceived", log)
                    },
                    { error: Throwable ->
                        Log.e(tag, "Gyro stream failed. Reason $error")
                    },
                    { Log.d(tag, "Gyro stream complete") }
                )
        }
        gyrDisposable?.let { streamingDisposables.add(it) }
    }

    private fun startMagStreaming() {
        magDisposable?.dispose() // Ensure any existing stream is disposed
        magDisposable = connectedDevice?.let {
            api.requestFullStreamSettings(it, PolarBleApi.PolarDeviceDataType.GYRO)
                .toFlowable()
                .flatMap { settings: PolarSensorSetting ->
                    api.startMagnetometerStreaming(it, settings)
                }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { polarMagData: PolarMagnetometerData ->
                        var log : String? = null
                        for (data in polarMagData.samples) {
                            log = "MAG    x: ${data.x} y: ${data.y} z: ${data.z} timeStamp: ${data.timeStamp}"
                            Log.d(tag, log)
                        }
                        methodChannel.invokeMethod("onMagDataReceived", log)
                    },
                    { error: Throwable ->
                        Log.e(tag, "Mag stream failed. Reason $error")
                    },
                    { Log.d(tag, "Mag stream complete") }
                )
        }
        magDisposable?.let { streamingDisposables.add(it) }
    }

    private fun startPPGStreaming() {
        ppgDisposable?.dispose() // Ensure any existing stream is disposed
        ppgDisposable = connectedDevice?.let {
            api.requestFullStreamSettings(it, PolarBleApi.PolarDeviceDataType.PPG)
                .toFlowable()
                .flatMap { settings: PolarSensorSetting ->
                    api.startPpgStreaming(it, settings)
                }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { polarPpgData: PolarPpgData ->
                        var log : String? = null
                        if (polarPpgData.type == PolarPpgData.PpgDataType.PPG3_AMBIENT1) {
                            for (data in polarPpgData.samples) {
                                log = "PPG    ppg0: ${data.channelSamples[0]} ppg1: ${data.channelSamples[1]} ppg2: ${data.channelSamples[2]} ambient: ${data.channelSamples[3]} timeStamp: ${data.timeStamp}"
                                Log.d(tag, log)
                            }
                        }
                        methodChannel.invokeMethod("onPPGDataReceived", log)
                    },
                    { error: Throwable ->
                        Log.e(tag, "Ppg stream failed. Reason $error")
                    },
                    { Log.d(tag, "Ppg stream complete") }
                )
        }
        ppgDisposable?.let { streamingDisposables.add(it) }
    }

    private fun startPPIStreaming() {
        ppiDisposable?.dispose() // Ensure any existing stream is disposed
        ppiDisposable = connectedDevice?.let {
            api.startPpiStreaming(it)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { ppiData: PolarPpiData ->
                        var log : String? = null
                        for (sample in ppiData.samples) {
                            log = "PPI    ppi: ${sample.ppi} blocker: ${sample.blockerBit} errorEstimate: ${sample.errorEstimate}"
                            Log.d(tag, log)
                        }
                        methodChannel.invokeMethod("onPPIDataReceived", log)
                    },
                    { error: Throwable ->
                        Log.e(tag, "Ppi stream failed. Reason $error")
                    },
                    { Log.d(tag, "Ppi stream complete") }
                )
        }
        ppiDisposable?.let { streamingDisposables.add(it) }
    }

    override fun onDestroy() {
        super.onDestroy()
        scanDisposable?.dispose()
        streamingDisposables.dispose()
        // Disconnect from the Polar device and perform cleanup
        api.shutDown()
    }
}
