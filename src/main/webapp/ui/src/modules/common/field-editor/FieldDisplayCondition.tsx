import Alert from "@mui/material/Alert";
import Autocomplete from "@mui/material/Autocomplete";
import FormControl from "@mui/material/FormControl";
import FormControlLabel from "@mui/material/FormControlLabel";
import FormHelperText from "@mui/material/FormHelperText";
import FormLabel from "@mui/material/FormLabel";
import MenuItem from "@mui/material/MenuItem";
import Radio from "@mui/material/Radio";
import RadioGroup from "@mui/material/RadioGroup";
import Stack from "@mui/material/Stack";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import { type ReactNode, useId, useState } from "react";
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
 * When one field is shown. Read-only, it is the "Shown when..." summary. In
 * edit mode the author picks "Always" or a condition on another radio or
 * choice field; only fields that would not end up depending on this one are
 * offered. A condition is applied once it is complete and the shared validator
 * accepts it. Until then it is a draft in this field only, and the field keeps
 * the rule it had. Either mode notes which fields depend on this one.
 *
 * Names are read only for the fields on screen: the chosen source, the
 * dependents, and the candidates while the menu is open. A MobX host can wrap
 * the component in `observer` so that only those names re-render it.
 *
 * Choosing "Always" clears the rule, but the condition is remembered: going
 * back to "Only when…" restores it, and applies it again if it is still valid.
 *
 * A draft lives as long as the component, so it survives a spell of
 * read-only, such as a save that fails. Hosts give the component a new key
 * (for example the record and its edit state) to start a new edit session.
 */
export default function FieldDisplayCondition({
  fieldKey,
  fields,
  onChange,
  editable,
}: FieldDisplayConditionProps): ReactNode {
  const { t, i18n } = useTranslation("common");
  const id = useId();
  const [draft, setDraft] = useState<DraftCondition | null>(null);
  /** The condition shown when "Always" was last chosen, restored by "Only when…". */
  const [remembered, setRemembered] = useState<DraftCondition | null>(null);
  const [sourceMenuOpen, setSourceMenuOpen] = useState(false);

  const graph = ruleGraphFor(fields);
  const rule = graph.byKey.get(fieldKey)?.rule ?? null;
  const nameOf = (field: RuleEditorField) => (field.name === "" ? t("fieldEditor.card.unnamed") : field.name);
  const isBelow = (other: RuleEditorField) => graph.indexOf(other.key) > graph.indexOf(fieldKey);
  const dependents = graph.dependentsOf(fieldKey);
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
  if (draft !== null && rule === null && sources.length === 0) {
    // with nothing left to read, a new condition can never be completed, so it is set aside like "Always" sets it aside
    setRemembered(draft);
    setDraft(null);
  }
  const shown = draft ?? rule;
  const condition = shown ? sanitiseCondition(graph, fieldKey, shown) : null;
  const source = condition?.sourceFieldKey ? (graph.byKey.get(condition.sourceFieldKey) ?? null) : null;
  const sourceDropped = shown?.sourceFieldKey != null && source === null;

  /** Applies the condition when it makes a valid rule; keeps it as this field's draft otherwise. */
  const update = (edited: DraftCondition) => {
    // options the chosen source does not offer are dropped, rather than kept invisibly in the draft
    const next = sanitiseCondition(graph, fieldKey, edited);
    const nextRule = ruleFromDraft(next);
    if (nextRule !== null && !ruleIsInvalid(fields, fieldKey, nextRule)) {
      setDraft(null);
      if (!sameDisplayRule(rule, nextRule)) onChange(nextRule);
      return;
    }
    setDraft(next);
  };

  const noSourcesId = `${id}-no-sources`;
  const complete = condition !== null && ruleFromDraft(condition) !== null;
  const draftMessage = rule
    ? complete
      ? t("fieldEditor.condition.invalidEdit")
      : t("fieldEditor.condition.incompleteEdit")
    : complete
      ? t("fieldEditor.condition.invalid")
      : t("fieldEditor.condition.incomplete");

  return (
    <FormControl component="fieldset" sx={{ minWidth: 0 }}>
      {/* the legend names the group, so the radio group inside it is left unnamed rather than announced twice */}
      <FormLabel component="legend">{t("fieldEditor.condition.title")}</FormLabel>
      <RadioGroup
        row
        value={condition ? CONDITIONAL : ALWAYS}
        onChange={({ target: { value } }) => {
          if (value === ALWAYS) {
            setRemembered(condition);
            setDraft(null);
            if (rule !== null) onChange(null);
            return;
          }
          const restored = remembered ? sanitiseCondition(graph, fieldKey, remembered) : null;
          update(
            restored?.sourceFieldKey != null
              ? restored
              : { sourceFieldKey: sources[0]?.key ?? null, operator: "IS_ANY_OF", values: [] },
          );
        }}
      >
        <FormControlLabel value={ALWAYS} control={<Radio />} label={t("fieldEditor.condition.always")} />
        <FormControlLabel
          value={CONDITIONAL}
          control={
            <Radio slotProps={{ input: { "aria-describedby": sources.length === 0 ? noSourcesId : undefined } }} />
          }
          label={t("fieldEditor.condition.conditional")}
          disabled={sources.length === 0 && condition === null}
        />
      </RadioGroup>
      {sources.length === 0 && (
        <FormHelperText id={noSourcesId} sx={{ mx: 0 }}>
          {t("fieldEditor.condition.noSources")}
        </FormHelperText>
      )}
      {condition && (
        <Stack spacing={1.5} sx={{ mt: 1 }}>
          {/* wraps by the card's own width, which in a side panel is much narrower than the window */}
          <Stack direction="row" spacing={1.5} useFlexGap sx={{ alignItems: "flex-start", flexWrap: "wrap" }}>
            <TextField
              select
              size="small"
              id={`${id}-source`}
              label={t("fieldEditor.condition.sourceLabel")}
              value={source?.key ?? ""}
              error={sourceDropped}
              helperText={
                sourceDropped
                  ? t("fieldEditor.condition.sourceMissing")
                  : source && isBelow(source)
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
                    error={condition.values.length === 0}
                    helperText={condition.values.length === 0 ? t("fieldEditor.condition.valuesRequired") : undefined}
                  />
                )}
                sx={{ flex: "2 1 200px" }}
              />
            )}
          </Stack>
          {draft !== null && (
            <Alert severity="warning" variant="outlined">
              {draftMessage}
            </Alert>
          )}
        </Stack>
      )}
      {dependentsNote && <Stack sx={{ mt: 1 }}>{dependentsNote}</Stack>}
    </FormControl>
  );
}
