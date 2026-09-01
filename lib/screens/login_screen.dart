import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';

class LoginScreen extends StatefulWidget {
  final VoidCallback onLoggedIn;
  const LoginScreen({super.key, required this.onLoggedIn});

  @override
  State<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends State<LoginScreen> {
  bool get isArabic => Localizations.localeOf(context).languageCode == 'ar';
  String _tr(String ar, String en) => isArabic ? ar : en;
  final _phone = TextEditingController();
  final _password = TextEditingController();
  bool _obscure = true;
  bool _loading = false;

  Future<void> _login() async {
    final phone = _phone.text.trim();
    final password = _password.text;
    if (phone.isEmpty || password.isEmpty) {
      ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(_tr('اكتب رقم الهاتف وكلمة المرور أولاً', 'Enter phone number and password first'))),
      );
      return;
    }
    setState(() => _loading = true);
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool('logged_in', true);
    await prefs.setString('user_phone', phone);
    if (mounted) widget.onLoggedIn();
  }

  @override
  void dispose() {
    _phone.dispose();
    _password.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return Directionality(
      textDirection: isArabic ? TextDirection.rtl : TextDirection.ltr,
      child: Scaffold(
        body: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.all(24),
            child: Card(
              child: Padding(
                padding: const EdgeInsets.all(24),
                child: Column(
                  children: [
                    const Icon(Icons.local_taxi, size: 72, color: Colors.blue),
                    const SizedBox(height: 16),
                    const Text('Super Driver', style: TextStyle(fontSize: 30, fontWeight: FontWeight.bold)),
                    const SizedBox(height: 8),
                    Text(_tr('سجّل الدخول للمتابعة', 'Sign in to continue'), style: const TextStyle(fontSize: 18)),
                    const SizedBox(height: 28),
                    TextField(
                      controller: _phone,
                      keyboardType: TextInputType.phone,
                      decoration: InputDecoration(labelText: _tr('رقم الهاتف', 'Phone number'), prefixIcon: const Icon(Icons.phone), border: const OutlineInputBorder()),
                    ),
                    const SizedBox(height: 14),
                    TextField(
                      controller: _password,
                      obscureText: _obscure,
                      decoration: InputDecoration(labelText: _tr('كلمة المرور', 'Password'), prefixIcon: const Icon(Icons.lock), border: const OutlineInputBorder(), suffixIcon: IconButton(icon: Icon(Icons.visibility), onPressed: () => setState(() => _obscure = !_obscure))),
                    ),
                    const SizedBox(height: 22),
                    SizedBox(width: double.infinity, child: FilledButton.icon(onPressed: _loading ? null : _login, icon: const Icon(Icons.login), label: Text(_loading ? _tr('جارٍ الدخول...', 'Signing in...') : _tr('تسجيل الدخول', 'Sign in')))),
                    const SizedBox(height: 10),
                    Text(_tr('هذه النسخة التجريبية تحفظ تسجيل الدخول على الجهاز.', 'This demo stores your sign-in locally on the device.'), textAlign: TextAlign.center, style: const TextStyle(color: Colors.grey)),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
