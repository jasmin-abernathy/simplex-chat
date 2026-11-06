#!/usr/bin/env python3
"""Regression check: Verger messaging's static palette remains restrained and legible."""
import re
import sys
from pathlib import Path

root = Path(__file__).resolve().parents[3]
palette_file = root / "apps/multiplatform/common/src/commonMain/kotlin/chat/simplex/common/ui/theme/Theme.kt"
app_file = root / "apps/multiplatform/common/src/commonMain/kotlin/chat/simplex/common/App.kt"
code = palette_file.read_text(encoding="utf-8")


def luminance(hex_value):
    rgb = [int(hex_value[i:i + 2], 16) / 255 for i in (0, 2, 4)]
    linear = [x / 12.92 if x <= 0.04045 else ((x + .055) / 1.055) ** 2.4 for x in rgb]
    return sum(x * w for x, w in zip(linear, (.2126, .7152, .0722)))


def contrast(a, b):
    x, y = sorted((luminance(a), luminance(b)))
    return (y + .05) / (x + .05)


def value(body, name):
    match = re.search(r"\b" + re.escape(name) + r"\s*=\s*(Color\.White|Color\(0xFF([A-Fa-f0-9]{6})\))", body)
    if not match:
        raise AssertionError(f"Color {name} not explicitly defined")
    return "FFFFFF" if match.group(1) == "Color.White" else match.group(2)


for variant, method in (("Light", "lightColors"), ("Dark", "darkColors")):
    start = f"val Verger{variant}ColorPalette = {method}("
    section = code.split(start, 1)[1].split("\n)", 1)[0]
    for foreground, background in (
        ("onPrimary", "primary"), ("onSecondary", "secondary"),
        ("onBackground", "background"), ("onSurface", "surface"),
    ):
        ratio = contrast(value(section, foreground), value(section, background))
        if ratio < 4.5:
            raise AssertionError(f"{variant}: {foreground}/{background} contrast {ratio:.2f} < 4.5")
        print(f"{variant} {foreground}/{background}: {ratio:.2f}:1")
    if f"val Verger{variant}AppColors = AppColors(" not in code:
        raise AssertionError(f"Missing Verger {variant} message bubble palette")

app = app_file.read_text(encoding="utf-8")
if "SimpleXTheme(vergerTheme = vergerGroupOnly)" not in app:
    raise AssertionError("Verger palette not scoped to the association messaging")
if "LocalVergerVisuals provides vergerTheme" not in code:
    raise AssertionError("Verger palette may be overridden by nested chat themes")
print("Verger common restrained palettes: OK")
