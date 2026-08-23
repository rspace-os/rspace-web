import { type ReactNode, useId } from "react";
import { cn } from "@/modules/common/utils/cn";

export type TableListCardField = {
  id: string;
  label: ReactNode;
  value: ReactNode;
  fullWidth?: boolean;
};

export type TableListCardItem = {
  id: string;
  title: ReactNode;
  fields: readonly TableListCardField[];
  selection?: ReactNode;
  actions?: ReactNode;
  selected?: boolean;
};

function TableListCard({ item }: { item: TableListCardItem }) {
  const titleId = useId();

  return (
    <article
      aria-labelledby={titleId}
      data-slot="table-list-card"
      data-state={item.selected ? "selected" : undefined}
      className="h-full overflow-hidden rounded-sm border bg-card shadow-xs data-[state=selected]:border-primary/40 data-[state=selected]:bg-primary/5"
    >
      <header className="flex min-w-0 items-start gap-3 border-b p-4">
        {item.selection ? <div className="shrink-0 pt-1">{item.selection}</div> : null}
        <div id={titleId} className="min-w-0 flex-1 font-medium">
          {item.title}
        </div>
      </header>
      {item.fields.length > 0 ? (
        <dl className="grid grid-cols-2 gap-x-5 gap-y-4 p-4">
          {item.fields.map((field) => (
            <div key={field.id} className={cn("min-w-0 text-sm", field.fullWidth && "col-span-full")}>
              <dt className="mb-1 text-xs font-medium text-muted-foreground">{field.label}</dt>
              <dd className="min-w-0 break-words">{field.value}</dd>
            </div>
          ))}
        </dl>
      ) : null}
      {item.actions ? (
        <footer className="flex flex-wrap justify-end gap-2 border-t bg-muted/25 p-3">{item.actions}</footer>
      ) : null}
    </article>
  );
}

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
      <ul className="grid list-none gap-3 p-0 @3xl/table-list:grid-cols-2">
        {items.map((item) => (
          <li key={item.id}>
            <TableListCard item={item} />
          </li>
        ))}
      </ul>
    </section>
  );
}
