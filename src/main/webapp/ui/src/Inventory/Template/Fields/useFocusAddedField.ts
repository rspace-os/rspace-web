import { type RefObject, useEffect, useRef } from "react";

/**
 * Moves focus to the name of a template field once it has been added, as the
 * ELN form editor does, so keyboard users land in the field they asked for.
 * Wrap the field cards in an element with `listRef`, and call `fieldAdding`
 * just before adding the field.
 *
 * A ref, not state: another render of the host remounts its field list, which
 * would drop the focus again.
 */
export default function useFocusAddedField(fieldCount: number): {
  listRef: RefObject<HTMLDivElement | null>;
  fieldAdding: () => void;
} {
  const listRef = useRef<HTMLDivElement>(null);
  const pending = useRef(false);
  useEffect(() => {
    if (!pending.current) return;
    pending.current = false;
    const cards = listRef.current?.querySelectorAll('[data-testid="TemplateField"]');
    cards?.[cards.length - 1]?.querySelector<HTMLElement>('input[name="name"]')?.focus();
  }, [fieldCount]);
  return {
    listRef,
    fieldAdding: () => {
      pending.current = true;
    },
  };
}
