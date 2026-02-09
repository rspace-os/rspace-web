import { useOauthTokenQuery } from "@/modules/common/hooks/auth";
import React, { Suspense, useState } from "react";
import RaidConnectionsAddForm from "@/my-rspace/profile/RaidConnections/RaidConnectionsAddForm";
import { Button, Stack } from "@mui/material";
import Typography from "@mui/material/Typography";
import RaidConnectionsDisassociateButton from "@/my-rspace/profile/RaidConnections/RaidConnectionsDisassociateButton";
import { useGetGroupByIdQuery } from "@/modules/groups/queries";
import { faSpinner } from "@fortawesome/free-solid-svg-icons/faSpinner";
import { FontAwesomeIcon } from "@fortawesome/react-fontawesome";

const RaidConnectionsEntry = ({
  groupId,
}: {
  groupId: string;
}) => {
  const [isEditing, setIsEditing] = useState(false);
  const { data: token } = useOauthTokenQuery();
  const { data: groupData } = useGetGroupByIdQuery({ id: groupId, token });

  const raidIdentifier = groupData?.raid?.raidIdentifier ?? "";
  const raidTitle = groupData?.raid?.raidTitle ?? "";

    return (
      <Stack
        spacing={2}
        direction="row"
        alignItems="center"
        sx={{ marginTop: 0 }}
      >
        {isEditing ? (
          <Suspense
            fallback={
              <FontAwesomeIcon
                icon={faSpinner}
                spin
                size="3x"
              />
            }
          >
            <RaidConnectionsAddForm
              groupId={groupId}
              handleCloseForm={() => setIsEditing(false)}
            />
          </Suspense>
        ) : (
          <>
            <Typography variant="body2">
              {raidIdentifier ? (
                <>
                  {raidTitle} ({raidIdentifier})
                </>
              ) : (
                "Not connected"
              )}
            </Typography>
            {raidIdentifier ? (
              <RaidConnectionsDisassociateButton
                groupId={groupId}
                raidIdentifier={raidIdentifier}
                raidTitle={raidTitle}
              />
            ) : (
              <Button
                type="button"
                variant="outlined"
                onClick={() => setIsEditing(true)}
              >
                Add
              </Button>
            )}
          </>
        )}
      </Stack>
    );

};

export default RaidConnectionsEntry;
