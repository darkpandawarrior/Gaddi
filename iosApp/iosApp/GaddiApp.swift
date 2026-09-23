import SwiftUI
import StoreKit
import UserNotifications
import FirebaseCore
import FirebaseMessaging

@main
struct GaddiApp: App {
    @UIApplicationDelegateAdaptor(GaddiAppDelegate.self) var delegate

    var body: some Scene {
        WindowGroup {
            ContentView()
                .ignoresSafeArea(.all)
        }
    }
}

class GaddiAppDelegate: NSObject, UIApplicationDelegate {

    func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]?
    ) -> Bool {
        if Bundle.main.path(forResource: "GoogleService-Info", ofType: "plist") != nil {
            FirebaseApp.configure()
        }
        UNUserNotificationCenter.current().requestAuthorization(
            options: [.alert, .sound, .badge]
        ) { _, _ in }
        application.registerForRemoteNotifications()
        return true
    }

    func applicationDidBecomeActive(_ application: UIApplication) {
        UNUserNotificationCenter.current().setBadgeCount(0) { _ in }
        checkAndRequestReview()
        checkForAppStoreUpdate()
    }

    func application(
        _ application: UIApplication,
        didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data
    ) {
        Messaging.messaging().apnsToken = deviceToken
    }

    // ── In-App Review (SKStoreReviewController) ─────────────────────────────────
    // Reads ledger_wins and review_shown_version directly from UserDefaults — the
    // same keys multiplatform-settings writes for iOS (NSUserDefaults backend).
    private func checkAndRequestReview() {
        let wins = UserDefaults.standard.integer(forKey: "ledger_wins")
        guard wins >= 3 else { return }

        let current = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? ""
        let shown = UserDefaults.standard.string(forKey: "review_shown_version") ?? ""
        guard shown != current else { return }

        guard let scene = UIApplication.shared.connectedScenes
            .first(where: { $0.activationState == .foregroundActive }) as? UIWindowScene
        else { return }

        SKStoreReviewController.requestReview(in: scene)
        UserDefaults.standard.set(current, forKey: "review_shown_version")
    }

    // ── App Store update check ───────────────────────────────────────────────────
    // Fetches the current App Store version via the iTunes lookup API and prompts
    // the user to update when a newer version is available.
    private func checkForAppStoreUpdate() {
        guard let bundleId = Bundle.main.bundleIdentifier,
              let url = URL(string: "https://itunes.apple.com/lookup?bundleId=\(bundleId)")
        else { return }

        URLSession.shared.dataTask(with: url) { [weak self] data, _, _ in
            guard let data = data,
                  let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
                  let results = (json["results"] as? [[String: Any]])?.first,
                  let storeVersion = results["version"] as? String,
                  let currentVersion = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String,
                  storeVersion.compare(currentVersion, options: .numeric) == .orderedDescending
            else { return }

            DispatchQueue.main.async {
                self?.presentUpdateAlert(storeVersion: storeVersion, storeURL: results["trackViewUrl"] as? String)
            }
        }.resume()
    }

    private func presentUpdateAlert(storeVersion: String, storeURL: String?) {
        let alert = UIAlertController(
            title: "Update Available",
            message: "Version \(storeVersion) is available on the App Store.",
            preferredStyle: .alert
        )
        alert.addAction(UIAlertAction(title: "Update", style: .default) { _ in
            guard let raw = storeURL, let url = URL(string: raw) else { return }
            UIApplication.shared.open(url)
        })
        alert.addAction(UIAlertAction(title: "Later", style: .cancel))

        UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap { $0.windows }
            .first { $0.isKeyWindow }?
            .rootViewController?
            .present(alert, animated: true)
    }
}
