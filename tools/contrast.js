#!/usr/bin/env node
/**
 * tools/contrast.js - WCAG 2.1 Contrast Ratio Calculator and Token Validator for Project Aegis.
 * Implements the W3C WCAG 2.1 relative luminance and contrast ratio algorithms.
 *
 * Usage:
 *   node tools/contrast.js                  (runs full verification suite)
 *   node tools/contrast.js #FFFFFF #3849D6  (evaluates specific color pair)
 */

function hexToRgb(hexStr) {
  let cleaned = hexStr.trim().replace(/['"]/g, '').replace(/^#/, '');
  if (cleaned.length === 3) {
    cleaned = cleaned.split('').map(c => c + c).join('');
  }
  if (cleaned.length !== 6) {
    throw new Error(`Invalid hex color: ${hexStr}`);
  }
  const r = parseInt(cleaned.slice(0, 2), 16);
  const g = parseInt(cleaned.slice(2, 4), 16);
  const b = parseInt(cleaned.slice(4, 6), 16);
  return [r, g, b];
}

function srgbChannelToLinear(c) {
  const cSrgb = c / 255.0;
  if (cSrgb <= 0.04045) {
    return cSrgb / 12.92;
  } else {
    return Math.pow((cSrgb + 0.055) / 1.055, 2.4);
  }
}

function relativeLuminance([r, g, b]) {
  const rLin = srgbChannelToLinear(r);
  const gLin = srgbChannelToLinear(g);
  const bLin = srgbChannelToLinear(b);
  return 0.2126 * rLin + 0.7152 * gLin + 0.0722 * bLin;
}

function contrastRatio(hex1, hex2) {
  const rgb1 = hexToRgb(hex1);
  const rgb2 = hexToRgb(hex2);
  const l1 = relativeLuminance(rgb1);
  const l2 = relativeLuminance(rgb2);
  const lighter = Math.max(l1, l2);
  const darker = Math.min(l1, l2);
  return (lighter + 0.05) / (darker + 0.05);
}

function evaluateWcag(cr) {
  return {
    ratio: cr,
    aa_normal: cr >= 4.5,
    aa_large: cr >= 3.0,
    aaa_normal: cr >= 7.0,
    aaa_large: cr >= 4.5,
  };
}

function printCheck(name, fgHex, bgHex) {
  const cr = contrastRatio(fgHex, bgHex);
  const evalRes = evaluateWcag(cr);
  const statusAa = evalRes.aa_normal ? 'PASS' : 'FAIL';
  const statusAaa = evalRes.aaa_normal ? 'PASS' : 'FAIL';
  console.log(
    `${name.padEnd(40)} | FG: ${fgHex.padEnd(7)} | BG: ${bgHex.padEnd(7)} | CR: ${cr.toFixed(2).padStart(5)}:1 | AA: ${statusAa.padEnd(4)} | AAA: ${statusAaa.padEnd(4)}`
  );
  return cr;
}

function main() {
  const args = process.argv.slice(2);
  if (args.length === 2) {
    const cr = contrastRatio(args[0], args[1]);
    const res = evaluateWcag(cr);
    console.log(`Contrast ratio between ${args[0]} and ${args[1]}: ${cr.toFixed(2)}:1`);
    console.log(`WCAG AA Normal Text (>= 4.5:1): ${res.aa_normal ? 'PASS' : 'FAIL'}`);
    console.log(`WCAG AA Large Text  (>= 3.0:1): ${res.aa_large ? 'PASS' : 'FAIL'}`);
    console.log(`WCAG AAA Normal Text(>= 7.0:1): ${res.aaa_normal ? 'PASS' : 'FAIL'}`);
  } else {
    console.log('=== Project Aegis WCAG 2.1 Contrast Ratio Verification ===');
    const darkCanvas = '#0B0E14';
    const darkSurface = '#121722';
    const lightCanvas = '#F7F8FA';
    const lightSurface = '#FFFFFF';

    console.log('\n--- DARK THEME (Surface: #121722 / Canvas: #0B0E14) ---');
    printCheck('Primary text (#E6EAF2 on surface)', '#E6EAF2', darkSurface);
    printCheck('Muted text (#93A0B8 on surface)', '#93A0B8', darkSurface);
    printCheck('Approve text (#2FBF71 on surface)', '#2FBF71', darkSurface);
    printCheck('Review text (#F5A524 on surface)', '#F5A524', darkSurface);
    printCheck('Decline text (#F0556A on surface)', '#F0556A', darkSurface);
    printCheck('Decline pill text (#FF6B81 on surface)', '#FF6B81', darkSurface);
    printCheck('Info text (#4DA3FF on surface)', '#4DA3FF', darkSurface);
    printCheck('Dark text on accent button (#0B0E14 on #6C7CFF)', '#0B0E14', '#6C7CFF');
    printCheck('White on adjusted accent (#FFFFFF on #5061FF)', '#FFFFFF', '#5061FF');

    console.log('\n--- LIGHT THEME (Surface: #FFFFFF / Canvas: #F7F8FA) ---');
    printCheck('Primary text (#0F1626 on surface)', '#0F1626', lightSurface);
    printCheck('Muted text (#5B677F on surface)', '#5B677F', lightSurface);
    printCheck('Adjusted Approve text (#137841 on surface)', '#137841', lightSurface);
    printCheck('Adjusted Review text (#945900 on surface)', '#945900', lightSurface);
    printCheck('Decline text (#CC2F46 on surface)', '#CC2F46', lightSurface);
    printCheck('Info text (#1F6FD1 on surface)', '#1F6FD1', lightSurface);
    printCheck('Light Accent text (#4B5BE0 on surface)', '#4B5BE0', lightSurface);
    printCheck('White on light accent button (#FFFFFF on #4B5BE0)', '#FFFFFF', '#4B5BE0');
    printCheck('White on solid light accent button (#FFFFFF on #3849D6)', '#FFFFFF', '#3849D6');
  }
}

if (require.main === module) {
  main();
}

module.exports = {
  hexToRgb,
  srgbChannelToLinear,
  relativeLuminance,
  contrastRatio,
  evaluateWcag,
};
