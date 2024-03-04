import 'package:flutter/cupertino.dart';

class ChannelDataWidget extends StatelessWidget {
  final String data;
  final String title;

  const ChannelDataWidget({
    Key? key,
    required this.data,
    required this.title,
  }) : super(key: key);

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.all(8.0),
      child: Column(
        children: [
          Text("$title Data: $data"),
        ],
      ),
    );
  }
}
