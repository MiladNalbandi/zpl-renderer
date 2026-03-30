# zpl-renderer

[![CI](https://github.com/Milimarty/zpl-renderer/actions/workflows/ci.yml/badge.svg)](https://github.com/Milimarty/zpl-renderer/actions/workflows/ci.yml)
[![JitPack](https://jitpack.io/v/Milimarty/zpl-renderer.svg)](https://jitpack.io/#Milimarty/zpl-renderer)
[![License: Apache 2.0](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

A pure-JVM **ZPL II label renderer** — converts Zebra Programming Language (ZPL) source code into a `BufferedImage` with no Zebra hardware, no Labelary API, and no native dependencies.

---

## Features

| Capability | Details |
|---|---|
| **Text** | `^FO`/`^FT`, `^A` (font + orientation), `^CF` (default font), `^FB` word-wrap, `^FR`/`^FI` reverse video, `^FH` hex escapes |
| **Barcodes** | Code 128, QR Code, EAN-8, EAN-13, UPC-A, Data Matrix (via [ZXing](https://github.com/zxing/zxing)) |
| **Graphic shapes** | `^GB` box/rectangle, `^GE` ellipse, `^GD` diagonal line |
| **Bitmap graphics** | `^GFA`/`^GFB` — nibble-based run-length encoding, `:Z64:` (zlib+Base64), `:B64:` |
| **Label structure** | `^XA`/`^XZ`, `^LH`, `^FW`, `^BY`, `^FX` comments |
| **DPI-aware** | 200 dpi and 300 dpi output; configurable label dimensions |
| **Caching** | Rendered images cached by ZPL + parameters (configurable) |
| **Java interop** | `@JvmStatic` / `@JvmOverloads` on all public methods |

---

## Installation

### Gradle (via JitPack)

```kotlin
repositories {
    maven("https://jitpack.io")
}

dependencies {
    implementation("com.github.Milimarty:zpl-renderer:v1.0.0")
}
```

### Maven (via JitPack)

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependency>
    <groupId>com.github.Milimarty</groupId>
    <artifactId>zpl-renderer</artifactId>
    <version>v1.0.0</version>
</dependency>
```

---

## Quick Start

### Kotlin

```kotlin
import com.miladnalbandi.zpl.ZplRenderer
import javax.imageio.ImageIO
import java.io.File

val zpl = """
    ^XA
    ^CF0,60
    ^FO50,50^FDHello, ZPL!^FS
    ^FO50,150^BY3,2,100^BC^FD123456789^FS
    ^XZ
""".trimIndent()

val image = ZplRenderer.render(zpl, dpi = 300, widthInch = 4.0, heightInch = 6.0)
ImageIO.write(image, "png", File("label.png"))
```

### Java

```java
import com.miladnalbandi.zpl.ZplRenderer;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

String zpl = "^XA^CF0,60^FO50,50^FDHello World^FS^XZ";
BufferedImage image = ZplRenderer.render(zpl, 300, 4.0, 6.0, false, true);
ImageIO.write(image, "png", new File("label.png"));
```

---

## API Reference

### `ZplRenderer`

```kotlin
object ZplRenderer {
    fun render(
        zpl:        String,
        dpi:        Int     = 300,      // output resolution (clamped to 72–600)
        widthInch:  Double  = 4.0,      // label width in inches
        heightInch: Double  = 6.0,      // label height in inches
        antialias:  Boolean = false,    // smoother text, slightly slower
        useCache:   Boolean = true,     // cache by ZPL + all parameters
    ): BufferedImage

    fun clearCache()                    // flush in-memory image cache
    fun setDebugMode(enabled: Boolean)  // log unhandled commands + stats
}
```

---

## Supported ZPL Commands

| Command | Description |
|---|---|
| `^XA` / `^XZ` | Label start / end |
| `^LH` | Label home offset |
| `^CF` | Change default font |
| `^FW` | Default field orientation |
| `^FO` / `^FT` | Field origin / typeset |
| `^A` | Font (size + orientation) |
| `^FB` | Field block (word-wrap) |
| `^FD` / `^FV` | Field data / variable |
| `^FS` | Field separator |
| `^FR` / `^FI` | Field reverse (white-on-black) |
| `^FH` | Field hex escape decoding |
| `^BY` | Barcode module / height defaults |
| `^BC` / `^B3` | Code 128 |
| `^BQ` | QR Code |
| `^B8` | EAN-8 |
| `^BE` | EAN-13 |
| `^BU` | UPC-A |
| `^BX` | Data Matrix |
| `^GB` | Graphic box |
| `^GE` | Graphic ellipse |
| `^GD` | Graphic diagonal |
| `^GFA` / `^GFB` | Graphic field (compressed bitmap) |
| `^FX` | Comment (no-op) |

---

## Architecture

```
ZplRenderer  (public API)
    │
    └─ ZplEngine  (Chain of Responsibility dispatcher)
           │
           ├─ ControlHandler     ^XA ^XZ ^LH ^CF ^FW ^FX …
           ├─ TextHandler        ^FO ^FT ^A ^FB ^FD ^FS ^FR …
           ├─ RectangleHandler   ^GB ^GE ^GD
           ├─ BitmapHandler      ^GFA ^GFB
           └─ BarcodeHandler     ^BY ^BC ^BQ ^B8 ^BE ^BU ^BX

ZplRenderer also uses:
    LocalZplGraphicDecoder   decodes ^GFA compressed bitmaps
    graphics/BarcodeUtil     ZXing wrapper for barcode generation
```

State is isolated per render call via `RenderContext` — reset fresh on every `^XA`. `ZplEngine` is an instance (not a global singleton), making it straightforward to extend with custom handlers.

---

## Development

### Prerequisites
- JDK 17+
- Gradle 9.x (wrapper included)

### Build & test

```bash
./gradlew build          # compile + test
./gradlew test           # tests only
./gradlew jacocoTestReport  # coverage report → build/reports/jacoco/
```

---

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). All ZPL command additions should include a corresponding test in `ZplRendererTest`.

## Security

See [SECURITY.md](SECURITY.md).

## License

[Apache License 2.0](LICENSE) © Milad Nalbandi
