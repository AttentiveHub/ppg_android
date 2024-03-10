import 'dart:ui';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

class DataPage extends StatefulWidget {
  const DataPage({super.key});

  @override
  State<DataPage> createState() => _DataPageState();
}

class _DataPageState extends State<DataPage> {
  final MethodChannel platform = const MethodChannel('com.attentive_hub.polar_ppg.data');

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
      } else if (call.method == "resetChannels") {
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
      }
    });
  }

  Widget channelDataCard(String title, String data) {
    return Container(
      margin: const EdgeInsets.all(8.0),
      padding: const EdgeInsets.all(16.0),
      decoration: BoxDecoration(
        color: Colors.greenAccent, // Background color of the container
        borderRadius: BorderRadius.circular(10), // Rounded corners
        boxShadow: [
          BoxShadow(
            color: Colors.grey.withOpacity(0.2),
            spreadRadius: 2,
            blurRadius: 5,
            offset: const Offset(0, 3), // changes position of shadow
          ),
        ],
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          Text(
            title,
            style: const TextStyle(
              fontSize: 16.0,
              fontWeight: FontWeight.bold,
              color: Colors.black,
            ),
          ),
          const SizedBox(height: 8.0), // Spacing between title and data
          Text(
            data.isEmpty ? "No Data" : data,
            style: const TextStyle(
              fontSize: 14.0,
              color: Colors.black,
            ),
          ),
        ],
      ),
    );
  }


  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text(
          "Data Page",
          style: TextStyle(fontWeight: FontWeight.bold), // Make text bold
        ),
        centerTitle: true, // Center the title
        backgroundColor: Colors.grey.shade400,
      ),
      body: SingleChildScrollView( // Allows scrolling when content overflows
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: <Widget>[
            channelDataCard("HR Data", hrData),
            channelDataCard("ECG Data", ecgData),
            channelDataCard("ACC Data", accData),
            channelDataCard("PPG Data", ppgData),
            channelDataCard("PPI Data", ppiData),
            channelDataCard("Gyro Data", gyroData),
            channelDataCard("Mag Data", magData),
          ],
        ),
      ),
    );
  }
}