import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import 'ChannelSelectionModel.dart';
import 'dataPage.dart';
import 'graphPage.dart';
import 'homePage.dart';

void main() {
  runApp(
    ChangeNotifierProvider(
      create: (context) => ChannelSelectionModel(),
      child: const MyApp(),
    ),
  );
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
      home: const MainPage(),
    );
  }
}

// New MainPage widget
class MainPage extends StatefulWidget {
  const MainPage({Key? key}) : super(key: key);

  @override
  State<MainPage> createState() => _MainPageState();
}

class _MainPageState extends State<MainPage> {
  int _selectedIndex = 0;

  final List<Widget> _pages = [
    const MyHomePage(title: 'Home'), // Assuming MyHomePage is your home page
    // const DataPage(), // Create this widget for displaying data
    const GraphPage(), // Placeholder widget for future graph functionalities
  ];

  void _onItemTapped(int index) {
    setState(() {
      _selectedIndex = index;
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: IndexedStack(
        index: _selectedIndex,
        children: _pages,
      ),
      bottomNavigationBar: BottomNavigationBar(
        items: const [
          BottomNavigationBarItem(icon: Icon(Icons.radio_button_checked), label: 'Configure'),
          BottomNavigationBarItem(icon: Icon(Icons.monitor_heart_outlined), label: 'Data'),
          BottomNavigationBarItem(icon: Icon(Icons.bar_chart), label: 'Graphs'),
        ],
        currentIndex: _selectedIndex,
        onTap: _onItemTapped,
        selectedItemColor: Colors.greenAccent,
        selectedLabelStyle: const TextStyle(fontWeight: FontWeight.bold),
      ),
    );
  }
}
