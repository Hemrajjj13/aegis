import React from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';
import type { UserRole } from '../types/auth';

interface RoleGuardProps {
  children: JSX.Element;
  allowedRoles?: UserRole[];
}

export const RoleGuard: React.FC<RoleGuardProps> = ({ children, allowedRoles }) => {
  const { isAuthenticated, isLoading, user } = useAuth();
  const location = useLocation();

  if (isLoading) {
    return (
      <div className="min-h-screen w-screen flex items-center justify-center bg-canvas text-text">
        <div className="flex flex-col items-center gap-3">
          <div className="w-6 h-6 border-2 border-accent border-t-transparent rounded-full animate-spin" />
          <span className="text-xs text-text-muted font-mono">Restoring secure session...</span>
        </div>
      </div>
    );
  }

  if (!isAuthenticated || !user) {
    return <Navigate to="/login" state={{ from: location }} replace />;
  }

  if (allowedRoles && allowedRoles.length > 0 && !allowedRoles.includes(user.role)) {
    return <Navigate to="/decisions" replace />;
  }

  return children;
};

