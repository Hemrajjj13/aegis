import React, { useState } from 'react';
import { Eye, EyeOff } from 'lucide-react';

interface PasswordInputProps extends React.InputHTMLAttributes<HTMLInputElement> {
  id: string;
  label: string;
  error?: string;
  hint?: string;
}

export const PasswordInput: React.FC<PasswordInputProps> = ({
  id,
  label,
  error,
  hint,
  className = '',
  ...props
}) => {
  const [showPassword, setShowPassword] = useState(false);

  const errorId = `${id}-error`;
  const hintId = `${id}-hint`;

  const ariaDescribedBy = [
    error ? errorId : null,
    hint ? hintId : null,
  ].filter(Boolean).join(' ') || undefined;

  return (
    <div className="flex flex-col gap-1.5 w-full">
      <div className="flex justify-between items-center">
        <label htmlFor={id} className="text-xs font-medium text-text">
          {label}
        </label>
        {hint && (
          <span id={hintId} className="text-[11px] text-text-muted">
            {hint}
          </span>
        )}
      </div>

      <div className="relative flex items-center">
        <input
          {...props}
          id={id}
          type={showPassword ? 'text' : 'password'}
          aria-invalid={!!error}
          aria-describedby={ariaDescribedBy}
          className={`w-full px-3 py-2 pr-10 text-xs rounded border bg-surface text-text placeholder:text-text-muted/60 transition-colors focus:outline-none focus:ring-1 focus:ring-accent ${
            error ? 'border-decline focus:ring-decline' : 'border-border focus:border-accent'
          } ${className}`}
        />
        <button
          type="button"
          onClick={() => setShowPassword((prev) => !prev)}
          className="absolute right-2 p-1 text-text-muted hover:text-text rounded transition-colors focus:outline-none focus:ring-1 focus:ring-accent"
          aria-label={showPassword ? `Hide ${label.toLowerCase()}` : `Show ${label.toLowerCase()}`}
        >
          {showPassword ? (
            <EyeOff className="w-4 h-4" aria-hidden="true" />
          ) : (
            <Eye className="w-4 h-4" aria-hidden="true" />
          )}
        </button>
      </div>

      {error && (
        <span id={errorId} role="alert" className="text-[11px] font-medium text-decline-text">
          {error}
        </span>
      )}
    </div>
  );
};

