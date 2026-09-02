# Novara Voice-to-Voice

This source snapshot adds a continuous voice conversation mode to `ChatScreen.kt`.

Behavior:
- Tap the microphone/voice button.
- Novara opens a dedicated voice screen.
- Android SpeechRecognizer listens directly without the normal speech-recognition dialog.
- The recognized speech is sent through the existing `ApiClient.sendMessage()` conversation flow.
- Novara speaks the AI response using Android TextToSpeech.
- When speaking finishes, listening starts again automatically.
- There is no need to type into the chat box or press Send while voice mode is active.
- The existing conversation ID is preserved, so voice turns stay in the same Novara conversation.
- RECORD_AUDIO is already present in the supplied AndroidManifest.xml.

Important:
- This uses DeepSeek/your existing Novara chat backend as the AI brain; it does not require a separate AI voice model.
- Speech recognition and TTS are handled on-device/through Android's configured speech services.
- A fully cloud-native realtime audio system (streaming audio in/out with interruption/barge-in) would require a separate realtime audio backend/API.
