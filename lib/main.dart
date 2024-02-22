import 'dart:async'; // Import async library for StreamController

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:fluttertoast/fluttertoast.dart';

void main() {
  runApp(const MyApp());
}

class MyApp extends StatelessWidget {
  const MyApp({Key? key}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Polar PPG App',
      theme: ThemeData(
        primarySwatch: Colors.blue,
      ),
      home: const MyHomePage(title: 'Polar PPG Connection Page'),
    );
  }
}

class MyHomePage extends StatefulWidget {
  const MyHomePage({Key? key, required this.title}) : super(key: key);
  final String title;

  @override
  State<MyHomePage> createState() => _MyHomePageState();
}

class _MyHomePageState extends State<MyHomePage> {
  final MethodChannel platform = const MethodChannel('com.attentive_hub.polar_ppg');
  final StreamController<List<Map<String, String>>> _devicesStreamController = StreamController.broadcast();
  List<Map<String, String>> _devices = [];
  String? _selectedDeviceId;
  String? _connectedDeviceId;
  String _connectionStatus = "Disconnected";

  @override
  void initState() {
    super.initState();
    platform.setMethodCallHandler((call) async {
      if (call.method == "onDeviceFound") {
        final String deviceId = call.arguments["deviceId"];
        final String name = call.arguments["name"] ?? "Unknown";
        setState(() {
          _devices.add({"deviceId": deviceId, "name": name});
          _devicesStreamController.add(_devices); // Update the stream with the new list of devices
        });
      } else if (call.method == "updateConnectionStatus") {
        final String status = call.arguments["status"];
        setState(() {
          _connectedDeviceId = status == "Connected" ? call.arguments["deviceId"] : null;
          _connectionStatus = status;
          if (status != "Connecting") {
            Fluttertoast.showToast(
              msg: "Connection status: $status",
              toastLength: Toast.LENGTH_SHORT,
              gravity: ToastGravity.CENTER,
              timeInSecForIosWeb: 1,
              textColor: Colors.white,
              fontSize: 16.0,
            );
          }
        });
      }
    });
  }

  void _startScan() async {
    _devices.clear();
    _devicesStreamController.add(_devices); // Clear the stream as well
    await platform.invokeMethod('startScan');
    _showDeviceSelectionDialog();
  }

  void _showDeviceSelectionDialog() {
    showDialog(
      context: context,
      builder: (BuildContext context) {
        // Use StatefulBuilder to create a scope of state within the dialog
        return StatefulBuilder(
          builder: (BuildContext context, StateSetter setStateDialog) {
            return AlertDialog(
              title: const Text("Select a Device"),
              content: SingleChildScrollView(
                child: StreamBuilder<List<Map<String, String>>>(
                  stream: _devicesStreamController.stream,
                  builder: (context, snapshot) {
                    if (snapshot.hasData && snapshot.data!.isNotEmpty) {
                      return ListBody(
                        children: snapshot.data!.map((device) {
                          bool isSelected = device["deviceId"] == _selectedDeviceId;
                          return ListTile(
                            title: Text(device["name"] ?? "Unknown"),
                            subtitle: Text(device["deviceId"] ?? ""),
                            selected: isSelected,
                            selectedTileColor: Colors.lightBlueAccent,
                            onTap: () {
                              // Use setStateDialog to update the state within the dialog
                              setStateDialog(() {
                                if (_selectedDeviceId == device["deviceId"]) {
                                  _selectedDeviceId = null;
                                } else {
                                  _selectedDeviceId = device["deviceId"];
                                }
                              });
                              // Reflect the selection change in the main state as well
                              setState(() {
                                _selectedDeviceId = _selectedDeviceId;
                              });
                            },
                          );
                        }).toList(),
                      );
                    } else {
                      return const Center(child: Text("No devices found."));
                    }
                  },
                ),
              ),
              actions: <Widget>[
                TextButton(
                  onPressed: () => Navigator.of(context).pop(),
                  child: const Text('Cancel'),
                ),
                TextButton(
                  onPressed: _selectedDeviceId != null ? () {
                    _pairDevice();
                    Navigator.of(context).pop(); // Close the dialog after pairing
                  } : null,
                  child: const Text('Pair'),
                ),
              ],
            );
          },
        );
      },
    );
  }


  void _pairDevice() async {
    if (_selectedDeviceId != null) {
      await platform.invokeMethod('connectToDevice', {'deviceId': _selectedDeviceId});
      _selectedDeviceId = null; // Reset selected device
    }
  }

  void _removeDevice() async {
    await platform.invokeMethod('disconnectFromDevice', {'deviceId': _connectedDeviceId});
    setState(() {
      _connectedDeviceId = null;
      _connectionStatus = "Disconnected";
    });
  }

  @override
  Widget build(BuildContext context) {
    bool isConnected = _connectionStatus == "Connected";

    return Scaffold(
      appBar: AppBar(
        title: Text(widget.title),
      ),
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: <Widget>[
            // Use a Row widget to place buttons next to each other
            Row(
              mainAxisAlignment: MainAxisAlignment.center, // Center the row content
              children: <Widget>[
                ElevatedButton(
                  onPressed: isConnected ? null : _startScan,
                  style: ElevatedButton.styleFrom(
                    backgroundColor: isConnected ? Colors.grey : Colors.blue, // Grey out if connected
                  ),
                  child: const Text('Find a Device'),
                ),
                const SizedBox(width: 20), // Provide some horizontal spacing between the buttons
                ElevatedButton(
                  onPressed: isConnected ? _removeDevice : null,
                  style: ElevatedButton.styleFrom(
                    backgroundColor: isConnected ? Colors.red : Colors.grey, // Grey out if not connected
                  ),
                  child: const Text('Unpair Device'),
                ),
              ],
            ),
            Padding(
              padding: const EdgeInsets.all(20.0),
              child: Text('Connection Status: $_connectionStatus',
                style: TextStyle(
                  fontSize: 15,
                  color: isConnected ? Colors.green : _connectionStatus == "Connecting" ? Colors.orange : Colors.red,
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  @override
  void dispose() {
    super.dispose();
    _devicesStreamController.close(); // Close the stream controller to prevent memory leaks
  }
}
