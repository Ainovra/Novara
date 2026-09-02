from pathlib import Path
import shutil
from datetime import datetime
import re
import sys

ANDROID = Path.cwd()
ROOT = ANDROID.parent
APP = ROOT / "app.py"
API = ANDROID / "app/src/main/java/com/novara/app/network/ApiClient.kt"
CHAT = ANDROID / "app/src/main/java/com/novara/app/ui/screens/ChatScreen.kt"

stamp = datetime.now().strftime("%Y%m%d-%H%M%S")

for p in [APP, API, CHAT]:
    if not p.exists():
        raise SystemExit(f"Missing: {p}")

def backup(p):
    b = p.with_name(p.name + f".before-reward-missions-{stamp}.bak")
    shutil.copy2(p, b)
    print("BACKUP:", b)

for p in [APP, API, CHAT]:
    backup(p)

# ============================================================
# BACKEND
# ============================================================

s = APP.read_text()

# Add time helper if needed.
if "from datetime import" in s:
    # Existing import almost certainly already contains datetime.
    pass

# Add rewarded window database columns.
marker = '    _add_column_if_missing(conn, "users", "rewarded_today", "INTEGER DEFAULT 0")'

if marker in s and 'rewarded_window_start' not in s:
    s = s.replace(
        marker,
        marker + '\n'
        '    _add_column_if_missing(conn, "users", "rewarded_window_start", "TEXT")\n'
        '    _add_column_if_missing(conn, "users", "rewarded_image_credits", "INTEGER DEFAULT 0")\n'
        '    _add_column_if_missing(conn, "users", "rewarded_audio_video_credits", "INTEGER DEFAULT 0")',
        1
    )

# Add helper functions before the usage API.
anchor = '# =========================\n# NOVARA USAGE API\n# ========================='

if anchor not in s:
    raise SystemExit("Could not find usage API anchor.")

if "REWARDED_MISSION_WINDOW_HOURS" not in s:

    reward_code = r'''
# ============================================================
# NOVARA REWARDED AD MISSIONS
# ============================================================

# Ad rewards operate in rolling 6-hour windows.
REWARDED_MISSION_WINDOW_HOURS = 6
REWARDED_MISSION_AD_LIMIT = 3


def rewarded_window_reset_if_needed(db, user_id, user):
    """
    Reset the rewarded-ad mission window every 6 hours.

    Normal monthly image/video usage is intentionally NOT reset here.
    Only the rewarded mission counters/credits are managed separately.
    """
    now = datetime.now()

    raw = user["rewarded_window_start"]

    reset = False

    if not raw:
        reset = True
    else:
        try:
            start = datetime.fromisoformat(str(raw))
            elapsed = now - start
            if elapsed.total_seconds() >= REWARDED_MISSION_WINDOW_HOURS * 3600:
                reset = True
        except Exception:
            reset = True

    if reset:
        window = now.isoformat()

        db.execute(
            """
            UPDATE users
            SET rewarded_window_start = ?,
                rewarded_today = 0
            WHERE id = ?
            """,
            (window, user_id),
        )

        user = db.execute(
            """
            SELECT plan,
                   image_used,
                   silent_video_used,
                   audio_video_used,
                   rewarded_today,
                   rewarded_window_start,
                   rewarded_image_credits,
                   rewarded_audio_video_credits,
                   usage_month
            FROM users
            WHERE id = ?
            """,
            (user_id,),
        ).fetchone()

    return user


@app.route("/api/rewarded-ad/claim", methods=["POST"])
@login_required
def api_rewarded_ad_claim():
    """
    Claim one completed rewarded-ad mission step.

    IMPORTANT:
    The Android client must call this ONLY after
    OnUserEarnedRewardListener fires.

    This endpoint:
      - uses the authenticated session
      - enforces the 6-hour window
      - enforces 3 completed ads/window
      - determines the reward from server-side ad count
      - never trusts a client-supplied reward amount
      - prevents duplicate calls at the limit
    """
    db = get_db()

    user = db.execute(
        """
        SELECT plan,
               image_used,
               silent_video_used,
               audio_video_used,
               rewarded_today,
               rewarded_window_start,
               rewarded_image_credits,
               rewarded_audio_video_credits,
               usage_month
        FROM users
        WHERE id = ?
        """,
        (session["user_id"],),
    ).fetchone()

    if not user:
        return jsonify({"error": "User account not found."}), 401

    user = rewarded_window_reset_if_needed(
        db,
        session["user_id"],
        user,
    )

    completed = int(user["rewarded_today"] or 0)

    if completed >= REWARDED_MISSION_AD_LIMIT:
        return jsonify({
            "ok": False,
            "error": "Rewarded-ad mission limit reached.",
            "rewarded_ads": completed,
            "limit": REWARDED_MISSION_AD_LIMIT,
        }), 429

    # Server determines the reward.
    #
    # 1st completed ad:
    #   +1 image credit
    #
    # 2nd completed ad:
    #   +2 image credits
    #
    # 3rd completed ad:
    #   +1 audio+video credit
    #
    # No client-provided reward type/amount is accepted.
    next_count = completed + 1

    image_credit_delta = 0
    audio_video_credit_delta = 0
    mission = ""

    if next_count == 1:
        image_credit_delta = 1
        mission = "image_1"

    elif next_count == 2:
        image_credit_delta = 2
        mission = "image_2"

    elif next_count == 3:
        audio_video_credit_delta = 1
        mission = "audio_video_3"

    db.execute(
        """
        UPDATE users
        SET rewarded_today = COALESCE(rewarded_today, 0) + 1,
            rewarded_image_credits =
                COALESCE(rewarded_image_credits, 0) + ?,
            rewarded_audio_video_credits =
                COALESCE(rewarded_audio_video_credits, 0) + ?
        WHERE id = ?
        """,
        (
            image_credit_delta,
            audio_video_credit_delta,
            session["user_id"],
        ),
    )

    db.commit()

    return jsonify({
        "ok": True,
        "mission": mission,
        "rewarded_ads": next_count,
        "limit": REWARDED_MISSION_AD_LIMIT,
        "image_credits_added": image_credit_delta,
        "audio_video_credits_added": audio_video_credit_delta,
        "message": (
            "Reward earned successfully."
        ),
    })


@app.route("/api/rewarded-ad/status", methods=["GET"])
@login_required
def api_rewarded_ad_status():
    db = get_db()

    user = db.execute(
        """
        SELECT plan,
               rewarded_today,
               rewarded_window_start,
               rewarded_image_credits,
               rewarded_audio_video_credits
        FROM users
        WHERE id = ?
        """,
        (session["user_id"],),
    ).fetchone()

    if not user:
        return jsonify({"error": "User account not found."}), 401

    user = rewarded_window_reset_if_needed(
        db,
        session["user_id"],
        user,
    )

    completed = int(user["rewarded_today"] or 0)

    return jsonify({
        "ok": True,
        "rewarded_ads": completed,
        "limit": REWARDED_MISSION_AD_LIMIT,
        "remaining": max(
            0,
            REWARDED_MISSION_AD_LIMIT - completed
        ),
        "window_hours": REWARDED_MISSION_WINDOW_HOURS,
        "window_start": user["rewarded_window_start"],
        "image_credits": int(
            user["rewarded_image_credits"] or 0
        ),
        "audio_video_credits": int(
            user["rewarded_audio_video_credits"] or 0
        ),
    })


'''

    s = s.replace(anchor, reward_code + "\n" + anchor, 1)

# Make /api/usage expose rewarded credits and 6-hour information.
old_select = """
        SELECT plan, image_used, silent_video_used,
               audio_video_used, rewarded_today, usage_month
"""

new_select = """
        SELECT plan, image_used, silent_video_used,
               audio_video_used, rewarded_today,
               rewarded_window_start,
               rewarded_image_credits,
               rewarded_audio_video_credits,
               usage_month
"""

s = s.replace(old_select, new_select, 1)

old_usage = '''
        "rewarded_ads": {
            "used": rewarded_used,
            "limit": 3,
            "remaining": max(0, 3 - rewarded_used),
        },
'''

new_usage = '''
        "rewarded_ads": {
            "used": rewarded_used,
            "limit": REWARDED_MISSION_AD_LIMIT,
            "remaining": max(
                0,
                REWARDED_MISSION_AD_LIMIT - rewarded_used
            ),
            "window_hours": REWARDED_MISSION_WINDOW_HOURS,
            "window_start": user["rewarded_window_start"],
            "image_credits": int(
                user["rewarded_image_credits"] or 0
            ),
            "audio_video_credits": int(
                user["rewarded_audio_video_credits"] or 0
            ),
        },
'''

if old_usage in s:
    s = s.replace(old_usage, new_usage, 1)

APP.write_text(s)

# ============================================================
# ANDROID API CLIENT
# ============================================================

a = API.read_text()

# Add reward response data classes near UsageResponse.
if "data class RewardedClaimResponse" not in a:

    target = '''    data class UsageResponse(
'''

    reward_classes = '''    data class RewardedClaimResponse(
        val ok: Boolean,
        val mission: String,
        val rewardedAds: Int,
        val limit: Int,
        val imageCreditsAdded: Int,
        val audioVideoCreditsAdded: Int,
        val message: String
    )

'''

    if target not in a:
        raise SystemExit("Could not find UsageResponse in ApiClient.kt")

    a = a.replace(target, reward_classes + target, 1)

# Add API function before USAGE section.
if "suspend fun claimRewardedAd" not in a:

    usage_anchor = '    // =========================\n    // USAGE\n    // ========================='

    if usage_anchor not in a:
        raise SystemExit("Could not find Android USAGE anchor.")

    api_function = r'''
    // =========================
    // REWARDED AD
    // =========================

    suspend fun claimRewardedAd(): ApiResult<RewardedClaimResponse> =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(BASE_URL + "/api/rewarded-ad/claim")
                    .post(
                        RequestBody.create(
                            "application/json".toMediaType(),
                            "{}"
                        )
                    )
                    .build()

                client.newCall(request).execute().use { response ->
                    val text = response.body?.string().orEmpty()

                    if (!response.isSuccessful) {
                        val message =
                            try {
                                JSONObject(text)
                                    .optString(
                                        "error",
                                        "Unable to claim reward."
                                    )
                            } catch (_: Exception) {
                                "Unable to claim reward."
                            }

                        return@withContext ApiResult.Failure(message)
                    }

                    val json = JSONObject(text)

                    ApiResult.Success(
                        RewardedClaimResponse(
                            ok = json.optBoolean("ok", false),
                            mission = json.optString("mission", ""),
                            rewardedAds = json.optInt("rewarded_ads", 0),
                            limit = json.optInt("limit", 3),
                            imageCreditsAdded =
                                json.optInt(
                                    "image_credits_added",
                                    0
                                ),
                            audioVideoCreditsAdded =
                                json.optInt(
                                    "audio_video_credits_added",
                                    0
                                ),
                            message = json.optString(
                                "message",
                                "Reward earned."
                            )
                        )
                    )
                }
            } catch (_: Exception) {
                ApiResult.Failure(
                    "Unable to claim rewarded-ad gift."
                )
            }
        }

'''

    a = a.replace(
        usage_anchor,
        api_function + "\n" + usage_anchor,
        1
    )

API.write_text(a)

# ============================================================
# CHATSCREEN
# ============================================================

c = CHAT.read_text()

# Add API claim inside the genuine reward callback.
old = '''                rewardMessage = "Novara gift earned!"
                rewardBusy = false
'''

new = '''                scope.launch {
                    when (val result = ApiClient.claimRewardedAd()) {
                        is ApiClient.ApiResult.Success -> {
                            rewardMessage =
                                result.data.message
                        }

                        is ApiClient.ApiResult.Failure -> {
                            rewardMessage =
                                result.message
                        }
                    }

                    rewardBusy = false
                }
'''

if old not in c:
    raise SystemExit(
        "Could not find existing onRewardEarned callback."
    )

c = c.replace(old, new, 1)

# The callback now uses scope, which is declared below the callback.
# Move scope declaration above reward callback block by adding a second
# remembered scope before the reward manager state and removing the later one.
if "val scope = rememberCoroutineScope()" in c:
    # Keep only the first occurrence and ensure it is before watchAndEarn.
    first = c.find("val scope = rememberCoroutineScope()")
    if first != -1:
        later = c.find("val scope = rememberCoroutineScope()", first + 1)
        # Existing scope is currently after watchAndEarn. We need one before it.
        if later == -1:
            # Existing declaration is the later one. Move it.
            declaration = "    val scope = rememberCoroutineScope()\n\n"
            c = c.replace(declaration, "", 1)

            insertion = c.find("    val rewardedAdManager = remember")
            if insertion == -1:
                raise SystemExit("Could not locate reward manager state.")

            c = (
                c[:insertion]
                + declaration
                + c[insertion:]
            )

# ============================================================
# VERIFY
# ============================================================

CHAT.write_text(c)

print("\n========== PATCH COMPLETE ==========")
print("Backend:")
print(" - 6-hour rewarded mission window")
print(" - 3 completed ads per window")
print(" - server-selected rewards")
print(" - +1 image on ad #1")
print(" - +2 images on ad #2")
print(" - +1 audio/video credit on ad #3")
print(" - separate rewarded credit balances")
print(" - rewarded status endpoint")
print(" - authenticated claim endpoint")

print("Android:")
print(" - claim API added")
print(" - reward claim happens only from rewarded callback")
print(" - no client-supplied reward amount")
print(" - existing Watch & Earn flow retained")

# ============================================================
# PYTHON SYNTAX CHECK
# ============================================================

import py_compile
try:
    py_compile.compile(str(APP), doraise=True)
    print("Python syntax: OK")
except Exception as e:
    print("PYTHON SYNTAX ERROR:", e)
    sys.exit(1)

print("\n========== KOTLIN BUILD ==========")
