import Button from "@mui/material/Button";
import Typography from "@mui/material/Typography";
import { useState } from "react";
import { Trans, useTranslation } from "react-i18next";
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
  const { t } = useTranslation("common");

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
        {t("profile.raidConnections.disassociate")}
      </Button>
      <ConfirmationDialog
        title={t("profile.raidConnections.confirmDisassociateTitle")}
        consequences={
          <>
            <Typography variant="body1">
              <Trans
                i18nKey="profile.raidConnections.confirmDisassociateText"
                ns="common"
                values={{ raidTitle, raidIdentifier }}
              />
            </Typography>
            {mutation.isError && (
              <Typography variant="body2">
                {t("profile.raidConnections.errorPrefix", { error: mutation.error?.message })}
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
