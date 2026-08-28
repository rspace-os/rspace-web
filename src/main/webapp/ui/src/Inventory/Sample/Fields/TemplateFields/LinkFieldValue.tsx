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
  /** Notifies the owning form that the committed link changed. Omit where there is nothing to tell:
   * the template editor tracks error state on its Name field only. */
  onChange?: () => void;
  /**
   * Whether to surface the field name above the editor and in the committed card. False in the
   * template editor, where the Name field just above already carries it.
   */
  showFieldName?: boolean;
  /** Overrides the editor's "Target" group heading; the template editor names the default's. */
  targetHeading?: string;
  /** Passed through to {@link LinkEditor}; see its docblock. */
  nestHeadings?: boolean;
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
function LinkFieldValue({
  field,
  sourceGlobalId,
  disabled,
  onChange,
  showFieldName = true,
  targetHeading,
  nestHeadings,
}: LinkFieldValueArgs): React.ReactNode {
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

  // Block record Save while the editor is open or holds unapplied changes; the `hasLink` guard
  // keeps an empty optional field saveable. A dedicated flag, not field.error, so an open editor
  // reads as in-progress. Gated on `!disabled` because in view mode there are no Apply/Discard
  // buttons to act on, and a record Save happens while still editable.
  useEffect(() => {
    field.setLinkEditInProgress(!disabled && (changed || (editing && hasLink)));
  }, [changed, editing, hasLink, disabled, field]);
  // Separate from the effect above on purpose: React runs a cleanup before every re-run, not only
  // on unmount, so keeping it there wrote false then the real value on every dependency change.
  // Clears the flag when the template editor unmounts a field marked for deletion.
  useEffect(() => () => field.setLinkEditInProgress(false), [field]);

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
    changed && !bothEmpty && !bothSet ? t("sample.fields.linkFieldValue.selectBothBeforeApplying") : "";

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
          t("fields.extraFields.link.targetNotFound", {
            globalId: nextLink.targetGlobalId,
          }),
        );
        return;
      }
    }
    field.setAttributesDirty({ link: nextLink });
    field.setLinkEditInProgress(false);
    setEditing(false);
    onChange?.();
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
        <LinkField name={showFieldName ? field.name : ""} link={committedLink} editable={!disabled} />
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
      {showFieldName && field.name && (
        <Typography variant="subtitle1" component="span" sx={{ fontWeight: 700 }}>
          {field.name}
        </Typography>
      )}
      <LinkEditor
        relationType={stagedRelationType}
        onRelationTypeChange={(value) => setStagedRelationType(value)}
        relationOptions={relationOptions}
        relationFreeSolo={false}
        relationLabel={t("fields.extraFields.fields.relationType")}
        targetHeading={targetHeading}
        nestHeadings={nestHeadings}
        targetGlobalId={stagedTargetGlobalId}
        onTargetChange={(globalId) => setStagedTarget(globalId)}
        targetError={Boolean(targetExistenceError) || !targetValidity.ok}
        targetHelperText={
          targetExistenceError ??
          (!targetValidity.ok ? targetValidity.reason : t("fields.extraFields.link.targetHelper"))
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
