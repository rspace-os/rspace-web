import Autocomplete from "@mui/material/Autocomplete";
import FormControl from "@mui/material/FormControl";
import FormControlLabel from "@mui/material/FormControlLabel";
import FormLabel from "@mui/material/FormLabel";
import MenuItem from "@mui/material/MenuItem";
import Radio from "@mui/material/Radio";
import RadioGroup from "@mui/material/RadioGroup";
import Stack from "@mui/material/Stack";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import { type ReactNode, useEffect, useEffectEvent, useId, useState } from "react";
import { useTranslation } from "react-i18next";
import * as v from "valibot";
import {
  CONDITION_OPERATORS,
  type ConditionOperator,
  ConditionOperatorSchema,
  type FieldDisplayRule,
  operatorRequiresValues,
  sameDisplayRule,
} from "@/modules/common/field-rules/displayRule";
import { formatList } from "@/modules/common/i18n/listFormat";
import DisplayRuleSummary from "./DisplayRuleSummary";
import {
  type DraftCondition,
  type RuleEditorField,
  ruleFromDraft,
  ruleGraphFor,
  ruleIsInvalid,
  sanitiseCondition,
} from "./ruleEditing";

type FieldDisplayConditionProps = {
  /** The key of the field this condition belongs to. */
  fieldKey: string;
  /**
   * Every field of the form or template, in display order. Pass the same array
   * to every field's condition, and keep it while only names change: the
   * candidate sources are worked out once per array.
   */
  fields: ReadonlyArray<RuleEditorField>;
  /** Stores a new rule for this field; null means the field is always shown. */
  onChange: (rule: FieldDisplayRule | null) => void;
  /**
   * Called with true when this field's condition can no longer be applied as
   * shown, and with false once it can again, the author chooses "Always", or
   * the component unmounts. Hosts block saving while any field reports true.
   */
  onIncompleteChange?: (incomplete: boolean) => void;
  editable: boolean;
};

function OperatorLabel({ operator }: { operator: ConditionOperator }): string {
  const { t } = useTranslation("common");
  switch (operator) {
    case "IS_ANY_OF":
      return t("fieldEditor.condition.operator.isAnyOf");
    case "IS_NONE_OF":
      return t("fieldEditor.condition.operator.isNoneOf");
    case "HAS_VALUE":
      return t("fieldEditor.condition.operator.hasValue");
    case "HAS_NO_VALUE":
      return t("fieldEditor.condition.operator.hasNoValue");
  }
}

const ALWAYS = "always";
const CONDITIONAL = "conditional";

/**
 * A condition not applied, and whether the shared validator refused it when it
 * was kept. That is decided once, not on every render, so a refused draft keeps
 * saying so until the author touches it, even if later edits elsewhere would
 * now let it apply. It is never applied without the author editing it: one that
 * has become storable is dropped on the way back from "Always" instead.
 */
type Draft = { readonly condition: DraftCondition; readonly rejected: boolean };

/**
 * When one field is shown. Read-only, it is the "Shown when..." summary. In
 * edit mode the author picks "Always" or a condition on another radio or
 * choice field; only fields that would not end up depending on this one are
 * offered; with none, the field menu says so. A condition is applied once it is
 * complete and the shared validator accepts it. Until then it is a draft in
 * this field only, the field keeps the rule it had, the controls show what is
 * missing, and `onIncompleteChange` tells the host to refuse saving. Either
 * mode notes which fields depend on this one.
 *
 * Names are read only for the fields on screen: the chosen source, the
 * dependents, and the candidates while the menu is open. A MobX host can wrap
 * the component in `observer` so that only those names re-render it.
 *
 * Choosing "Always" clears the rule, but the condition is remembered: going
 * back to "Only when…" restores it, and applies it again if it is still valid.
 *
 * A draft lives as long as the component, and hosts refuse to save while one
 * exists. Hosts give the component a new key (for example the record and its
 * edit state) to start a new edit session.
 */
export default function FieldDisplayCondition({
  fieldKey,
  fields,
  onChange,
  onIncompleteChange,
  editable,
}: FieldDisplayConditionProps): ReactNode {
  const { t, i18n } = useTranslation("common");
  const id = useId();
  const [draft, setDraft] = useState<Draft | null>(null);
  /**
   * What "Always" set aside, restored by "Only when…": the rule that was applied, and any
   * unfinished edit on top of it, so arrowing through the radios never loses a stored rule.
   * Used once, and forgotten when a rule is applied, so an abandoned edit never resurfaces.
   */
  const [remembered, setRemembered] = useState<{
    applied: FieldDisplayRule | null;
    draft: DraftCondition | null;
  } | null>(null);
  const [sourceMenuOpen, setSourceMenuOpen] = useState(false);

  const graph = ruleGraphFor(fields);
  const rule = graph.byKey.get(fieldKey)?.rule ?? null;
  const nameOf = (field: RuleEditorField) => (field.name === "" ? t("fieldEditor.card.unnamed") : field.name);
  const isBelow = (other: RuleEditorField) => graph.indexOf(other.key) > graph.indexOf(fieldKey);
  const dependents = graph.dependentsOf(fieldKey);
  const shown = draft?.condition ?? rule;
  // read-only, the rule is only summarised, so the fields it may read are not worked out
  const condition = editable && shown ? sanitiseCondition(graph, fieldKey, shown) : null;
  // a stored rule whose field is gone cannot be saved as shown either
  const incomplete = draft !== null || (condition !== null && ruleFromDraft(condition) === null);
  const currentReporter = useEffectEvent(() => onIncompleteChange);
  // another fieldKey is another field: the old one is cleared, by its own reporter, and the new one reported
  useEffect(() => {
    if (!incomplete) return;
    const report = currentReporter();
    report?.(true);
    return () => report?.(false);
  }, [incomplete, fieldKey]);
  const dependentsNote = dependents.length > 0 && (
    <Typography variant="body2" color="text.secondary">
      {t("fieldEditor.condition.dependents", {
        fields: formatList(dependents.map(nameOf), i18n.resolvedLanguage ?? i18n.language),
      })}
    </Typography>
  );

  if (!editable) {
    const source = rule ? (graph.byKey.get(rule.sourceFieldKey) ?? null) : null;
    if (rule === null && !dependentsNote) return null;
    return (
      <Stack spacing={0.5}>
        <DisplayRuleSummary
          rule={rule}
          sourceName={source ? nameOf(source) : null}
          sourceIsBelow={source !== null && isBelow(source)}
        />
        {dependentsNote}
      </Stack>
    );
  }

  const sources = graph.sourcesFor(fieldKey);
  const source = condition?.sourceFieldKey ? (graph.byKey.get(condition.sourceFieldKey) ?? null) : null;
  const sourceDropped = shown?.sourceFieldKey != null && source === null;
  // complete, but the shared validator refused it: too many options, or too long to store
  const rejected = draft?.rejected ?? false;

  /** Whether the shared validator refuses `condition`, complete, as this field's rule. */
  const isRejected = (condition: DraftCondition) => {
    const candidate = ruleFromDraft(condition);
    return candidate !== null && ruleIsInvalid(fields, fieldKey, candidate);
  };

  /** Applies the condition when it makes a valid rule; keeps it as this field's draft otherwise. */
  const update = (edited: DraftCondition) => {
    // options the chosen source does not offer are dropped, rather than kept invisibly in the draft
    const next = sanitiseCondition(graph, fieldKey, edited);
    const nextRule = ruleFromDraft(next);
    const nextRejected = isRejected(next);
    if (nextRule !== null && !nextRejected) {
      setDraft(null);
      setRemembered(null);
      if (!sameDisplayRule(rule, nextRule)) onChange(nextRule);
      return;
    }
    setDraft({ condition: next, rejected: nextRejected });
  };

  return (
    <FormControl component="fieldset" sx={{ minWidth: 0 }}>
      {/* the legend names the group, so the radio group inside it is left unnamed rather than announced twice */}
      <FormLabel component="legend">{t("fieldEditor.condition.title")}</FormLabel>
      <RadioGroup
        row
        value={condition ? CONDITIONAL : ALWAYS}
        onChange={({ target: { value } }) => {
          if (value === ALWAYS) {
            setRemembered({ applied: rule, draft: draft?.condition ?? null });
            setDraft(null);
            if (rule !== null) onChange(null);
            return;
          }
          const applied = remembered?.applied ?? null;
          setRemembered(null);
          if (applied !== null && !ruleIsInvalid(fields, fieldKey, applied)) {
            onChange(applied);
            /*
             * the stored rule comes back as it was. An unfinished edit stays pending on top only while
             * it still cannot be applied: the author only ever saw it refused, so one that changes
             * elsewhere made storable is dropped rather than applied, and does not block saving
             */
            const pending = remembered?.draft ? sanitiseCondition(graph, fieldKey, remembered.draft) : null;
            if (pending?.sourceFieldKey == null || (ruleFromDraft(pending) !== null && !isRejected(pending))) {
              setDraft(null);
              return;
            }
            setDraft({ condition: pending, rejected: isRejected(pending) });
            return;
          }
          // a saved rule that no longer validates as it was still comes back with whatever of it still applies
          const fallback = remembered?.draft ?? remembered?.applied ?? null;
          const restored = fallback ? sanitiseCondition(graph, fieldKey, fallback) : null;
          update(
            restored?.sourceFieldKey != null
              ? restored
              : { sourceFieldKey: sources[0]?.key ?? null, operator: "IS_ANY_OF", values: [] },
          );
        }}
      >
        <FormControlLabel value={ALWAYS} control={<Radio />} label={t("fieldEditor.condition.always")} />
        <FormControlLabel value={CONDITIONAL} control={<Radio />} label={t("fieldEditor.condition.conditional")} />
      </RadioGroup>
      {condition && (
        // wraps by the card's own width, which in a side panel is much narrower than the window
        <Stack direction="row" spacing={1.5} useFlexGap sx={{ mt: 1, alignItems: "flex-start", flexWrap: "wrap" }}>
          <TextField
            select
            size="small"
            id={`${id}-source`}
            label={t("fieldEditor.condition.sourceLabel")}
            value={source?.key ?? ""}
            error={source === null}
            helperText={
              source === null
                ? // MenuList skips a disabled item, so the helper text is what says there is nothing to choose
                  sources.length === 0
                  ? t("fieldEditor.condition.noOtherFields")
                  : sourceDropped
                    ? t("fieldEditor.condition.sourceMissing")
                    : t("fieldEditor.condition.sourceRequired")
                : isBelow(source)
                  ? t("fieldEditor.summary.sourceBelowHint", { source: nameOf(source) })
                  : undefined
            }
            onChange={({ target: { value } }) => update({ ...condition, sourceFieldKey: value })}
            slotProps={{
              select: {
                open: sourceMenuOpen,
                onOpen: () => setSourceMenuOpen(true),
                onClose: () => setSourceMenuOpen(false),
              },
            }}
            sx={{ flex: "1 1 200px" }}
          >
            {/* closed, only the chosen field is listed, so renaming another field does not re-render this one */}
            {(sourceMenuOpen ? sources : source ? [source] : []).map((candidate) => (
              <MenuItem key={candidate.key} value={candidate.key}>
                {nameOf(candidate)}
              </MenuItem>
            ))}
            {/* no value, so the select does not take it for the chosen field */}
            {sourceMenuOpen && sources.length === 0 && (
              <MenuItem disabled>{t("fieldEditor.condition.noOtherFields")}</MenuItem>
            )}
          </TextField>
          <TextField
            select
            size="small"
            id={`${id}-operator`}
            label={t("fieldEditor.condition.operatorLabel")}
            value={condition.operator}
            onChange={({ target: { value } }) =>
              update({ ...condition, operator: v.parse(ConditionOperatorSchema, value) })
            }
            sx={{ flex: "1 1 160px" }}
          >
            {CONDITION_OPERATORS.map((operator) => (
              <MenuItem key={operator} value={operator}>
                <OperatorLabel operator={operator} />
              </MenuItem>
            ))}
          </TextField>
          {operatorRequiresValues(condition.operator) && (
            <Autocomplete
              multiple
              size="small"
              id={`${id}-values`}
              options={[...(source?.options ?? [])]}
              value={[...condition.values]}
              onChange={(_event, values) => update({ ...condition, values })}
              renderInput={(params) => (
                <TextField
                  {...params}
                  label={t("fieldEditor.condition.valuesLabel")}
                  error={condition.values.length === 0 || rejected}
                  helperText={
                    condition.values.length === 0
                      ? t("fieldEditor.condition.valuesRequired")
                      : rejected
                        ? t("fieldEditor.condition.valuesRejected")
                        : undefined
                  }
                />
              )}
              sx={{ flex: "2 1 200px" }}
            />
          )}
        </Stack>
      )}
      {dependentsNote && <Stack sx={{ mt: 1 }}>{dependentsNote}</Stack>}
    </FormControl>
  );
}
