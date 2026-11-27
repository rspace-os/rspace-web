import { useDebouncer } from "@tanstack/react-pacer";
import { SearchIcon, XIcon } from "lucide-react";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Input } from "@/modules/common/ui/input";

/** Render with a new `key` to discard the draft, for example after a reset. */
export function CollectionSearchInput({
  value,
  collectionLabel,
  debounceMs,
  onCommit,
}: {
  value: string;
  collectionLabel: string;
  debounceMs: number;
  onCommit: (value: string) => void;
}) {
  const { t } = useTranslation("common");
  // A draft belongs to the value it was typed over; any change of that value drops it, even back to an earlier value.
  const [pending, setPending] = useState<{ text: string; base: string } | null>(null);
  const current = pending?.base === value ? pending : null;
  if (pending !== current) setPending(current);
  const draft = current?.text ?? value;

  // Pacer runs the latest render's function, so a scheduled commit only commits a draft that is still current.
  const debouncer = useDebouncer(
    () => {
      if (current && current.text !== value) onCommit(current.text);
    },
    { wait: debounceMs },
  );

  const commitNow = (next: string) => {
    debouncer.cancel();
    if (next !== value) onCommit(next);
  };

  const change = (next: string) => {
    setPending({ text: next, base: value });
    if (debounceMs === 0) commitNow(next);
    else debouncer.maybeExecute();
  };

  return (
    <div className="relative min-w-56 flex-1">
      <SearchIcon
        aria-hidden="true"
        className="absolute top-1/2 left-3 size-3.5 -translate-y-1/2 text-muted-foreground"
      />
      <Input
        aria-label={t("tableList.search.label", { collection: collectionLabel })}
        className="h-8 bg-background pr-9 pl-9"
        placeholder={t("tableList.search.label", { collection: collectionLabel })}
        value={draft}
        onChange={(event) => change(event.target.value)}
        onKeyDown={(event) => {
          if (event.key !== "Enter") return;
          event.preventDefault();
          commitNow(draft);
        }}
      />
      {draft ? (
        <button
          type="button"
          aria-label={t("tableList.search.clear")}
          className="absolute top-1/2 right-3 -translate-y-1/2 rounded-sm text-muted-foreground hover:text-foreground focus-visible:ring-2 focus-visible:ring-ring"
          onClick={() => {
            setPending(null);
            commitNow("");
          }}
        >
          <XIcon aria-hidden="true" />
        </button>
      ) : null}
    </div>
  );
}
