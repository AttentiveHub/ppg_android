import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:fluttertoast/fluttertoast.dart';

class MyHomePage extends StatefulWidget {
  const MyHomePage({Key? key, required this.title}) : super(key: key);
  final String title;

  @override
  State<MyHomePage> createState() => _MyHomePageState();
}

class _MyHomePageState extends State<MyHomePage> {
  final MethodChannel platform = const MethodChannel('com.attentive_hub.polar_ppg.main');

  final StreamController<List<Map<String, String>>> _devicesStreamController = StreamController.broadcast();
  final List<Map<String, String>> _devices = [];
  String? _selectedDeviceId;
  String? _connectedDeviceId;
  String _connectionStatus = "Disconnected";

  final List<String> _channels = ['HR  ', 'ECG', 'ACC', 'PPG', 'PPI ', 'Gyro',];// 'Magnetometer'];
  final List<int> _selectedChannelIndices = [];
  bool _channelsConfirmed = false; // Tracks whether the selection has been confirmed
  final int _maxChannels = 3;

  bool _isSdkEnabled = false;
  bool _isRecordingEnabled = false;

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
              textColor: status == "Connected" ? Colors.greenAccent : Colors.redAccent,
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
        if (!_channelsConfirmed && (isSelected || _selectedChannelIndices.length < _maxChannels) && _connectionStatus == "Connected") {
          if ((_channels[index] != 'PPI ' && _channels[index] != 'HR  ' && _channels[index] != 'ECG') || !_isSdkEnabled){
            setState(() {
              if (isSelected) {
                _selectedChannelIndices.remove(index);
              } else {
                _selectedChannelIndices.add(index);
              }
            });
          } else {
              Fluttertoast.showToast(
              msg: "Cannot Select this channel with SDK Mode On.",
              toastLength: Toast.LENGTH_SHORT,
              gravity: ToastGravity.CENTER,
              timeInSecForIosWeb: 1,
              backgroundColor: Colors.red,
              textColor: Colors.white,
              fontSize: 16.0,
            );
          }
        } else if(_connectionStatus != "Connected") {
          // Show a warning message if the condition is not met
          Fluttertoast.showToast(
            msg: "Pair with a sensor first.",
            toastLength: Toast.LENGTH_SHORT,
            gravity: ToastGravity.CENTER,
            timeInSecForIosWeb: 1,
            backgroundColor: Colors.red,
            textColor: Colors.white,
            fontSize: 16.0,
          );
        } else if(_channelsConfirmed) {
          // Show a warning message if the condition is not met
          Fluttertoast.showToast(
            msg: "Reset to edit choices.",
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
            msg: "Cannot choose more than $_maxChannels channels.",
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
            color: isSelected ? Colors.redAccent : Colors.white,
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
    return Container(
      margin: const EdgeInsets.all(12), // Adjust the margin as needed
      padding: const EdgeInsets.all(12), // Adjust padding as needed for the content inside the container
      decoration: BoxDecoration(
        color: Colors.greenAccent, // Background color of the container
        borderRadius: BorderRadius.circular(15), // Adjust for rounded corners
        border: Border.all(
          color: Colors.grey.shade300, // Color of the border
          width: 1, // Width of the border
        ),
        boxShadow: [
          BoxShadow(
            color: Colors.grey.shade200, // Shadow color
            blurRadius: 5, // Shadow blur radius
            offset: const Offset(0, 3), // Shadow position
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          Center(
            child: Padding(
              padding: const EdgeInsets.only(top: 0.0, bottom: 12.0),
              child: Text(
                'Channel Selection',
                style: TextStyle(
                  fontWeight: FontWeight.bold, // Make text bold
                  fontSize: Theme.of(context).textTheme.titleLarge?.fontSize, // Use the large title font size
                ),
              ),
            ),
          ),
          Padding(
            padding: const EdgeInsets.only(right: 0.0, left: 40.0, bottom: 7.0),
            child: Wrap(
              spacing: 40.0, // Horizontal space between buttons
              runSpacing: 10.0, // Vertical space between lines
              children: List<Widget>.generate(_channels.length, (index) {
                return _buildSelectableChannel(index);
              }),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildSDKToggle() {
    return SwitchListTile(
      title: const Text(
        'Enable SDK Mode',
        style: TextStyle(fontWeight: FontWeight.bold), // Make text bold
      ),
      value: _isSdkEnabled,
      onChanged: _channelsConfirmed || _selectedChannelIndices.any((index) => ['PPI ', 'HR  ', 'ECG'].contains(_channels[index])) ? null : (bool value) {
        setState(() {
          _isSdkEnabled = value;
        });
      },
      activeTrackColor: Colors.greenAccent,
      inactiveTrackColor: Colors.grey,
      contentPadding: const EdgeInsets.symmetric(horizontal: 80.0), // Adjust padding as needed
    );
  }

  Widget _buildRecordToggle() {
    return SwitchListTile(
      title: const Text(
        'Enable Recording',
        style: TextStyle(fontWeight: FontWeight.bold), // Make text bold
      ),
      value: _isRecordingEnabled,
      onChanged: _channelsConfirmed ? null : (bool value) {
        setState(() {
          _isRecordingEnabled = value;
        });
      },
      activeTrackColor: Colors.redAccent,
      inactiveTrackColor: Colors.grey,
      contentPadding: const EdgeInsets.symmetric(horizontal: 80.0), // Adjust padding as needed
    );
  }

  Widget _buildConfirmResetButtons() {
    return Row(
      mainAxisAlignment: MainAxisAlignment.center,
      children: [
        ElevatedButton(
          onPressed: !_channelsConfirmed && _selectedChannelIndices.isNotEmpty ? () {
            final selectedChannels = _selectedChannelIndices.map((index) => _channels[index]).toList();
            platform.invokeMethod("toggleSDKMode", {'toggle': _isSdkEnabled}).then((_) {
                platform.invokeMethod("toggleRecord", {'record': _isRecordingEnabled}).then((_) {
                  platform.invokeMethod('startListeningToChannels', selectedChannels).then((_) {
                    setState(() {
                      _channelsConfirmed = true; // Confirm selection
                    });
                  });
                });
            });
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
            platform.invokeMethod('stopListeningToChannels').then((_) {
              setState(() {
                _channelsConfirmed = false; // Reset confirmation
                _selectedChannelIndices.clear(); // Clear selections
              });
            });
          } : null, // Disable button if conditions are not met
          style: ElevatedButton.styleFrom(
            backgroundColor: _channelsConfirmed ? Colors.redAccent : Colors.grey, // Color when disabled
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
        backgroundColor: Colors.grey.shade600,
      ),
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.start,
          children: <Widget>[
            // Use a Row widget to place buttons next to each other
            const Padding(padding: EdgeInsets.only(top: 30.0, bottom: 0.0)),
            Row(
              mainAxisAlignment: MainAxisAlignment.center, // Center the row content
              children: <Widget>[
                ElevatedButton(
                  onPressed: isConnected ? null :_connectionStatus == "Connecting" ? null : _startScan,
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
                    backgroundColor: isConnected ? Colors.redAccent : Colors.grey, // Grey out if not connected
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
            const Padding(padding: EdgeInsets.only(top: 50.0, bottom: 0.0)),
            _buildChannelSelectionGrid(),
            const Padding(padding: EdgeInsets.only(top: 45.0, bottom: 0.0)),
            _buildSDKToggle(),
            // const Padding(padding: EdgeInsets.only(top: 20.0, bottom: 0.0)),
            _buildRecordToggle(),
            const Padding(padding: EdgeInsets.only(top: 60.0, bottom: 0.0)),
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