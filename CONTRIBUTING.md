# Contributing to zpl-renderer

Thank you for taking the time to contribute!

## Ways to Contribute

- **Bug reports** — open an issue with the ZPL that fails, plus a reference image from [labelary.com](https://labelary.com)
- **New ZPL command support** — see the guide below
- **Performance improvements** — benchmarks welcome
- **Documentation** — fix typos, improve examples

## Development Setup

```bash
git clone https://github.com/Milimarty/zpl-renderer.git
cd zpl-renderer
./gradlew build        # compile + run all tests
./gradlew test         # tests only
```

## Adding a New ZPL Command

1. Identify the right handler in `src/main/kotlin/.../handler/`:
   - Text / field commands → `TextHandler`
   - Graphic shapes → `RectangleHandler`
   - Barcodes → `BarcodeHandler`
   - Bitmaps → `BitmapHandler`
   - Label-level / control → `ControlHandler`
2. Add a `when` branch in the handler's `handle()` method.
3. Add a test in `ZplRendererTest` exercising the new command.
4. Update the **Supported ZPL Commands** table in `README.md`.

## Pull Request Guidelines

1. Fork the repo and create a branch from `main`.
2. All tests must pass: `./gradlew test`.
3. Keep commits focused; write clear messages in imperative mood (`add EAN-8 support`, not `added ean8`).
4. Open a PR with a description of what changed and why.

## Code Style

Follow the [Kotlin coding conventions](https://kotlinlang.org/docs/coding-conventions.html).
Keep the public API surface minimal and fully documented with KDoc.

## Code of Conduct

Please read [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md).
