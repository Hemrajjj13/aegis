import { execSync } from 'node:child_process';
import { writeFileSync, mkdirSync } from 'node:fs';

const pages = [
  { name: 'home', url: 'http://127.0.0.1:4321/' },
  { name: 'features', url: 'http://127.0.0.1:4321/features' },
  { name: 'pricing', url: 'http://127.0.0.1:4321/pricing' },
  { name: 'docs', url: 'http://127.0.0.1:4321/docs' },
  { name: 'contact', url: 'http://127.0.0.1:4321/contact' },
];

mkdirSync('.lighthouse-reports', { recursive: true });

console.log('Running Lighthouse audits across 5 key pages...\n');
console.log('| Page | Performance | Accessibility | Best Practices | SEO |');
console.log('| :--- | :---: | :---: | :---: | :---: |');

for (const p of pages) {
  const outputPath = `.lighthouse-reports/${p.name}.json`;
  const cmd = `npx lighthouse ${p.url} --output=json --output-path=${outputPath} --chrome-flags="--headless --no-sandbox" --quiet --only-categories=performance,accessibility,best-practices,seo`;
  try {
    execSync(cmd, { stdio: 'ignore' });
    const report = JSON.parse(await import('node:fs').then(fs => fs.readFileSync(outputPath, 'utf8')));
    const perf = Math.round((report.categories.performance?.score || 0) * 100);
    const a11y = Math.round((report.categories.accessibility?.score || 0) * 100);
    const bp = Math.round((report.categories['best-practices']?.score || 0) * 100);
    const seo = Math.round((report.categories.seo?.score || 0) * 100);
    console.log(`| ${p.name} | ${perf} | ${a11y} | ${bp} | ${seo} |`);
  } catch (err) {
    console.error(`Failed to audit ${p.name}:`, err.message);
  }
}
