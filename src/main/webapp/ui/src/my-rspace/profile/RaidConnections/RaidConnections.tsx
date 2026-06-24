import { faSpinner } from "@fortawesome/free-solid-svg-icons/faSpinner";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import Link from "@mui/material/Link";
import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import { Suspense } from "react";
import { Trans, useTranslation } from "react-i18next";
import ErrorBoundary from "@/components/ErrorBoundary";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import { useGetGroupByIdQuery } from "@/modules/groups/queries";
import { useRaidIntegrationInfoAjaxQuery } from "@/modules/raid/queries";
import RaidConnectionsEntry from "@/my-rspace/profile/RaidConnections/RaidConnectionsEntry";

const RaidConnections = ({ groupId }: { groupId: string }) => {
  const { t } = useTranslation("common");
  const { data: token } = useOauthTokenQuery();
  const { data: integrationData } = useRaidIntegrationInfoAjaxQuery();
  const { data: groupData, error: groupError } = useGetGroupByIdQuery({ id: groupId, token });

  if (!integrationData.success) {
    return (
      <Typography variant="body2">
        {t("profile.raidConnections.errorLoadingIntegrationInfo", { error: integrationData.errorMsg })}
      </Typography>
    );
  }

  if (groupError) {
    return (
      <Typography variant="body2">
        {t("profile.raidConnections.errorLoadingGroupInfo", { error: groupError.message })}
      </Typography>
    );
  }

  if (groupData === null) {
    return <Typography variant="body2">{t("profile.raidConnections.groupNotFound")}</Typography>;
  }

  const groupType = groupData?.type;

  const hasConnectedServers = Object.entries(integrationData?.data.options || {})
    .filter(([key]) => key !== "RAID_CONFIGURED_SERVERS")
    .some(([, value]) => !Array.isArray(value) && value.RAID_OAUTH_CONNECTED);

  const shouldShowUnavailableMessage =
    !integrationData?.data?.available ||
    !integrationData?.data?.enabled ||
    !hasConnectedServers ||
    groupType !== "PROJECT_GROUP";

  const _isEnabled = integrationData?.data?.enabled;

  const getUnavailableMessage = () => {
    if (groupType !== "PROJECT_GROUP") {
      return <>{t("profile.raidConnections.disabledForProjectType")}</>;
    }

    if (!integrationData?.data?.available) {
      return <>{t("profile.raidConnections.notAvailable")}</>;
    }

    if (!integrationData?.data?.enabled) {
      return <Trans i18nKey="profile.raidConnections.notEnabled" ns="common" components={{ a: <Link /> }} />;
    }

    if (!hasConnectedServers) {
      return <Trans i18nKey="profile.raidConnections.noConnectedServers" ns="common" components={{ a: <Link /> }} />;
    }

    return null;
  };

  const raidIdentifier = groupData?.raid?.raidIdentifier ?? "";
  const raidTitle = groupData?.raid?.raidTitle ?? "";

  return (
    <Stack spacing={1} sx={{ width: "100%" }} direction="column">
      <Typography variant="h6">{t("profile.raidConnections.title")}</Typography>
      <ErrorBoundary>
        <Suspense fallback={<FontAwesomeIcon icon={faSpinner} spin size="3x" />}>
          {shouldShowUnavailableMessage ? (
            <Stack spacing={1} direction="column">
              <Typography variant="body2">{getUnavailableMessage()}</Typography>
              {raidIdentifier && (
                <Typography variant="body2">
                  <strong>
                    {/* It shouldn't be possible for groups to change types
                       (so a project group can never become a lab group),
                        but handling it here just in case */}
                    {!integrationData.data?.available || groupType !== "PROJECT_GROUP"
                      ? t("profile.raidConnections.previously")
                      : t("profile.raidConnections.currently")}{" "}
                    {t("profile.raidConnections.connectedTo")}
                  </strong>{" "}
                  {raidTitle} ({raidIdentifier})
                </Typography>
              )}
            </Stack>
          ) : (
            <RaidConnectionsEntry groupId={groupId} />
          )}
        </Suspense>
      </ErrorBoundary>
    </Stack>
  );
};

export default RaidConnections;
