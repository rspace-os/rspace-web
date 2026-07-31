import HistoryIcon from "@mui/icons-material/History";
import Autocomplete from "@mui/material/Autocomplete";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Chip from "@mui/material/Chip";
import FormHelperText from "@mui/material/FormHelperText";
import IconButton from "@mui/material/IconButton";
import Stack from "@mui/material/Stack";
import TextField from "@mui/material/TextField";
import type React from "react";
import { useState } from "react";
import { useTranslation } from "react-i18next";
import { Heading, HeadingContext } from "../../../../components/DynamicHeadingLevel";
import RecordTypeIcon from "../../../../components/RecordTypeIcon";
import ElnRecordPicker from "./ElnRecordPicker";
import { iconForGlobalId } from "./iconForGlobalId";
import LinkTargetBrowser from "./LinkTargetBrowser";
import VersionLockDialog from "./VersionLockDialog";

export interface LinkEditorProps {
  /** Current relation type (controlled). */
  relationType: string;
  onRelationTypeChange: (value: string) => void;
  /** Allowed relation types: a constrained list or the full DataCite set. */
  relationOptions: ReadonlyArray<string>;
  /**
   * When true the relation field accepts free text (extra-field links allow any
   * DataCite type); when false it is a constrained pick from `relationOptions`
   * (template-field links restrict to the template's allowed set).
   */
  relationFreeSolo: boolean;
  relationLabel: string;
  relationError?: boolean;
  relationHelperText?: string;

  /** Current target Global ID (controlled). */
  targetGlobalId: string;
  /** Optional display name shown in the target chip ("GID — name"). */
  targetName?: string;
  /** Called for picks, typed edits, and clearing (globalId/name are "" when cleared). */
  onTargetChange: (globalId: string, name: string) => void;
  targetError: boolean;
  targetHelperText: string;

  /** A separate error shown under the target field (e.g. "select both …"). */
  validationMessage?: string;

  versionPin: number | null;
  onVersionPinChange: (versionPin: number | null) => void;
  /** Whether the version-pin affordance is enabled (caller owns target validity). */
  canPinVersion: boolean;
}

/**
 * The shared link-editor UI used by both the extra-field editor (UpdateField)
 * and the template-field editor (LinkFieldValue), in three labelled groups:
 * "Target" (the target chip, the Target Global ID field and the two Browse
 * buttons, all on one wrapping row), then the relation-type field, then
 * "Version" (the version pill and version-pin control). Plus the three
 * picker/version dialogs.
 *
 * Controlled and presentational: it owns only the open-state of its three
 * dialogs and a neutral vertical layout. All staged values, validation, the
 * commit buttons, and the commit logic stay with each caller, which differ
 * (constrained vs free-solo relations, a target name, Box vs Grid placement,
 * and different model-commit calls).
 */
export default function LinkEditor({
  relationType,
  onRelationTypeChange,
  relationOptions,
  relationFreeSolo,
  relationLabel,
  relationError,
  relationHelperText,
  targetGlobalId,
  targetName,
  onTargetChange,
  targetError,
  targetHelperText,
  validationMessage,
  versionPin,
  onVersionPinChange,
  canPinVersion,
}: LinkEditorProps): React.ReactElement {
  const { t } = useTranslation("inventory");
  const [browserOpen, setBrowserOpen] = useState(false);
  const [elnOpen, setElnOpen] = useState(false);
  const [versionDialogOpen, setVersionDialogOpen] = useState(false);

  return (
    // The group headings descend from whatever level the caller's own label sits at, rather than
    // being hardcoded: this component is mounted at two different depths (the extra-field editor and
    // the template-field editor). The extra HeadingContext makes "Target"/"Version" children of the
    // enclosing field label rather than its siblings.
    <HeadingContext>
      <Box>
        <Heading variant="h6" sx={{ fontWeight: 700, fontSize: "1rem" }}>
          {t("fields.link.editor.target")}
        </Heading>
        <Stack
          direction="row"
          spacing={1}
          // useFlexGap so a wrapped line gets real vertical spacing; Stack's default
          // sibling-margin implementation of `spacing` leaves wrapped lines touching
          useFlexGap
          sx={{ mt: 0.5, flexWrap: "wrap", alignItems: "flex-start" }}
          data-test-id="LinkEditor-targetRow"
        >
          <TextField
            label={t("fields.link.editor.targetGlobalId")}
            value={targetGlobalId}
            onChange={(e) => onTargetChange(e.target.value, "")}
            size="small"
            variant="standard"
            // a Global ID is short (about 20 characters at the very most, usually far fewer), so the
            // field is sized to its content rather than flexing to fill the row
            sx={{ width: "11em", flexShrink: 0 }}
            helperText={targetHelperText}
            error={targetError}
            slotProps={{ htmlInput: { "aria-label": t("fields.link.editor.targetGlobalId") } }}
          />
          <Button
            size="small"
            variant="outlined"
            aria-label={t("fields.link.editor.browseInventory")}
            onClick={() => setBrowserOpen(true)}
          >
            {t("fields.link.editor.browseInventory")}
          </Button>
          <Button
            size="small"
            variant="outlined"
            aria-label={t("fields.link.editor.browseEln")}
            onClick={() => setElnOpen(true)}
          >
            {t("fields.link.editor.browseEln")}
          </Button>
        </Stack>
        {/* The selected target sits below the id field and pickers, not beside them: it is the
          outcome of using them, and in the row it competed for width and had its delete icon
          clipped (MuiChip-label is overflow: hidden, so a shrunk chip loses the X). */}
        {targetGlobalId
          ? (() => {
              const iconData = iconForGlobalId(targetGlobalId);
              return (
                <Box sx={{ mt: 1 }}>
                  <Chip
                    // Match the committed (non-edit) LinkField pill. size="small"
                    // gives the same geometry; the pl restores the left padding
                    // the accented theme strips from deletable chips
                    // (`&.MuiChip-deletable { padding: 0 }`). Without it the type
                    // icon — which gets no MuiChip-icon margin because
                    // RecordTypeIcon wraps it in a tooltip — sits flush against
                    // the left edge instead of the non-edit pill's 4px
                    // (spacing(0.5)). The selector is repeated to out-specify the
                    // theme's two-class `.MuiChip-deletable` rule. The cancel
                    // button just widens the chip.
                    size="small"
                    sx={{ "&.MuiChip-deletable": { pl: 0.5 } }}
                    icon={iconData ? <RecordTypeIcon record={iconData} aria-hidden /> : undefined}
                    label={targetName ? `${targetGlobalId} — ${targetName}` : targetGlobalId}
                    onDelete={() => onTargetChange("", "")}
                    data-test-id="LinkTarget-globalId"
                  />
                </Box>
              );
            })()
          : null}
        {validationMessage ? <FormHelperText error>{validationMessage}</FormHelperText> : null}
        <Box sx={{ mt: 2 }}>
          <Autocomplete
            freeSolo={relationFreeSolo}
            options={relationOptions}
            value={relationFreeSolo ? relationType : relationType === "" ? null : relationType}
            onChange={relationFreeSolo ? undefined : (_event, value) => onRelationTypeChange(value ?? "")}
            onInputChange={relationFreeSolo ? (_event, value) => onRelationTypeChange(value) : undefined}
            renderInput={(params) => {
              const { slotProps, ...textFieldProps } = params;
              return (
                <TextField
                  {...textFieldProps}
                  variant="standard"
                  label={relationLabel}
                  error={relationError}
                  helperText={relationHelperText}
                  slotProps={{
                    ...slotProps,
                    htmlInput: {
                      ...slotProps.htmlInput,
                      "aria-label": relationLabel,
                    },
                  }}
                />
              );
            }}
          />
        </Box>
        <Heading variant="h6" sx={{ mt: 2, fontWeight: 700, fontSize: "1rem" }}>
          {t("fields.link.editor.version")}
        </Heading>
        <Stack direction="row" spacing={1} sx={{ mt: 0.5, alignItems: "center" }} data-test-id="LinkEditor-versionRow">
          <Chip
            size="small"
            variant="outlined"
            label={
              versionPin != null
                ? t("fields.link.editor.pinnedVersion", { version: versionPin })
                : t("fields.link.editor.latest")
            }
            data-test-id="LinkEditor-version"
          />
          <IconButton
            size="small"
            aria-label={
              targetGlobalId
                ? t("fields.link.editor.pinVersionFor", { globalId: targetGlobalId })
                : t("fields.link.editor.pinVersion")
            }
            disabled={!canPinVersion}
            onClick={() => setVersionDialogOpen(true)}
          >
            <HistoryIcon fontSize="small" />
          </IconButton>
        </Stack>

        <LinkTargetBrowser
          open={browserOpen}
          onCancel={() => setBrowserOpen(false)}
          onPick={(target) => {
            onTargetChange(target.globalId, target.name);
            setBrowserOpen(false);
          }}
        />
        <ElnRecordPicker
          open={elnOpen}
          onCancel={() => setElnOpen(false)}
          onPick={(target) => {
            onTargetChange(target.globalId, target.name);
            setElnOpen(false);
          }}
        />
        <VersionLockDialog
          open={versionDialogOpen}
          globalId={targetGlobalId}
          currentVersionPin={versionPin}
          onConfirm={(pin) => {
            setVersionDialogOpen(false);
            onVersionPinChange(pin);
          }}
          onCancel={() => setVersionDialogOpen(false)}
        />
      </Box>
    </HeadingContext>
  );
}
