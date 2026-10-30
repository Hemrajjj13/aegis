import { spawn } from 'node:child_process';
import { readFileSync, unlinkSync } from 'node:fs';

const CHROME_PATH = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';
process.env.CHROME_PATH = CHROME_PATH;

async function runLighthouse() {
  console.log('Starting astro preview...');
  const preview = spawn('npx', ['astro', 'preview', '--port', '4321'], {
    cwd: process.cwd(),
    shell: true,
    stdio: 'pipe',
  });

  // wait 4 seconds for server to be ready
  await new Promise((resolve) => setTimeout(resolve, 4000));

  async function audit(preset, outFile) {
    console.log(`Running Lighthouse for ${preset}...`);
    const args = [
      'lighthouse',
      'http://localhost:4321/',
      '--output=json',
      `--output-path=${outFile}`,
      '--chrome-flags="--headless --no-sandbox --disable-gpu"',
      preset === 'desktop' ? '--preset=desktop' : '',
    ].filter(Boolean);

    await new Promise((resolve, reject) => {
      const lh = spawn('npx', args, { shell: true, stdio: 'inherit' });
      lh.on('close', (code) => {
        if (code === 0) resolve();
        else reject(new Error(`Lighthouse exited with code ${code}`));
      });
    });

    const result = JSON.parse(readFileSync(outFile, 'utf8'));
    try { unlinkSync(outFile); } catch { }

    const cats = result.categories;
    const scores = {
      performance: Math.round((cats.performance?.score || 0) * 100),
      accessibility: Math.round((cats.accessibility?.score || 0) * 100),
      bestPractices: Math.round((cats['best-practices']?.score || 0) * 100),
      seo: Math.round((cats.seo?.score || 0) * 100),
    };
    return scores;
  }

  try {
    const mobile = await audit('mobile', 'lh-mobile.json');
    const desktop = await audit('desktop', 'lh-desktop.json');

    console.log('\n========================================');
    console.log('LIGHTHOUSE RESULTS (single-machine measurement)');
    console.log('========================================');
    console.log('Mobile:');
    console.log(`  Performance:    ${mobile.performance}`);
    console.log(`  Accessibility:  ${mobile.accessibility}`);
    console.log(`  Best Practices: ${mobile.bestPractices}`);
    console.log(`  SEO:            ${mobile.seo}`);
    console.log('Desktop:');
    console.log(`  Performance:    ${desktop.performance}`);
    console.log(`  Accessibility:  ${desktop.accessibility}`);
    console.log(`  Best Practices: ${desktop.bestPractices}`);
    console.log(`  SEO:            ${desktop.seo}`);
    console.log('========================================\n');
  } finally {
    preview.kill('SIGTERM');
  }
}

runLighthouse().catch((err) => {
  console.error(err);
  process.exit(1);
});
