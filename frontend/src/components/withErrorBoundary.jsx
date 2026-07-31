// TICKET-ADV113 — withErrorBoundary HOC: wraps a component in an error boundary.
import React from 'react';

export class ErrorBoundary extends React.Component {
  constructor(props) {
    super(props);
    this.state = { hasError: false, error: null };
  }

  static getDerivedStateFromError(error) {
    return { hasError: true, error };
  }

  componentDidCatch(error, info) {
    console.error('ErrorBoundary caught an error:', error, info);
  }

  handleReset = () => {
    this.setState({ hasError: false, error: null });
  };

  render() {
    if (this.state.hasError) {
      const { fallback: Fallback } = this.props;

      if (Fallback) {
        if (typeof Fallback === 'function') {
          return <Fallback error={this.state.error} resetErrorBoundary={this.handleReset} onReset={this.handleReset} />;
        }
        return Fallback;
      }

      return (
        <div className="error-fallback" role="alert">
          <h2>Something went wrong</h2>
          <p>{this.state.error?.message || 'An unexpected error occurred.'}</p>
          <button type="button" onClick={this.handleReset}>
            Try again
          </button>
        </div>
      );
    }

    return this.props.children;
  }
}

export function withErrorBoundary(Component, Fallback) {
  function WithErrorBoundary(props) {
    return (
      <ErrorBoundary fallback={Fallback}>
        <Component {...props} />
      </ErrorBoundary>
    );
  }
  WithErrorBoundary.displayName = `withErrorBoundary(${Component.displayName || Component.name || 'Component'})`;
  return WithErrorBoundary;
}
