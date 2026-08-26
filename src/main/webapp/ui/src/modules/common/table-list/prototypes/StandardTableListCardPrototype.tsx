// PROTOTYPE — shared card anatomy for a future TableList card view.
import type { ReactNode } from "react";

export type StandardTableListCardField = {
  id: string;
  label: ReactNode;
  value: ReactNode;
  fullWidth?: true;
};

export type StandardTableListCardItem = {
  id: string;
  accessibleName: string;
  title: ReactNode;
  fields: readonly StandardTableListCardField[];
  selection?: ReactNode;
  actions?: ReactNode;
};

export function StandardTableListCard({ item }: { item: StandardTableListCardItem }) {
  return (
    <article aria-label={item.accessibleName} className="overflow-hidden rounded-sm border bg-card shadow-xs">
      <header className="flex min-w-0 items-start gap-3 border-b p-4">
        {item.selection ? <div className="shrink-0 pt-1">{item.selection}</div> : null}
        <div className="min-w-0 flex-1">{item.title}</div>
      </header>
      {item.fields.length > 0 ? (
        <dl className="divide-y px-4">
          {item.fields.map((field) => (
            <div
              key={field.id}
              className={
                field.fullWidth ? "grid gap-2 py-3 text-sm" : "grid grid-cols-[6rem_minmax(0,1fr)] gap-3 py-3 text-sm"
              }
            >
              <dt className="font-medium text-muted-foreground">{field.label}</dt>
              <dd className="min-w-0">{field.value}</dd>
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

export function StandardTableListCardGrid({
  label,
  items,
}: {
  label: string;
  items: readonly StandardTableListCardItem[];
}) {
  return (
    <ul className="grid list-none gap-3 p-0 sm:grid-cols-2 xl:grid-cols-3" aria-label={label}>
      {items.map((item) => (
        <li key={item.id}>
          <StandardTableListCard item={item} />
        </li>
      ))}
    </ul>
  );
}
