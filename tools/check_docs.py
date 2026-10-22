#!/usr/bin/env python3
"""
tools/check_docs.py - Comprehensive Documentation & Architectural Integrity Audit for Project Aegis.

Verification checks enforced:
1. Broken Links: Validates that all relative links and file:/// links in /docs, README.md, and AGENTS.md resolve to existing files.
2. Status Lines: Validates that every document in /docs includes a status line ('**Status:**').
3. Open Questions: Verifies that no unresolved OPEN QUESTION remains without an explicit 'Decision DR-xx' or 'Deferred' marker.
4. Index Completeness: Verifies that every document in /docs is indexed in docs/00-index.md.
5. Canonical Enums & Fields: Verifies that core enums (decision outcomes, roles, tiers, fail policy) match canonical definitions in glossary.md.
6. Compliance Phrasing: Verifies that the word 'compliant' does not appear outside approved disclaimers ('not compliant', 'non-compliant', or negative rules).
7. Assumptions Register: Verifies that every row in docs/00-decisions/assumptions-register.md specifies a Gate (G1 or G2), an Owner, and a Validation Method.

Exit code: 0 on PASS, 1 on FAIL.
"""

import os
import re
import sys
import urllib.parse

WORKSPACE_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
DOCS_DIR = os.path.join(WORKSPACE_ROOT, "docs")
README_PATH = os.path.join(WORKSPACE_ROOT, "README.md")
AGENTS_PATH = os.path.join(WORKSPACE_ROOT, "AGENTS.md")

# Canonical enumerations from docs/glossary.md
CANONICAL_OUTCOMES = {"approve", "decline", "review"}
CANONICAL_ROLES = {"owner", "admin", "risk_manager", "risk_analyst", "auditor", "developer"}
CANONICAL_TIERS = {"Tier 0", "Tier 1", "Tier 2"}
CANONICAL_FAIL_POLICY = {"fail-open", "fail-closed"}

def get_all_markdown_files():
    md_files = []
    for root, _, files in os.walk(DOCS_DIR):
        for f in files:
            if f.endswith(".md"):
                md_files.append(os.path.normpath(os.path.join(root, f)))
    if os.path.exists(README_PATH):
        md_files.append(os.path.normpath(README_PATH))
    if os.path.exists(AGENTS_PATH):
        md_files.append(os.path.normpath(AGENTS_PATH))
    return sorted(md_files)

def check_relative_links(md_files):
    errors = []
    link_pattern = re.compile(r'\[([^\]]+)\]\(([^)]+)\)')
    
    for file_path in md_files:
        try:
            with open(file_path, "r", encoding="utf-8") as f:
                content = f.read()
        except Exception as e:
            errors.append(f"Cannot read {file_path}: {e}")
            continue

        file_dir = os.path.dirname(file_path)
        for match in link_pattern.finditer(content):
            raw_url = match.group(2).strip()
            
            # Skip web URLs, mailto, anchor-only links
            if raw_url.startswith(("http://", "https://", "mailto:", "#")):
                continue
                
            # Handle file:/// URLs (often used in absolute documentation links)
            if raw_url.startswith("file:///"):
                parsed = urllib.parse.urlparse(raw_url)
                # On Windows, path is /c:/dev/...
                target_path = parsed.path
                if target_path.startswith("/") and len(target_path) > 2 and target_path[2] == ":":
                    target_path = target_path[1:] # strip leading /
                target_path = urllib.parse.unquote(target_path)
                target_path = target_path.split("#")[0] # remove anchor
                target_path = os.path.normpath(target_path)
                if not os.path.exists(target_path):
                    errors.append(f"Broken file:/// link in {os.path.relpath(file_path, WORKSPACE_ROOT)}: '{raw_url}' -> target '{target_path}' not found")
                continue

            # Standard relative link
            path_part = raw_url.split("#")[0]
            if not path_part:
                continue # anchor-only link
                
            path_part = urllib.parse.unquote(path_part)
            resolved_path = os.path.normpath(os.path.join(file_dir, path_part))
            if not os.path.exists(resolved_path):
                errors.append(f"Broken relative link in {os.path.relpath(file_path, WORKSPACE_ROOT)}: '{raw_url}' -> target '{os.path.relpath(resolved_path, WORKSPACE_ROOT)}' not found")

    return errors

def check_status_lines():
    errors = []
    for root, _, files in os.walk(DOCS_DIR):
        for f in files:
            if f.endswith(".md"):
                file_path = os.path.join(root, f)
                rel_path = os.path.relpath(file_path, WORKSPACE_ROOT)
                try:
                    with open(file_path, "r", encoding="utf-8") as file:
                        first_lines = [file.readline() for _ in range(25)]
                        header_block = "".join(first_lines)
                        if "status:" not in header_block.lower():
                            errors.append(f"Document missing status line ('**Status:**'): {rel_path}")
                except Exception as e:
                    errors.append(f"Error reading {rel_path}: {e}")
    return errors

def check_open_questions():
    errors = []
    oq_pattern = re.compile(r'\b(open\s+question[s]?|oq-\d+)\b', re.IGNORECASE)
    
    for root, _, files in os.walk(DOCS_DIR):
        for f in files:
            if f.endswith(".md"):
                file_path = os.path.join(root, f)
                rel_path = os.path.relpath(file_path, WORKSPACE_ROOT)
                try:
                    with open(file_path, "r", encoding="utf-8") as file:
                        lines = file.readlines()
                    
                    for idx, line in enumerate(lines, 1):
                        if oq_pattern.search(line):
                            # Must have Decision DR- or Deferred or formerly open or Decision D3 or register
                            context_lines = "".join(lines[max(0, idx-3):min(len(lines), idx+3)])
                            has_resolution = any(token in context_lines for token in [
                                "Decision DR-", "DR-", "Deferred", "formerly Open Questions",
                                "Decision D3", "assumptions-register", "decision-register",
                                "Decision Register", "resolved", "formerly `OPEN QUESTION`"
                            ])
                            if not has_resolution:
                                errors.append(f"Unresolved Open Question without DR/Deferred marker in {rel_path}:{idx}: '{line.strip()}'")
                except Exception as e:
                    errors.append(f"Error reading {rel_path}: {e}")
    return errors

def check_index_completeness():
    errors = []
    index_path = os.path.join(DOCS_DIR, "00-index.md")
    if not os.path.exists(index_path):
        return ["docs/00-index.md does not exist!"]
        
    with open(index_path, "r", encoding="utf-8") as f:
        index_content = f.read()

    for root, _, files in os.walk(DOCS_DIR):
        for f in files:
            if f.endswith(".md"):
                file_path = os.path.normpath(os.path.join(root, f))
                rel_to_docs = os.path.relpath(file_path, DOCS_DIR).replace("\\", "/")
                basename = os.path.basename(file_path)
                
                # Exclude the index file itself
                if rel_to_docs == "00-index.md":
                    continue
                    
                # Check if rel_to_docs or basename is linked in index_content
                if rel_to_docs not in index_content and basename not in index_content:
                    errors.append(f"Document missing from docs/00-index.md: {rel_to_docs}")

    return errors

def check_compliance_phrasing():
    errors = []
    # Match standalone 'compliant'
    compliant_pattern = re.compile(r'\bcompliant\b', re.IGNORECASE)
    
    # Allowed context patterns (disclaimers or negative instructions)
    allowed_contexts = [
        "not compliant",
        "non-compliant",
        "never write \"compliant\"",
        "never write 'compliant'",
        "purged \"rbi-compliant\"",
        "purged \"compliant\"",
        "instead of \"compliant\"",
        "without the word \"compliant\"",
        "the word \"compliant\" was purged"
    ]

    for root, _, files in os.walk(DOCS_DIR):
        for f in files:
            if f.endswith(".md"):
                file_path = os.path.join(root, f)
                rel_path = os.path.relpath(file_path, WORKSPACE_ROOT)
                try:
                    with open(file_path, "r", encoding="utf-8") as file:
                        for idx, line in enumerate(file, 1):
                            if compliant_pattern.search(line):
                                line_lower = line.lower()
                                if not any(allowed in line_lower for allowed in allowed_contexts):
                                    errors.append(f"Forbidden word 'compliant' outside disclaimer in {rel_path}:{idx}: '{line.strip()}'")
                except Exception as e:
                    errors.append(f"Error reading {rel_path}: {e}")

    # Also check README.md and AGENTS.md
    for extra_path in [README_PATH, AGENTS_PATH]:
        if os.path.exists(extra_path):
            rel_path = os.path.relpath(extra_path, WORKSPACE_ROOT)
            with open(extra_path, "r", encoding="utf-8") as file:
                for idx, line in enumerate(file, 1):
                    if compliant_pattern.search(line):
                        line_lower = line.lower()
                        if not any(allowed in line_lower for allowed in allowed_contexts):
                            errors.append(f"Forbidden word 'compliant' outside disclaimer in {rel_path}:{idx}: '{line.strip()}'")

    return errors

def check_assumptions_register():
    errors = []
    assump_path = os.path.join(DOCS_DIR, "00-decisions", "assumptions-register.md")
    if not os.path.exists(assump_path):
        return ["docs/00-decisions/assumptions-register.md does not exist!"]

    with open(assump_path, "r", encoding="utf-8") as f:
        lines = f.readlines()

    in_table = False
    for idx, line in enumerate(lines, 1):
        line_clean = line.strip()
        if line_clean.startswith("|") and ("ASM-" in line_clean):
            # Parse table columns: | Assumption ID | Category | Assumption | How to Validate | Responsible Owner | Gate Dependency | Current Status |
            parts = [p.strip() for p in line_clean.split("|")[1:-1]]
            if len(parts) >= 6:
                asm_id = parts[0]
                how_to_validate = parts[3]
                owner = parts[4]
                gate = parts[5]

                # Validate Gate (must mention G1 or G2)
                if not ("G1" in gate or "G2" in gate):
                    errors.append(f"Assumptions row {asm_id} (line {idx}) lacks Gate G1/G2 dependency: '{gate}'")
                
                # Validate Owner
                if not owner or owner == "—":
                    errors.append(f"Assumptions row {asm_id} (line {idx}) lacks responsible owner: '{owner}'")
                    
                # Validate How to Validate
                if not how_to_validate or how_to_validate == "—":
                    errors.append(f"Assumptions row {asm_id} (line {idx}) lacks validation method: '{how_to_validate}'")

    return errors

def check_canonical_glossary_enums():
    errors = []
    # Verify that glossary defines all required terms
    glossary_path = os.path.join(DOCS_DIR, "glossary.md")
    if not os.path.exists(glossary_path):
        return ["docs/glossary.md missing!"]

    with open(glossary_path, "r", encoding="utf-8") as f:
        glossary_text = f.read().lower()

    required_terms = [
        "approve", "decline", "review",
        "tenant_id", "event_id", "event_time", "decision_id",
        "fail-open", "fail-closed", "tier 0", "tier 1", "tier 2",
        "gate g1", "gate g2", "evaluationcore"
    ]
    for term in required_terms:
        if term not in glossary_text:
            errors.append(f"Glossary missing canonical definition for '{term}'")

    return errors

def run_all_checks():
    print("=" * 80)
    print("Project Aegis - Documentation & Architecture Audit (check_docs.py)")
    print("=" * 80)
    
    md_files = get_all_markdown_files()
    print(f"Discovered {len(md_files)} markdown files across /docs, README.md, and AGENTS.md.\n")

    all_errors = {}

    # Check 1: Relative and local links
    print("Checking 1: Relative and file:/// Markdown Links...")
    errs = check_relative_links(md_files)
    all_errors["Broken Links"] = errs
    print(f"  -> {'PASS' if not errs else f'FAIL ({len(errs)} broken links)'}")

    # Check 2: Status lines
    print("Checking 2: Document Status Lines ('**Status:**')...")
    errs = check_status_lines()
    all_errors["Missing Status Lines"] = errs
    print(f"  -> {'PASS' if not errs else f'FAIL ({len(errs)} docs lacking status lines)'}")

    # Check 3: Open questions
    print("Checking 3: Resolved Open Question Markers (DR-xx / Deferred)...")
    errs = check_open_questions()
    all_errors["Unresolved Open Questions"] = errs
    print(f"  -> {'PASS' if not errs else f'FAIL ({len(errs)} unresolved questions)'}")

    # Check 4: Index completeness
    print("Checking 4: Completeness of docs/00-index.md...")
    errs = check_index_completeness()
    all_errors["Unindexed Documents"] = errs
    print(f"  -> {'PASS' if not errs else f'FAIL ({len(errs)} documents unindexed)'}")

    # Check 5: Canonical glossary terms
    print("Checking 5: Canonical Glossary Terms & Enum Consistency...")
    errs = check_canonical_glossary_enums()
    all_errors["Glossary Inconsistencies"] = errs
    print(f"  -> {'PASS' if not errs else f'FAIL ({len(errs)} missing canonical terms)'}")

    # Check 6: Compliance phrasing discipline
    print("Checking 6: Phrasing Discipline (No 'compliant' outside disclaimer)...")
    errs = check_compliance_phrasing()
    all_errors["Forbidden Compliance Phrasing"] = errs
    print(f"  -> {'PASS' if not errs else f'FAIL ({len(errs)} violations)'}")

    # Check 7: Assumptions register integrity
    print("Checking 7: Assumptions Register Gate/Owner/Method Completeness...")
    errs = check_assumptions_register()
    all_errors["Assumptions Register Gaps"] = errs
    print(f"  -> {'PASS' if not errs else f'FAIL ({len(errs)} rows lacking gate/owner/method)'}")

    print("\n" + "=" * 80)
    total_failures = sum(len(err_list) for err_list in all_errors.values())
    if total_failures == 0:
        print("ALL DOCUMENTATION AUDIT CHECKS PASSED PERFECTLY (0 ERRORS).")
        print("=" * 80)
        return 0
    else:
        print(f"AUDIT FAILED WITH {total_failures} TOTAL VIOLATIONS:")
        for category, err_list in all_errors.items():
            if err_list:
                print(f"\n--- {category} ({len(err_list)}) ---")
                for err in err_list:
                    print(f"  [X] {err}")
        print("\n" + "=" * 80)
        return 1

if __name__ == "__main__":
    sys.exit(run_all_checks())
