import React from 'react';

export function PageSkeleton() {
  return (
    <div className="page-skeleton" role="status" aria-label="Loading page">
      <div className="page-skeleton__title" />
      <div className="page-skeleton__row" />
      <div className="page-skeleton__row" />
      <div className="page-skeleton__row" />
    </div>
  );
}
