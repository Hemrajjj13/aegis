# CONTENT-TODO.md

Every placeholder that must be resolved before launch. The build-time check
(`tools/check-website-content.mjs`) FAILS the production build while any of these
remain, unless `ALLOW_PLACEHOLDERS=true`.

- `siteConfig.contactEmail` — real contact email (founder inbox).
- `siteConfig.legalName` — registered legal entity name.
- `siteConfig.about.founderName` — founder name.
- `siteConfig.about.founderBio` — founder bio.
- `siteConfig.productName` — final product name (currently the working name "Aegis").
- Domain / `PUBLIC_SITE_URL` — final public domain.
- Founder details must come from `docs/15-go-to-market.md`; do not invent.
