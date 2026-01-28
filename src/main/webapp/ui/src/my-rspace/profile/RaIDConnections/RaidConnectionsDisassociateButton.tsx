import { useState } from "react";
import Button from "@mui/material/Button";
import Typography from "@mui/material/Typography";
import { ConfirmationDialog } from "@/components/ConfirmationDialog";
import { useRemoveRaidIdentifierMutation } from "@/modules/raid/mutations";

interface RaidConnectionsDisassociateButtonProps {
  groupId: string;
  raidIdentifier: string;
  raidTitle: string;
}

const RaidConnectionsDisassociateButton = ({
  groupId,
  raidIdentifier,
  raidTitle,
}: RaidConnectionsDisassociateButtonProps) => {
  const [open, setOpen] = useState(false);
  const mutation = useRemoveRaidIdentifierMutation({ groupId });

  const handleConfirmDisassociate = async () => {
    await mutation.mutateAsync();
    setOpen(false);
  };

  return (
    <>
      <Button
        type="button"
        variant="outlined"
        color="error"
        onClick={() => {
          mutation.reset();
          setOpen(true);
        }}
      >
        Disassociate
      </Button>
      <ConfirmationDialog
        title="Confirm Disassociation"
        consequences={
          <>
            <Typography variant="body1">
              Are you sure you want to disassociate the RaID identifier{" "}
              <strong>{raidTitle}</strong> ({raidIdentifier}) from this project
              group?
            </Typography>
            {mutation.isError && (
              <Typography variant="body2">
                Error: {mutation.error?.message}
              </Typography>
            )}
          </>
        }
        variant="warning"
        callback={handleConfirmDisassociate}
        handleCloseDialog={() => {
          setOpen(false);
        }}
        open={open}
      />
    </>
  );
};

export default RaidConnectionsDisassociateButton;