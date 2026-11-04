import React from 'react';
import { Navigate } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';
import { getDefaultLandingRoute } from '../utils/authRoutes';

export const RootRedirect: React.FC = () => {
  const { isAuthenticated, isLoading, user } = useAuth();

  if (isLoading) {
    return (
      <div className="min-h-screen w-screen flex items-center justify-center bg-canvas text-text">
        <div className="w-6 h-6 border-2 border-accent border-t-transparent rounded-full animate-spin" />
      </div>
    );
  }

  if (isAuthenticated && user) {
    return <Navigate to={getDefaultLandingRoute(user.role)} replace />;
  }

  return <Navigate to="/login" replace />;
};

