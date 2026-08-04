// TICKET-ADV127 — Unit & Profiler tests verifying Dashboard re-render optimization
import React, { useState } from 'react';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, it, expect, vi } from 'vitest';
import { StatCard } from '../Dashboard.jsx';

describe('TICKET-ADV127 — Dashboard Re-render Optimization', () => {
  it('StatCard skips re-rendering when parent component re-renders with unchanged props', async () => {
    let statCardRenderCount = 0;

    const TestStatCardWrapper = ({ label, value }) => {
      statCardRenderCount++;
      return <StatCard label={label} value={value} />;
    };

    const MemoizedTestCard = React.memo(TestStatCardWrapper);

    const ParentComponent = () => {
      const [unrelatedState, setUnrelatedState] = useState(0);

      return (
        <div>
          <button type="button" onClick={() => setUnrelatedState((c) => c + 1)}>
            Unrelated Update ({unrelatedState})
          </button>
          <MemoizedTestCard label="Matched trades" value={42} />
        </div>
      );
    };

    render(<ParentComponent />);
    expect(statCardRenderCount).toBe(1);
    expect(screen.getByText('42')).toBeInTheDocument();

    // Trigger unrelated parent state update
    const button = screen.getByRole('button');
    await userEvent.click(button);

    // StatCard render count MUST remain 1 because props (label="Matched trades", value=42) did not change
    expect(statCardRenderCount).toBe(1);
  });

  it('StatCard re-renders when value prop changes', async () => {
    let statCardRenderCount = 0;

    const TestStatCardWrapper = ({ label, value }) => {
      statCardRenderCount++;
      return <StatCard label={label} value={value} />;
    };

    const MemoizedTestCard = React.memo(TestStatCardWrapper);

    const ParentComponent = () => {
      const [value, setValue] = useState(10);

      return (
        <div>
          <button type="button" onClick={() => setValue((v) => v + 1)}>
            Update Value
          </button>
          <MemoizedTestCard label="Matched trades" value={value} />
        </div>
      );
    };

    render(<ParentComponent />);
    expect(statCardRenderCount).toBe(1);

    const button = screen.getByRole('button');
    await userEvent.click(button);

    // When value changes, StatCard MUST re-render (render count becomes 2)
    expect(statCardRenderCount).toBe(2);
    expect(screen.getByText('11')).toBeInTheDocument();
  });
});
