import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:provider/provider.dart';
import 'ChannelSelectionModel.dart'; // Adjust the import path as necessary

class DataPage extends StatefulWidget {
  const DataPage({Key? key}) : super(key: key);

  @override
  State<DataPage> createState() => _DataPageState();
}

class _DataPageState extends State<DataPage> {
  // final MethodChannel platform = const MethodChannel('com.attentive_hub.polar_ppg.Data');
  Map<String, String> channelData = {};

  @override
  void initState() {
    super.initState();
    // platform.setMethodCallHandler(_handlePlatformMessages);
  }

  Future<void> _handlePlatformMessages(MethodCall call) async {
    if (mounted) {
      setState(() {
        // Assuming each method call provides a unique data key and value
        channelData[call.method] = call.arguments.toString();
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    // Using Provider to listen for changes in ChannelSelectionModel
    final channels = Provider.of<ChannelSelectionModel>(context).selectedChannels;

    // Start or stop listening based on channels state
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (channels == null || channels.isEmpty) {
        // platform.invokeMethod('stopListeningToChannels');
      } else {
        // platform.invokeMethod('startListeningToChannels', channels);
      }
    });

    return Scaffold(
      appBar: AppBar(title: const Text("Data Page")),
      body: Center(
        child: ListView(
          children: channelData.entries.map((entry) {
            return Padding(
              padding: const EdgeInsets.all(8.0),
              child: Text("${entry.key}: ${entry.value}"),
            );
          }).toList(),
        ),
      ),
    );
  }
}
