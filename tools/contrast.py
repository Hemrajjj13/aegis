#!/usr/bin/env python3
"""
tools/contrast.py - WCAG 2.1 Contrast Ratio Calculator and Token Validator for Project Aegis.
Implements the W3C WCAG 2.1 relative luminance and contrast ratio algorithms.
"""

import sys

def hex_to_rgb(hex_str):
    hex_str = hex_str.strip().lstrip('#')
    if len(hex_str) == 3:
        hex_str = ''.join([c * 2 for c in hex_str])
    if len(hex_str) != 6:
        raise ValueError(f"Invalid hex color: {hex_str}")
    return tuple(int(hex_str[i:i+2], 16) for i in (0, 2, 4))

def srgb_channel_to_linear(c):
    c_srgb = c / 255.0
    if c_srgb <= 0.04045:
        return c_srgb / 12.92
    else:
        return ((c_srgb + 0.055) / 1.055) ** 2.4

def relative_luminance(rgb):
    r, g, b = rgb
    r_lin = srgb_channel_to_linear(r)
    g_lin = srgb_channel_to_linear(g)
    b_lin = srgb_channel_to_linear(b)
    return 0.2126 * r_lin + 0.7152 * g_lin + 0.0722 * b_lin

def contrast_ratio(hex1, hex2):
    rgb1 = hex_to_rgb(hex1)
    rgb2 = hex_to_rgb(hex2)
    l1 = relative_luminance(rgb1)
    l2 = relative_luminance(rgb2)
    lighter = max(l1, l2)
    darker = min(l1, l2)
    return (lighter + 0.05) / (darker + 0.05)

def evaluate_wcag(cr):
    return {
        "ratio": cr,
        "aa_normal": cr >= 4.5,
        "aa_large": cr >= 3.0,
        "aaa_normal": cr >= 7.0,
        "aaa_large": cr >= 4.5,
    }

def print_check(name, fg_hex, bg_hex):
    cr = contrast_ratio(fg_hex, bg_hex)
    eval_res = evaluate_wcag(cr)
    status_aa = "PASS" if eval_res["aa_normal"] else "FAIL"
    status_aaa = "PASS" if eval_res["aaa_normal"] else "FAIL"
    print(f"{name:40} | FG: {fg_hex:7} | BG: {bg_hex:7} | CR: {cr:5.2f}:1 | AA: {status_aa:4} | AAA: {status_aaa:4}")
    return cr

if __name__ == "__main__":
    if len(sys.argv) == 3:
        cr = contrast_ratio(sys.argv[1], sys.argv[2])
        res = evaluate_wcag(cr)
        print(f"Contrast ratio between {sys.argv[1]} and {sys.argv[2]}: {cr:.2f}:1")
        print(f"WCAG AA Normal Text (>= 4.5:1): {'PASS' if res['aa_normal'] else 'FAIL'}")
        print(f"WCAG AA Large Text  (>= 3.0:1): {'PASS' if res['aa_large'] else 'FAIL'}")
        print(f"WCAG AAA Normal Text(>= 7.0:1): {'PASS' if res['aaa_normal'] else 'FAIL'}")
    else:
        print("=== Project Aegis WCAG 2.1 Contrast Ratio Verification ===")
        dark_canvas = "#0B0E14"
        dark_surface = "#121722"
        light_canvas = "#F7F8FA"
        light_surface = "#FFFFFF"

        print("\n--- DARK THEME (Surface: #121722 / Canvas: #0B0E14) ---")
        print_check("Primary text (#E6EAF2 on surface)", "#E6EAF2", dark_surface)
        print_check("Muted text (#93A0B8 on surface)", "#93A0B8", dark_surface)
        print_check("Approve text (#2FBF71 on surface)", "#2FBF71", dark_surface)
        print_check("Review text (#F5A524 on surface)", "#F5A524", dark_surface)
        print_check("Decline text (#F0556A on surface)", "#F0556A", dark_surface)
        print_check("Decline pill text (#FF6B81 on surface)", "#FF6B81", dark_surface)
        print_check("Info text (#4DA3FF on surface)", "#4DA3FF", dark_surface)
        print_check("Dark text on accent button (#0B0E14 on #6C7CFF)", "#0B0E14", "#6C7CFF")
        print_check("White on adjusted accent (#FFFFFF on #5061FF)", "#FFFFFF", "#5061FF")

        print("\n--- LIGHT THEME (Surface: #FFFFFF / Canvas: #F7F8FA) ---")
        print_check("Primary text (#0F1626 on surface)", "#0F1626", light_surface)
        print_check("Muted text (#5B677F on surface)", "#5B677F", light_surface)
        print_check("Adjusted Approve text (#137841 on surface)", "#137841", light_surface)
        print_check("Adjusted Review text (#945900 on surface)", "#945900", light_surface)
        print_check("Decline text (#CC2F46 on surface)", "#CC2F46", light_surface)
        print_check("Info text (#1F6FD1 on surface)", "#1F6FD1", light_surface)
        print_check("Light Accent text (#4B5BE0 on surface)", "#4B5BE0", light_surface)
        print_check("White on light accent button (#FFFFFF on #4B5BE0)", "#FFFFFF", "#4B5BE0")
