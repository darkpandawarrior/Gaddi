package com.kursi.ai.provider

import com.siddharth.kmp.llmchat.SecureKeyStore

actual fun createSecureKeyStore(): SecureKeyStore = SecureKeyStore()
