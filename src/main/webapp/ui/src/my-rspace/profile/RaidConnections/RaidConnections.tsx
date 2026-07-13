import { faSpinner } from "@fortawesome/free-solid-svg-icons/faSpinner";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import Stack from "@mui/material/Stack";
import Typography from "@mui/material/Typography";
import { Suspense } from "react";
import { useTranslation } from "react-i18next";
import ErrorBoundary from "@/components/ErrorBoundary";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import TransRichText from "@/modules/common/i18n/TransRichText";
import { useGetGroupByIdQuery } from "@/modules/groups/queries";
import { useRaidIntegrationInfoAjaxQuery } from "@/modules/raid/queries";
import { formatRaidConnectionLabel } from "@/my-rspace/profile/RaidConnections/formatRaidConnectionLabel";
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
      return <TransRichText i18nKey="common:profile.raidConnections.notEnabled" />;
    }

    if (!hasConnectedServers) {
      return <TransRichText i18nKey="common:profile.raidConnections.noConnectedServers" />;
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
                  {/* It shouldn't be possible for groups to change types
                     (so a project group can never become a lab group),
                      but handling it here just in case */}
                  <TransRichText
                    i18nKey={
                      !integrationData.data?.available || groupType !== "PROJECT_GROUP"
                        ? "common:profile.raidConnections.previouslyConnectedTo"
                        : "common:profile.raidConnections.currentlyConnectedTo"
                    }
                    values={{ raid: formatRaidConnectionLabel({ raidIdentifier, raidTitle }) }}
                  />
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
