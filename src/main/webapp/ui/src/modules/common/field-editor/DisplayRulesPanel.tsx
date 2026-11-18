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
import { type ReactNode, useId, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import {
  CONDITION_OPERATORS,
  type ConditionOperator,
  type FieldDisplayRule,
  operatorRequiresValues,
} from "@/modules/common/field-rules/displayRule";
import { formatList } from "@/modules/common/i18n/listFormat";
import {
  type DraftCondition,
  groupDisplayRules,
  type RuleChange,
  type RuleEditorField,
  ruleFromDraft,
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
 * A rule being edited that cannot be stored yet: its condition is incomplete
 * or it shows no field. Its target fields have no rule until it is complete.
 */
type Draft = DraftCondition & { id: string; targetKeys: ReadonlyArray<string> };

type Row = {
  id: string;
  condition: DraftCondition;
  targetKeys: ReadonlyArray<string>;
  /** Set for a rule stored on its target fields; unset for a draft. */
  rule?: FieldDisplayRule;
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
  const { condition, targetKeys } = row;
  const source = fields.find((field) => field.key === condition.sourceFieldKey) ?? null;
  const sources = sourceCandidates(fields, targetKeys);
  const targets = targetCandidates(fields, condition.sourceFieldKey, targetKeys, claimedKeys);
  const needsValues = operatorRequiresValues(condition.operator);
  const complete = ruleFromDraft(condition) !== null;
  const title = row.rule
    ? heading(row.rule, source?.name ?? null)
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
        <Stack direction={{ xs: "column", md: "row" }} spacing={1.5} useFlexGap sx={{ alignItems: "flex-start" }}>
          <TextField
            select
            size="small"
            id={`${id}-source`}
            label={t("fieldEditor.rules.sourceLabel")}
            value={source && sources.includes(source) ? source.key : ""}
            error={condition.sourceFieldKey !== null && source === null}
            helperText={
              condition.sourceFieldKey !== null && source === null ? t("fieldEditor.rules.sourceMissing") : undefined
            }
            onChange={({ target: { value } }) => {
              const newSource = fields.find((field) => field.key === value);
              onConditionChange({
                ...condition,
                sourceFieldKey: value,
                values: condition.values.filter((option) => newSource?.options.includes(option)),
              });
            }}
            sx={{ minWidth: 200, width: { xs: "100%", md: "auto" } }}
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
              onConditionChange({ ...condition, operator: value as ConditionOperator })
            }
            sx={{ minWidth: 160, width: { xs: "100%", md: "auto" } }}
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
              sx={{ minWidth: 200, flexGrow: 1, width: { xs: "100%", md: "auto" } }}
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
        {(!complete || targetKeys.length === 0) && (
          <Alert severity="warning" variant="outlined">
            {t("fieldEditor.rules.incomplete")}
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
        <ListItem key={group.id} disableGutters>
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
 * not end up depending on themselves.
 */
export default function DisplayRulesPanel({ fields, onChange, editable }: DisplayRulesPanelProps): ReactNode {
  const { t } = useTranslation("common");
  const headingId = useId();
  const [drafts, setDrafts] = useState<ReadonlyArray<Draft>>([]);
  /*
   * A row keeps its React identity while it moves between being a stored rule
   * and a draft, so the control the author is using keeps focus.
   */
  const rowIdByTarget = useRef(new Map<string, string>());
  const rowOrder = useRef<Array<string>>([]);
  const nextDraftId = useRef(0);

  const liveKeys = new Set(fields.map((field) => field.key));
  const liveDrafts = drafts.map((draft) => ({
    ...draft,
    targetKeys: draft.targetKeys.filter((key) => liveKeys.has(key)),
  }));
  const usedIds = new Set(liveDrafts.map((draft) => draft.id));
  const rows: Array<Row> = [
    ...groupDisplayRules(fields).map((group) => {
      const remembered = rowIdByTarget.current.get(group.targetKeys[0]);
      const id = remembered !== undefined && !usedIds.has(remembered) ? remembered : group.id;
      usedIds.add(id);
      return { id, condition: group.rule, targetKeys: group.targetKeys, rule: group.rule };
    }),
    ...liveDrafts.map((draft) => ({
      id: draft.id,
      condition: { sourceFieldKey: draft.sourceFieldKey, operator: draft.operator, values: draft.values },
      targetKeys: draft.targetKeys,
    })),
  ];
  for (const row of rows) if (!rowOrder.current.includes(row.id)) rowOrder.current.push(row.id);
  rows.sort((a, b) => rowOrder.current.indexOf(a.id) - rowOrder.current.indexOf(b.id));

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

  const remember = (rowId: string, targetKeys: ReadonlyArray<string>) => {
    for (const key of targetKeys) rowIdByTarget.current.set(key, rowId);
  };
  const replaceDraft = (draft: Draft | null, rowId: string) =>
    setDrafts((current) => {
      const others = current.filter((existing) => existing.id !== rowId);
      return draft ? [...others, draft] : others;
    });

  /** Stores the row's rule on its targets when it is complete; keeps it as a draft otherwise. */
  const update = (row: Row, condition: DraftCondition, targetKeys: ReadonlyArray<string>) => {
    const rule = ruleFromDraft(condition);
    for (const key of row.targetKeys) {
      if (!targetKeys.includes(key) && rowIdByTarget.current.get(key) === row.id) rowIdByTarget.current.delete(key);
    }
    const changes: Array<RuleChange> = row.targetKeys
      .filter((key) => !targetKeys.includes(key) || rule === null)
      .map((key) => ({ key, rule: null }));
    if (rule !== null && targetKeys.length > 0) {
      remember(row.id, targetKeys);
      changes.push(...targetKeys.map((key) => ({ key, rule })));
      replaceDraft(null, row.id);
    } else {
      replaceDraft({ id: row.id, ...condition, targetKeys }, row.id);
    }
    if (changes.length > 0) onChange(changes);
  };

  const claimedBy = (rowId: string) =>
    new Set(liveDrafts.filter((draft) => draft.id !== rowId).flatMap((draft) => draft.targetKeys));
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
                if (row.rule) onChange(row.targetKeys.map((key) => ({ key, rule: null })));
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
          setDrafts((current) => [
            ...current,
            {
              id: `draft-${nextDraftId.current}`,
              sourceFieldKey: firstSource?.key ?? null,
              operator: "IS_ANY_OF",
              values: [],
              targetKeys: [],
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
