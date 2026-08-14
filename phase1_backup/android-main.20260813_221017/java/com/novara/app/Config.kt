package com.novara.app

/**
 * The only website URL used by the Android shell.
 *
 * Render today -> your permanent custom domain later.
 * Change WEB_BASE_URL in app/build.gradle, then release a new AAB once if the
 * domain itself changes. Normal website/backend updates never require an AAB.
 */
object Config {
    val WEB_BASE_URL: String = BuildConfig.WEB_BASE_URL.trimEnd('/')
    val HOME_URL: String = WEB_BASE_URL

    const val AUTH_HANDOFF_SCHEME = "novara"
    const val AUTH_HANDOFF_HOST = "auth-complete"
    const val MOBILE_CONSUME_PATH = "/auth/mobile-consume"
    const val GOOGLE_LOGIN_PATH = "/auth/google"
}
