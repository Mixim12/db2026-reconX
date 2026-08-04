// TICKET-ADV114 — Compound <DataTable> with Header / Body / Pagination subcomponents.
import { createContext, useContext, Children, isValidElement } from 'react';

const DataTableContext = createContext({ sort: null, page: 0, size: 20 });

function Header({ columns }) {
  const { sort, onSortChange } = useContext(DataTableContext);
  return (
    <thead className="data-table__header">
      <tr>
        {columns.map((c) => (
          <th key={c.key} scope="col" className="data-table__th">
            <button
              type="button"
              className={`data-table__sort data-table__sort--${sort === c.key ? 'active' : 'idle'}`}
              onClick={() => onSortChange && onSortChange(c.key)}
            >
              {c.label}
            </button>
          </th>
        ))}
      </tr>
    </thead>
  );
}

// `render` returns the whole <tr> (including its key) so rows stay valid table markup.
function Body({ rows, render }) {
  return <tbody className="data-table__body">{rows.map(render)}</tbody>;
}

function Pagination({ page, totalPages, onChange }) {
  return (
    <nav className="data-table__pagination" aria-label="Pagination">
      <button type="button" disabled={page === 0} onClick={() => onChange(page - 1)}>‹</button>
      <span>{page + 1} / {totalPages}</span>
      <button type="button" disabled={page >= totalPages - 1} onClick={() => onChange(page + 1)}>›</button>
    </nav>
  );
}

export default function DataTable({ children, sort, page = 0, size = 20, onSortChange }) {
  // Header/Body must share a single <table> parent, while Pagination is a sibling
  // <nav>. Partitioning here keeps the compound API flat for callers.
  const tableChildren = [];
  const siblings = [];
  Children.forEach(children, (child) => {
    if (isValidElement(child) && (child.type === Header || child.type === Body)) {
      tableChildren.push(child);
    } else if (child) {
      siblings.push(child);
    }
  });

  return (
    <DataTableContext.Provider value={{ sort, page, size, onSortChange }}>
      <div className="data-table">
        <table className="data-table__table">{tableChildren}</table>
        {siblings}
      </div>
    </DataTableContext.Provider>
  );
}

DataTable.Header = Header;
DataTable.Body = Body;
DataTable.Pagination = Pagination;
