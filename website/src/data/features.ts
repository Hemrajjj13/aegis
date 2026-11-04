export type FeatureStatus = 'In development' | 'Planned';

export interface Feature {
  id: string;
  title: string;
  description: string;
  status: FeatureStatus;
}

export const features: Feature[] = [
  {
    id: 'decision-api',
    title: 'Decision API',
    description:
      'Synchronous POST /v1/decisions returning approve, review or decline with the fired rules and scores.',
    status: 'In development',
  },
  {
    id: 'explainable-decisions',
    title: 'Explainable decisions',
    description:
      'Every verdict ships with the reasons, rule weights and score breakdown behind it.',
    status: 'In development',
  },
  {
    id: 'rule-versions-maker-checker',
    title: 'Rule versions with maker-checker approval',
    description:
      'Draft, simulate and submit rule changes for a second-person approval before they go live.',
    status: 'Planned',
  },
  {
    id: 'replay-compare',
    title: 'Replay and compare',
    description:
      'Run a candidate rule version against a historical dataset and diff the outcomes against the live version.',
    status: 'Planned',
  },
  {
    id: 'audit-trail',
    title: 'Audit trail',
    description:
      'An immutable record of every decision and every rule change, sealed for later verification.',
    status: 'In development',
  },
  {
    id: 'tenant-isolation',
    title: 'Tenant isolation',
    description:
      'Row-level security so each tenant only ever sees its own transactions and rules.',
    status: 'In development',
  },
  {
    id: 'dataset-import-backtest',
    title: 'Dataset import and backtest report',
    description:
      'Upload historical CSV or Parquet with client-side hashing and get a backtest comparison report.',
    status: 'Planned',
  },
];
