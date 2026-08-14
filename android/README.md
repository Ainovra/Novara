# Novara Android WebView

A native Android shell around the existing Novara Flask website.

## Architecture

GitHub -> Render -> Novara Flask website -> Android WebView -> Google Play

The Android app contains no Gemini, Hugging Face, Google OAuth client secret, Neon database password, or other private backend secret.

## 1. Set the website URL

Open `app/build.gradle` and change:

`buildConfigField 'String', 'WEB_BASE_URL', '"https://YOUR-NOVARA-RENDER-URL.onrender.com"'`

to your real HTTPS Render URL.

Later, if you use a permanent custom domain, change that one line to:

`buildConfigField 'String', 'WEB_BASE_URL', '"https://yourdomain.com"'`

If you make that domain change after release, publish one Android update. After the app points to the permanent domain, normal website/backend updates do not require another AAB.

## 2. Android package/version

- Application ID: `com.novara.app`
- App name: `Novara`
- versionCode: `1`
- versionName: `1.0.0`
- compileSdk/targetSdk: `36`
- minSdk: `23`

As of August 31, 2026, Google Play requires new apps and updates to target Android 16 / API 36 or higher.

## 3. Google Sign-In flow

Google sign-in is intentionally not loaded inside the WebView.

1. WebView intercepts Novara `/auth/google`.
2. Android opens the URL in a Custom Tab with `?mobile=1`.
3. Google authenticates in the browser context.
4. Flask creates a short-lived, one-time mobile auth code in PostgreSQL.
5. Flask redirects to `novara://auth-complete?code=...`.
6. Android receives the code and loads `/auth/mobile-consume?code=...` inside its WebView.
7. Flask consumes the code, creates the normal Flask session cookie, and redirects to `/app` or `/terms`.

The code is stored hashed in the database, expires after 120 seconds, and is deleted when consumed.

## 4. Backend secrets

Keep these only in Render environment variables:

- FLASK_SECRET
- DATABASE_URL
- GEMINI_API_KEY
- HF_TOKEN
- GOOGLE_CLIENT_ID
- GOOGLE_CLIENT_SECRET
- VIDEO_API_URL (if used)
- VIDEO_API_KEY (if used)
- ADMIN_SECRET
- CONTACT_EMAIL
- HF_IMAGE_MODEL (optional)

Never commit `.env` or real secrets to GitHub.

## 5. Render

Recommended Render Web Service settings:

- Runtime: Python
- Build Command: `pip install -r requirements.txt`
- Start Command: `gunicorn app:app --bind 0.0.0.0:$PORT`
- Branch: your production branch, normally `main`
- Auto-Deploy: On Commit

Connect the GitHub repository to Render. With Auto-Deploy enabled, a push/merge to the linked branch triggers a new deployment.

## 6. Website changes that do NOT need a new AAB

- Flask/Python changes
- HTML changes
- CSS changes
- JavaScript changes
- AI prompts
- AI model selection
- server-side image generation logic
- database queries/migrations
- website settings/features
- normal bug fixes

Workflow:

GitHub push -> Render deploy -> existing Android WebView loads the updated website.

## 7. Changes that DO need a new AAB

- Android permissions
- native notifications
- native camera/getUserMedia implementation
- native file handling
- WebView configuration
- Android intents/deep links
- Android-specific APIs
- native icon
- native splash implementation
- native libraries
- package/application ID
- other Android-native code

For an Android-native release, increase `versionCode`, update `versionName` as appropriate, build a signed AAB, and upload it to Google Play Console.

## 8. Play Store checklist

Before production submission:

- Add the real Novara launcher icon (the project already includes the supplied Novara logo as `drawable-nodpi/novara_logo.png`).
- Verify the Privacy Policy URL: `/legal/privacy`.
- Verify Terms URL: `/legal/terms`.
- Verify in-app account deletion: `/profile` -> Delete account.
- Verify external account deletion URL: `/account/delete`.
- Complete Google Play Data safety form accurately.
- Complete the account deletion/data deletion questions.
- Test login, logout, signup, guest mode, Google login, uploads, image generation, chat history, sharing, and deletion on a real Android device.
- Test offline mode and retry.
- Test Android back navigation.
- Test file picker and camera capture.
- Test both fresh install and upgrade from an earlier version.

## 9. Important backend hardening

The patch keeps the existing Novara architecture and adds only the pieces needed for Android/Play compatibility. It also:

- requires `FLASK_SECRET` instead of silently using the production-unsafe fallback secret;
- adds secure Flask session cookie flags;
- adds one-time mobile OAuth handoff codes;
- restores/implements public privacy and terms routes;
- adds an in-app and external account-deletion route;
- deletes attachment files referenced by a deleted user's messages;
- keeps Neon/PostgreSQL as the server-side database.

The latest `app.py` still saves uploads/generated images to the local `uploads/` directory. Treat that filesystem as temporary on hosts where the filesystem is not persistent. For long-term production storage, migrate uploads/generated images to object storage separately; this is not required for the WebView architecture itself.
