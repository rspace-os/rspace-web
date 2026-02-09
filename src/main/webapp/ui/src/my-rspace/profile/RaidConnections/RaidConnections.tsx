import Typography from "@mui/material/Typography";
import React, { Suspense } from "react";
import Stack from "@mui/material/Stack";
import ErrorBoundary from "@/components/ErrorBoundary";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import { useRaidIntegrationInfoAjaxQuery } from "@/modules/raid/queries";
import RaidConnectionsEntry from "@/my-rspace/profile/RaidConnections/RaidConnectionsEntry";
import { useGetGroupByIdQuery } from "@/modules/groups/queries";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { faSpinner } from "@fortawesome/free-solid-svg-icons/faSpinner";
import Link from "@mui/material/Link";

const RaidConnections = ({ groupId }: { groupId: string }) => {
  const { data: token } = useOauthTokenQuery();
  const { data: integrationData } = useRaidIntegrationInfoAjaxQuery();
  const {
    data: groupData,
    error: groupError
  } = useGetGroupByIdQuery({ id: groupId, token });

  if (!integrationData.success) {
    return (
      <Typography variant="body2">
        Error loading RAiD integration info: {integrationData.errorMsg}
      </Typography>
    );
  }

  if (groupError) {
    return (
      <Typography variant="body2">
        Error loading group info: {groupError.message}
      </Typography>
    );
  }

  if (groupData === null) {
    return (
      <Typography variant="body2">
        Group not found, or you may not have permission to view this group.
      </Typography>
    );
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

  const isEnabled = integrationData?.data?.enabled;

  const getUnavailableMessage = () => {
    if (groupType !== "PROJECT_GROUP") {
      return <>RAiD is disabled for this project - only project groups can have RAiD connections.</>;
    }

    if (!integrationData?.data?.available) {
      return <>RAiD is not available for this RSpace instance. Please contact your system administrator to enable RAiD.</>;
    }

    if (!integrationData?.data?.enabled) {
      return <>RAiD is not enabled for your account. To add or change RAiD connections, go to the <Link href="/apps">Apps page</Link> and enable RAiD.</>;
    }

    if (!hasConnectedServers) {
      return (
        <>
          RAiD has been enabled, but no RAiD servers have been connected yet.
          Please go to the <Link href="/apps">Apps page</Link> and add a RAiD
          server.
        </>
      );
    }

    return null;
  }

  const raidIdentifier = groupData?.raid?.raidIdentifier ?? "";
  const raidTitle = groupData?.raid?.raidTitle ?? "";

  return (
    <Stack spacing={1} sx={{ width: "100%" }} direction="column">
      <Typography variant="h6">RAiD Connections</Typography>
      <ErrorBoundary>
        <Suspense
          fallback={<FontAwesomeIcon icon={faSpinner} spin size="3x" />}
        >
          {shouldShowUnavailableMessage ? (
            <Stack spacing={1} direction="column">
              <Typography variant="body2">{getUnavailableMessage()}</Typography>
              {raidIdentifier && (
                <Typography variant="body2">
                  <strong>
                    {/* It shouldn't be possible for groups to change types
                       (so a project group can never become a lab group),
                        but handling it here just in case */}
                    {(!integrationData.data?.available ||
                    groupType !== "PROJECT_GROUP")
                      ? "Previously"
                      : "Currently"}{" "}
                    connected to:
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
