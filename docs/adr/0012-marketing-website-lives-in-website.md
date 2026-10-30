# ADR-0012: Marketing website lives in `website/`

**Status:** Accepted
**Date:** 2026-10-28

## Context

Project Aegis needs a public marketing home page. The repository already contains a React operator console in `frontend/` and a Java backend in `backend/`. Adding marketing pages to either would couple public, unauthenticated content to authenticated application bundles and risk leaking console routes.

## Decision

A separate static site lives in the top-level `website/` directory. It is built with Astro (static output), TypeScript (strict) and Tailwind, with no backend, no login, no cookies and no analytics. It reuses the colour tokens from `frontend/src/index.css` and `DESIGN.md`.

## Consequences

- `website/` is an independent workspace with its own `package.json` and build pipeline.
- No changes to `backend/`, `frontend/`, `api/` or protected docs 06, 07, 09.
- The marketing site is static and deployable to any static host.
- A build-time content check (`tools/check-website-content.mjs`) enforces the content rules.
