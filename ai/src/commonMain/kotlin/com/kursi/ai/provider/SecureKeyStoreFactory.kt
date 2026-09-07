package com.kursi.ai.provider

import com.siddharth.kmp.llmchat.SecureKeyStore

/**
 * Per-platform [SecureKeyStore] construction. Android's actual needs a live
 * [android.content.Context] the same way [OnDeviceAiProvider]'s android actual does — see
 * [KursiAiContextProvider] — every other platform's [SecureKeyStore] takes none.
 */
expect fun createSecureKeyStore(): SecureKeyStore
