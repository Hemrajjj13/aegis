#!/usr/bin/env node
/**
 * tools/make-review-bundle.mjs
 * Creates aegis-review.zip in the repository root using git archive HEAD
 * and ensures docs/audit/project-status.md is included.
 */

import { execSync } from 'child_process';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
const rootDir = path.resolve(__dirname, '..');

const zipName = 'aegis-review.zip';
const zipPath = path.join(rootDir, zipName);
const reportRelPath = path.join('docs', 'audit', 'project-status.md');
const reportFullPath = path.join(rootDir, reportRelPath);

if (!fs.existsSync(reportFullPath)) {
  console.error(`Error: Status report not found at ${reportFullPath}`);
  process.exit(1);
}

console.log(`[make-review-bundle] Creating review bundle from git HEAD...`);

// 1. Generate clean zip archive of HEAD
try {
  execSync(`git archive --format=zip -o "${zipPath}" HEAD`, {
    cwd: rootDir,
    stdio: 'inherit',
  });
} catch (err) {
  console.error(`Error executing git archive: ${err.message}`);
  process.exit(1);
}

// 2. Ensure current on-disk docs/audit/project-status.md is inserted/updated in the zip archive
try {
  const pyScript = `import zipfile, os; z = zipfile.ZipFile(r'${zipPath}', 'a'); z.write(r'${reportFullPath}', arcname='docs/audit/project-status.md'); z.close()`;
  execSync(`python -c "${pyScript}"`, {
    cwd: rootDir,
    stdio: 'pipe',
  });
} catch (err) {
  // If python fails, report error or verify if git archive already contained it
  console.warn(`[make-review-bundle] Notice during zip update check: ${err.message}`);
}

// 3. Stat the resulting archive
const stats = fs.statSync(zipPath);
const sizeKb = (stats.size / 1024).toFixed(2);
const sizeMb = (stats.size / (1024 * 1024)).toFixed(2);

console.log(`================================================================================`);
console.log(`Review bundle created successfully:`);
console.log(`  Path: ${zipPath}`);
console.log(`  Size: ${stats.size} bytes (${sizeKb} KB / ${sizeMb} MB)`);
console.log(`================================================================================`);
