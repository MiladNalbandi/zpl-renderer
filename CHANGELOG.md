# Changelog

All notable changes to this project will be documented in this file.
Format: [Keep a Changelog](https://keepachangelog.com/en/1.0.0/) — Versioning: [SemVer](https://semver.org/).

---

## [1.0.0] — 2024-03-30

### Added
- Initial open-source release, extracted from [zpl-label-viewer](https://github.com/Milimarty/zpl-label-viewer)
- `ZplRenderer` — public entry point (`render`, `clearCache`, `setDebugMode`)
- `ZplEngine` — instantiable Chain-of-Responsibility dispatcher (no longer a global singleton)
- `ControlHandler` — `^XA`, `^XZ`, `^LH`, `^CF`, `^FW`, `^FX`, printer no-ops
- `TextHandler` — `^FO`, `^FT`, `^A`, `^FB`, `^FD`, `^FV`, `^FS`, `^FR`, `^FI`, `^FH`, `^FN`, `^CI`
- `RectangleHandler` — `^GB`, `^GE`, `^GD`
- `BitmapHandler` — `^GFA`, `^GFB`
- `BarcodeHandler` — `^BC`/`^B3` (Code 128), `^BQ` (QR), `^B8` (EAN-8), `^BE` (EAN-13), `^BU` (UPC-A), `^BX` (Data Matrix)
- `LocalZplGraphicDecoder` — nibble-based RLE, `:Z64:` (zlib+Base64), `:B64:`
- `BarcodeUtil` — ZXing wrapper with EAN/UPC/DataMatrix fallbacks
- `RenderContext` — full per-render mutable state with KDoc
- JUnit 5 test suite (18 tests)
- Apache 2.0 license
- GitHub Actions CI
