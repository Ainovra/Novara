package com.novara.app.ads

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
        // Novara production rewarded ad unit.
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
