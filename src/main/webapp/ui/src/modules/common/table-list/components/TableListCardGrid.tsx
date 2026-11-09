import type { ReactNode } from "react";
import { TableListCard, type TableListCardItem } from "./TableListCard";

export function TableListCardGrid({
  label,
  items,
  selectionControl,
  busy,
}: {
  label: string;
  items: readonly TableListCardItem[];
  selectionControl?: ReactNode;
  busy?: boolean;
}) {
  return (
    <section aria-label={label} aria-busy={busy} data-slot="table-list-card-view" className="py-3">
      {selectionControl ? <div className="mb-3 flex items-center gap-2 px-1 text-sm">{selectionControl}</div> : null}
      <ul className="grid grid-cols-1 list-none gap-3 p-0 @3xl/table-list:grid-cols-2">
        {items.map((item) => (
          <li key={item.id}>
            <TableListCard item={item} />
          </li>
        ))}
      </ul>
    </section>
  );
}
