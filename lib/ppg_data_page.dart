import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

class PPGDataPage extends StatefulWidget {
  const PPGDataPage({Key? key}) : super(key: key);

  @override
  _PPGDataPageState createState() => _PPGDataPageState();
}

class _PPGDataPageState extends State<PPGDataPage> {
  List<String> _ppgData = [];
  static const platform = MethodChannel('com.attentive_hub.polar_ppg');

  @override
  void initState() {
    super.initState();
    _listenForPPGData();
  }

  void _listenForPPGData() {
    platform.setMethodCallHandler((call) async {
      if (call.method == "onPPGDataReceived") {
        final String ppgData = "ppg0: ${call.arguments['ppg0']}, ppg1: ${call.arguments['ppg1']}, ppg2: ${call.arguments['ppg2']}, ambient: ${call.arguments['ambient']}, timeStamp: ${call.arguments['timeStamp']}";
        updatePPGData(ppgData);
      }
      return null; // Adding return null to avoid warning about missing return.
    });
  }

  void updatePPGData(String data) {
    setState(() {
      _ppgData.add(data);
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('PPG Data'),
      ),
      body: ListView.builder(
        itemCount: _ppgData.length,
        itemBuilder: (context, index) {
          return ListTile(
            title: Text(_ppgData[index]),
          );
        },
      ),
    );
  }
}
