import { describe, it, expect } from 'vitest';
import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';

function extractBlock(css: string, selectorRegex: RegExp): Record<string, string> {
  const m = css.match(selectorRegex);
  if (!m) return {};
  const block = m[1];
  const out: Record<string, string> = {};
  const varRe = /--([a-zA-Z0-9-]+)\s*:\s*([^;]+);/g;
  let mm;
  while ((mm = varRe.exec(block)) !== null) {
    out['--' + mm[1]] = mm[2].trim().replace(/\s*\/\*.*$/, '').trim();
  }
  return out;
}

const frontendCss = readFileSync(resolve(__dirname, '../../frontend/src/index.css'), 'utf8');
const websiteCss = readFileSync(resolve(__dirname, '../src/styles/global.css'), 'utf8');

describe('colour tokens match frontend', () => {
  it('dark theme tokens equal', () => {
    const fe = extractBlock(frontendCss, /:root\s*\{([^}]*)\}/m);
    const web = extractBlock(websiteCss, /:root\s*\{([^}]*)\}/m);
    expect(web).toEqual(fe);
  });
  it('light theme tokens equal', () => {
    const fe = extractBlock(frontendCss, /\[data-theme="light"\][^}]*\{([^}]*)\}/m);
    const web = extractBlock(websiteCss, /\[data-theme="light"\][^}]*\{([^}]*)\}/m);
    expect(web).toEqual(fe);
  });
});
