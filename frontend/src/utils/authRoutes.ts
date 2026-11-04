import type { UserRole } from '../types/auth';

export function getDefaultLandingRoute(role?: UserRole): string {
  switch (role) {
    case 'owner':
      return '/settings';
    case 'admin':
      return '/settings/members';
    case 'risk_manager':
      return '/rules';
    case 'rule_author':
      return '/rules';
    case 'risk_analyst':
      return '/cases';
    case 'auditor':
      return '/audit';
    case 'developer':
    default:
      return '/decisions';
  }
}

