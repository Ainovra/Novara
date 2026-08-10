# Novara latest UI / image update

## Main updates
- The composer now uses one smart action button: microphone when empty, upward-arrow Send when text/file is present.
- Voice input has a polished listening waveform and haptic feedback.
- Generated images show a hint: type a change to edit the latest generated image.
- Image editing is automatic for edit-style requests such as "make the sky blue" after an image was generated.
- Image generation prompts are made stricter so named subjects are preserved instead of being casually substituted.
- Pollinations current API is used when `POLLINATIONS_API_KEY` is set on Render.

## Render environment variables
- `POLLINATIONS_API_KEY` — keep this secret on Render; never put it in GitHub or frontend JavaScript.
- `POLLINATIONS_IMAGE_MODEL` — optional, defaults to `flux`.
- `POLLINATIONS_EDIT_MODEL` — optional, defaults to `kontext`.

The existing `static/icons/` images are intentionally not included in this ZIP because the project already has the user's icon files in GitHub. Do not delete that folder when replacing files.

## Important limitation
The Capabilities page includes the Code execution and Artifacts controls to match the requested UI, but this codebase does not contain a secure server-side code-execution sandbox. Turning that UI switch on does not create a sandbox by itself.
