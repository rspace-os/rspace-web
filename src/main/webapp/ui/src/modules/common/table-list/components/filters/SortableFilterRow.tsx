import type { ReactNode } from "react";
import { SortableControlRow } from "../SortableControlRow";

export function SortableFilterRow({ id, moveLabel, children }: { id: number; moveLabel: string; children: ReactNode }) {
  return (
    <SortableControlRow id={id} moveLabel={moveLabel} className="sm:col-span-full sm:grid-cols-subgrid">
      {children}
    </SortableControlRow>
  );
}
