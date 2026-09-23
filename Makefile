# Gaddi — platform build targets
# Usage:  make <target>
#   make all        — build all platforms
#   make desktop    — macOS DMG (run on Windows for .exe, Linux for .deb)
#   make android    — debug APK ready for sideload / ADB install
#   make web        — Kotlin/WASM bundle, serve with any HTTP server
#   make ios        — iOS framework (open cmp-ios in Xcode to archive IPA)
#   make server     — runnable server distribution (start with outputs/server/bin/server)
#   make run-server — build + start the game server on port 8080
#   make clean      — delete outputs/

OUTPUTS := outputs

.PHONY: all desktop android web ios server run-server clean

all: desktop android web server
	@echo ""
	@echo "All builds complete. Artifacts in outputs/"

desktop:
	@echo "==> Building Desktop (macOS DMG)..."
	./gradlew :cmp-desktop:packageDistributionForCurrentOS
	mkdir -p $(OUTPUTS)/desktop
	cp -r cmp-desktop/build/compose/binaries/main-release/dmg/*.dmg $(OUTPUTS)/desktop/ 2>/dev/null || \
	cp -r cmp-desktop/build/compose/binaries/main/dmg/*.dmg $(OUTPUTS)/desktop/ 2>/dev/null || \
	cp -r cmp-desktop/build/compose/binaries/main-release/app/*.app $(OUTPUTS)/desktop/ 2>/dev/null || true
	@# Also copy the runnable JAR as a fallback launcher
	./gradlew :cmp-desktop:packageUberJarForCurrentOS 2>/dev/null || true
	cp cmp-desktop/build/compose/jars/*.jar $(OUTPUTS)/desktop/ 2>/dev/null || true
	@echo "Desktop build done → $(OUTPUTS)/desktop/"

android:
	@echo "==> Building Android APK (debug)..."
	./gradlew :cmp-android:assembleDebug
	mkdir -p $(OUTPUTS)/android
	cp cmp-android/build/outputs/apk/debug/cmp-android-debug.apk $(OUTPUTS)/android/gaddi-debug.apk
	@echo "Android APK done → $(OUTPUTS)/android/gaddi-debug.apk"
	@echo "Install: adb install -r $(OUTPUTS)/android/gaddi-debug.apk"

android-release:
	@echo "==> Building Android APK (release, unsigned)..."
	./gradlew :cmp-android:assembleRelease
	mkdir -p $(OUTPUTS)/android
	cp cmp-android/build/outputs/apk/release/cmp-android-release-unsigned.apk $(OUTPUTS)/android/gaddi-release-unsigned.apk
	@echo "Release APK (unsigned) → $(OUTPUTS)/android/gaddi-release-unsigned.apk"

web:
	@echo "==> Building Web (Kotlin/WASM)..."
	./gradlew :cmp-web:wasmJsBrowserDistribution
	mkdir -p $(OUTPUTS)/web
	rm -rf $(OUTPUTS)/web/*
	cp -r cmp-web/build/dist/wasmJs/productionExecutable/. $(OUTPUTS)/web/
	@echo "Web bundle done → $(OUTPUTS)/web/"
	@echo "Serve: cd $(OUTPUTS)/web && python3 -m http.server 8081"

ios:
	@echo "==> Building iOS Framework (simulator arm64)..."
	./gradlew :cmp-ios:linkReleaseFrameworkIosSimulatorArm64
	mkdir -p $(OUTPUTS)/ios
	cp -r cmp-ios/build/bin/iosSimulatorArm64/releaseFramework/KursiKit.framework $(OUTPUTS)/ios/ 2>/dev/null || \
	find cmp-ios/build -name "*.framework" -exec cp -r {} $(OUTPUTS)/ios/ \; 2>/dev/null || true
	@echo "iOS framework done → $(OUTPUTS)/ios/"
	@echo "Note: open the Xcode project in cmp-ios/ to build and archive an IPA."

server:
	@echo "==> Building Server (Ktor/Netty)..."
	./gradlew :server:installDist
	mkdir -p $(OUTPUTS)/server
	rm -rf $(OUTPUTS)/server/*
	cp -r server/build/install/server/. $(OUTPUTS)/server/
	@echo "Server build done → $(OUTPUTS)/server/"
	@echo "Start: $(OUTPUTS)/server/bin/server"
	@echo "Or:    PORT=8080 $(OUTPUTS)/server/bin/server"

run-server:
	@echo "==> Starting Gaddi server on port 8080..."
	./gradlew :server:run

clean:
	rm -rf $(OUTPUTS)/
	@echo "outputs/ deleted."
