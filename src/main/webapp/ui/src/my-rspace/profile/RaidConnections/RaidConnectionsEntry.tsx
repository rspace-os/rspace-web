import { faSpinner } from "@fortawesome/free-solid-svg-icons/faSpinner";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";
// biome-ignore lint/style/noRestrictedImports: initial biome migration
import { Button, Stack } from "@mui/material";
import Typography from "@mui/material/Typography";
import { Suspense, useState } from "react";
import { useTranslation } from "react-i18next";
import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import { useGetGroupByIdQuery } from "@/modules/groups/queries";
import RaidConnectionsAddForm from "@/my-rspace/profile/RaidConnections/RaidConnectionsAddForm";
import RaidConnectionsDisassociateButton from "@/my-rspace/profile/RaidConnections/RaidConnectionsDisassociateButton";

const RaidConnectionsEntry = ({ groupId }: { groupId: string }) => {
  const { t } = useTranslation("common");
  const [isEditing, setIsEditing] = useState(false);
  const { data: token } = useOauthTokenQuery();
  const { data: groupData } = useGetGroupByIdQuery({ id: groupId, token });

  const raidIdentifier = groupData?.raid?.raidIdentifier ?? "";
  const raidTitle = groupData?.raid?.raidTitle ?? "";

  return (
    <Stack spacing={2} direction="row" sx={{ alignItems: "center", marginTop: 0 }}>
      {isEditing ? (
        <Suspense fallback={<FontAwesomeIcon icon={faSpinner} spin size="3x" />}>
          <RaidConnectionsAddForm groupId={groupId} handleCloseForm={() => setIsEditing(false)} />
        </Suspense>
      ) : (
        <>
          <Typography variant="body2">
            {raidIdentifier ? (
              <>
                {raidTitle} ({raidIdentifier})
              </>
            ) : (
              t("profile.raidConnections.notConnected")
            )}
          </Typography>
          {raidIdentifier ? (
            <RaidConnectionsDisassociateButton
              groupId={groupId}
              raidIdentifier={raidIdentifier}
              raidTitle={raidTitle}
            />
          ) : (
            <Button type="button" variant="outlined" onClick={() => setIsEditing(true)}>
              {t("actions.add")}
            </Button>
          )}
        </>
      )}
    </Stack>
  );
};

export default RaidConnectionsEntry;
