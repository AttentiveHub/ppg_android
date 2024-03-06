package com.attentive_hub.polar_ppg.polar_ppg

import android.annotation.SuppressLint
import android.util.Log
import com.polar.sdk.api.PolarBleApi
import com.polar.sdk.api.model.PolarAccelerometerData
import com.polar.sdk.api.model.PolarEcgData
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

class StreamManager(private val api: PolarBleApi, private val connectedDevice: String, private val dataMethodChannel: MethodChannel) {
    private var hrDisposable: Disposable? = null
    private var ecgDisposable: Disposable? = null
    private var accDisposable: Disposable? = null
    private var ppgDisposable: Disposable? = null
    private var ppiDisposable: Disposable? = null
    private var gyrDisposable: Disposable? = null
    private var magDisposable: Disposable? = null

    var streamingDisposables = CompositeDisposable()

    private val tag = "PPG LEGO"

    fun startListeningToChannels(channels: List<String>?) {
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

    fun stopListeningToChannels() {
        streamingDisposables.clear() // Stop all streams
        dataMethodChannel.invokeMethod("resetChannels", null)
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
                        var log : String? = null
                        for (sample in hrData.samples) {
                            log = "HR     bpm: ${sample.hr} rrs: ${sample.rrsMs} rrAvailable: ${sample.rrAvailable} contactStatus: ${sample.contactStatus} contactStatusSupported: ${sample.contactStatusSupported}"
                            Log.d(tag, log)
                        }
                        dataMethodChannel.invokeMethod("onHRDataReceived", log)
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
        ecgDisposable = connectedDevice.let {
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
                        dataMethodChannel.invokeMethod("onECGDataReceived", log)
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
        accDisposable = connectedDevice.let {
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
                        dataMethodChannel.invokeMethod("onACCDataReceived", log)
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
                        var log : String? = null
                        for (data in polarGyroData.samples) {
                            log = "GYR    x: ${data.x} y: ${data.y} z: ${data.z} timeStamp: ${data.timeStamp}"
                            Log.d(tag, log)
                        }
                        dataMethodChannel.invokeMethod("onGyrDataReceived", log)
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
                        var log : String? = null
                        for (data in polarMagData.samples) {
                            log = "MAG    x: ${data.x} y: ${data.y} z: ${data.z} timeStamp: ${data.timeStamp}"
                            Log.d(tag, log)
                        }
                        dataMethodChannel.invokeMethod("onMagDataReceived", log)
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
                        var log : String? = null
                        if (polarPpgData.type == PolarPpgData.PpgDataType.PPG3_AMBIENT1) {
                            for (data in polarPpgData.samples) {
                                log = "PPG    ppg0: ${data.channelSamples[0]} ppg1: ${data.channelSamples[1]} ppg2: ${data.channelSamples[2]} ambient: ${data.channelSamples[3]} timeStamp: ${data.timeStamp}"
                                Log.d(tag, log)
                            }
                        }
                        dataMethodChannel.invokeMethod("onPPGDataReceived", log)
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
                        var log : String? = null
                        for (sample in ppiData.samples) {
                            log = "PPI    ppi: ${sample.ppi} blocker: ${sample.blockerBit} errorEstimate: ${sample.errorEstimate}"
                            Log.d(tag, log)
                        }
                        dataMethodChannel.invokeMethod("onPPIDataReceived", log)
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