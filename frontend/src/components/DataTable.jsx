// TICKET-ADV114 — Compound <DataTable> with Header / Body / Pagination subcomponents.
import { createContext, useContext, Children, isValidElement } from 'react';

const DataTableContext = createContext({ sort: null, page: 0, size: 20, colCount: 1 });

function Header({ columns }) {
  const { sort, onSortChange } = useContext(DataTableContext);
  return (
    <thead className="data-table__header">
      <tr>
        {columns.map((c) => {
          const isActive = sort?.field === c.key;
          const sortClass = isActive ? 'active' : 'idle';
          return (
            <th key={c.key} scope="col" className="data-table__th">
              <button
                type="button"
                className={`data-table__sort data-table__sort--${sortClass}`}
                onClick={() => {
                  if (onSortChange) {
                    const newDir = isActive && sort?.dir === 'asc' ? 'desc' : 'asc';
                    onSortChange({ field: c.key, dir: newDir });
                  }
                }}
              >
                {c.label} {isActive ? (sort.dir === 'asc' ? '↑' : '↓') : ''}
              </button>
            </th>
          );
        })}
      </tr>
    </thead>
  );
}
Header.isTablePart = true;

// `render` returns the whole <tr> (including its key) so rows stay valid table markup.
function Body({ rows, render }) {
  const { colCount } = useContext(DataTableContext);
  if (!rows || rows.length === 0) {
    return (
      <tbody className="data-table__body">
        <tr>
          <td colSpan={colCount} className="data-table__empty">No records found</td>
        </tr>
      </tbody>
    );
  }
  return <tbody className="data-table__body">{rows.map(render)}</tbody>;
}
Body.isTablePart = true;

function Pagination({ page, totalPages, onChange }) {
  if (totalPages === 0) return null;
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
  let colCount = 1;

  Children.forEach(children, (child) => {
    if (isValidElement(child)) {
      if (child.props && child.props.columns) {
        colCount = child.props.columns.length;
      }
      
      if (child.type === Header || child.type === Body || child.type?.isTablePart) {
        tableChildren.push(child);
      } else {
        siblings.push(child);
      }
    } else if (child) {
      siblings.push(child);
    }
  });

  return (
    <DataTableContext.Provider value={{ sort, page, size, onSortChange, colCount }}>
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
