# Novara Final UI + Image Update

## Image generation
- Uses Hugging Face Inference Providers.
- Default model: black-forest-labs/FLUX.1-schnell.
- Render environment variable required: HF_TOKEN.
- Optional: HF_IMAGE_MODEL.
- Hugging Face Free accounts currently have limited monthly Inference Provider credits; this is for testing, not unlimited free production usage.

## UI fixes
- Restored the full Settings/Profile route and settings UI from the latest polished backend.
- Voice assistant is beside Send; empty input shows a clean microphone button, typed input shows an upward Send arrow.
- Restored the existing AI-response Read Aloud (speaker) button.
- Fixed all Novara icon paths for icons stored directly in /static/ (not /static/icons/).
- Added consistent Novara logo/favicon usage to welcome, login, signup, terms, profile, privacy, legal terms and chat pages.
- Manifest icon paths now match /static/.
- Existing user icon PNGs are intentionally not included in this ZIP; keep them in static/.

## Important
Merge/replace these files in the existing repository; do not delete the existing static icon PNGs.
