import 'package:flutter/material.dart';

class GraphPage extends StatelessWidget {
  const GraphPage({Key? key}) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text("Graphs"),
      ),
      body: const Center(
        child: Text("Graphs will be displayed here."),
      ),
    );
  }
}
