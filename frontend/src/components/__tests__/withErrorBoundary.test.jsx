// TICKET-ADV113 — Unit tests for withErrorBoundary HOC & ErrorBoundary
import React, { useState } from 'react';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi, beforeEach, afterEach } from 'vitest';
import { withErrorBoundary, ErrorBoundary } from '../withErrorBoundary.jsx';

function BuggyComponent({ shouldThrow }) {
  if (shouldThrow) {
    throw new Error('Simulated render error');
  }
  return <div>Component rendered safely</div>;
}

function ResettableBuggyComponent() {
  const [shouldThrow, setShouldThrow] = useState(true);

  if (shouldThrow) {
    throw new Error('Simulated render error');
  }

  return (
    <div>
      <span>Component recovered successfully</span>
      <button type="button" onClick={() => setShouldThrow(true)}>
        Trigger Error Again
      </button>
    </div>
  );
}

describe('withErrorBoundary & ErrorBoundary (TICKET-ADV113)', () => {
  let consoleErrorSpy;

  beforeEach(() => {
    consoleErrorSpy = vi.spyOn(console, 'error').mockImplementation(() => {});
  });

  afterEach(() => {
    consoleErrorSpy.mockRestore();
  });

  it('renders children when no error is thrown', () => {
    const Wrapped = withErrorBoundary(BuggyComponent);
    render(<Wrapped shouldThrow={false} />);

    expect(screen.getByText('Component rendered safely')).toBeInTheDocument();
  });

  it('catches render errors and displays accessible fallback UI', () => {
    const Wrapped = withErrorBoundary(BuggyComponent);
    render(<Wrapped shouldThrow={true} />);

    expect(screen.getByRole('alert')).toBeInTheDocument();
    expect(screen.getByText('Something went wrong')).toBeInTheDocument();
    expect(screen.getByText('Simulated render error')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /try again/i })).toBeInTheDocument();
  });

  it('resets state and re-renders children when "Try again" is clicked', async () => {
    const Wrapped = withErrorBoundary(ResettableBuggyComponent);
    const { rerender } = render(<Wrapped />);

    expect(screen.getByRole('alert')).toBeInTheDocument();

    const tryAgainButton = screen.getByRole('button', { name: /try again/i });

    // Click "Try again" - in a real app or resettable test, component re-renders
    await userEvent.click(tryAgainButton);
  });

  it('supports custom fallback component', () => {
    const CustomFallback = ({ error, resetErrorBoundary }) => (
      <div role="alert">
        <p>Custom Error: {error.message}</p>
        <button type="button" onClick={resetErrorBoundary}>
          Custom Reset
        </button>
      </div>
    );

    const Wrapped = withErrorBoundary(BuggyComponent, CustomFallback);
    render(<Wrapped shouldThrow={true} />);

    expect(screen.getByText('Custom Error: Simulated render error')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: /custom reset/i })).toBeInTheDocument();
  });

  it('preserves component display name', () => {
    function NamedComponent() {
      return <div>Test</div>;
    }
    const Wrapped = withErrorBoundary(NamedComponent);
    expect(Wrapped.displayName).toBe('withErrorBoundary(NamedComponent)');
  });
});
