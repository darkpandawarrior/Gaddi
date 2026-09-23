#!/usr/bin/env bash
# Generate platform icon assets from the SVG source.
# Requires: rsvg-convert or Inkscape (Linux/macOS), ImageMagick (ico), iconutil (macOS)
# Run once before packaging: ./scripts/generate_icons.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"

SVG_SRC="$REPO_ROOT/cmp-web/src/wasmJsMain/resources/favicon.svg"
ICONS_DIR="$REPO_ROOT/cmp-desktop/src/jvmMain/resources/icons"
WEB_DIR="$REPO_ROOT/cmp-web/src/wasmJsMain/resources"

mkdir -p "$ICONS_DIR"

echo "→ Generating icons from $SVG_SRC"

# Prefer rsvg-convert, fall back to Inkscape
if command -v rsvg-convert &>/dev/null; then
  RENDER() { rsvg-convert -w "$1" -h "$1" "$SVG_SRC" -o "$2"; }
elif command -v inkscape &>/dev/null; then
  RENDER() { inkscape -w "$1" -h "$1" "$SVG_SRC" -o "$2"; }
else
  echo "✗ Need rsvg-convert (librsvg) or inkscape to rasterise. Install with:"
  echo "  macOS: brew install librsvg"
  echo "  Linux: sudo apt install librsvg2-bin"
  exit 1
fi

# ── PNG rasters ───────────────────────────────────────────────────────────────
echo "  512×512  gaddi_512.png  (Linux desktop / PWA)"
RENDER 512 "$ICONS_DIR/gaddi_512.png"

echo "  512×512  icon-512.png   (web PWA)"
cp "$ICONS_DIR/gaddi_512.png" "$WEB_DIR/icon-512.png"

echo "  192×192  icon-192.png   (web PWA)"
RENDER 192 "$WEB_DIR/icon-192.png"

echo "  180×180  apple-touch-icon.png"
RENDER 180 "$WEB_DIR/apple-touch-icon.png"

# ── macOS .icns ───────────────────────────────────────────────────────────────
if [[ "$(uname)" == "Darwin" ]]; then
  echo "  macOS .icns"
  ICONSET=$(mktemp -d).iconset
  mkdir -p "$ICONSET"
  for SIZE in 16 32 64 128 256 512; do
    RENDER $SIZE         "$ICONSET/icon_${SIZE}x${SIZE}.png"
    RENDER $((SIZE * 2)) "$ICONSET/icon_${SIZE}x${SIZE}@2x.png"
  done
  iconutil -c icns "$ICONSET" -o "$ICONS_DIR/gaddi.icns"
  rm -rf "$ICONSET"
  echo "  ✓ gaddi.icns"
else
  echo "  ⚠ Skipping .icns — only generated on macOS (iconutil required)"
fi

# ── Windows .ico ──────────────────────────────────────────────────────────────
if command -v convert &>/dev/null; then
  echo "  Windows .ico"
  TMPDIR_ICO=$(mktemp -d)
  for SIZE in 16 32 48 64 128 256; do
    RENDER $SIZE "$TMPDIR_ICO/icon_$SIZE.png"
  done
  convert "$TMPDIR_ICO"/icon_16.png \
          "$TMPDIR_ICO"/icon_32.png \
          "$TMPDIR_ICO"/icon_48.png \
          "$TMPDIR_ICO"/icon_64.png \
          "$TMPDIR_ICO"/icon_128.png \
          "$TMPDIR_ICO"/icon_256.png \
          "$ICONS_DIR/gaddi.ico"
  rm -rf "$TMPDIR_ICO"
  echo "  ✓ gaddi.ico"
else
  echo "  ⚠ Skipping .ico — ImageMagick (convert) not found"
  echo "    Install with: brew install imagemagick"
fi

echo ""
echo "✓ Done. Files written to:"
echo "  $ICONS_DIR"
echo "  $WEB_DIR"
