package com.attentive_hub.polar_ppg.polar_ppg

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.*

class RecordManager(private val context: Context, private val baseUri: Uri) {
    private var recordingEnabled = false

    private lateinit var currentSessionUri: Uri
    private val channelFileUris: MutableMap<String, Uri> = mutableMapOf()
    private val writeLock = Any()

    fun toggleRecord(enable: Boolean, channels: List<String>) {
        recordingEnabled = enable
        if (recordingEnabled) {
            startRecordingSession(channels)
        }
    }

    private fun startRecordingSession(channels: List<String>) {
        val timestamp = SimpleDateFormat("HH-mm-ss_yyyy-MM-dd", Locale.US).format(Date())
        val sessionFolderName = "Session_$timestamp"
        val documentFile = DocumentFile.fromTreeUri(context, baseUri) ?: return
        val directory = documentFile.createDirectory(sessionFolderName) ?: return
        currentSessionUri = directory.uri

        channels.forEach { channel ->
            createFileForChannel(directory, channel.trim())
        }
    }

    private fun createFileForChannel(directory: DocumentFile, channel: String) {
        val file = directory.createFile("text/plain", "$channel.txt") ?: return
        val header = getHeaderForRow(channel)
        appendToFile(file.uri, header, true)
        channelFileUris[channel] = file.uri
    }

    fun writeData(channel: String, data: String, isHeader: Boolean = false) {
        if (!recordingEnabled) return // No session to write to

        val fileUri = channelFileUris[channel] ?: return
        appendToFile(fileUri, data, isHeader)
    }

    private fun appendToFile(fileUri: Uri, data: String, isHeader: Boolean) {
        val formattedData = if (isHeader) {
            data
        } else {
            val currentTimeStamp = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", Locale.US).format(Date())
            "$currentTimeStamp;$data"
        }

        synchronized(writeLock) {
            context.contentResolver.openOutputStream(fileUri, "wa")?.use { outputStream ->
                OutputStreamWriter(outputStream).apply {
                    append(formattedData)
                    append("\n")
                    flush()
                    close()
                }
            }
        }
    }

    private fun getHeaderForRow(channel: String): String {
        return when(channel) {
            "HR" -> "Phone timestamp;HR [bpm];"
            "ECG" -> "Unavailable"
            "ACC" -> "Phone timestamp;sensor timestamp [ns];X [mg];Y [mg];Z [mg];"
            "PPG" -> "Phone timestamp;sensor timestamp [ns];channel 0;channel 1;channel 2;ambient;"
            "PPI" -> "Phone timestamp;PP-interval [ms];error estimate [ms];blocker;contact;contact;hr [bpm];"
            "Gyro" -> "Phone timestamp;sensor timestamp [ns];X [dps];Y [dps];Z [dps];"
            "Magnetometer" -> "Phone timestamp;sensor timestamp [ns];X [G];Y [G];Z [G];"
            else -> "Data"
        }
    }

    fun shareData() {
        if (!recordingEnabled) return // No session to share

        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND_MULTIPLE
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Session Data")
            val uris = ArrayList<Uri>()
            channelFileUris.values.forEach { uri ->
                uris.add(uri)
            }
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooserIntent = Intent.createChooser(shareIntent, "Share via")
        chooserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) // If you're in a non-Activity context
        context.startActivity(chooserIntent)
    }
}