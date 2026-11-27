import { faSpinner } from "@fortawesome/free-solid-svg-icons/faSpinner";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import Alert from "@mui/material/Alert";
import AlertTitle from "@mui/material/AlertTitle";
import FormControlLabel from "@mui/material/FormControlLabel";
import Stack from "@mui/material/Stack";
import Switch from "@mui/material/Switch";
import Typography from "@mui/material/Typography";
import { useStore } from "@tanstack/react-form";
import { useTranslation } from "react-i18next";
import type { ExportForm } from "@/Export/exportForm";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import { formatList } from "@/modules/common/i18n/listFormat";
import TransRichText from "@/modules/common/i18n/TransRichText";
import { getRaidExportEligibility } from "@/modules/raid/services/export";
import { useCommonGroupsShareListingQuery } from "@/modules/share/queries";

interface ExportDialogRaidProps {
  form: ExportForm;
}

function ExportDialogRaid({ form }: ExportDialogRaidProps) {
  const { t, i18n } = useTranslation("workspace");
  const language = i18n.resolvedLanguage ?? i18n.language;
  const { data: token } = useOauthTokenQuery();
  const sharedItemIds = useStore(form.store, (state) => state.values.exportSelection.exportIds) as string[];
  const exportToRaid = useStore(form.store, (state) => state.values.repositoryForm.exportToRaid);
  const { data, error } = useCommonGroupsShareListingQuery({
    token,
    params: { sharedItemIds },
  });

  if (!data) {
    return <FontAwesomeIcon icon={faSpinner} spin size="2x" />;
  }

  if (error) {
    return (
      <Alert severity="error">
        <AlertTitle>{t("export.raid.error.title")}</AlertTitle>
        <Typography variant="body1">{t("export.raid.error.message", { message: error.message })}</Typography>
        <Typography variant="body1">{t("export.raid.error.nextHint")}</Typography>
      </Alert>
    );
  }

  const raidExportStatus = getRaidExportEligibility(data);

  const getErrorMessage = () => {
    if (raidExportStatus.isEligible) return null;
    switch (raidExportStatus.reason) {
      case "MISSING_GROUPS":
        return t("export.raid.ineligible.missingGroups", {
          groupIds: formatList(raidExportStatus.missingGroupIds, language),
        });
      case "NO_PROJECT_GROUPS":
        return t("export.raid.ineligible.noProjectGroups");
      case "NO_RAID_ASSOCIATION_FOUND":
        return t("export.raid.ineligible.noRaidAssociation", {
          groups: formatList(
            raidExportStatus.projectGroups.map((group) => group.name),
            language,
          ),
        });
      case "MULTIPLE_RAIDS_FOUND":
        return t("export.raid.ineligible.multipleRaids", {
          groups: formatList(
            raidExportStatus.projectGroups.map((group) => group.name),
            language,
          ),
        });
      default:
        return t("export.raid.ineligible.unknown");
    }
  };

  return (
    <>
      {!raidExportStatus.isEligible ? (
        <Alert severity="error">
          <AlertTitle>{t("export.raid.ineligible.title")}</AlertTitle>
          <Typography variant="body1">{getErrorMessage()}</Typography>
        </Alert>
      ) : (
        <Stack direction="column" spacing={1}>
          <Alert severity="info">
            <AlertTitle>{t("export.raid.eligible.title")}</AlertTitle>
            <Stack direction="column" spacing={2}>
              <Typography variant="body1">
                <TransRichText
                  i18nKey="workspace:export.raid.eligible.projectGroupLine"
                  values={{ name: raidExportStatus.projectGroup.name }}
                />
              </Typography>
              <Typography variant="body1">
                <TransRichText
                  i18nKey="workspace:export.raid.eligible.raidDetails"
                  values={{
                    title: raidExportStatus.raid.raidTitle,
                    identifier: raidExportStatus.raid.raidIdentifier,
                  }}
                />
              </Typography>
              <Typography variant="body1">{t("export.raid.eligible.instructions")}</Typography>
            </Stack>
          </Alert>
          <FormControlLabel
            control={
              <Switch
                checked={exportToRaid}
                onChange={({ target: { checked } }) => form.setFieldValue("repositoryForm.exportToRaid", checked)}
                value="repository"
                color="primary"
                disabled={!raidExportStatus.isEligible}
                data-test-id="repo"
                slotProps={{ input: { role: "checkbox" } }}
              />
            }
            label={t("export.raid.eligible.reportLabel")}
          />
        </Stack>
      )}
    </>
  );
}

export default ExportDialogRaid;
