# Vectorizer

Vectorizer is a JavaFX 26 desktop application for batch raster-image import,
Potrace-based SVG conversion, preview, and export. It accepts JPG/PNG files or
one ZIP archive containing supported images. Multiple medium-detail custom-color
conversions are processed in parallel.

In Custom color mode, the application reduces the image to the selected number
of colors (up to five). Each color is traced as a separate Potrace layer and
the layers are combined into one SVG. Potrace itself traces one monochrome
layer at a time; the application performs the color separation and composition.
Two to four colors are recommended for fast previews. Five colors preserve more
detail but require more Potrace layer conversions. Unlimited mode keeps the
original palette for small palettes and uses a bounded color palette for large
photographs to keep processing responsive.

## Requirements

- JDK 24
- Maven 3.9+
- Potrace on `PATH`

## Install Potrace

Install Potrace using the package manager for your operating system, then
confirm that the command is available:

```text
potrace --version
```

On Windows, download the Potrace binary from the official Potrace project and
add its directory to `PATH`. On Debian/Ubuntu, use `sudo apt install potrace`.
On macOS with Homebrew, use `brew install potrace`.

If Potrace is unavailable, the application shows:
`Potrace CLI not found. Please install it and add it to PATH.`

## Run

```text
mvn clean javafx:run
```

## Test

```text
mvn test
```

The tests include a Java-based fake Potrace process, so the unit test suite does
not require Potrace to be installed.

## Package

```text
mvn clean package
```

The shaded JAR is generated in `target/`. JavaFX runtime modules may still
need to be supplied by the target platform when running a packaged desktop
application.

The executable artifact is:

```text
target/vectorizer-1.0-SNAPSHOT.jar
```
