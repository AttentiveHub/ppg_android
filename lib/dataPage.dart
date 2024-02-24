import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';
import 'ChannelSelectionModel.dart'; // Import your ChannelSelectionModel

class DataPage extends StatefulWidget {
  const DataPage({super.key});

  @override
  _DataPageState createState() => _DataPageState();
}

class _DataPageState extends State<DataPage> {
  final MethodChannel platform = const MethodChannel('com.attentive_hub.polar_ppg');
  String hrData = '';
  String ecgData = '';
  String accData = '';
  String ppgData = '';
  String ppiData = '';
  String gyroData = '';
  String magData = '';

  @override
  void initState() {
    super.initState();
    platform.setMethodCallHandler((call) async {
      if (call.method == "onHRDataReceived") {
        setState(() {
          hrData = call.arguments;
        });
      } else if (call.method == "onECGDataReceived") {
        setState(() {
          ecgData = call.arguments;
        });
      } else if (call.method == "onACCDataReceived") {
        setState(() {
          accData = call.arguments;
        });
      } else if (call.method == "onGyrDataReceived") {
        setState(() {
          gyroData = call.arguments;
        });
      } else if (call.method == "onMagDataReceived") {
        setState(() {
          magData = call.arguments;
        });
      } else if (call.method == "onPPGDataReceived") {
        setState(() {
          ppgData = call.arguments;
        });
      } else if (call.method == "onPPIDataReceived") {
        setState(() {
          ppiData = call.arguments;
        });
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    final channels = Provider.of<ChannelSelectionModel>(context).selectedChannels;

    if (channels == null) {
      platform.invokeMethod('stopListeningToChannels');
      setState(() {
        hrData = "";
        ecgData = "";
        accData = "";
        ppgData = "";
        ppiData = "";
        gyroData = "";
        hrData = "";
        magData = "";
      });
    } else {
      platform.invokeMethod('startListeningToChannels', channels);
    }

    return Scaffold(
      appBar: AppBar(
        title: const Text("Data Page"),
      ),
      body: Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: <Widget>[
            Padding(
              padding: const EdgeInsets.all(8.0),
              child: Text("HR Data: $hrData"),
            ),
            Padding(
              padding: const EdgeInsets.all(8.0),
              child: Text("ECG Data: $ecgData"),
            ),
            Padding(
              padding: const EdgeInsets.all(8.0),
              child: Text("ACC Data: $accData"),
            ),
            Padding(
              padding: const EdgeInsets.all(8.0),
              child: Text("PPG Data: $ppgData"),
            ),
            Padding(
              padding: const EdgeInsets.all(8.0),
              child: Text("PPI Data: $ppiData"),
            ),
            Padding(
              padding: const EdgeInsets.all(8.0),
              child: Text("Gyro Data: $gyroData"),
            ),
            Padding(
              padding: const EdgeInsets.all(8.0),
              child: Text("Mag Data: $magData"),
            ),
          ],
        ),
      ),
    );
  }
}