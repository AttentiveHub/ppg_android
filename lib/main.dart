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
      title: 'Polar Logger2 App',
      theme: ThemeData(
        primarySwatch: Colors.blue,
        primaryColor: Colors.green,
      ),
      home: const MyHomePage(title: 'Polar Logger2 Page'),
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
  final List<String> _channels = ['HR', 'ACC', 'ECG', 'GYR', 'PPG', 'PPI', 'MAG'];
  List<int> _selectedChannelIndices = [];
  bool _channelsConfirmed = false; // Tracks whether the selection has been confirmed

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
              textColor: status == "Connected" ? Colors.greenAccent : Colors.red,
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
              title: const Text("Choose a Sensor"),
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
                            selected: isSelected,
                            selectedTileColor: Colors.greenAccent,
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
                      return const Center(child: Text("No Sensors found."));
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

  Widget _buildSelectableChannel(int index) {
    bool isSelected = _selectedChannelIndices.contains(index);
    return InkWell(
      onTap: () {
        // Check if the condition to allow interaction is met
        if (!_channelsConfirmed && (isSelected || _selectedChannelIndices.length < 4)) {
          setState(() {
            if (isSelected) {
              _selectedChannelIndices.remove(index);
            } else {
              _selectedChannelIndices.add(index);
            }
          });
        } else if(_channelsConfirmed) {
          // Show a warning message if the condition is not met
          Fluttertoast.showToast(
            msg: "You have to reset to edit choices.",
            toastLength: Toast.LENGTH_SHORT,
            gravity: ToastGravity.CENTER,
            timeInSecForIosWeb: 1,
            backgroundColor: Colors.red,
            textColor: Colors.white,
            fontSize: 16.0,
          );
        } else {
          // Show a warning message if the condition is not met
          Fluttertoast.showToast(
            msg: "You cannot choose more than 4 channels.",
            toastLength: Toast.LENGTH_SHORT,
            gravity: ToastGravity.CENTER,
            timeInSecForIosWeb: 1,
            backgroundColor: Colors.red,
            textColor: Colors.white,
            fontSize: 16.0,
          );
        }
      },
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: <Widget>[
          Icon(
            isSelected ? Icons.radio_button_checked : Icons.radio_button_unchecked,
            color: isSelected ? Colors.greenAccent : Colors.grey,
          ),
          Padding(
            padding: const EdgeInsets.only(left: 8.0),
            child: Text(
              _channels[index],
              style: const TextStyle(fontWeight: FontWeight.bold), // Make text bold
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildChannelSelectionGrid() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start, // Align content to the start
      children: <Widget>[
        Padding(
          padding: const EdgeInsets.only(top: 40.0, bottom: 12.0), // Add some space below the heading
          child: Text(
            'Choose Your Channels:',
            style: Theme.of(context).textTheme.titleLarge, // Styling for the heading
          ),
        ),
        Padding( // Apply padding to the Wrap widget to align it with the title
          padding: const EdgeInsets.only(right: 25.0),
          child: Wrap(
            spacing: 20.0, // Horizontal space between buttons
            runSpacing: 5.0, // Vertical space between lines
            children: List<Widget>.generate(_channels.length, (index) {
              return _buildSelectableChannel(index);
            }),
          ),
        ),
      ],
    );
  }

  Widget _buildConfirmResetButtons() {
    return Row(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        ElevatedButton(
          onPressed: !_channelsConfirmed && _selectedChannelIndices.isNotEmpty ? () {
            setState(() {
              _channelsConfirmed = true; // Confirm selection
            });
            // Optionally: Invoke logic to handle confirmed selection here
          } : null, // Disable button if conditions are not met
          style: ElevatedButton.styleFrom(
            backgroundColor: !_channelsConfirmed && _selectedChannelIndices.isNotEmpty ? Colors.greenAccent : Colors.grey, // Color when disabled
          ),
          child: const Text(
            'Confirm',
            style: TextStyle(color: Colors.black),
          ),
        ),
        const SizedBox(width: 20), // Space between buttons
        ElevatedButton(
          onPressed: _channelsConfirmed ? () {
            setState(() {
              _channelsConfirmed = false; // Reset confirmation
              _selectedChannelIndices.clear(); // Clear selections
            });
          } : null, // Disable button if conditions are not met
          style: ElevatedButton.styleFrom(
            backgroundColor: _channelsConfirmed ? Colors.red : Colors.grey, // Color when disabled
          ),
          child: const Text(
            'Reset',
            style: TextStyle(color: Colors.black),
          ),
        ),
      ],
    );
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
          mainAxisAlignment: MainAxisAlignment.start,
          children: <Widget>[
            // Use a Row widget to place buttons next to each other
            const Padding(
              padding: EdgeInsets.only(top: 15.0, bottom: 0.0)),
            Row(
              mainAxisAlignment: MainAxisAlignment.center, // Center the row content
              children: <Widget>[
                ElevatedButton(
                  onPressed: isConnected ? null : _startScan,
                  style: ElevatedButton.styleFrom(
                    backgroundColor: isConnected ? Colors.grey : Colors.greenAccent, // Grey out if connected
                  ),
                  child: const Text('Find a Sensor',
                    style: TextStyle(color: Colors.black),
                  ),
                ),
                const SizedBox(width: 20), // Provide some horizontal spacing between the buttons
                ElevatedButton(
                  onPressed: isConnected ? _removeDevice : null,
                  style: ElevatedButton.styleFrom(
                    backgroundColor: isConnected ? Colors.red : Colors.grey, // Grey out if not connected
                  ),
                  child: const Text('Unpair Sensor',
                    style: TextStyle(color: Colors.black),
                    ),
                ),
              ],
            ),
            Padding(
              padding: const EdgeInsets.all(8.0),
              child: RichText(
                text: TextSpan(
                  style: const TextStyle(fontSize: 15, color: Colors.black), // Default text style
                  children: <TextSpan>[
                    const TextSpan(text: 'Connection Status: '), // Text in default style
                    TextSpan(
                      text: _connectionStatus, // Text that needs color
                      style: TextStyle(
                        color: isConnected ? Colors.green : _connectionStatus == "Connecting" ? Colors.orange : Colors.red, // Conditional color
                      ),
                    ),
                  ],
                ),
              ),
            ),
            _buildChannelSelectionGrid(),
            const Padding(
                padding: EdgeInsets.only(top: 16.0, bottom: 0.0)),
            _buildConfirmResetButtons(),
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
