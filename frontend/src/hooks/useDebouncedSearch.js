// TICKET-ADV117 — useDebouncedSearch(query, delay) custom hook
import { useState, useEffect } from 'react';

export function useDebouncedSearch(query, delay = 300) {
  const [debouncedValue, setDebouncedValue] = useState(query);

  useEffect(() => {
    const handler = setTimeout(() => {
      setDebouncedValue(query);
    }, delay);

    return () => {
      clearTimeout(handler);
    };
  }, [query, delay]);

  return debouncedValue;
}
