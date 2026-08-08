# MyAI — Standalone Web App (Render.com deployment)

Ye guide MyAI ko ek **hamesha-online, standalone app** banane ke liye hai — Termux ya phone khule rakhne ki zaroorat nahi. Ek baar deploy karne ke baad, ek fixed URL milega (jaise `myai.onrender.com`) jo kahin se bhi kholo, chale.

## Step 1 — GitHub pe code upload karo

1. [github.com](https://github.com) pe free account banao (agar nahi hai)
2. New repository banao, naam do `myai-app` (private ya public, dono chalega)
3. Is zip ke andar ki saari files us repo me upload kar do:
   - Browser se: repo page pe "Add file" → "Upload files" → poora `MyAI-App` folder ka content drag-drop kar do
   - (`app.py`, `requirements.txt`, `Procfile`, `templates/`, `static/`, `knowledge/`)

## Step 2 — Render.com pe deploy karo

1. [render.com](https://render.com) pe free account banao — "Sign up with GitHub" use karo, sabse easy hai
2. Dashboard me **New +** → **Web Service**
3. Apna `myai-app` GitHub repo select karo
4. Settings me:
   - **Name:** `myai` (jo bhi chaho)
   - **Runtime:** Python 3
   - **Build Command:** `pip install -r requirements.txt`
   - **Start Command:** `gunicorn app:app`
   - **Instance Type:** Free
5. **Environment Variables** section me add karo:
   - Key: `GEMINI_API_KEY`
   - Value: apna naya Gemini API key
6. **Create Web Service** click karo

Render 2-3 minute me build karega. Build complete hote hi ek URL milega jaise:

```
https://myai.onrender.com
```

Bas — yehi tumhara standalone app hai. Kahin se bhi, kisi bhi phone/laptop se ye URL kholo, MyAI chalu milega.

## Step 3 — Phone pe "app" jaisa banao

Us URL ko phone browser me kholo → menu → **"Add to Home Screen"**. Ab icon home screen pe aa jayega, tap karne pe full-screen app jaisa khulega — bilkul standalone app jaisa feel.

## Important notes

- **Free tier sleep:** Render ka free plan ~15 min inactivity ke baad app ko "sleep" kar deta hai. Agli baar koi kholega to pehli request thodi slow (10-20 sec) hogi, phir normal chalega. Ye normal hai free tier ka behavior.
- **File persistence:** Render ka free tier har deploy/restart pe filesystem reset kar deta hai — matlab uploaded PDFs, memory, aur history kabhi kabhi clear ho sakte hain agar app redeploy hoti hai. Agar permanent storage chahiye, baad me database (jaise free Postgres) add kar sakte hain — abhi ke liye chalu karne ke liye ye theek hai.
- **API quota:** Wahi Gemini free tier limit (~20 requests/day) applicable rahegi — is app me per message sirf 1 API call hoti hai (pehle 3 thi), isliye quota zyada chalegi.

## Local testing (optional, before deploying)

```bash
pip install -r requirements.txt --break-system-packages
export GEMINI_API_KEY="apna_key"
python app.py
```
Phir `http://127.0.0.1:5000` browser me kholo.
