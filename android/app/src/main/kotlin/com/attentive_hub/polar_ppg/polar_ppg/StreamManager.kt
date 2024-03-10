package com.attentive_hub.polar_ppg.polar_ppg

import android.annotation.SuppressLint
import android.util.Log
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.model.PolarAccelerometerData
import com.polar.sdk.api.model.PolarGyroData
import com.polar.sdk.api.model.PolarHrData
import com.polar.sdk.api.model.PolarMagnetometerData
import com.polar.sdk.api.model.PolarPpgData
import com.polar.sdk.api.model.PolarPpiData
import com.polar.sdk.api.model.PolarSensorSetting
import io.flutter.plugin.common.MethodChannel
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class StreamManager(private val api: PolarBleApi, private val connectedDevice: String, private val recordManager: RecordManager, private val dataMethodChannel: MethodChannel) {
    private var hrDisposable: Disposable? = null
    private var accDisposable: Disposable? = null
    private var ppgDisposable: Disposable? = null
    private var ppiDisposable: Disposable? = null
    private var gyrDisposable: Disposable? = null
    private var magDisposable: Disposable? = null

    var streamingDisposables = CompositeDisposable()

    val job = Job()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private lateinit var channels: List<String>

    private val tag = "PPG LEGO"

    fun startListeningToChannels(channels: List<String>?) {
        if (channels != null) {
            this.channels = channels
        }
        channels?.forEach { channel ->
            when (channel.trim()) {
                "HR" -> startHRStreaming()
                "ECG" -> startECGStreaming()
                "ACC" -> startACCStreaming()
                "PPG" -> startPPGStreaming()
                "PPI" -> startPPIStreaming()
                "Gyro" -> startGyroStreaming()
                "Magnetometer" -> startMagStreaming()
            }
        }
    }

    fun stopListeningToChannels() {
        streamingDisposables.clear() // Stop all streams
        dataMethodChannel.invokeMethod("resetChannels", null)
        recordManager.shareData()
    }

    fun toggleSDKMode(toggle: Boolean) {
        if(toggle) {
            enableSDKMode(connectedDevice)
        } else {
            disableSDKMode(connectedDevice)
        }
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

    private fun startHRStreaming() {
        hrDisposable?.dispose() // Ensure any existing stream is disposed
        hrDisposable = connectedDevice.let {
            api.startHrStreaming(it)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { hrData: PolarHrData ->
                        var log : String?
                        for (sample in hrData.samples) {
                            scope.launch {
                                recordManager.writeData("HR", "${sample.hr};")
                            }
                            log = "HR     bpm: ${sample.hr} rrs: ${sample.rrsMs} rrAvailable: ${sample.rrAvailable} contactStatus: ${sample.contactStatus} contactStatusSupported: ${sample.contactStatusSupported}"
                            dataMethodChannel.invokeMethod("onHRDataReceived", log)
                        }
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
//        ecgDisposable?.dispose() // Ensure any existing stream is disposed
//        ecgDisposable = connectedDevice.let {
//            api.requestFullStreamSettings(it, PolarBleApi.PolarDeviceDataType.ECG)
//                .toFlowable()
//                .flatMap { settings: PolarSensorSetting ->
//                    api.startEcgStreaming(it, settings)
//                }
//                .subscribe(
//                    { polarEcgData: PolarEcgData ->
//                        var log : String?
//                        for (data in polarEcgData.samples) {
//                            log = "    yV: ${data.voltage} timeStamp: ${data.timeStamp};"
//                            scope.launch {
//                                recordManager.writeData("ECG", log)
//                            }
//                            dataMethodChannel.invokeMethod("onECGDataReceived", log)
//                        }
//                    },
//                    { error: Throwable ->
//                        Log.e(tag, "ECG stream failed. Reason $error")
//                    },
//                    { Log.d(tag, "ECG stream complete") }
//                )
//        }
//        ecgDisposable?.let { streamingDisposables.add(it) }
    }

    private fun startACCStreaming() {
        accDisposable?.dispose() // Ensure any existing stream is disposed
        accDisposable = connectedDevice.let {
            api.requestFullStreamSettings(it, PolarBleApi.PolarDeviceDataType.ACC)
                .toFlowable()
                .flatMap { settings: PolarSensorSetting ->
                    api.startAccStreaming(it, settings)
                }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { polarAccelerometerData: PolarAccelerometerData ->
                        var log : String?
                        for (data in polarAccelerometerData.samples) {
                            scope.launch {
                                recordManager.writeData("ACC", "${data.timeStamp};${data.x};${data.y};${data.z};")
                            }
                            log = "ACC    x: ${data.x} y: ${data.y} z: ${data.z} timeStamp: ${data.timeStamp}"
                            dataMethodChannel.invokeMethod("onACCDataReceived", log)
                        }
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
        gyrDisposable = connectedDevice.let {
            api.requestFullStreamSettings(it, PolarBleApi.PolarDeviceDataType.GYRO)
                .toFlowable()
                .flatMap { settings: PolarSensorSetting ->
                    api.startGyroStreaming(it, settings)
                }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { polarGyroData: PolarGyroData ->
                        var log : String?
                        for (data in polarGyroData.samples) {
                            scope.launch {
                                recordManager.writeData("Gyro", "${data.timeStamp};${data.x};${data.y};${data.z};")
                            }
                            log = "GYR    x: ${data.x} y: ${data.y} z: ${data.z} timeStamp: ${data.timeStamp}"
                            dataMethodChannel.invokeMethod("onGyrDataReceived", log)
                        }
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
        magDisposable = connectedDevice.let {
            api.requestFullStreamSettings(it, PolarBleApi.PolarDeviceDataType.GYRO)
                .toFlowable()
                .flatMap { settings: PolarSensorSetting ->
                    api.startMagnetometerStreaming(it, settings)
                }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { polarMagData: PolarMagnetometerData ->
                        var log : String?
                        for (data in polarMagData.samples) {
                            scope.launch {
                                recordManager.writeData("Magnetometer", "${data.timeStamp};${data.x};${data.y};${data.z};")
                            }
                            log = "MAG    x: ${data.x} y: ${data.y} z: ${data.z} timeStamp: ${data.timeStamp}"
                            dataMethodChannel.invokeMethod("onMagDataReceived", log)
                        }
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
        ppgDisposable = connectedDevice.let {
            api.requestFullStreamSettings(it, PolarBleApi.PolarDeviceDataType.PPG)
                .toFlowable()
                .flatMap { settings: PolarSensorSetting ->
                    api.startPpgStreaming(it, settings)
                }
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { polarPpgData: PolarPpgData ->
                        var log : String?
                        if (polarPpgData.type == PolarPpgData.PpgDataType.PPG3_AMBIENT1) {
                            for (data in polarPpgData.samples) {
                                scope.launch {
                                    recordManager.writeData("PPG", "${data.timeStamp};${data.channelSamples[0]};${data.channelSamples[1]};${data.channelSamples[2]};${data.channelSamples[3]};")
                                }
                                log = "PPG    ppg0: ${data.channelSamples[0]} ppg1: ${data.channelSamples[1]} ppg2: ${data.channelSamples[2]} ambient: ${data.channelSamples[3]} timeStamp: ${data.timeStamp}"
                                dataMethodChannel.invokeMethod("onPPGDataReceived", log)
                            }
                        }
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
        ppiDisposable = connectedDevice.let {
            api.startPpiStreaming(it)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                    { ppiData: PolarPpiData ->
                        var log : String?
                        for (sample in ppiData.samples) {
                            scope.launch {
                                recordManager.writeData("PPI", "${sample.ppi};${sample.errorEstimate};${sample.blockerBit};${sample.skinContactStatus};${sample.skinContactSupported};${sample.hr};")
                            }
                            log = "PPI    ppi: ${sample.ppi} blocker: ${sample.blockerBit} errorEstimate: ${sample.errorEstimate}"
                            dataMethodChannel.invokeMethod("onPPIDataReceived", log)
                        }
                    },
                    { error: Throwable ->
                        Log.e(tag, "Ppi stream failed. Reason $error")
                    },
                    { Log.d(tag, "Ppi stream complete") }
                )
        }
        ppiDisposable?.let { streamingDisposables.add(it) }
    }
}