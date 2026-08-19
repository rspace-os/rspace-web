import { useId, useState } from "react";
import useDebounce from "@/hooks/ui/useDebounce";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import { useRelationshipOptions } from "@/modules/common/relationship-picker/relationshipOptionQueries";
import type { RelationshipSource } from "@/modules/common/relationship-picker/relationshipSources";

type TargetFieldValueInputProps = {
  source: RelationshipSource;
  ariaLabel: string;
  value: string;
  onChange: (value: string) => void;
  idLinkLabel: (globalId: string) => string;
};

/**
 * The value control for a filter on a field of a relationship's target, such as target.name.
 *
 * <p>A plain text box would leave the user guessing which names exist, and the picker proper is
 * the wrong control here: this filter matches text on the server rather than selecting one row.
 * So the input stays free text, and the target collection supplies suggestions as the user types.
 */
export function TargetFieldValueInput({ source, ariaLabel, value, onChange, idLinkLabel }: TargetFieldValueInputProps) {
  const listId = useId();
  const [term, setTerm] = useState(value);
  const setSuggestionTerm = useDebounce(setTerm, 250);
  const { data: token } = useOauthTokenQuery({ useRestApiV2: true });
  // Suggestions are a convenience, so a refused or failed search must leave the filter usable.
  const { options } = useRelationshipOptions({
    source,
    term,
    token,
    labels: { idLinkLabel },
    enabled: term.trim().length >= 2,
  });

  return (
    <>
      <input
        aria-label={ariaLabel}
        className="h-8 min-w-0 rounded-sm border bg-background px-2 text-xs"
        list={listId}
        type="text"
        value={value}
        onChange={(event) => {
          setSuggestionTerm(event.target.value);
          onChange(event.target.value);
        }}
      />
      <datalist id={listId}>
        {options.map((option) => (
          <option key={option.value} value={option.label} />
        ))}
      </datalist>
    </>
  );
}
