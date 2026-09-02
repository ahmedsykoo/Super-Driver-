import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:shared_preferences/shared_preferences.dart';
import 'screens/home_screen.dart';
import 'screens/login_screen.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  final prefs = await SharedPreferences.getInstance();
  final code = prefs.getString('locale') ?? 'ar';
  final isDark = prefs.getBool('is_dark_mode') ?? true;
  final loggedIn = prefs.getBool('logged_in') ?? false;
  runApp(SuperDriverApp(
    initialLocale: Locale(code),
    initialDarkMode: isDark,
    loggedIn: loggedIn,
  ));
}

class SuperDriverApp extends StatefulWidget {
  final Locale initialLocale;
  final bool initialDarkMode;
  final bool loggedIn;

  const SuperDriverApp({
    super.key,
    required this.initialLocale,
    required this.initialDarkMode,
    required this.loggedIn,
  });

  @override
  State<SuperDriverApp> createState() => _SuperDriverAppState();
}

class _SuperDriverAppState extends State<SuperDriverApp> {
  late Locale _locale = widget.initialLocale;
  late bool _isDark = widget.initialDarkMode;
  late bool _loggedIn = widget.loggedIn;

  Future<void> _setLocale(Locale locale) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString('locale', locale.languageCode);
    if (mounted) setState(() => _locale = locale);
  }

  Future<void> _toggleDarkMode() async {
    final prefs = await SharedPreferences.getInstance();
    final newMode = !_isDark;
    await prefs.setBool('is_dark_mode', newMode);
    if (mounted) setState(() => _isDark = newMode);
  }

  @override
  Widget build(BuildContext context) {
    const primaryColor = Color(0xFF0284C7);
    const accentColor = Color(0xFF10B981);
    const darkBg = Color(0xFF0F172A);
    const darkCard = Color(0xFF1E293B);

    final darkTheme = ThemeData(
      useMaterial3: true,
      brightness: Brightness.dark,
      scaffoldBackgroundColor: darkBg,
      colorScheme: const ColorScheme.dark(
        primary: primaryColor,
        secondary: accentColor,
        surface: darkCard,
        surfaceContainerHighest: Color(0xFF334155),
        onPrimary: Colors.white,
        onSurface: Color(0xFFF8FAFC),
      ),
      cardTheme: CardTheme(
        color: darkCard,
        elevation: 0,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(20),
          side: const BorderSide(color: Color(0xFF334155), width: 1),
        ),
        margin: const EdgeInsets.only(bottom: 12),
      ),
      appBarTheme: const AppBarTheme(
        backgroundColor: darkBg,
        elevation: 0,
        scrolledUnderElevation: 0,
        centerTitle: true,
        titleTextStyle: TextStyle(
          fontSize: 20,
          fontWeight: FontWeight.bold,
          letterSpacing: 0.5,
          color: Colors.white,
        ),
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: const Color(0xFF0F172A),
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(14),
          borderSide: const BorderSide(color: Color(0xFF334155)),
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(14),
          borderSide: const BorderSide(color: Color(0xFF334155)),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(14),
          borderSide: const BorderSide(color: primaryColor, width: 2),
        ),
        labelStyle: const TextStyle(color: Color(0xFF94A3B8)),
      ),
    );

    final lightTheme = ThemeData(
      useMaterial3: true,
      brightness: Brightness.light,
      scaffoldBackgroundColor: const Color(0xFFF1F5F9),
      colorScheme: const ColorScheme.light(
        primary: primaryColor,
        secondary: accentColor,
        surface: Colors.white,
        surfaceContainerHighest: Color(0xFFE2E8F0),
        onPrimary: Colors.white,
        onSurface: Color(0xFF0F172A),
      ),
      cardTheme: CardTheme(
        color: Colors.white,
        elevation: 1,
        shadowColor: Colors.black12,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(20),
          side: const BorderSide(color: Color(0xFFE2E8F0), width: 1),
        ),
        margin: const EdgeInsets.only(bottom: 12),
      ),
      appBarTheme: const AppBarTheme(
        backgroundColor: Color(0xFFF1F5F9),
        elevation: 0,
        scrolledUnderElevation: 0,
        centerTitle: true,
        titleTextStyle: TextStyle(
          fontSize: 20,
          fontWeight: FontWeight.bold,
          letterSpacing: 0.5,
          color: Color(0xFF0F172A),
        ),
      ),
      inputDecorationTheme: InputDecorationTheme(
        filled: true,
        fillColor: Colors.white,
        border: OutlineInputBorder(
          borderRadius: BorderRadius.circular(14),
          borderSide: const BorderSide(color: Color(0xFFCBD5E1)),
        ),
        enabledBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(14),
          borderSide: const BorderSide(color: Color(0xFFCBD5E1)),
        ),
        focusedBorder: OutlineInputBorder(
          borderRadius: BorderRadius.circular(14),
          borderSide: const BorderSide(color: primaryColor, width: 2),
        ),
        labelStyle: const TextStyle(color: Color(0xFF64748B)),
      ),
    );

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
      themeMode: _isDark ? ThemeMode.dark : ThemeMode.light,
      theme: lightTheme,
      darkTheme: darkTheme,
      home: _loggedIn
          ? HomeScreen(
              onLocaleChange: _setLocale,
              onThemeToggle: _toggleDarkMode,
              isDark: _isDark,
            )
          : LoginScreen(onLoggedIn: () => setState(() => _loggedIn = true)),
    );
  }
}
