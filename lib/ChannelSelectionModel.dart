import 'package:flutter/cupertino.dart';

class ChannelSelectionModel extends ChangeNotifier {
  List<String>? _selectedChannels;

  List<String>? get selectedChannels => _selectedChannels;

  void setSelectedChannels(List<String> channels) {
    _selectedChannels = channels;
    notifyListeners();
  }

  void resetChannels() {
    _selectedChannels = null;
    notifyListeners();
  }
}
