# Novara — Web App

Naye features ke saath: Google/Gmail login, username-password login, chat history (multiple conversations), photo/video/PDF attachments, speech-to-text, light/dark theme, opening animation.

## 1. GitHub pe upload

Is baar in files ko GitHub repo me daalo (jahan pehle wali files thi, unhe replace/update karna hai):

**Root me:**
- `app.py` (replace purani wali)
- `requirements.txt` (replace)
- `Procfile` (same rahegi)
- `README.md` (replace)

**`templates/` folder me (replace sab):**
- `login.html`
- `signup.html`
- `index.html`
- `admin.html`

**`static/` folder me (replace sab):**
- `style.css`
- `script.js`

GitHub pe har file ko edit karke (pencil icon) purana content select-all karke delete karo, naya paste karo, phir commit karo. Ya naya upload karke overwrite karo.

## 2. Render pe naye Environment Variables add karo

Render dashboard → apni service → **Environment** tab me:

| Key | Value |
|---|---|
| `GEMINI_API_KEY` | (already hai, wahi rahega) |
| `FLASK_SECRET` | koi bhi random lambi string (jaise `novara-secret-8x92mK`) |
| `ADMIN_SECRET` | koi bhi secret jo sirf tumhe pata ho (admin panel access ke liye) |
| `GOOGLE_CLIENT_ID` | Step 3 se milega |
| `GOOGLE_CLIENT_SECRET` | Step 3 se milega |

`GOOGLE_CLIENT_ID`/`SECRET` na bhi daalo to app chalega — bas Google login button chhupa rahega, sirf username/password wala login milega.

## 3. Google Sign-In setup (Google Cloud Console)

Ye ek baar ka setup hai — free hai, Google account chahiye.

1. [console.cloud.google.com](https://console.cloud.google.com) kholo
2. Top pe **"New Project"** banao, koi naam do (jaise `novara-app`)
3. Left menu → **APIs & Services** → **OAuth consent screen**
   - User Type: **External** select karo
   - App name: `Novara`
   - User support email + developer email: apna Gmail daalo
   - Baaki default rehne do, **Save and Continue** karte jao (Scopes/Test users skip kar sakte ho)
4. Left menu → **APIs & Services** → **Credentials**
5. **+ Create Credentials** → **OAuth client ID**
   - Application type: **Web application**
   - Name: `Novara Web`
   - **Authorized redirect URIs** me add karo:
     ```
     https://your-app-name.onrender.com/auth/google/callback
     ```
     (apna asli Render URL daalna, jo tumhe pehle deploy karne pe mila tha)
6. **Create** dabao — ek popup me **Client ID** aur **Client Secret** milega
7. Ye dono copy karke Render ke Environment Variables me daalo (`GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET`)
8. Render pe service **redeploy** karo (Manual Deploy → Deploy latest commit)

Bas — ab login page pe "Continue with Google" button kaam karega.

## 4. Naye features

- **Google/Gmail login** — ek click me sign in, koi password yaad rakhne ki zaroorat nahi
- **Username + password login** — bina Google ke bhi account bana sakte ho
- **Multiple conversations** — sidebar me har purani chat alag dikhegi, click karke wapas khul jayegi
- **Photo attach** — image bhejo, AI usse "dekh" ke jawab dega (Gemini vision)
- **PDF attach** — seedha chat se PDF bhejo, us par sawaal poocho
- **Video attach** — attach ho jayega, dikhega, lekin AI usse analyze nahi karta (ye limitation hai — video samajhna abhi is setup me support nahi hai)
- **Speech-to-text** — mic icon dabao, bolo, text apne aap type ho jayega (Chrome/Android browsers me best kaam karta hai)
- **Light/Dark mode** — sidebar me sun/moon icon se toggle karo, choice yaad rehti hai
- **Opening animation** — app khulte hi "Novara" naam ka smooth intro animation
- **Admin panel** — `https://your-app.onrender.com/admin?key=YOUR_ADMIN_SECRET` pe jaake sabhi registered users dekh sakte ho (username, email, kitni conversations) — passwords kabhi nahi dikhte, wo hamesha hashed/encrypted rehte hain, ye sabki safety ke liye hai

## Quota note

Har message = 1 hi Gemini API call. Free tier ki daily limit (~20 requests/day) same rahegi.
