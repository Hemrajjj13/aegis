import { readdirSync, statSync, readFileSync } from 'node:fs';
import { join, extname } from 'node:path';

const dist = process.argv[2] || 'dist';

// Allow placeholders in dev by default, but fail in production unless ALLOW_PLACEHOLDERS=true
const allowPlaceholders = process.env.ALLOW_PLACEHOLDERS === 'true' ||
  (process.env.ALLOW_PLACEHOLDERS === undefined && process.env.NODE_ENV !== 'production');

const banned = [
  'trusted by',
  'bank-grade',
  'guarantee',
  'compliant',
  'certified',
  'SOC 2',
  'AI-powered',
  '99.9%',
];

const placeholders = [
  'founder@example.com',
  'Placeholder Legal Entity Ltd.',
  'Founder name not yet published',
  'Founder bio placeholder',
];

function walk(dir, out = []) {
  try {
    for (const f of readdirSync(dir)) {
      const p = join(dir, f);
      const s = statSync(p);
      if (s.isDirectory()) walk(p, out);
      else if (extname(f) === '.html') out.push(p);
    }
  } catch (err) {
    console.error(`Cannot read directory: ${dir}`, err.message);
    process.exit(1);
  }
  return out;
}

const files = walk(dist);
if (files.length === 0) {
  console.error(`No HTML files found in ${dist}`);
  process.exit(1);
}

let failed = false;
for (const f of files) {
  const html = readFileSync(f, 'utf8').toLowerCase();

  for (const phrase of banned) {
    if (html.includes(phrase.toLowerCase())) {
      console.error(`BANNED PHRASE in ${f}: "${phrase}"`);
      failed = true;
    }
  }

  for (const ph of placeholders) {
    if (html.includes(ph.toLowerCase())) {
      if (!allowPlaceholders) {
        console.error(`PLACEHOLDER FOUND in production build ${f}: "${ph}"`);
        failed = true;
      } else {
        console.warn(`[DEV NOTE] Placeholder present in ${f}: "${ph}" (allowed by dev default)`);
      }
    }
  }
}

if (failed) {
  console.error('check-website-content: FAILED');
  process.exit(1);
}
console.log('check-website-content: OK');
