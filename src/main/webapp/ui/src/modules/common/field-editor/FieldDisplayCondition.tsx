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
  type ConditionOperator,
  ConditionOperatorSchema,
  type FieldDisplayRule,
  OPERATORS_BY_SOURCE_KIND,
  operatorComparesValue,
  operatorRequiresValues,
  type SourceKind,
  sameDisplayRule,
  sourceKindOf,
} from "@/modules/common/field-rules/displayRule";
import { trimRuleValue } from "@/modules/common/field-rules/ruleValues";
import { formatList } from "@/modules/common/i18n/listFormat";
import DisplayRuleSummary from "./DisplayRuleSummary";
import {
  comparisonValueProblem,
  type DraftCondition,
  type RuleEditorField,
  type RuleGraph,
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
   * Told when the condition starts or stops showing names of fields it could
   * read besides its rule's source: while it has a draft, or its field menu is
   * open. A host that re-renders a condition only when names it shows change
   * then knows when to include those names.
   */
  onShowsSourcesChange?: (fieldKey: string, shows: boolean) => void;
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
    case "EQUALS":
      return t("fieldEditor.condition.operator.equals");
    case "NOT_EQUALS":
      return t("fieldEditor.condition.operator.notEquals");
    case "CONTAINS":
      return t("fieldEditor.condition.operator.contains");
    case "LESS_THAN":
      return t("fieldEditor.condition.operator.lessThan");
    case "AT_MOST":
      return t("fieldEditor.condition.operator.atMost");
    case "GREATER_THAN":
      return t("fieldEditor.condition.operator.greaterThan");
    case "AT_LEAST":
      return t("fieldEditor.condition.operator.atLeast");
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
type Draft = {
  readonly condition: DraftCondition;
  readonly rejected: boolean;
  /** The kind of field it reads, as when it was kept; null with no field to read. */
  readonly sourceKind: SourceKind | null;
};

/**
 * Whether the field a condition reads is still there but now offers another
 * kind than `sourceKind`. Hosts clear the rules that read a field when it
 * changes kind, so a condition written for the old kind is dropped with them.
 */
function sourceRetyped(graph: RuleGraph, sourceFieldKey: string | null, sourceKind: SourceKind | null): boolean {
  const source = sourceFieldKey === null ? undefined : graph.byKey.get(sourceFieldKey);
  return sourceKind !== null && source !== undefined && sourceKindOf(source) !== sourceKind;
}

/**
 * When one field is shown. Read-only, it is the "Shown when..." summary. In
 * edit mode the author picks "Always" or a condition on another field that a
 * rule can read: options, one-line text or a number, each with its own
 * conditions and value control. Only fields that would not end up depending
 * on this one are offered; with none, the field menu says so. A condition is applied once it is
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
 * back to "Only when…" restores it, and applies it again if it is still valid,
 * unless the field it read has changed kind in the meantime, even if it has changed back.
 *
 * A draft lives as long as the component, or until the field it reads changes
 * kind, and hosts refuse to save while one exists. Hosts give the component a
 * new key (for example the record and its edit state) to start a new edit
 * session.
 */
export default function FieldDisplayCondition({
  fieldKey,
  fields,
  onChange,
  onShowsSourcesChange,
  onIncompleteChange,
  editable,
}: FieldDisplayConditionProps): ReactNode {
  const { t, i18n } = useTranslation("common");
  const id = useId();
  const [keptDraft, setDraft] = useState<Draft | null>(null);
  /**
   * What "Always" set aside, restored by "Only when…": the rule that was applied, with the kind
   * of field it read, and any unfinished edit on top of it, so arrowing through the radios never
   * loses a stored rule. Used once, and forgotten when a rule is applied, so an abandoned edit
   * never resurfaces; either is forgotten once its field changes kind, even if it changes back.
   */
  const [remembered, setRemembered] = useState<{
    applied: FieldDisplayRule | null;
    appliedKind: SourceKind | null;
    draft: Draft | null;
  } | null>(null);
  const [sourceMenuOpen, setSourceMenuOpen] = useState(false);
  /**
   * The comparison value as typed. The applied rule holds it trimmed, so the
   * box shows this while it trims to the condition's value, and a space typed
   * between words is not lost.
   */
  const [typedValue, setTypedValue] = useState<string | null>(null);

  const graph = ruleGraphFor(fields);
  const rule = graph.byKey.get(fieldKey)?.rule ?? null;
  const kindOf = (key: string | null) => {
    const field = key === null ? undefined : graph.byKey.get(key);
    return field ? sourceKindOf(field) : null;
  };
  /*
   * the field it reads has since changed kind, as the author confirmed, so the edit goes rather than blocking the
   * save. The confirmation counts only stored rules, so a draft that reads a different field from this field's rule
   * goes unannounced; accepted, since a draft is never saved and the author has just retyped the field it reads.
   */
  const draftRetyped =
    keptDraft !== null && sourceRetyped(graph, keptDraft.condition.sourceFieldKey, keptDraft.sourceKind);
  if (draftRetyped) setDraft(null);
  const draft = draftRetyped ? null : keptDraft;
  const showsSources = editable && (draft !== null || sourceMenuOpen);
  useEffect(() => {
    if (!showsSources || !onShowsSourcesChange) return;
    onShowsSourcesChange(fieldKey, true);
    return () => onShowsSourcesChange(fieldKey, false);
  }, [showsSources, onShowsSourcesChange, fieldKey]);
  // likewise, what "Always" set aside is forgotten as soon as its field changes kind, so changing it back does not restore it
  const rememberedAppliedRetyped =
    remembered?.applied != null && sourceRetyped(graph, remembered.applied.sourceFieldKey, remembered.appliedKind);
  const rememberedDraftRetyped =
    remembered?.draft != null &&
    sourceRetyped(graph, remembered.draft.condition.sourceFieldKey, remembered.draft.sourceKind);
  if (remembered && (rememberedAppliedRetyped || rememberedDraftRetyped))
    setRemembered({
      applied: rememberedAppliedRetyped ? null : remembered.applied,
      appliedKind: rememberedAppliedRetyped ? null : remembered.appliedKind,
      draft: rememberedDraftRetyped ? null : remembered.draft,
    });
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
          sourceKind={source ? sourceKindOf(source) : undefined}
          sourceIsBelow={source !== null && isBelow(source)}
        />
        {dependentsNote}
      </Stack>
    );
  }

  const sources = graph.sourcesFor(fieldKey);
  const source = condition?.sourceFieldKey ? (graph.byKey.get(condition.sourceFieldKey) ?? null) : null;
  const sourceDropped = shown?.sourceFieldKey != null && source === null;
  // complete, but the shared validator refused it: too many options, not a number, or too long to store
  const rejected = draft?.rejected ?? false;
  const sourceKind = source ? sourceKindOf(source) : null;
  // with no source chosen, only the current condition is listed, since which ones apply depends on the field
  const operators = sourceKind ? OPERATORS_BY_SOURCE_KIND[sourceKind] : condition ? [condition.operator] : [];
  const valueProblem = condition && sourceKind ? comparisonValueProblem(sourceKind, condition) : null;
  const conditionValue = condition?.values[0] ?? "";
  const shownValue =
    typedValue !== null && trimRuleValue(typedValue) === trimRuleValue(conditionValue) ? typedValue : conditionValue;

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
      if (!sameDisplayRule(rule, nextRule)) onChange(nextRule);
      return;
    }
    setDraft({ condition: next, rejected: nextRejected, sourceKind: kindOf(next.sourceFieldKey) });
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
            setRemembered({ applied: rule, appliedKind: kindOf(rule?.sourceFieldKey ?? null), draft });
            setDraft(null);
            if (rule !== null) onChange(null);
            return;
          }
          const applied = remembered?.applied ?? null;
          const unfinished = remembered?.draft?.condition ?? null;
          setRemembered(null);
          if (applied !== null && !ruleIsInvalid(fields, fieldKey, applied)) {
            onChange(applied);
            /*
             * the stored rule comes back as it was. An unfinished edit stays pending on top only while
             * it still cannot be applied: the author only ever saw it refused, so one that changes
             * elsewhere made storable is dropped rather than applied, and does not block saving
             */
            const pending = unfinished ? sanitiseCondition(graph, fieldKey, unfinished) : null;
            if (pending?.sourceFieldKey == null || (ruleFromDraft(pending) !== null && !isRejected(pending))) {
              setDraft(null);
              return;
            }
            setDraft({ condition: pending, rejected: isRejected(pending), sourceKind: kindOf(pending.sourceFieldKey) });
            return;
          }
          /*
           * with no valid stored rule, an unfinished edit comes back only while it still cannot be
           * applied, as above; otherwise a saved rule that no longer validates as it was comes back
           * with whatever of it still applies
           */
          const pending = unfinished ? sanitiseCondition(graph, fieldKey, unfinished) : null;
          const stillRefused =
            pending?.sourceFieldKey != null && (ruleFromDraft(pending) === null || isRejected(pending));
          const restored = stillRefused ? pending : applied ? sanitiseCondition(graph, fieldKey, applied) : null;
          update(
            restored?.sourceFieldKey != null
              ? restored
              : {
                  sourceFieldKey: sources[0]?.key ?? null,
                  operator: OPERATORS_BY_SOURCE_KIND[sources[0] ? sourceKindOf(sources[0]) : "OPTIONS"][0],
                  values: [],
                },
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
            onChange={({ target: { value } }) => {
              const chosen = graph.byKey.get(value);
              const kind = chosen ? sourceKindOf(chosen) : null;
              // another kind of field takes other conditions, so the condition starts again
              if (kind !== null && kind !== sourceKind) {
                setTypedValue(null);
                update({ sourceFieldKey: value, operator: OPERATORS_BY_SOURCE_KIND[kind][0], values: [] });
                return;
              }
              update({ ...condition, sourceFieldKey: value });
            }}
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
            {operators.map((operator) => (
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
          {operatorComparesValue(condition.operator) && (
            // plain text with no inputMode: a decimal keypad would have no minus key
            <TextField
              size="small"
              type="text"
              id={`${id}-value`}
              label={t("fieldEditor.condition.valueLabel")}
              value={shownValue}
              onChange={({ target: { value } }) => {
                setTypedValue(value);
                update({ ...condition, values: [value] });
              }}
              error={valueProblem !== null || rejected}
              helperText={
                valueProblem === "blank"
                  ? t("fieldEditor.condition.valueRequired")
                  : valueProblem === "notANumber"
                    ? t("fieldEditor.condition.numberRequired")
                    : rejected
                      ? t("fieldEditor.condition.valueTooLong")
                      : undefined
              }
              sx={{ flex: "2 1 200px" }}
            />
          )}
        </Stack>
      )}
      {dependentsNote && <Stack sx={{ mt: 1 }}>{dependentsNote}</Stack>}
    </FormControl>
  );
}
