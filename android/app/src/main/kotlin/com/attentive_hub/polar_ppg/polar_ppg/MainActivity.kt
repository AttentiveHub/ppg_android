package com.attentive_hub.polar_ppg.polar_ppg

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.PolarBleApiCallback
import com.polar.sdk.api.PolarBleApiDefaultImpl
import com.polar.sdk.api.model.PolarDeviceInfo
import io.flutter.embedding.android.FlutterActivity
import io.flutter.plugin.common.MethodChannel
import java.util.*

class MainActivity: FlutterActivity() {
    private lateinit var api: PolarBleApi
    private lateinit var deviceId: String

    private lateinit var bluetoothManager: BluetoothManager
    private lateinit var recordManager: RecordManager
    private lateinit var streamManager: StreamManager

    private lateinit var mainMethodChannel: MethodChannel
    private lateinit var dataMethodChannel: MethodChannel

    private val requestCodePermissions = 101
    private val requestCodeDirectory = 102

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mainMethodChannel = flutterEngine?.dartExecutor?.binaryMessenger?.let { MethodChannel(it, "com.attentive_hub.polar_ppg.main") }!!
        dataMethodChannel = flutterEngine?.dartExecutor?.binaryMessenger?.let { MethodChannel(it, "com.attentive_hub.polar_ppg.data") }!!

        flutterEngine?.dartExecutor?.let { it ->
            MethodChannel(it.binaryMessenger, "com.attentive_hub.polar_ppg.main").setMethodCallHandler { call, result ->
                when (call.method) {
                    "startScan" -> {
                        bluetoothManager.startDeviceScan()
                        result.success(null)
                    }
                    "connectToDevice" -> {
                        val deviceId : String ? = call.argument("deviceId")
                        deviceId?.let {
                            bluetoothManager.connectToDevice(it) // Assuming you have a connectToDevice function that takes a device ID
                            result.success(null)
                        } ?: result.error("INVALID_ID", "Device ID is null or invalid.", null)
                    }
                    "disconnectFromDevice" -> {
                        val deviceId : String ? = call.argument("deviceId")
                        deviceId?.let {
                            bluetoothManager.disconnectFromDevice(it) // Assuming you have a connectToDevice function that takes a device ID
                            result.success(null)
                        } ?: result.error("INVALID_ID", "Device ID is null or invalid.", null)
                    }

                    "toggleRecord" -> {
                        val record : Boolean = call.argument("record")!!
                        val channels: List<String>? = call.argument("selectedChannels")
                        if (channels != null) {
                            recordManager.toggleRecord(record, channels)
                        }
                        result.success(null)
                    }

                    "toggleSDKMode" -> {
                        val toggle : Boolean = call.argument("toggle")!!
                        streamManager.toggleSDKMode(toggle)
                        result.success(null)
                    }
                    "startListeningToChannels" -> {
                        startStreamingService()
                        val channels: List<String>? = call.arguments()
                        streamManager.startListeningToChannels(channels)
                        result.success(null)
                    }
                    "stopListeningToChannels" -> {
                        stopStreamingService()
                        streamManager.stopListeningToChannels()
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
            Manifest.permission.ACCESS_FINE_LOCATION,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.POST_NOTIFICATIONS
            } else {""}
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
            checkDirectoryAndPermissions()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == requestCodePermissions && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
            checkDirectoryAndPermissions()
        } else {
            Log.d("MyApp", "Permission not granted")
        }
    }

    private fun checkDirectoryAndPermissions() {
        val sharedPreferences = getSharedPreferences("MyAppPreferences", MODE_PRIVATE)
        val directoryUriString = sharedPreferences.getString("directoryUri", null)
        if (directoryUriString != null) {
            initializePolarSDK()
        } else {
            // No directory selected, open directory picker
            openDirectoryPicker()
        }
    }

    private fun openDirectoryPicker() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
        intent.addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        startActivityForResult(intent, requestCodeDirectory)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == requestCodeDirectory && resultCode == RESULT_OK) {
            data?.data?.also { uri ->
                contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                val sharedPreferences = getSharedPreferences("MyAppPreferences", MODE_PRIVATE)
                with(sharedPreferences.edit()) {
                    putString("directoryUri", uri.toString())
                    apply()
                }
                initializePolarSDK()
            }
        }
    }

    private fun getSavedDirectoryUri(): Uri? {
        val sharedPreferences = getSharedPreferences("MyAppPreferences", MODE_PRIVATE)
        val directoryUriString = sharedPreferences.getString("directoryUri", null) ?: return null
        return Uri.parse(directoryUriString)
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

        bluetoothManager = BluetoothManager(api, mainMethodChannel)
        val savedUri = getSavedDirectoryUri()
        savedUri?.let {
            recordManager = RecordManager(this, it)
        }

        api.setApiCallback(object : PolarBleApiCallback() {
            override fun blePowerStateChanged(powered: Boolean) {
                Log.d("MyApp", "BLE power: $powered")
            }

            override fun deviceConnected(polarDeviceInfo: PolarDeviceInfo) {
                Log.d("MyApp", "CONNECTED: ${polarDeviceInfo.deviceId}")
                deviceId = polarDeviceInfo.deviceId
                streamManager = StreamManager(api, deviceId, recordManager, dataMethodChannel)

                sendConnectionStatusToFlutter("Connected", polarDeviceInfo.deviceId)
            }

            override fun deviceConnecting(polarDeviceInfo: PolarDeviceInfo) {
                Log.d("MyApp", "CONNECTING: ${polarDeviceInfo.deviceId}")

                sendConnectionStatusToFlutter("Connecting", polarDeviceInfo.deviceId)
            }

            override fun deviceDisconnected(polarDeviceInfo: PolarDeviceInfo) {
                Log.d("MyApp", "DISCONNECTED: ${polarDeviceInfo.deviceId}")

                sendConnectionStatusToFlutter("Disconnected", polarDeviceInfo.deviceId)
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
            mainMethodChannel.invokeMethod("updateConnectionStatus", mapOf(
                "status" to status,
                "deviceId" to deviceId
            ))
        }
    }

    private fun startStreamingService() {
        val serviceIntent = Intent(this, StreamingService::class.java)
        ContextCompat.startForegroundService(this, serviceIntent)
    }

    private fun stopStreamingService() {
        val serviceIntent = Intent(this, StreamingService::class.java)
        stopService(serviceIntent)
    }

    override fun onDestroy() {
        super.onDestroy()

        bluetoothManager.scanDisposable?.dispose()
        streamManager.streamingDisposables.dispose()
        streamManager.job.cancel()

        api.shutDown()
    }
}
