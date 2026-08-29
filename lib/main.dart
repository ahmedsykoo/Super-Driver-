import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'screens/home_screen.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  final prefs = await SharedPreferences.getInstance();
  final code = prefs.getString('locale') ?? 'ar';
  runApp(SuperDriverApp(initialLocale: Locale(code)));
}

class SuperDriverApp extends StatefulWidget {
  final Locale initialLocale;
  const SuperDriverApp({super.key, required this.initialLocale});

  @override
  State<SuperDriverApp> createState() => _SuperDriverAppState();
}

class _SuperDriverAppState extends State<SuperDriverApp> {
  late Locale _locale = widget.initialLocale;

  Future<void> _setLocale(Locale locale) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString('locale', locale.languageCode);
    if (mounted) setState(() => _locale = locale);
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      title: 'Super Driver',
      locale: _locale,
      supportedLocales: const [Locale('ar'), Locale('en')],
      localizationsDelegates: const [
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
      theme: ThemeData(useMaterial3: true, colorSchemeSeed: Colors.blue),
      home: HomeScreen(onLocaleChange: _setLocale),
    );
  }
}
