import type { RepoDetails } from "@/Export/repositories/common";
import {
  useCommonGroupsShareListingQuery,
} from "@/modules/share/queries";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import { getRaidExportEligibility } from "@/modules/raid/services/export";
import Alert from "@mui/material/Alert";
import Switch from "@mui/material/Switch";
import FormControlLabel from "@mui/material/FormControlLabel";
import React from "react";
import { DEFAULT_STATE } from "@/Export/constants";
import { SwitchBaseProps } from "@mui/material/internal/SwitchBase";
import AlertTitle from "@mui/material/AlertTitle";
import Typography from "@mui/material/Typography";
import Stack from "@mui/material/Stack";
import { observer } from "mobx-react";
import { faSpinner } from "@fortawesome/free-solid-svg-icons/faSpinner";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";

interface ExportDialogRaidProps {
  state: typeof DEFAULT_STATE;
  updateRepoConfig: (repoDetails: RepoDetails) => void;
}

const ExportDialogRaid = ({ state, updateRepoConfig }: ExportDialogRaidProps) => {
  const { data: token } = useOauthTokenQuery();
  const sharedItemIds = state.exportSelection.exportIds as string[];
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
        <AlertTitle>Error</AlertTitle>
        <Typography variant="body1">
          An error occurred while determining RAiD export eligibility:{" "}
          {error.message}
        </Typography>
        <Typography variant="body1">
          Please press Next to continue without reporting to RAiD.
        </Typography>
      </Alert>
    );
  }

  const raidExportStatus = getRaidExportEligibility(data);

  const getErrorMessage = () => {
    if (raidExportStatus.isEligible) return null;
    switch (raidExportStatus.reason) {
      case "MISSING_GROUPS":
        return `You do not have access to the following groups, therefore RSpace is unable to determine whether you have the rights to export the items to RAiD: ${raidExportStatus.missingGroupIds.join(", ")}. For more information, please contact the owner of the item, or press Next to continue without reporting to RAiD.`;
      case "NO_PROJECT_GROUPS":
        return "No project groups are associated with all shared items selected. Please press Next to continue without reporting to RAiD.";
      case "NO_RAID_ASSOCIATION_FOUND":
        return `None of the project groups (${raidExportStatus.projectGroups.map((group) => group.name).join(", ")}) associated with the shared items have a RAiD association. Please press Next to continue without reporting to RAiD.`;
      case "MULTIPLE_RAIDS_FOUND":
        return `Multiple project groups (${raidExportStatus.projectGroups.map((group) => group.name).join(", ")}) associated with the shared items have RAiD associations, which is not supported. Please narrow your selection, or press Next to continue without reporting to RAiD.`;
      default:
        return "An unknown error occurred while determining RAiD export eligibility. Please press Next to continue without reporting to RAiD.";
    }
  }

  const handleSwitchChange: SwitchBaseProps['onChange'] = ({ target: { checked } }) => {
    const newRepoConfig: (typeof DEFAULT_STATE)["repositoryConfig"] = {
      ...state.repositoryConfig,
      exportToRaid: checked,
    };
    updateRepoConfig(
      newRepoConfig
    );
  };

  return (
    <>
      {!raidExportStatus.isEligible ? (
        <Alert severity="error">
          <AlertTitle>Cannot report to RAiD</AlertTitle>
          <Typography variant="body1">{getErrorMessage()}</Typography>
        </Alert>
      ) : (
        <Stack direction="column" spacing={1}>
          <Alert severity="info">
            <AlertTitle>Report to RAiD</AlertTitle>
            <Stack direction="column" spacing={2}>
              <Typography variant="body1">
                The content you're about to export is part of the project group{" "}
                <strong>{raidExportStatus.projectGroup.name}</strong> which is
                associated with the following RAiD identifier:
              </Typography>
              <Typography variant="body1">
                <strong>{raidExportStatus.raid.raidTitle}</strong> ({raidExportStatus.raid.raidIdentifier}).
              </Typography>
              <Typography variant="body1">
                By enabling reporting to RAID below, the DOI of your repository
                export will be automatically added to your RAiD record. Otherwise, click Next to continue without reporting to RAiD.
              </Typography>
            </Stack>
          </Alert>
          <FormControlLabel
            control={
              <Switch
                checked={state.repositoryConfig.exportToRaid}
                onChange={handleSwitchChange}
                value="repository"
                color="primary"
                disabled={!raidExportStatus.isEligible}
                data-test-id="repo"
              />
            }
            label={"Report to RAiD"}
          />
        </Stack>
      )}
    </>
  );
};

export default observer(ExportDialogRaid);
