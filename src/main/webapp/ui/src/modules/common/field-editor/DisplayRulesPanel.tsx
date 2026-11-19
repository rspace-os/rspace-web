import AddIcon from "@mui/icons-material/Add";
import DeleteOutlinedIcon from "@mui/icons-material/DeleteOutlined";
import Alert from "@mui/material/Alert";
import Autocomplete from "@mui/material/Autocomplete";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import IconButton from "@mui/material/IconButton";
import List from "@mui/material/List";
import ListItem from "@mui/material/ListItem";
import ListItemText from "@mui/material/ListItemText";
import MenuItem from "@mui/material/MenuItem";
import Paper from "@mui/material/Paper";
import Stack from "@mui/material/Stack";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import { partition, sortBy } from "es-toolkit";
import { type ReactNode, useId, useLayoutEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import * as v from "valibot";
import {
  CONDITION_OPERATORS,
  type ConditionOperator,
  ConditionOperatorSchema,
  type FieldDisplayRule,
  operatorRequiresValues,
} from "@/modules/common/field-rules/displayRule";
import { formatList } from "@/modules/common/i18n/listFormat";
import {
  changesAreInvalid,
  type DraftCondition,
  groupDisplayRules,
  type RuleChange,
  type RuleEditorField,
  ruleFromDraft,
  sameRule,
  sanitiseDraft,
  sourceCandidates,
  targetCandidates,
} from "./ruleEditing";
import { useDescribeRule } from "./useDescribeRule";

type DisplayRulesPanelProps = {
  /** Every field of the form or template that can take part in a rule, in display order. */
  fields: ReadonlyArray<RuleEditorField>;
  /** Applies new rules to fields; all changes of one edit arrive in one call. */
  onChange: (changes: ReadonlyArray<RuleChange>) => void;
  editable: boolean;
};

/**
 * A rule being edited that cannot be stored yet: its condition is incomplete,
 * it shows no field, or the validator rejects it. A draft that edits a stored
 * rule leaves that rule on its fields until the edit can be applied.
 */
type Draft = DraftCondition & {
  id: string;
  targetKeys: ReadonlyArray<string>;
  /** The fields of the stored rule this draft edits; empty for a new rule. */
  replaces: ReadonlyArray<string>;
};

type Row = {
  id: string;
  condition: DraftCondition;
  targetKeys: ReadonlyArray<string>;
  /** The rule on the fields this row covers, which stays until an edit applies. */
  stored: { rule: FieldDisplayRule; targetKeys: ReadonlyArray<string> } | null;
  isDraft: boolean;
  /** A draft whose source can no longer be read, so the author must pick another. */
  sourceDropped: boolean;
};

function OperatorLabel({ operator }: { operator: ConditionOperator }): string {
  const { t } = useTranslation("common");
  switch (operator) {
    case "IS_ANY_OF":
      return t("fieldEditor.rules.operator.isAnyOf");
    case "IS_NONE_OF":
      return t("fieldEditor.rules.operator.isNoneOf");
    case "HAS_VALUE":
      return t("fieldEditor.rules.operator.hasValue");
    case "HAS_NO_VALUE":
      return t("fieldEditor.rules.operator.hasNoValue");
  }
}

type RuleCardProps = {
  row: Row;
  position: number;
  fields: ReadonlyArray<RuleEditorField>;
  claimedKeys: ReadonlySet<string>;
  onConditionChange: (condition: DraftCondition) => void;
  onTargetsChange: (targetKeys: ReadonlyArray<string>) => void;
  onDelete: () => void;
};

function RuleCard({
  row,
  position,
  fields,
  claimedKeys,
  onConditionChange,
  onTargetsChange,
  onDelete,
}: RuleCardProps): ReactNode {
  const { t } = useTranslation("common");
  const { heading } = useDescribeRule();
  const id = useId();
  const { condition, targetKeys, stored } = row;
  const source = fields.find((field) => field.key === condition.sourceFieldKey) ?? null;
  const sources = sourceCandidates(fields, targetKeys);
  const ownKeys = [...targetKeys, ...(stored?.targetKeys ?? [])];
  const targets = targetCandidates(fields, condition.sourceFieldKey, ownKeys, claimedKeys);
  const sourceMissing = row.sourceDropped || (condition.sourceFieldKey !== null && source === null);
  const needsValues = operatorRequiresValues(condition.operator);
  const complete = ruleFromDraft(condition) !== null && targetKeys.length > 0;
  const storedSource = stored ? fields.find((field) => field.key === stored.rule.sourceFieldKey) : undefined;
  const title = stored
    ? heading(stored.rule, storedSource?.name ?? null)
    : t("fieldEditor.rules.newRuleHeading", { position: position + 1 });

  return (
    <Paper variant="outlined" sx={{ p: 2 }} component="li" aria-labelledby={`${id}-heading`}>
      <Stack spacing={2}>
        <Stack direction="row" spacing={1} sx={{ justifyContent: "space-between", alignItems: "flex-start" }}>
          <Typography variant="subtitle2" component="h4" id={`${id}-heading`} sx={{ overflowWrap: "anywhere" }}>
            {title}
          </Typography>
          <IconButton
            size="small"
            onClick={onDelete}
            aria-label={t("fieldEditor.rules.deleteRule", { rule: title })}
            title={t("fieldEditor.rules.deleteRule", { rule: title })}
          >
            <DeleteOutlinedIcon fontSize="small" />
          </IconButton>
        </Stack>
        {/* wraps by the card's own width, which in a side panel is much narrower than the window */}
        <Stack direction="row" spacing={1.5} useFlexGap sx={{ alignItems: "flex-start", flexWrap: "wrap" }}>
          <TextField
            select
            size="small"
            id={`${id}-source`}
            label={t("fieldEditor.rules.sourceLabel")}
            value={source && sources.includes(source) ? source.key : ""}
            error={sourceMissing}
            helperText={sourceMissing ? t("fieldEditor.rules.sourceMissing") : undefined}
            onChange={({ target: { value } }) => {
              const newSource = fields.find((field) => field.key === value);
              onConditionChange({
                ...condition,
                sourceFieldKey: value,
                values: condition.values.filter((option) => newSource?.options.includes(option)),
              });
            }}
            sx={{ flex: "1 1 200px" }}
          >
            {sources.map((candidate) => (
              <MenuItem key={candidate.key} value={candidate.key}>
                {candidate.name}
              </MenuItem>
            ))}
          </TextField>
          <TextField
            select
            size="small"
            id={`${id}-operator`}
            label={t("fieldEditor.rules.operatorLabel")}
            value={condition.operator}
            onChange={({ target: { value } }) =>
              onConditionChange({ ...condition, operator: v.parse(ConditionOperatorSchema, value) })
            }
            sx={{ flex: "1 1 160px" }}
          >
            {CONDITION_OPERATORS.map((operator) => (
              <MenuItem key={operator} value={operator}>
                <OperatorLabel operator={operator} />
              </MenuItem>
            ))}
          </TextField>
          {needsValues && (
            <Autocomplete
              multiple
              size="small"
              id={`${id}-values`}
              options={[...(source?.options ?? [])]}
              value={[...condition.values]}
              onChange={(_event, values) => onConditionChange({ ...condition, values })}
              renderInput={(params) => (
                <TextField
                  {...params}
                  label={t("fieldEditor.rules.valuesLabel")}
                  error={condition.values.length === 0}
                  helperText={condition.values.length === 0 ? t("fieldEditor.rules.valuesRequired") : undefined}
                />
              )}
              sx={{ flex: "2 1 200px" }}
            />
          )}
        </Stack>
        <Autocomplete
          multiple
          size="small"
          id={`${id}-targets`}
          options={targets}
          getOptionLabel={(field) => field.name}
          isOptionEqualToValue={(a, b) => a.key === b.key}
          value={fields.filter((field) => targetKeys.includes(field.key))}
          onChange={(_event, picked) => onTargetsChange(picked.map((field) => field.key))}
          renderInput={(params) => (
            <TextField
              {...params}
              label={t("fieldEditor.rules.targetsLabel")}
              error={targetKeys.length === 0}
              helperText={targetKeys.length === 0 ? t("fieldEditor.rules.targetsRequired") : undefined}
            />
          )}
        />
        {row.isDraft && (
          <Alert severity="warning" variant="outlined">
            {stored
              ? complete
                ? t("fieldEditor.rules.invalidEdit")
                : t("fieldEditor.rules.incompleteEdit")
              : complete
                ? t("fieldEditor.rules.invalid")
                : t("fieldEditor.rules.incomplete")}
          </Alert>
        )}
      </Stack>
    </Paper>
  );
}

function ReadOnlyRules({ fields }: { fields: ReadonlyArray<RuleEditorField> }): ReactNode {
  const { t, i18n } = useTranslation("common");
  const { heading } = useDescribeRule();
  const groups = groupDisplayRules(fields);
  const nameOf = (key: string) => fields.find((field) => field.key === key)?.name ?? null;
  if (groups.length === 0) {
    return (
      <Typography variant="body2" color="text.secondary">
        {t("fieldEditor.rules.none")}
      </Typography>
    );
  }
  return (
    <List dense disablePadding>
      {groups.map((group) => (
        <ListItem key={group.targetKeys[0]} disableGutters>
          <ListItemText
            primary={heading(group.rule, nameOf(group.rule.sourceFieldKey))}
            secondary={t("fieldEditor.rules.shows", {
              fields: formatList(
                group.targetKeys.map((key) => nameOf(key) ?? key),
                i18n.resolvedLanguage ?? i18n.language,
              ),
            })}
          />
        </ListItem>
      ))}
    </List>
  );
}

/**
 * Lists the display rules of a whole form or template, once each. Fields whose
 * rules are identical are shown as one rule with several target fields.
 * Options come from the source field; a rule can only show fields that would
 * not end up depending on themselves. A rule is only applied once it is
 * complete and the shared validator accepts it; until then it stays a draft
 * and any rule it edits stays on its fields. Only deleting a rule clears it.
 */
export default function DisplayRulesPanel({ fields, onChange, editable }: DisplayRulesPanelProps): ReactNode {
  const { t } = useTranslation("common");
  const headingId = useId();
  const [drafts, setDrafts] = useState<ReadonlyArray<Draft>>([]);
  /*
   * A row keeps its React identity, and its place, while it moves between
   * being a stored rule and a draft, so the control the author is using keeps
   * focus. Both change in event handlers, plus a layout effect that only adds
   * saved rows the first time they appear.
   */
  // every field of a rule maps to its row, so the row keeps its identity when its first field is deleted
  const [rowIdByTarget, setRowIdByTarget] = useState<ReadonlyMap<string, string>>(
    () =>
      new Map(
        groupDisplayRules(fields).flatMap(({ targetKeys }) => targetKeys.map((key) => [key, targetKeys[0]] as const)),
      ),
  );
  /** Rows in display order: saved rules as they first appear, then new rules as they are added. */
  const [rowOrder, setRowOrder] = useState<ReadonlyArray<string>>(() =>
    groupDisplayRules(fields).map(({ targetKeys }) => targetKeys[0]),
  );
  const nextDraftId = useRef(0);

  // fields whose saved rule a draft is editing stay out of the groups, so the draft owns only them
  const shadowed = new Set(drafts.flatMap((draft) => draft.replaces));
  const draftRows: Array<Row> = drafts.flatMap((draft) => {
    const replaced = fields.filter((field) => draft.replaces.includes(field.key) && field.rule !== null);
    const sanitised = sanitiseDraft(fields, draft, draft.targetKeys);
    // an edit whose fields have all been deleted, with nothing else to show, has nothing left to edit
    if (draft.replaces.length > 0 && replaced.length === 0 && sanitised.targetKeys.length === 0) return [];
    return {
      id: draft.id,
      ...sanitised,
      stored:
        replaced.length > 0 && replaced[0].rule
          ? { rule: replaced[0].rule, targetKeys: replaced.map((field) => field.key) }
          : null,
      isDraft: true,
      sourceDropped: draft.sourceFieldKey !== null && sanitised.condition.sourceFieldKey === null,
    };
  });
  const usedIds = new Set(drafts.map((draft) => draft.id));
  const groupRows: Array<Row> = groupDisplayRules(fields.filter((field) => !shadowed.has(field.key))).map(
    ({ rule, targetKeys }) => {
      const preferred = rowIdByTarget.get(targetKeys[0]) ?? targetKeys[0];
      const id = usedIds.has(preferred) ? `${preferred}/${targetKeys[0]}` : preferred;
      usedIds.add(id);
      return { id, condition: rule, targetKeys, stored: { rule, targetKeys }, isDraft: false, sourceDropped: false };
    },
  );
  const [ordered, unordered] = partition([...groupRows, ...draftRows], (row) => rowOrder.includes(row.id));
  const rows = [...sortBy(ordered, [(row) => rowOrder.indexOf(row.id)]), ...unordered];

  /*
   * Saved rules that arrive after the first render (fields loading late) get
   * a place and an identity as soon as they appear, so deleting one of their
   * fields before any edit does not rekey or move them. A layout effect
   * records them before paint, so no click can run before they are recorded.
   */
  const savedRows = rows.filter((row) => !row.isDraft);
  const savedSignature = JSON.stringify(savedRows.map(({ id, targetKeys }) => [id, targetKeys]));
  // the signature stands for savedRows, which is a new array on every render
  useLayoutEffect(() => {
    setRowOrder((current) => {
      const unseen = savedRows.map(({ id }) => id).filter((id) => !current.includes(id));
      return unseen.length === 0 ? current : [...current, ...unseen];
    });
    setRowIdByTarget((current) => {
      const unmapped = savedRows.flatMap((row) =>
        row.targetKeys.filter((key) => !current.has(key)).map((key) => [key, row.id] as const),
      );
      return unmapped.length === 0 ? current : new Map([...current, ...unmapped]);
    });
  }, [savedSignature]);

  if (!editable) {
    return (
      <Box component="section" aria-labelledby={headingId}>
        <Typography variant="h6" component="h3" id={headingId}>
          {t("fieldEditor.rules.title")}
        </Typography>
        <ReadOnlyRules fields={fields} />
      </Box>
    );
  }

  const forgetRow = (rowId: string) =>
    setRowIdByTarget((current) => new Map([...current].filter(([, id]) => id !== rowId)));
  const replaceDraft = (draft: Draft | null, rowId: string) =>
    setDrafts((current) => {
      const others = current.filter((existing) => existing.id !== rowId);
      return draft ? [...others, draft] : others;
    });

  /** Applies the row's rule to its targets when it is complete and valid; keeps it as a draft otherwise. */
  const update = (row: Row, condition: DraftCondition, targetKeys: ReadonlyArray<string>) => {
    const rule = ruleFromDraft(condition);
    const storedKeys = row.stored?.targetKeys ?? [];
    if (rule !== null && targetKeys.length > 0) {
      const changes: Array<RuleChange> = [
        ...storedKeys.filter((key) => !targetKeys.includes(key)).map((key) => ({ key, rule: null })),
        ...targetKeys.map((key) => ({ key, rule })),
      ];
      if (!changesAreInvalid(fields, changes)) {
        // every field the rule will be on, including those of a saved rule it merges with, now belongs to this row
        const merged = fields
          .filter(
            (field) =>
              !shadowed.has(field.key) &&
              !storedKeys.includes(field.key) &&
              field.rule !== null &&
              sameRule(field.rule, rule),
          )
          .map((field) => field.key);
        setRowIdByTarget(
          (current) =>
            new Map([
              ...[...current].filter(([, id]) => id !== row.id),
              ...[...merged, ...targetKeys].map((key) => [key, row.id] as const),
            ]),
        );
        replaceDraft(null, row.id);
        onChange(changes);
        return;
      }
    }
    replaceDraft({ id: row.id, ...condition, targetKeys, replaces: storedKeys }, row.id);
  };

  const claimedBy = (rowId: string) =>
    new Set(rows.filter((row) => row.isDraft && row.id !== rowId).flatMap((row) => row.targetKeys));
  const firstSource = sourceCandidates(fields, [])[0] ?? null;

  return (
    <Box component="section" aria-labelledby={headingId}>
      <Typography variant="h6" component="h3" id={headingId}>
        {t("fieldEditor.rules.title")}
      </Typography>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        {t("fieldEditor.rules.explanation")}
      </Typography>
      {rows.length === 0 && (
        <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
          {t("fieldEditor.rules.none")}
        </Typography>
      )}
      {rows.length > 0 && (
        <Stack component="ul" spacing={2} sx={{ listStyle: "none", p: 0, m: 0, mb: 2 }}>
          {rows.map((row, position) => (
            <RuleCard
              key={row.id}
              row={row}
              position={position}
              fields={fields}
              claimedKeys={claimedBy(row.id)}
              onConditionChange={(condition) => update(row, condition, row.targetKeys)}
              onTargetsChange={(targetKeys) => update(row, row.condition, targetKeys)}
              onDelete={() => {
                replaceDraft(null, row.id);
                forgetRow(row.id);
                setRowOrder((current) => current.filter((id) => id !== row.id));
                if (row.stored) onChange(row.stored.targetKeys.map((key) => ({ key, rule: null })));
              }}
            />
          ))}
        </Stack>
      )}
      <Button
        variant="outlined"
        size="small"
        startIcon={<AddIcon />}
        disabled={firstSource === null}
        onClick={() => {
          nextDraftId.current += 1;
          const id = `draft-${nextDraftId.current}`;
          setRowOrder((current) => [...current, id]);
          setDrafts((current) => [
            ...current,
            {
              id,
              sourceFieldKey: firstSource?.key ?? null,
              operator: "IS_ANY_OF",
              values: [],
              targetKeys: [],
              replaces: [],
            },
          ]);
        }}
      >
        {t("fieldEditor.rules.addRule")}
      </Button>
      {firstSource === null && (
        <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
          {t("fieldEditor.rules.noSources")}
        </Typography>
      )}
    </Box>
  );
}
