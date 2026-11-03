import { readdirSync, statSync, readFileSync } from 'node:fs';
import { join, extname } from 'node:path';
import { gzipSync } from 'node:zlib';

const dist = 'dist';
let total = 0;
function walk(dir) {
  for (const f of readdirSync(dir)) {
    const p = join(dir, f);
    const s = statSync(p);
    if (s.isDirectory()) walk(p);
    else total += s.size;
  }
}
walk(dist);

let jsGzTotal = 0;
function walkJs(dir) {
  for (const f of readdirSync(dir)) {
    const p = join(dir, f);
    const s = statSync(p);
    if (s.isDirectory()) walkJs(p);
    else if (extname(f) === '.js') jsGzTotal += gzipSync(readFileSync(p)).length;
  }
}
walkJs(dist);

console.log(`Total transfer: ${total} bytes (${(total / 1024).toFixed(1)} KB)`);
console.log(`JS gzipped: ${jsGzTotal} bytes (${(jsGzTotal / 1024).toFixed(1)} KB)`);

let fail = false;
// Multi-page site budget (13 static HTML pages + shared self-hosted fonts + CSS)
if (total > 500 * 1024) {
  console.error(`FAIL: total transfer (${(total / 1024).toFixed(1)} KB) exceeds 500 KB budget`);
  fail = true;
}
if (jsGzTotal > 20 * 1024) {
  console.error(`FAIL: JS gzipped (${(jsGzTotal / 1024).toFixed(1)} KB) exceeds 20 KB budget`);
  fail = true;
}

// Check for third-party render-blocking scripts or styles in index.html
const indexHtml = readFileSync(join(dist, 'index.html'), 'utf8');
const thirdPartyRender = indexHtml.match(/<(?:script|link)[^>]*(?:src|href)=["']https?:\/\/[^"']*(?:rel=["']stylesheet["']|as=["']style["'])?/gi) || [];
const renderBlocking = thirdPartyRender.filter((tag) => /rel=["']stylesheet["']/i.test(tag) || /<script/i.test(tag));
if (renderBlocking.length > 0) {
  console.error('FAIL: Found third-party render resources in index.html:', renderBlocking);
  fail = true;
} else {
  console.log('Zero third-party render resources: OK');
}

if (fail) process.exit(1);
console.log('Budget OK');
