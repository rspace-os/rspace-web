import SettingsIcon from "@mui/icons-material/Settings";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import { observer } from "mobx-react-lite";
import type React from "react";
import { useEffect, useState } from "react";
import { useTranslation } from "react-i18next";
import IconButtonWithTooltip from "../../../../components/IconButtonWithTooltip";
import type { Field, FieldLink } from "../../../../stores/definitions/Field";
import { DATACITE_RELATION_TYPES } from "../../../components/Fields/Link/dataciteRelationTypes";
import { isInventoryGlobalId, supportsVersionPin } from "../../../components/Fields/Link/iconForGlobalId";
import LinkEditor from "../../../components/Fields/Link/LinkEditor";
import LinkField from "../../../components/Fields/Link/LinkField";
import { validateTarget } from "../../../components/Fields/Link/linkTarget";
import { checkLinkTargetExists } from "../../../components/Fields/Link/linkTargetExists";
import useLinkTargetSummary from "../../../components/Fields/Link/useLinkTargetSummary";

type LinkFieldValueArgs = {
  field: Field;
  /** The Global ID of the sample owning this field, used to forbid self-links. */
  sourceGlobalId: string;
  disabled: boolean;
  onChange: () => void;
};

/**
 * Editor + display for a sample's structured Link field value. A committed link is shown with the
 * same {@link LinkField} card used for manually-created (extra-field) links: an outline, relation
 * and target chips, an info dialog, a version pill (and version-pin control), and an Open button.
 * A settings cog overlaid on the card reveals the editor, where the relationship type (constrained to the template field's
 * allowed set, or any DataCite type when the whitelist is empty) and a target are chosen and then
 * committed on Apply, mirroring the extra-field Link editor. The editor body itself is the shared
 * {@link LinkEditor}.
 */
function LinkFieldValue({ field, sourceGlobalId, disabled, onChange }: LinkFieldValueArgs): React.ReactNode {
  const { t } = useTranslation("inventory");
  const committedRelationType = field.link?.relationType ?? "";
  const committedTargetGlobalId = field.link?.targetGlobalId ?? "";
  const committedVersionPin = field.link?.versionPin ?? null;
  const hasLink = committedTargetGlobalId !== "";

  // Show the display card for an existing link; drop straight into the editor for an empty field.
  const [editing, setEditing] = useState(!hasLink);
  // set when Apply finds the typed target does not resolve on the server
  const [targetExistenceError, setTargetExistenceError] = useState<string | null>(null);
  const [checkingTarget, setCheckingTarget] = useState(false);

  // staged (uncommitted) edits; committed to the field model only on Apply
  const [stagedRelationType, setStagedRelationType] = useState<string>(committedRelationType);
  const [stagedTargetGlobalId, setStagedTargetGlobalId] = useState<string>(committedTargetGlobalId);
  const [stagedVersionPin, setStagedVersionPin] = useState<number | null>(committedVersionPin);

  // A no-access committed target (an unshared ELN item) cannot be
  // version-pinned, so the clock stays greyed while editing it. Keyed on the
  // committed target and only fetched while editing, so it clears after a
  // successful Apply commits a different, readable target and is reimposed on
  // the next edit after Discard. Inventory targets keep a limited-read view, so
  // they are never "no access".
  const committedSummary = useLinkTargetSummary(editing ? committedTargetGlobalId : "");
  const committedNoAccess = committedSummary?.readable === false && !isInventoryGlobalId(committedTargetGlobalId);

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

  // Block record Save while the link editor is open or holds unapplied changes; the
  // `hasLink` guard keeps an empty optional field saveable. A dedicated flag, not
  // field.error (see Field.linkEditInProgress), so an open editor reads as in-progress.
  // In view mode (`disabled`) the editor - and its Apply/Discard buttons - is never shown, so a
  // left-open editor must not keep the field flagged: that would block save with an "Apply or
  // discard" message the user has no way to act on. A record Save happens while still editable
  // (`disabled` is false), so gating on `!disabled` does not weaken the guard.
  useEffect(() => {
    field.setLinkEditInProgress(!disabled && (changed || (editing && hasLink)));
  }, [changed, editing, hasLink, disabled, field]);

  const relationOptions =
    field.allowedRelationTypes.length > 0 ? field.allowedRelationTypes : [...DATACITE_RELATION_TYPES];

  const bothEmpty = stagedRelationType === "" && stagedTargetGlobalId === "";
  const bothSet = stagedRelationType !== "" && stagedTargetGlobalId !== "";
  // typed targets are validated like the extra-field editor: parseable, a
  // supported prefix, and not a self-link
  const targetValidity =
    stagedTargetGlobalId === "" ? { ok: true, reason: "" } : validateTarget(stagedTargetGlobalId, sourceGlobalId);
  // a stageable value is either fully cleared (removing the link) or a complete, valid link
  const canApply = changed && !checkingTarget && !targetExistenceError && (bothEmpty || (bothSet && targetValidity.ok));

  const validationMessage =
    changed && !bothEmpty && !bothSet ? "Select both a relationship type and a target before applying." : "";

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
        setTargetExistenceError(`${nextLink.targetGlobalId} does not exist, or you do not have permission to view it.`);
        return;
      }
    }
    field.setAttributesDirty({ link: nextLink });
    field.setLinkEditInProgress(false);
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

  const committedLink = field.link;
  // Show the committed link card whenever there is a committed link and we are not actively
  // editing it - or, in view mode (disabled), always: the editor is never shown when disabled, so
  // a transient `editing` flag left set must not collapse an existing link to the "None" placeholder.
  if (committedLink && committedTargetGlobalId !== "" && (!editing || disabled)) {
    return (
      <Box sx={{ position: "relative" }}>
        {!disabled && (
          <Box sx={{ position: "absolute", top: 0, right: 0, zIndex: 1 }}>
            <IconButtonWithTooltip
              title={t("fields.link.linkField.editLink")}
              size="small"
              onClick={() => setEditing(true)}
              icon={<SettingsIcon fontSize="small" />}
            />
          </Box>
        )}
        <LinkField name={field.name} link={committedLink} editable={!disabled} />
      </Box>
    );
  }

  // View mode with no link to show or edit: show an explicit placeholder
  // rather than headers with blank space beneath them.
  if (disabled) {
    return (
      <Typography variant="body2" color="text.secondary">
        {t("sample.fields.linkFieldValue.none")}
      </Typography>
    );
  }

  return (
    <Box>
      {/* The FormField label is hidden, so surface the field name here. */}
      {field.name && (
        <Typography variant="subtitle1" component="span" sx={{ fontWeight: 700 }}>
          {field.name}
        </Typography>
      )}
      <LinkEditor
        relationType={stagedRelationType}
        onRelationTypeChange={(value) => setStagedRelationType(value)}
        relationOptions={relationOptions}
        relationFreeSolo={false}
        relationLabel="Relationship type"
        targetGlobalId={stagedTargetGlobalId}
        onTargetChange={(globalId) => setStagedTarget(globalId)}
        targetError={Boolean(targetExistenceError) || !targetValidity.ok}
        targetHelperText={
          targetExistenceError ??
          (!targetValidity.ok ? targetValidity.reason : "Paste a Global ID, or use the Browse buttons above.")
        }
        validationMessage={validationMessage}
        versionPin={stagedVersionPin}
        onVersionPinChange={(pin) => setStagedVersionPin(pin)}
        canPinVersion={
          stagedTargetGlobalId !== "" &&
          targetValidity.ok &&
          supportsVersionPin(stagedTargetGlobalId) &&
          !committedNoAccess
        }
      />
      <Stack direction="row" spacing={1} sx={{ mt: 1 }}>
        <Button
          color="callToAction"
          disableElevation
          variant="contained"
          aria-label={t("sample.fields.linkFieldValue.applyLabel")}
          onClick={() => {
            void apply();
          }}
          disabled={!canApply}
          data-test-id="ApplyLinkButton"
        >
          {t("sample.fields.linkFieldValue.apply")}
        </Button>
        <Button
          variant="text"
          aria-label={t("sample.fields.linkFieldValue.discardLabel")}
          onClick={discard}
          disabled={!changed && !hasLink}
          data-test-id="DiscardLinkButton"
        >
          {t("sample.fields.linkFieldValue.discard")}
        </Button>
      </Stack>
    </Box>
  );
}

export default observer(LinkFieldValue);
