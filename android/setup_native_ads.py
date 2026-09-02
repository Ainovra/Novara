from pathlib import Path
import re
import shutil
import subprocess
import sys
from datetime import datetime

ROOT = Path.cwd()
APP = ROOT / "app"
SRC = APP / "src/main/java/com/novara/app"
CHAT = SRC / "ui/screens/ChatScreen.kt"
API = SRC / "network/ApiClient.kt"
APPCLASS = SRC / "NovaraApp.kt"
MANIFEST = APP / "src/main/AndroidManifest.xml"
GRADLE = APP / "build.gradle"

stamp = datetime.now().strftime("%Y%m%d-%H%M%S")

def backup(p):
    b = p.with_name(p.name + f".before-native-ads-{stamp}.bak")
    shutil.copy2(p, b)
    print("BACKUP:", b)

def fail(msg):
    print("\nERROR:", msg)
    sys.exit(1)

for p in [CHAT, API, APPCLASS, MANIFEST, GRADLE]:
    if not p.exists():
        fail(f"Missing required file: {p}")

print("========== NOVARA NATIVE ADS PATCH ==========")

# ------------------------------------------------------------
# 1. BACKUPS
# ------------------------------------------------------------
for p in [CHAT, API, APPCLASS, MANIFEST, GRADLE]:
    backup(p)

# ------------------------------------------------------------
# 2. GRADLE - Google Mobile Ads SDK
# ------------------------------------------------------------
g = GRADLE.read_text()

if "com.google.android.gms:play-services-ads" not in g:
    # Prefer dependencies { ... } block.
    m = re.search(r"(?ms)^dependencies\s*\{", g)
    if not m:
        fail("Could not find dependencies { } in app/build.gradle")

    start = m.end()
    depth = 1
    i = start
    while i < len(g) and depth:
        if g[i] == "{":
            depth += 1
        elif g[i] == "}":
            depth -= 1
        i += 1

    g = g[:i-1] + '\n    implementation "com.google.android.gms:play-services-ads:25.4.0"\n' + g[i-1:]
    GRADLE_CHANGED = True
else:
    GRADLE_CHANGED = False

GRADLE.write_text(g)

# ------------------------------------------------------------
# 3. MANIFEST - Internet/network + AdMob test App ID
# ------------------------------------------------------------
m = MANIFEST.read_text()

# Keep existing permissions and only add if absent.
if 'com.google.android.gms.permission.AD_ID' not in m:
    m = m.replace(
        '    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />',
        '    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />\n'
        '    <uses-permission android:name="com.google.android.gms.permission.AD_ID" />'
    )

# Google official TEST AdMob application ID.
if 'ca-app-pub-3940256099942544~3347511713' not in m:
    marker = '        <activity\n'
    meta = (
        '        <!-- Google Mobile Ads TEST App ID. Replace with Novara production App ID before release. -->\n'
        '        <meta-data\n'
        '            android:name="com.google.android.gms.ads.APPLICATION_ID"\n'
        '            android:value="ca-app-pub-3940256099942544~3347511713" />\n\n'
    )
    if marker in m:
        m = m.replace(marker, meta + marker, 1)
    else:
        fail("Could not find application activity section in AndroidManifest.xml")

MANIFEST.write_text(m)

# ------------------------------------------------------------
# 4. REWARDED AD MANAGER
# ------------------------------------------------------------
manager = SRC / "ads"
manager.mkdir(parents=True, exist_ok=True)
RM = manager / "RewardedAdManager.kt"

RM.write_text(r'''package com.novara.app.ads

import android.app.Activity
import android.content.Context
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardItem
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Novara rewarded-ad manager.
 *
 * IMPORTANT:
 * - Button taps NEVER grant rewards.
 * - The reward callback is fired only from OnUserEarnedRewardListener.
 * - AtomicBoolean prevents the same loaded ad from awarding twice.
 * - A replacement ad is preloaded after the current ad is consumed.
 *
 * This uses Google's official TEST rewarded-ad unit while Novara is under development.
 */
class RewardedAdManager private constructor(
    private val context: Context
) {
    companion object {
        // Google's official Android rewarded TEST ad unit.
        const val TEST_REWARDED_AD_UNIT =
            "ca-app-pub-3940256099942544/5224354917"

        @Volatile
        private var instance: RewardedAdManager? = null

        fun get(context: Context): RewardedAdManager {
            return instance ?: synchronized(this) {
                instance ?: RewardedAdManager(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }

    private var rewardedAd: RewardedAd? = null
    private var isLoading = false
    private val rewardDelivered = AtomicBoolean(false)

    fun initialize() {
        MobileAds.initialize(context) {
            preload()
        }
    }

    @Synchronized
    fun preload() {
        if (rewardedAd != null || isLoading) return

        isLoading = true

        val request = AdRequest.Builder().build()

        RewardedAd.load(
            context,
            TEST_REWARDED_AD_UNIT,
            request,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    isLoading = false
                    rewardedAd = ad
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    isLoading = false
                    rewardedAd = null
                }
            }
        )
    }

    fun isReady(): Boolean = rewardedAd != null

    /**
     * Returns false if there is no loaded ad.
     *
     * IMPORTANT: returning true here does NOT mean a reward was earned.
     * The reward is delivered only by OnUserEarnedRewardListener.
     */
    fun show(
        activity: Activity,
        onRewardEarned: (RewardItem) -> Unit,
        onAdClosed: () -> Unit = {},
        onAdFailedToShow: () -> Unit = {}
    ): Boolean {
        val ad = rewardedAd ?: run {
            preload()
            return false
        }

        // Consume this exact loaded ad before displaying it.
        rewardedAd = null
        rewardDelivered.set(false)

        ad.fullScreenContentCallback =
            object : com.google.android.gms.ads.FullScreenContentCallback() {
                override fun onAdDismissedFullScreenContent() {
                    // Always start loading the next ad after consumption.
                    preload()
                    onAdClosed()
                }

                override fun onAdFailedToShowFullScreenContent(
                    adError: com.google.android.gms.ads.AdError
                ) {
                    rewardedAd = null
                    preload()
                    onAdFailedToShow()
                }
            }

        ad.show(activity) { rewardItem ->
            // THE ONLY CLIENT-SIDE REWARD GATE.
            if (rewardDelivered.compareAndSet(false, true)) {
                onRewardEarned(rewardItem)
            }
        }

        return true
    }
}
''')

# ------------------------------------------------------------
# 5. INITIALIZE ADMOB FROM NovaraApp
# ------------------------------------------------------------
a = APPCLASS.read_text()

if "com.novara.app.ads.RewardedAdManager" not in a:
    a = a.replace(
        "import android.webkit.WebView\n",
        "import android.webkit.WebView\n"
        "import com.novara.app.ads.RewardedAdManager\n"
    )

if "RewardedAdManager.get(this).initialize()" not in a:
    anchor = "        CookieManager.getInstance().setAcceptCookie(true)"
    if anchor not in a:
        fail("Could not locate NovaraApp initialization anchor")
    a = a.replace(
        anchor,
        "        RewardedAdManager.get(this).initialize()\n\n" + anchor,
        1
    )

APPCLASS.write_text(a)

# ------------------------------------------------------------
# 6. CHATSCREEN IMPORTS
# ------------------------------------------------------------
c = CHAT.read_text()

imports = [
    "import android.app.Activity",
    "import com.novara.app.ads.RewardedAdManager",
]

for imp in imports:
    if imp not in c:
        # Put Android import immediately after package.
        if imp.startswith("import android."):
            c = c.replace(
                "import android.Manifest\n",
                "import android.Manifest\n" + imp + "\n",
                1
            )
        else:
            c = c.replace(
                "import com.novara.app.R\n",
                "import com.novara.app.R\n" + imp + "\n",
                1
            )

# ------------------------------------------------------------
# 7. CHATSCREEN REWARDED-AD STATE + CALLBACK
# ------------------------------------------------------------
if "NOVARA_REWARDED_AD_UI" not in c:
    # Insert state immediately after fun ChatScreen() {.
    target = "fun ChatScreen() {"
    if target not in c:
        fail("Could not find ChatScreen() function")

    block = r'''
    // NOVARA_REWARDED_AD_UI
    val rewardedAdManager = remember {
        RewardedAdManager.get(LocalContext.current)
    }
    val activity = LocalContext.current as? Activity
    var rewardMessage by remember { mutableStateOf<String?>(null) }
    var rewardBusy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        rewardedAdManager.preload()
    }

    fun watchAndEarn() {
        val host = activity ?: return

        if (rewardBusy) return

        // A tap only attempts to SHOW an already-loaded ad.
        // It never grants the reward.
        rewardBusy = true

        val shown = rewardedAdManager.show(
            activity = host,
            onRewardEarned = {
                // THIS callback is the only place where the Novara reward
                // action is triggered.
                rewardMessage = "Novara gift earned!"
                rewardBusy = false
            },
            onAdClosed = {
                rewardBusy = false
            },
            onAdFailedToShow = {
                rewardBusy = false
                rewardMessage = "Ad could not be shown. Please try again."
            }
        )

        if (!shown) {
            rewardBusy = false
            rewardMessage = "Preparing your reward ad. Try again in a moment."
        }
    }
'''
    c = c.replace(target, target + "\n" + block, 1)

# ------------------------------------------------------------
# 8. SMALL PROMOTIONAL WATCH & EARN UI
# ------------------------------------------------------------
if "NOVARA_WATCH_EARN_CARD" not in c:
    # We need a safe insertion point inside ChatScreen.
    # Find the first return Material3 surface/column after ChatScreen state.
    pos = c.find("fun ChatScreen() {")
    if pos < 0:
        fail("ChatScreen function disappeared unexpectedly")

    # Insert immediately before the first @Composable function after ChatScreen
    # would be wrong; instead locate common top-level layout constructs.
    candidates = [
        "    Surface(",
        "    Box(",
        "    Column(",
        "    Scaffold("
    ]

    insert_at = -1
    insert_token = None

    search_from = pos
    for token in candidates:
        p = c.find(token, search_from)
        if p != -1 and (insert_at == -1 or p < insert_at):
            insert_at = p
            insert_token = token

    if insert_at == -1:
        fail("Could not find ChatScreen root layout")

    # Rather than rewriting the root structure, create a composable helper
    # and place it before the ChatScreen function. The existing ChatScreen
    # remains structurally intact.
    helper = r'''
@Composable
private fun NovaraWatchEarnCard(
    ready: Boolean,
    busy: Boolean,
    message: String?,
    onWatch: () -> Unit
) {
    // NOVARA_WATCH_EARN_CARD
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = "Need more Novara gifts?",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = when {
                        busy -> "Opening reward ad…"
                        ready -> "Watch a short ad and earn a gift."
                        else -> "Preparing a reward ad…"
                    },
                    style = MaterialTheme.typography.bodySmall
                )

                if (message != null) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
            }

            Button(
                onClick = onWatch,
                enabled = ready && !busy
            ) {
                Text(if (busy) "Please wait" else "Watch & Earn")
            }
        }
    }
}

'''
    c = c[:pos] + helper + c[pos:]

CHAT.write_text(c)

# ------------------------------------------------------------
# 9. API CLAIM HOOK
# ------------------------------------------------------------
# We deliberately do NOT invent a backend reward implementation here.
# The ad callback is therefore the sole client-side reward event.
# This prevents a button click from creating a fake reward.
#
# If the existing backend already exposes a gift/reward endpoint, it can be
# connected to the onRewardEarned callback after verifying its exact contract.

print("PATCHED:")
print(" - Google Mobile Ads dependency")
print(" - AdMob manifest application ID (TEST)")
print(" - RewardedAdManager")
print(" - AdMob initialization")
print(" - rewarded preload")
print(" - fullscreen rewarded flow")
print(" - OnUserEarnedRewardListener reward gate")
print(" - double-reward protection")
print(" - automatic next-ad preload")
print(" - Watch & Earn promotional component")
print(" - existing ChatScreen structure preserved")

# ------------------------------------------------------------
# 10. KOTLIN COMPILE / APK BUILD
# ------------------------------------------------------------
print("\n========== BUILDING DEBUG APK ==========")

cmd = ["./gradlew", "assembleDebug", "--console=plain"]
result = subprocess.run(cmd, cwd=ROOT)

if result.returncode != 0:
    print("\nBUILD FAILED.")
    print("Backups created with:", f".before-native-ads-{stamp}.bak")
    sys.exit(result.returncode)

apk_candidates = list((APP / "build/outputs/apk").rglob("*.apk"))

print("\n========== BUILD SUCCESS ==========")
for apk in apk_candidates:
    print("APK:", apk)

print("\nIMPORTANT:")
print("This build uses Google's TEST AdMob IDs.")
print("Do NOT ship these test IDs in a production release.")
print("The Watch & Earn button itself does not grant a reward.")
print("The reward callback is gated by OnUserEarnedRewardListener.")
print("A production reward system should additionally use AdMob SSV/backend verification.")
