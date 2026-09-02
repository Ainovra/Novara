NOVARA — ALL-IN-ONE VOICE CONVERSATION UPDATE

This source includes the complete requested voice changes in one ChatScreen.kt:

1. Normal chat remains intact.
2. Left microphone is Voice-to-Text only and places recognized speech into the message field.
3. Send remains the normal send button.
4. A separate Voice-to-Voice button is the final control on the right.
5. Voice mode has a dedicated sophisticated voice visual; the Novara logo is NOT shown inside voice mode.
6. Six selectable voice profiles:
   - Calm
   - Warm
   - Soft
   - Clear
   - Deep
   - Bright
7. Voice profile can be changed from inside voice mode at any time; selection is persisted.
8. Profiles use the phone's installed TTS voices when available, with natural pitch/rate adjustments.
9. Mute button stops voice listening and can be toggled back on.
10. Faster recognition settings and partial results are enabled.
11. Barge-in behavior: if the user starts speaking while Novara is speaking, partial recognition stops Novara's TTS immediately and the recognized interruption is processed.
12. Existing API/DeepSeek/chat logic is retained.

IMPORTANT:
- This is a source-code update. Build it in the existing Novara Android project.
- It intentionally changes only ChatScreen.kt plus this README.
- The exact timbre/number of underlying TTS voices depends on the Android TTS engine installed on the device.

BUILD:
cd ~/Novara/android
unzip -o /sdcard/Download/novara_all_voice_requirements.zip -d .
./gradlew assembleDebug --console=plain

Install/test the generated debug APK before making any unrelated changes.
