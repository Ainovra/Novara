Novara dedicated Voice-to-Voice button patch

Changes:
- Removes the duplicate microphone/voice control that was placed before the text field.
- Keeps one dedicated FilledIconButton for Voice-to-Voice beside the normal composer controls.
- Normal text field and Send button remain independent.
- Tap the dedicated mic to open the existing full-screen continuous voice mode.
- Tap X while active to stop voice mode.

Apply:
unzip -o /sdcard/Download/novara_voice_button_patch.zip -d ~/Novara/android

Then build:
cd ~/Novara/android && ./gradlew assembleDebug --console=plain
