# Backend integration for the Android WebView shell

Use the supplied **latest app.py** as the base. Do not replace it with the older ZIP version.

The file `backend_changes/app.py` is the latest app.py with the minimal Android/Play-Store compatibility changes applied.

## Changes made

1. `FLASK_SECRET` is now required instead of silently using `change-this-secret-in-production`.
2. Secure session-cookie flags are enabled for HTTPS production hosting.
3. A `mobile_auth_codes` table is created in the existing database.
4. Google OAuth supports `?mobile=1`.
5. A short-lived one-time mobile auth code is issued after Google OAuth.
6. `/auth/mobile-consume` exchanges that code for the normal Flask session cookie inside the WebView.
7. `/profile` is restored for the supplied settings page.
8. `/api/account/delete` is restored and now removes message attachments belonging to the deleted user.
9. `/account/delete` provides the external account-deletion resource required for Play Store account-creation apps.
10. `/account/deleted`, `/legal/privacy`, and `/legal/terms` are available.
11. Password login preserves a safe local `next` path when the user was sent to login from `/account/delete`.

## Files to merge

- Replace the repository's current `app.py` with `backend_changes/app.py`.
- Merge `backend_changes/login.html` into `templates/login.html` (it only adds safe `next` preservation and keeps the existing Novara UI).
- Keep the existing `templates/account-delete.html`; the supplied copy only corrects its login return path.
- Keep all existing `static/` assets, including the Novara PNGs.
- Keep the existing `requirements.txt`; it already includes Flask, gunicorn, Authlib, psycopg2-binary, pypdf, requests, and huggingface_hub.
- Add the supplied `Procfile` if you want Render to use it as the start command source.

## Render environment variables

Set these in Render. Never put them in Android or GitHub source:

`FLASK_SECRET`
`DATABASE_URL`
`GEMINI_API_KEY`
`HF_TOKEN`
`GOOGLE_CLIENT_ID`
`GOOGLE_CLIENT_SECRET`
`VIDEO_API_URL` (only if video generation is configured)
`VIDEO_API_KEY` (only if video generation is configured)
`ADMIN_SECRET`
`CONTACT_EMAIL`
`HF_IMAGE_MODEL` (optional)

`CONTACT_EMAIL` is used only by the external deletion page for users who can no longer log in.

## Google OAuth redirect URI

Keep the normal web callback pointing at your deployed HTTPS domain:

`https://YOUR_DOMAIN/auth/google/callback`

There is no separate Android Google client secret. Android only receives a short-lived one-time handoff code.
