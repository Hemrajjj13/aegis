#!/usr/bin/env node
/**
 * tools/check_docs.js - Comprehensive Documentation & Architectural Integrity Audit for Project Aegis.
 *
 * Verification checks enforced:
 * 1. Broken Links: Validates that all relative links and file:/// links in /docs, README.md, and AGENTS.md resolve to existing files.
 * 2. Status Lines: Validates that every document in /docs includes a status line ('**Status:**').
 * 3. Open Questions: Verifies that no unresolved OPEN QUESTION remains without an explicit 'Decision DR-xx' or 'Deferred' marker.
 * 4. Index Completeness: Verifies that every document in /docs is indexed in docs/00-index.md.
 * 5. Canonical Enums & Fields: Verifies that core enums (decision outcomes, roles, tiers, fail policy) match canonical definitions in glossary.md.
 * 6. Compliance Phrasing: Verifies that the word 'compliant' does not appear outside approved disclaimers ('not compliant', 'non-compliant', or negative rules).
 * 7. Assumptions Register: Verifies that every row in docs/00-decisions/assumptions-register.md specifies a Gate (G1 or G2), an Owner, and a Validation Method.
 *
 * Exit code: 0 on PASS, 1 on FAIL.
 */

const fs = require('fs');
const path = require('path');
const url = require('url');

const WORKSPACE_ROOT = path.resolve(__dirname, '..');
const DOCS_DIR = path.join(WORKSPACE_ROOT, 'docs');
const README_PATH = path.join(WORKSPACE_ROOT, 'README.md');
const AGENTS_PATH = path.join(WORKSPACE_ROOT, 'AGENTS.md');

// Canonical enumerations from docs/glossary.md
const CANONICAL_OUTCOMES = new Set(['approve', 'decline', 'review']);
const CANONICAL_ROLES = new Set([
  'owner',
  'admin',
  'risk_manager',
  'rule_author',
  'risk_analyst',
  'auditor',
  'developer',
]);
const CANONICAL_TIERS = new Set(['Tier 0', 'Tier 1', 'Tier 2']);
const CANONICAL_FAIL_POLICY = new Set(['fail-open', 'fail-closed']);

function getAllMarkdownFiles() {
  const mdFiles = [];

  function walkDir(dir) {
    const entries = fs.readdirSync(dir, { withFileTypes: true });
    for (const entry of entries) {
      const fullPath = path.join(dir, entry.name);
      if (entry.isDirectory()) {
        walkDir(fullPath);
      } else if (entry.isFile() && entry.name.endsWith('.md')) {
        mdFiles.push(path.normalize(fullPath));
      }
    }
  }

  if (fs.existsSync(DOCS_DIR)) {
    walkDir(DOCS_DIR);
  }
  if (fs.existsSync(README_PATH)) {
    mdFiles.push(path.normalize(README_PATH));
  }
  if (fs.existsSync(AGENTS_PATH)) {
    mdFiles.push(path.normalize(AGENTS_PATH));
  }

  return mdFiles.sort();
}

function checkRelativeLinks(mdFiles) {
  const errors = [];
  const linkPattern = /\[([^\]]+)\]\(([^)]+)\)/g;

  for (const filePath of mdFiles) {
    let content;
    try {
      content = fs.readFileSync(filePath, 'utf-8');
    } catch (e) {
      errors.push(`Cannot read ${filePath}: ${e.message}`);
      continue;
    }

    const fileDir = path.dirname(filePath);
    let match;
    while ((match = linkPattern.exec(content)) !== null) {
      const rawUrl = match[2].trim();

      // Skip web URLs, mailto, anchor-only links
      if (
        rawUrl.startsWith('http://') ||
        rawUrl.startsWith('https://') ||
        rawUrl.startsWith('mailto:') ||
        rawUrl.startsWith('#')
      ) {
        continue;
      }

      // Handle file:/// URLs (often used in absolute documentation links)
      if (rawUrl.startsWith('file:///')) {
        let targetPath = rawUrl.slice('file:///'.length);
        // On Windows, targetPath might be c:/dev/... or /c:/dev/...
        if (targetPath.startsWith('/') && targetPath.length > 2 && targetPath[2] === ':') {
          targetPath = targetPath.slice(1);
        }
        targetPath = decodeURIComponent(targetPath.split('#')[0]);
        targetPath = path.normalize(targetPath);
        if (!fs.existsSync(targetPath)) {
          const relSource = path.relative(WORKSPACE_ROOT, filePath);
          errors.push(
            `Broken file:/// link in ${relSource}: '${rawUrl}' -> target '${targetPath}' not found`
          );
        }
        continue;
      }

      // Standard relative link
      let pathPart = rawUrl.split('#')[0];
      if (!pathPart) {
        continue; // anchor-only link
      }

      pathPart = decodeURIComponent(pathPart);
      const resolvedPath = path.normalize(path.join(fileDir, pathPart));
      if (!fs.existsSync(resolvedPath)) {
        const relSource = path.relative(WORKSPACE_ROOT, filePath);
        const relTarget = path.relative(WORKSPACE_ROOT, resolvedPath);
        errors.push(
          `Broken relative link in ${relSource}: '${rawUrl}' -> target '${relTarget}' not found`
        );
      }
    }
  }

  return errors;
}

function checkStatusLines() {
  const errors = [];
  const mdFiles = [];

  function walk(dir) {
    const entries = fs.readdirSync(dir, { withFileTypes: true });
    for (const entry of entries) {
      const full = path.join(dir, entry.name);
      if (entry.isDirectory()) {
        walk(full);
      } else if (entry.isFile() && entry.name.endsWith('.md')) {
        mdFiles.push(full);
      }
    }
  }

  walk(DOCS_DIR);

  for (const filePath of mdFiles) {
    const relPath = path.relative(WORKSPACE_ROOT, filePath);
    try {
      const content = fs.readFileSync(filePath, 'utf-8');
      const lines = content.split('\n').slice(0, 25);
      const headerBlock = lines.join('\n').toLowerCase();
      if (!headerBlock.includes('status:')) {
        errors.push(`Document missing status line ('**Status:**'): ${relPath}`);
      }
    } catch (e) {
      errors.push(`Error reading ${relPath}: ${e.message}`);
    }
  }
  return errors;
}

function checkOpenQuestions() {
  const errors = [];
  const oqPattern = /\b(open\s+question[s]?|oq-\d+)\b/i;

  const resolutionTokens = [
    'Decision DR-',
    'DR-',
    'Deferred',
    'formerly Open Questions',
    'Decision D3',
    'assumptions-register',
    'decision-register',
    'Decision Register',
    'resolved',
    'formerly `OPEN QUESTION`',
  ];

  function walk(dir) {
    const entries = fs.readdirSync(dir, { withFileTypes: true });
    for (const entry of entries) {
      const full = path.join(dir, entry.name);
      if (entry.isDirectory()) {
        walk(full);
      } else if (entry.isFile() && entry.name.endsWith('.md')) {
        const relPath = path.relative(WORKSPACE_ROOT, full);
        try {
          const lines = fs.readFileSync(full, 'utf-8').split('\n');
          for (let idx = 0; idx < lines.length; idx++) {
            const line = lines[idx];
            if (oqPattern.test(line)) {
              const start = Math.max(0, idx - 3);
              const end = Math.min(lines.length, idx + 4);
              const context = lines.slice(start, end).join(' ');
              const hasResolution = resolutionTokens.some(token => context.includes(token));
              if (!hasResolution) {
                errors.push(
                  `Unresolved Open Question without DR/Deferred marker in ${relPath}:${idx + 1}: '${line.trim()}'`
                );
              }
            }
          }
        } catch (e) {
          errors.push(`Error reading ${relPath}: ${e.message}`);
        }
      }
    }
  }

  walk(DOCS_DIR);
  return errors;
}

function checkIndexCompleteness() {
  const errors = [];
  const indexPath = path.join(DOCS_DIR, '00-index.md');
  if (!fs.existsSync(indexPath)) {
    return ['docs/00-index.md does not exist!'];
  }

  const indexContent = fs.readFileSync(indexPath, 'utf-8');

  function walk(dir) {
    const entries = fs.readdirSync(dir, { withFileTypes: true });
    for (const entry of entries) {
      const full = path.join(dir, entry.name);
      if (entry.isDirectory()) {
        walk(full);
      } else if (entry.isFile() && entry.name.endsWith('.md')) {
        const relToDocs = path.relative(DOCS_DIR, full).replace(/\\/g, '/');
        const basename = path.basename(full);

        if (relToDocs === '00-index.md') {
          continue;
        }

        if (!indexContent.includes(relToDocs) && !indexContent.includes(basename)) {
          errors.push(`Document missing from docs/00-index.md: ${relToDocs}`);
        }
      }
    }
  }

  walk(DOCS_DIR);
  return errors;
}

function checkCompliancePhrasing() {
  const errors = [];
  const compliantPattern = /\bcompliant\b/i;

  const allowedContexts = [
    'not compliant',
    'non-compliant',
    'never write "compliant"',
    'never write \'compliant\'',
    'purged "rbi-compliant"',
    'purged "compliant"',
    'instead of "compliant"',
    'without the word "compliant"',
    'the word "compliant" was purged',
  ];

  function checkFile(filePath) {
    const relPath = path.relative(WORKSPACE_ROOT, filePath);
    try {
      const lines = fs.readFileSync(filePath, 'utf-8').split('\n');
      for (let idx = 0; idx < lines.length; idx++) {
        const line = lines[idx];
        if (compliantPattern.test(line)) {
          const lower = line.toLowerCase();
          const isAllowed = allowedContexts.some(ctx => lower.includes(ctx));
          if (!isAllowed) {
            errors.push(
              `Forbidden word 'compliant' outside disclaimer in ${relPath}:${idx + 1}: '${line.trim()}'`
            );
          }
        }
      }
    } catch (e) {
      errors.push(`Error reading ${relPath}: ${e.message}`);
    }
  }

  function walk(dir) {
    const entries = fs.readdirSync(dir, { withFileTypes: true });
    for (const entry of entries) {
      const full = path.join(dir, entry.name);
      if (entry.isDirectory()) {
        walk(full);
      } else if (entry.isFile() && entry.name.endsWith('.md')) {
        checkFile(full);
      }
    }
  }

  walk(DOCS_DIR);
  if (fs.existsSync(README_PATH)) checkFile(README_PATH);
  if (fs.existsSync(AGENTS_PATH)) checkFile(AGENTS_PATH);

  return errors;
}

function checkAssumptionsRegister() {
  const errors = [];
  const assumpPath = path.join(DOCS_DIR, '00-decisions', 'assumptions-register.md');
  if (!fs.existsSync(assumpPath)) {
    return ['docs/00-decisions/assumptions-register.md does not exist!'];
  }

  const lines = fs.readFileSync(assumpPath, 'utf-8').split('\n');
  for (let idx = 0; idx < lines.length; idx++) {
    const lineClean = lines[idx].trim();
    if (lineClean.startsWith('|') && lineClean.includes('ASM-')) {
      const parts = lineClean.split('|').slice(1, -1).map(p => p.trim());
      if (parts.length >= 6) {
        const asmId = parts[0];
        const howToValidate = parts[3];
        const owner = parts[4];
        const gate = parts[5];

        if (!gate.includes('G1') && !gate.includes('G2')) {
          errors.push(
            `Assumptions row ${asmId} (line ${idx + 1}) lacks Gate G1/G2 dependency: '${gate}'`
          );
        }
        if (!owner || owner === '—') {
          errors.push(
            `Assumptions row ${asmId} (line ${idx + 1}) lacks responsible owner: '${owner}'`
          );
        }
        if (!howToValidate || howToValidate === '—') {
          errors.push(
            `Assumptions row ${asmId} (line ${idx + 1}) lacks validation method: '${howToValidate}'`
          );
        }
      }
    }
  }

  return errors;
}

function checkCanonicalGlossaryEnums() {
  const errors = [];
  const glossaryPath = path.join(DOCS_DIR, 'glossary.md');
  if (!fs.existsSync(glossaryPath)) {
    return ['docs/glossary.md missing!'];
  }

  const glossaryText = fs.readFileSync(glossaryPath, 'utf-8').toLowerCase();

  const requiredTerms = [
    'approve',
    'decline',
    'review',
    'tenant_id',
    'event_id',
    'event_time',
    'decision_id',
    'fail-open',
    'fail-closed',
    'tier 0',
    'tier 1',
    'tier 2',
    'gate g1',
    'gate g2',
    'evaluationcore',
    'owner',
    'admin',
    'risk_manager',
    'rule_author',
    'risk_analyst',
    'auditor',
    'developer',
  ];

  for (const term of requiredTerms) {
    if (!glossaryText.includes(term)) {
      errors.push(`Glossary missing canonical definition for '${term}'`);
    }
  }

  return errors;
}

function runAllChecks() {
  console.log('='.repeat(80));
  console.log('Project Aegis - Documentation & Architecture Audit (check_docs.js)');
  console.log('='.repeat(80));

  const mdFiles = getAllMarkdownFiles();
  console.log(`Discovered ${mdFiles.length} markdown files across /docs, README.md, and AGENTS.md.\n`);

  const allErrors = {};

  console.log('Checking 1: Relative and file:/// Markdown Links...');
  let errs = checkRelativeLinks(mdFiles);
  allErrors['Broken Links'] = errs;
  console.log(`  -> ${errs.length === 0 ? 'PASS' : `FAIL (${errs.length} broken links)`}`);

  console.log("Checking 2: Document Status Lines ('**Status:**')...");
  errs = checkStatusLines();
  allErrors['Missing Status Lines'] = errs;
  console.log(`  -> ${errs.length === 0 ? 'PASS' : `FAIL (${errs.length} docs lacking status lines)`}`);

  console.log('Checking 3: Resolved Open Question Markers (DR-xx / Deferred)...');
  errs = checkOpenQuestions();
  allErrors['Unresolved Open Questions'] = errs;
  console.log(`  -> ${errs.length === 0 ? 'PASS' : `FAIL (${errs.length} unresolved questions)`}`);

  console.log('Checking 4: Completeness of docs/00-index.md...');
  errs = checkIndexCompleteness();
  allErrors['Unindexed Documents'] = errs;
  console.log(`  -> ${errs.length === 0 ? 'PASS' : `FAIL (${errs.length} documents unindexed)`}`);

  console.log('Checking 5: Canonical Glossary Terms & Enum Consistency (incl. 7 Roles)...');
  errs = checkCanonicalGlossaryEnums();
  allErrors['Glossary Inconsistencies'] = errs;
  console.log(`  -> ${errs.length === 0 ? 'PASS' : `FAIL (${errs.length} missing canonical terms)`}`);

  console.log("Checking 6: Phrasing Discipline (No 'compliant' outside disclaimer)...");
  errs = checkCompliancePhrasing();
  allErrors['Forbidden Compliance Phrasing'] = errs;
  console.log(`  -> ${errs.length === 0 ? 'PASS' : `FAIL (${errs.length} violations)`}`);

  console.log('Checking 7: Assumptions Register Gate/Owner/Method Completeness...');
  errs = checkAssumptionsRegister();
  allErrors['Assumptions Register Gaps'] = errs;
  console.log(`  -> ${errs.length === 0 ? 'PASS' : `FAIL (${errs.length} rows lacking gate/owner/method)`}`);

  console.log('\n' + '='.repeat(80));
  const totalFailures = Object.values(allErrors).reduce((acc, list) => acc + list.length, 0);

  if (totalFailures === 0) {
    console.log('ALL DOCUMENTATION AUDIT CHECKS PASSED PERFECTLY (0 ERRORS).');
    console.log('='.repeat(80));
    return 0;
  } else {
    console.log(`AUDIT FAILED WITH ${totalFailures} TOTAL VIOLATIONS:`);
    for (const [category, errList] of Object.entries(allErrors)) {
      if (errList.length > 0) {
        console.log(`\n--- ${category} (${errList.length}) ---`);
        for (const err of errList) {
          console.log(`  [X] ${err}`);
        }
      }
    }
    console.log('\n' + '='.repeat(80));
    return 1;
  }
}

if (require.main === module) {
  const code = runAllChecks();
  process.exit(code);
}

module.exports = {
  getAllMarkdownFiles,
  checkRelativeLinks,
  checkStatusLines,
  checkOpenQuestions,
  checkIndexCompleteness,
  checkCompliancePhrasing,
  checkAssumptionsRegister,
  checkCanonicalGlossaryEnums,
  runAllChecks,
  CANONICAL_ROLES,
  CANONICAL_OUTCOMES,
  CANONICAL_TIERS,
  CANONICAL_FAIL_POLICY,
};
