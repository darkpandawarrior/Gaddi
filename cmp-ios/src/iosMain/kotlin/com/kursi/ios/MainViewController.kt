package com.kursi.ios

import androidx.compose.ui.window.ComposeUIViewController
import com.kursi.shared.GaddiApp

/**
 * Entry point exposed to Swift/Objective-C.
 *
 * The Xcode app calls `MainViewControllerKt.MainViewController()` to obtain a
 * [UIViewController] that hosts the full Compose UI tree ([GaddiApp]).
 *
 * Usage in Swift:
 * ```swift
 * import KursiKit
 *
 * struct ContentView: UIViewControllerRepresentable {
 *     func makeUIViewController(context: Context) -> UIViewController {
 *         MainViewControllerKt.MainViewController()
 *     }
 *     func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
 * }
 * ```
 *
 * ktlint:standard:function-naming — the Swift side calls this by name
 * (MainViewControllerKt.MainViewController(), see the KDoc above), and
 * ComposeUIViewController factories are PascalCase across Compose Multiplatform.
 * Renaming it would break the Xcode app, not just a style rule.
 */
@Suppress("ktlint:standard:function-naming")
fun MainViewController() = ComposeUIViewController { GaddiApp() }
