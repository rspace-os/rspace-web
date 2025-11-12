import Typography from "@mui/material/Typography";
import React, { Suspense } from "react";
import Stack from "@mui/material/Stack";
import ErrorBoundary from "@/components/ErrorBoundary";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import { useRaidIntegrationInfoAjaxQuery } from "@/modules/raid/queries";
import RaIDConnectionsEntry from "@/my-rspace/profile/RaIDConnections/RaIDConnectionsEntry";
import { useGetGroupByIdQuery } from "@/modules/groups/queries";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
import { faSpinner } from "@fortawesome/free-solid-svg-icons/faSpinner";

const RaIDConnections = ({ groupId }: { groupId: string }) => {
  const { data: token } = useOauthTokenQuery();
  const { data: integrationData } = useRaidIntegrationInfoAjaxQuery();
  const {
    data: groupsData,
    error: groupsError
  } = useGetGroupByIdQuery({ id: groupId, token });

  if (!integrationData.success) {
    return (
      <Typography variant="body2">
        Error loading RaID integration info: {integrationData.errorMsg}
      </Typography>
    );
  }

  if (groupsError) {
    return (
      <Typography variant="body2">
        Error loading group info: {groupsError.message}
      </Typography>
    );
  }

  const groupType = groupsData?.type;

  const hasConnectedServers = Object.entries(integrationData?.data.options || {})
    .filter(([key]) => key !== "RAID_CONFIGURED_SERVERS")
    .some(([, value]) => !Array.isArray(value) && value.RAID_OAUTH_CONNECTED);

  const isUnavailable =
    !integrationData?.data?.available ||
    !integrationData?.data?.enabled ||
    !hasConnectedServers ||
    groupType !== "PROJECT_GROUP";

  const getUnavailableMessage = () => {
    if (groupType !== "PROJECT_GROUP") {
      return "RaID is disabled for this project - only project groups can have RaID Connections.";
    }

    if (!integrationData?.data?.available) {
      return "RaID is not available for this RSpace instance. Please contact your system administrator to enable RaID.";
    }

    if (!integrationData?.data?.enabled) {
      return "RaID is not enabled for this RSpace instance. To enable RaID Connections, go to the Apps page.";
    }

    if (!hasConnectedServers) {
      return "RaID has been enabled, but no RaID servers have been connected yet. Please go to the Apps page and enable a RaID server.";
    }

    return ""
  }

  return (
    <Stack spacing={1} sx={{ width: "100%" }} direction="column">
      <Typography variant="h6">RaID Connections</Typography>
      <ErrorBoundary>
        <Suspense
          fallback={<FontAwesomeIcon icon={faSpinner} spin size="3x" />}
        >
          {isUnavailable ? (
            <Typography variant="body2">{getUnavailableMessage()}</Typography>
          ) : (
            <RaIDConnectionsEntry groupId={groupId} />
          )}
        </Suspense>
      </ErrorBoundary>
    </Stack>
  );
};

export default RaIDConnections;