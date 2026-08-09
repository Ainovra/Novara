# Novara — Web App

Naye features ke saath: chat rename, chat sharing (read-only public link), intro/welcome screen (ab wire ho gaya), Terms & Conditions gate, message par like/dislike/copy/share/play(awaaz), web search toggle, video generation/editing studio (placeholder, provider connect karna hoga), aur "Novara galtiyan kar sakta hai" disclaimer.

## 1. GitHub pe upload

Is baar in files ko GitHub repo me daalo (jahan pehle wali files thi, unhe replace/update karna hai):

**Root me:**
- `app.py` (replace purani wali)
- `requirements.txt` (replace — koi naya dependency nahi juda)
- `Procfile` (same rahegi)
- `README.md` (replace)

**`templates/` folder me (replace sab + 3 nayi files add karo):**
- `login.html` (chota sa change — redirect ab terms/app par hota hai)
- `signup.html` (chota sa change)
- `index.html` (naya UI: rename/share icons, web search toggle, message actions, disclaimer)
- `admin.html` (same)
- `welcome.html` (same content, ab isko route mil gaya hai)
- `terms.html` — **NAYI FILE**
- `share.html` — **NAYI FILE**
- `video.html` — **NAYI FILE**

**`static/` folder me (replace sab):**
- `style.css`
- `script.js`

## 2. Ek zaroori badlaav — home page ab `/app` hai

Pehle chat "/" par khulti thi. Ab:
- `/` → agar login nahi ho to `/welcome` (intro) dikhayega, login ho to seedha chat kholega
- `/welcome` → intro/onboarding slides (Claude jaisa)
- `/login`, `/signup` → same
- `/terms` → naye users ko yahan se guzarna padta hai (checkbox tick karke) pehli baar
- `/app` → asli chat screen
- `/video` → Video Studio (generation + editing)
- `/share/<code>` → kisi bhi share ki hui chat ka public read-only link

Agar kahin bhi pehle se "/" ka hardcoded link diya hai (jaise bookmarks), wo apne aap sahi jagah redirect ho jayega — code karne ki zaroorat nahi.

## 3. Render pe Environment Variables (same jo pehle the)

| Key | Value |
|---|---|
| `GEMINI_API_KEY` | (already hai, wahi rahega) |
| `FLASK_SECRET` | koi bhi random lambi string |
| `ADMIN_SECRET` | admin panel access ke liye |
| `GOOGLE_CLIENT_ID` / `GOOGLE_CLIENT_SECRET` | Google login ke liye (optional) |

**Naye, optional (Video Studio ko live karne ke liye):**

| Key | Value |
|---|---|
| `VIDEO_API_URL` | Kisi video-generation provider (Runway, Pika, Kling, etc.) ka API endpoint |
| `VIDEO_API_KEY` | Us provider ki API key |

Jab tak `VIDEO_API_URL` set nahi karte, Video Studio page dikhega lekin "abhi configure nahi hai" bolega — koi crash nahi hoga. Jis din koi video-gen provider choose karo, sirf `app.py` mein `api_video_generate()` function ke andar us provider ke request/response format ke hisaab se thoda tweak karna padega.

## 4. Web search — bina API key ke

`WEB_HINTS` (jaise "latest", "aaj ka", "news") wale sawaal par, ya jab user manually **Web search** toggle on karke poochta hai, Novara DuckDuckGo se free mein top results utha ke Gemini ko context ke roop mein deta hai — koi search API key nahi chahiye. Agar aage chal ke behtar/zyada reliable results chahiye ho to `search_web()` function ko Serper.dev, Tavily, ya Bing Search API se replace kar sakte ho (in sab ki apni free/paid tier hoti hai).

## 5. Naye features — kya kaam karta hai kaise

- **Rename** — sidebar mein har chat par hover karo, ✎ icon dabao, naya naam save karo
- **Share** — ↗ icon dabao (sidebar ya kisi bhi AI jawab ke neeche) — ek public read-only link milega jo koi bhi khol sakta hai; "Sharing band karein" se link nakaam kar sakte ho
- **Like/Dislike** — har AI jawab ke neeche 👍👎 — feedback database mein save hota hai
- **Copy** — 📋 se jawab ka text clipboard mein copy ho jaata hai
- **Play (awaaz)** — 🔊 se jawab browser ki built-in text-to-speech se sunaya jaata hai (koi extra API/cost nahi)
- **Web search toggle** — topbar mein "Web search" button — on karne par har sawaal ke saath current web results bhi AI ko diye jaate hain
- **Terms & Conditions** — naya signup/Google login karne wale users ko pehli baar `/terms` par tick lagana padta hai, tabhi chat khulti hai
- **Introduction / Welcome** — pehli visit par 4 slides ka intro (jaisa Claude/ChatGPT karte hain), "Skip" ka option bhi hai
- **Video Studio** — sidebar mein "🎬 Video Studio" link — generation aur editing dono ke liye UI ready hai, bas ek provider connect karna baaki hai
- **"Novara galtiyan kar sakta hai"** — chat input ke neeche hamesha dikhta hai, jaise ChatGPT/Claude mein hota hai

## 6. Purane features (waise hi chalte rahenge)

- Google/Gmail login, username-password login
- Multiple conversations, photo/PDF attach (video attach hota hai par AI usse analyze nahi karta)
- Speech-to-text (mic icon)
- Light/Dark mode
- Admin panel — `https://your-app.onrender.com/admin?key=YOUR_ADMIN_SECRET`

## 7. Newest changes (this update)

- **English UI** — the welcome/intro slides and all interface text now show in English instead of Roman Hindi. (Novara's actual chat replies still adapt to whatever language you write in — this only changed static UI labels.)
- **Image generation** — a new "🎨 Create image" toggle sits next to "Web search" in the top bar. Turn it on, describe what you want, and Novara generates an image instead of replying with text. It calls a separate model (`IMAGE_MODEL` env var, defaults to `gemini-3.5-flash-image`) via the same Gemini API key.
  - **Important:** whether your Gemini API key actually has access to an image-generation model depends on your Google AI Studio plan/region. If you get an error when generating, open Google AI Studio, check which image-capable model name is available to your key, and set it as the `IMAGE_MODEL` environment variable on Render (Environment tab → Add Environment Variable → key `IMAGE_MODEL`, value = the exact model name).
- **Real microphone waveform** — the mic button now shows live animated bars that move with your actual voice volume (using the Web Audio API), not a generic canned animation. This requires microphone permission in the browser.
- **Broader attachments** — the paperclip now accepts images, videos, PDFs, and common document/plugin-style files (`.doc`, `.docx`, `.ppt`, `.pptx`, `.xls`, `.xlsx`, `.txt`, `.csv`, `.json`, `.zip`, `.apk`). Only images and PDFs are actually understood by the AI (vision + text extraction); other file types are stored and shown as attachments but not analyzed.
- **Packaging for Android (APK)** — this app is a responsive web app, which is the easiest starting point for wrapping into an Android APK later (e.g. with a WebView shell in Android Studio, or a tool like Capacitor/Trusted Web Activity). No changes were needed here for that — just deploy this to Render first, then point your Android project's WebView/TWA at your live `https://your-app.onrender.com` URL.

## 8. New: redesigned login/signup

Login and signup pages now show two options as white rounded buttons: **Continue with Google** and **Continue with email**. There's also a "Skip — continue as guest" text link at the bottom.

**Continue with email** just reveals the same username/password fields as before, in a cleaner flow.

## 9. New: permanent database (Neon PostgreSQL)

Until now, Novara stored everything (users, chats, messages) in a local SQLite file on Render's server — which can get wiped whenever Render restarts or redeploys the app. This section connects a real, permanent cloud database instead. **No Termux, no local install, no terminal commands** — this is done entirely through two websites in your browser.

Render's own free PostgreSQL expires after 30 days and gets deleted, so we're using **[Neon](https://neon.tech)** instead, which has a genuinely permanent free tier.

### Steps

1. Go to [neon.tech](https://neon.tech) and sign up (signing up with GitHub is easiest)
2. A project is created automatically (or click "Create Project")
3. On your project's dashboard, find the **Connection String** — it looks like:
   ```
   postgresql://username:password@ep-xxxx.neon.tech/dbname?sslmode=require
   ```
4. Copy it
5. Go to your Render dashboard → your service → **Environment** tab
6. **Add Environment Variable**:
   - Key: `DATABASE_URL`
   - Value: paste the connection string from step 4
7. **Manual Deploy → Deploy latest commit**

That's it. The app automatically detects `DATABASE_URL` and switches from the local file to Neon — no other setup needed. If `DATABASE_URL` isn't set, the app keeps working exactly as before (local SQLite), so this is safe to try.

**Note on file attachments (images/PDFs/videos):** this only makes your *database* (accounts, chats, messages) permanent. Uploaded files on Render's free tier can still be lost on redeploy, since Render's free disk isn't persistent either. That's a separate, optional upgrade for later if it becomes a problem — for now, chat history and accounts are the important part, and those are now safe.

## Quota note

Har text message = 1 Gemini API call (web search results context ke roop mein add hote hain, extra API call Gemini ki taraf se nahi lagti). Image generation ek alag, separate API call hai. Free tier ki daily limit (~20 requests/day) same rahegi — image generation bhi isi quota mein count hoti hai.
