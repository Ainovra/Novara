Novara Final Voice Fix Patch

This single patch fixes:
- Kotlin compilation errors from the previous all-in-one patch.
- Missing Image and painterResource imports.
- selectedVoiceProfile scope error.
- startVoiceListening forward-reference issue using safe restart state.
- Duplicate @Composable annotation.
- VoiceModeOverlay marked @Composable.
- Left microphone no longer launches the Android app/browser chooser.
  It now uses the in-app SpeechRecognizer and writes speech into the text box.
- Keeps the existing dedicated Voice-to-Voice button, Send button, six voice profiles,
  mute, barge-in, and voice UI behavior.

Apply:
cd ~/Novara/android
unzip -o /sdcard/Download/novara_final_voice_fix.zip -d .
./gradlew assembleDebug --console=plain
