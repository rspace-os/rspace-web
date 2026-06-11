import React, { useState, useEffect } from "react";
import { observer } from "mobx-react-lite";
import Autocomplete from "@mui/material/Autocomplete";
import MuiTextField from "@mui/material/TextField";
import Button from "@mui/material/Button";
import Box from "@mui/material/Box";
import IconButton from "@mui/material/IconButton";
import Stack from "@mui/material/Stack";
import Chip from "@mui/material/Chip";
import Typography from "@mui/material/Typography";
import FormHelperText from "@mui/material/FormHelperText";
import HistoryIcon from "@mui/icons-material/History";
import { type Field, type FieldLink } from "../../../../stores/definitions/Field";
import { DATACITE_RELATION_TYPES } from "../../../components/Fields/Link/dataciteRelationTypes";
import LinkTargetBrowser from "../../../components/Fields/Link/LinkTargetBrowser";
import ElnRecordPicker from "../../../components/Fields/Link/ElnRecordPicker";
import RecordTypeIcon from "../../../../components/RecordTypeIcon";
import {
  iconForGlobalId,
  openUrlForTarget,
  supportsVersionPin,
} from "../../../components/Fields/Link/iconForGlobalId";
import LinkField from "../../../components/Fields/Link/LinkField";
import { validateTarget } from "../../../components/Fields/Link/linkTarget";
import { checkLinkTargetExists } from "../../../components/Fields/Link/linkTargetExists";
import VersionLockDialog from "../../../components/Fields/Link/VersionLockDialog";

type LinkFieldValueArgs = {
  field: Field;
  /** The Global ID of the sample owning this field, used to forbid self-links. */
  sourceGlobalId: string;
  disabled: boolean;
  onChange: () => void;
};

/**
 * Opens the link target in a new tab. A Gallery file opens at its location in the
 * Gallery; other targets keep the /globalId route, honouring a pinned version
 * (e.g. an SD audit view).
 */
function openTargetInNewTab(link: FieldLink): void {
  window.open(openUrlForTarget(link.targetGlobalId, link.versionPin), "_blank");
}

/**
 * Editor + display for a sample's structured Link field value. A committed link is shown with the
 * same {@link LinkField} card used for manually-created (extra-field) links: an outline, relation
 * and target chips, an info dialog, a version pill (and version-pin control), and an Open button.
 * "Edit" reveals the editor, where the relationship type (constrained to the template field's
 * allowed set, or any DataCite type when the whitelist is empty) and a target are chosen and then
 * committed on Apply, mirroring the extra-field Link editor.
 */
function LinkFieldValue({
  field,
  sourceGlobalId,
  disabled,
  onChange,
}: LinkFieldValueArgs): React.ReactNode {
  const committedRelationType = field.link?.relationType ?? "";
  const committedTargetGlobalId = field.link?.targetGlobalId ?? "";
  const committedVersionPin = field.link?.versionPin ?? null;
  const hasLink = committedTargetGlobalId !== "";

  // Show the display card for an existing link; drop straight into the editor for an empty field.
  const [editing, setEditing] = useState(!hasLink);
  const [browserOpen, setBrowserOpen] = useState(false);
  const [elnOpen, setElnOpen] = useState(false);
  const [versionDialogOpen, setVersionDialogOpen] = useState(false);
  // set when Apply finds the typed target does not resolve on the server
  const [targetExistenceError, setTargetExistenceError] = useState<
    string | null
  >(null);
  const [checkingTarget, setCheckingTarget] = useState(false);

  // staged (uncommitted) edits; committed to the field model only on Apply
  const [stagedRelationType, setStagedRelationType] = useState<string>(
    committedRelationType,
  );
  const [stagedTargetGlobalId, setStagedTargetGlobalId] = useState<string>(
    committedTargetGlobalId,
  );
  const [stagedVersionPin, setStagedVersionPin] = useState<number | null>(
    committedVersionPin,
  );

  const setStagedTarget = (targetGlobalId: string): void => {
    setTargetExistenceError(null);
    setStagedTargetGlobalId(targetGlobalId);
    // a version pin belongs to a specific target, so retargeting reverts to Latest
    if (targetGlobalId !== stagedTargetGlobalId) {
      setStagedVersionPin(null);
    }
  };

  // re-sync staged state when the committed link changes (record switch, post-save round-trip)
  useEffect(() => {
    setStagedRelationType(committedRelationType);
    setStagedTargetGlobalId(committedTargetGlobalId);
    setStagedVersionPin(committedVersionPin);
  }, [committedRelationType, committedTargetGlobalId, committedVersionPin]);

  const changed =
    stagedRelationType !== committedRelationType ||
    stagedTargetGlobalId !== committedTargetGlobalId ||
    stagedVersionPin !== committedVersionPin;

  // surface unapplied editor state on the field model so the record-level Save
  // is blocked (with a clear message) instead of silently dropping the staged
  // edit and saving the previous link
  useEffect(() => {
    field.setError(changed);
  }, [changed, field]);

  const relationOptions =
    field.allowedRelationTypes.length > 0
      ? field.allowedRelationTypes
      : [...DATACITE_RELATION_TYPES];

  const bothEmpty = stagedRelationType === "" && stagedTargetGlobalId === "";
  const bothSet = stagedRelationType !== "" && stagedTargetGlobalId !== "";
  // typed targets are validated like the extra-field editor: parseable, a
  // supported prefix, and not a self-link
  const targetValidity =
    stagedTargetGlobalId === ""
      ? { ok: true, reason: "" }
      : validateTarget(stagedTargetGlobalId, sourceGlobalId);
  // a stageable value is either fully cleared (removing the link) or a complete, valid link
  const canApply =
    changed &&
    !checkingTarget &&
    !targetExistenceError &&
    (bothEmpty || (bothSet && targetValidity.ok));

  const validationMessage =
    changed && !bothEmpty && !bothSet
      ? "Select both a relationship type and a target before applying."
      : "";

  const apply = async (): Promise<void> => {
    const nextLink: FieldLink | null = bothEmpty
      ? null
      : {
          relationType: stagedRelationType,
          targetGlobalId: stagedTargetGlobalId,
          versionPin: stagedVersionPin,
        };
    // a structurally-valid Global ID must also resolve to a real, readable
    // record; check (re)targeted links against the server before committing
    if (nextLink && nextLink.targetGlobalId !== committedTargetGlobalId) {
      setCheckingTarget(true);
      const exists = await checkLinkTargetExists(nextLink.targetGlobalId);
      setCheckingTarget(false);
      if (!exists) {
        setTargetExistenceError(
          `${nextLink.targetGlobalId} does not exist, or you do not have permission to view it.`,
        );
        return;
      }
    }
    field.setAttributesDirty({ link: nextLink });
    field.setError(false);
    setEditing(false);
    onChange();
  };

  const discard = (): void => {
    setTargetExistenceError(null);
    setStagedRelationType(committedRelationType);
    setStagedTargetGlobalId(committedTargetGlobalId);
    setStagedVersionPin(committedVersionPin);
    if (hasLink) {
      setEditing(false);
    }
  };

  // Display the committed link with the same card as a manually-created link.
  const committedLink = field.link;
  if (committedLink && committedTargetGlobalId !== "" && !editing) {
    return (
      <LinkField
        // the FormField wrapper already renders the field label, so suppress the card's own name
        name=""
        link={committedLink}
        targetDeleted={false}
        editable={!disabled}
        onOpen={() => openTargetInNewTab(committedLink)}
        onEdit={() => setEditing(true)}
      />
    );
  }

  // View mode with no link to show or edit: show an explicit placeholder
  // rather than headers with blank space beneath them.
  if (disabled) {
    return (
      <Typography variant="body2" color="text.secondary">
        None
      </Typography>
    );
  }

  return (
    <Box>
      <Autocomplete
        options={relationOptions}
        value={stagedRelationType === "" ? null : stagedRelationType}
        onChange={(_event, value) => setStagedRelationType(value ?? "")}
        renderInput={(params) => {
          const { slotProps, ...textFieldProps } = params;
          return (
            <MuiTextField
              {...textFieldProps}
              variant="standard"
              label="Relationship type"
              slotProps={{
                ...slotProps,
                htmlInput: {
                  ...slotProps.htmlInput,
                  "aria-label": "Relationship type",
                },
              }}
            />
          );
        }}
      />
      <Stack
        direction="row"
        spacing={1}
        sx={{ mt: 1, flexWrap: "wrap", alignItems: "center" }}
      >
        {stagedTargetGlobalId
          ? (() => {
              const iconData = iconForGlobalId(stagedTargetGlobalId);
              return (
                <Chip
                  icon={
                    iconData ? (
                      <RecordTypeIcon record={iconData} aria-hidden />
                    ) : undefined
                  }
                  label={stagedTargetGlobalId}
                  onDelete={() => setStagedTarget("")}
                  data-test-id="LinkTarget-globalId"
                />
              );
            })()
          : null}
        <Button
          size="small"
          variant="outlined"
          aria-label="Browse Inventory"
          onClick={() => setBrowserOpen(true)}
        >
          Browse Inventory
        </Button>
        <Button
          size="small"
          variant="outlined"
          aria-label="Browse ELN"
          onClick={() => setElnOpen(true)}
        >
          Browse ELN
        </Button>
      </Stack>
      <MuiTextField
        label="Target Global ID"
        value={stagedTargetGlobalId}
        onChange={(e) => setStagedTarget(e.target.value)}
        fullWidth
        size="small"
        variant="standard"
        sx={{ mt: 1 }}
        helperText={
          targetExistenceError ??
          (!targetValidity.ok
            ? targetValidity.reason
            : "Paste a Global ID, or use the Browse buttons above.")
        }
        error={Boolean(targetExistenceError) || !targetValidity.ok}
        slotProps={{ htmlInput: { "aria-label": "Target Global ID" } }}
      />
      {validationMessage && (
        <FormHelperText error>{validationMessage}</FormHelperText>
      )}
      <Stack direction="row" spacing={1} sx={{ mt: 1, alignItems: "center" }}>
        <Chip
          size="small"
          variant="outlined"
          label={
            stagedVersionPin != null
              ? `Pinned to v${stagedVersionPin}`
              : "Latest"
          }
          data-test-id="LinkEditor-version"
        />
        <IconButton
          size="small"
          aria-label={
            stagedTargetGlobalId
              ? `Pin version for ${stagedTargetGlobalId}`
              : "Pin version"
          }
          disabled={
            stagedTargetGlobalId === "" ||
            !targetValidity.ok ||
            !supportsVersionPin(stagedTargetGlobalId)
          }
          onClick={() => setVersionDialogOpen(true)}
        >
          <HistoryIcon fontSize="small" />
        </IconButton>
      </Stack>
      <Stack direction="row" spacing={1} sx={{ mt: 1 }}>
        <Button
          color="callToAction"
          disableElevation
          variant="contained"
          aria-label="Apply link"
          onClick={() => {
            void apply();
          }}
          disabled={!canApply}
          data-test-id="ApplyLinkButton"
        >
          Apply
        </Button>
        <Button
          variant="text"
          aria-label="Discard link changes"
          onClick={discard}
          disabled={!changed && !hasLink}
          data-test-id="DiscardLinkButton"
        >
          Discard
        </Button>
      </Stack>
      <LinkTargetBrowser
        open={browserOpen}
        onCancel={() => setBrowserOpen(false)}
        onPick={(target) => {
          setStagedTarget(target.globalId);
          setBrowserOpen(false);
        }}
      />
      <ElnRecordPicker
        open={elnOpen}
        onCancel={() => setElnOpen(false)}
        onPick={(target) => {
          setStagedTarget(target.globalId);
          setElnOpen(false);
        }}
      />

      <VersionLockDialog
        open={versionDialogOpen}
        globalId={stagedTargetGlobalId}
        currentVersionPin={stagedVersionPin}
        onConfirm={(versionPin) => {
          setVersionDialogOpen(false);
          // staged like every other link property; committed on Apply
          setStagedVersionPin(versionPin);
        }}
        onCancel={() => setVersionDialogOpen(false)}
      />
    </Box>
  );
}

export default observer(LinkFieldValue);
