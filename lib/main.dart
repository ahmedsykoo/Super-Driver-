import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'screens/home_screen.dart';
import 'screens/login_screen.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  final prefs = await SharedPreferences.getInstance();
  final code = prefs.getString('locale') ?? 'ar';
  final loggedIn = prefs.getBool('logged_in') ?? false;
  runApp(SuperDriverApp(initialLocale: Locale(code), loggedIn: loggedIn));
}

class SuperDriverApp extends StatefulWidget {
  final Locale initialLocale;
  final bool loggedIn;
  const SuperDriverApp({super.key, required this.initialLocale, required this.loggedIn});

  @override
  State<SuperDriverApp> createState() => _SuperDriverAppState();
}

class _SuperDriverAppState extends State<SuperDriverApp> {
  late Locale _locale = widget.initialLocale;
  late bool _loggedIn = widget.loggedIn;

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
      home: _loggedIn
          ? HomeScreen(onLocaleChange: _setLocale)
          : LoginScreen(onLoggedIn: () => setState(() => _loggedIn = true)),
    );
  }
}
