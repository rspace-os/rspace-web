import { Trash2Icon } from "lucide-react";
import type { SortDirection } from "@/modules/common/collection/config";
import { Button } from "@/modules/common/ui/button";
import { SortableControlRow } from "./SortableControlRow";

export function SortRuleRow({
  id,
  label,
  moveLabel,
  direction,
  directionLabel,
  ascendingLabel,
  descendingLabel,
  removeLabel,
  onDirectionChange,
  onRemove,
}: {
  id: string;
  label: string;
  moveLabel: string;
  direction: SortDirection;
  directionLabel: string;
  ascendingLabel: string;
  descendingLabel: string;
  removeLabel: string;
  onDirectionChange: (direction: SortDirection) => void;
  onRemove: () => void;
}) {
  return (
    <SortableControlRow
      id={id}
      moveLabel={moveLabel}
      className="grid-cols-[1.5rem_minmax(8rem,1fr)_minmax(8rem,12rem)_2rem]"
    >
      <span className="text-xs font-medium">{label}</span>
      <select
        aria-label={directionLabel}
        className="h-8 min-w-0 rounded-sm border bg-background px-2 text-xs"
        value={direction}
        onChange={(event) => onDirectionChange(event.target.value as SortDirection)}
      >
        <option value="asc">{ascendingLabel}</option>
        <option value="desc">{descendingLabel}</option>
      </select>
      <Button aria-label={removeLabel} size="icon-xs" variant="ghost" onClick={onRemove}>
        <Trash2Icon aria-hidden="true" />
      </Button>
    </SortableControlRow>
  );
}
