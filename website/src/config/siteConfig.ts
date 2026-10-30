// All missing facts live here as explicit placeholders.
// See website/CONTENT-TODO.md for the list that must be resolved before launch.

export type Placeholder = {
  value: string;
  placeholder: true;
  todo: string;
};

export const isPlaceholder = (v: unknown): v is Placeholder =>
  typeof v === 'object' && v !== null && (v as { placeholder?: boolean }).placeholder === true;

export const siteConfig = {
  productName: 'Aegis', // working name only; may contain "Aegis"
  tagline: 'Change fraud rules with confidence by testing them against your history before you ship.',
  description:
    'A real-time, explainable transaction decision engine. Replay candidate rules against historical data before deploying them to production.',
  contactEmail: {
    value: 'founder@example.com',
    placeholder: true,
    todo: 'Replace with real contact email (founder inbox).',
  } as Placeholder,
  consoleUrl: import.meta.env.PUBLIC_CONSOLE_URL || 'http://localhost:5173',
  siteUrl: import.meta.env.PUBLIC_SITE_URL || 'http://localhost:4321',
  legalName: {
    value: 'Placeholder Legal Entity Ltd.',
    placeholder: true,
    todo: 'Replace with registered legal entity name.',
  } as Placeholder,
  about: {
    founderName: {
      value: 'Founder name not yet published',
      placeholder: true,
      todo: 'Add founder name and short bio from docs/15.',
    } as Placeholder,
    founderBio: {
      value:
        'Founder bio placeholder. The product is built by a solo founder with AI coding agents; details to be added.',
      placeholder: true,
      todo: 'Replace with real founder bio.',
    } as Placeholder,
  },
  year: new Date().getFullYear(),
};

export const mailtoTalkToUs = (): string => {
  const email = isPlaceholder(siteConfig.contactEmail) ? siteConfig.contactEmail.value : siteConfig.contactEmail;
  const subject = encodeURIComponent(`Talk to us about ${siteConfig.productName}`);
  return `mailto:${email}?subject=${subject}`;
};
