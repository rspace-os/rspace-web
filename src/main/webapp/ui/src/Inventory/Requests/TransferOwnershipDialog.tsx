import Alert from "@mui/material/Alert";
import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import FormControl from "@mui/material/FormControl";
import Typography from "@mui/material/Typography";
import type React from "react";
import { useTranslation } from "react-i18next";
import { formatList } from "@/modules/common/i18n/listFormat";
import TransRichText from "@/modules/common/i18n/TransRichText";
import type PersonModel from "@/stores/models/PersonModel";
import PeopleField from "../components/Inputs/PeopleField";
import type { OtherActiveSampleRequest } from "./queries";

export default function TransferOwnershipDialog({
  open,
  requestId,
  sampleName,
  requesterFullName,
  isNewlyCreatedSample,
  subSampleCount,
  otherActiveRequests,
  transferRecipient,
  transferRequesterPerson,
  isProcessingAction,
  onRecipientChange,
  onClose,
  onSubmit,
}: {
  open: boolean;
  requestId: number;
  sampleName: string;
  requesterFullName: string;
  /**
   * True when `sampleName` is a sample the Operations Wizard just created (reached via the
   * "wizard" radio), rather than the one originally requested (every other route into this
   * dialog). Swaps the info box to explain the new sample instead of the usual ownership
   * warning, and the "moved to {requester}'s bench" bullet to name the new sample rather than
   * generic subsamples.
   */
  isNewlyCreatedSample: boolean;
  subSampleCount: number | null;
  otherActiveRequests: Array<OtherActiveSampleRequest> | null;
  transferRecipient: PersonModel | null;
  transferRequesterPerson: PersonModel | null;
  isProcessingAction: boolean;
  onRecipientChange: (person: PersonModel | null) => void;
  onClose: () => void;
  onSubmit: () => void;
}): React.ReactNode {
  const { t, i18n } = useTranslation(["inventory", "common"]);

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>
        {t("requestsManagement.detail.transferDialog.heading", { sampleName, requester: requesterFullName })}
      </DialogTitle>
      <DialogContent>
        <Alert severity="info" sx={{ mb: 2 }}>
          {isNewlyCreatedSample ? (
            t("requestsManagement.detail.transferDialog.newSampleHint", { sampleName })
          ) : (
            <TransRichText
              i18nKey="inventory:requestsManagement.detail.transferDialog.warning"
              values={{ requester: requesterFullName }}
            />
          )}
        </Alert>
        <Typography component="p" variant="body1" sx={{ mb: 1 }}>
          {t("requestsManagement.detail.transferDialog.whatWillHappen")}
        </Typography>
        <Box component="ul" sx={{ mt: 0, mb: 2, pl: 3 }}>
          {/* subSampleCount is always the originally requested sample's own count, never the
              newly created one's - not a sensible bullet to show about a different sample. */}
          {!isNewlyCreatedSample && subSampleCount !== null && subSampleCount > 1 && (
            <Typography component="li" variant="body2">
              {subSampleCount === 2
                ? t("requestsManagement.detail.transferDialog.bullets.subsamplesTransferredBoth", {
                    requester: requesterFullName,
                  })
                : t("requestsManagement.detail.transferDialog.bullets.subsamplesTransferred", {
                    count: subSampleCount,
                    requester: requesterFullName,
                  })}
            </Typography>
          )}
          <Typography component="li" variant="body2">
            {isNewlyCreatedSample
              ? t("requestsManagement.detail.transferDialog.bullets.newSampleMoved", {
                  requester: requesterFullName,
                })
              : t("requestsManagement.detail.transferDialog.bullets.subsamplesMoved", {
                  requester: requesterFullName,
                })}
          </Typography>
          <Typography component="li" variant="body2">
            {t("requestsManagement.detail.transferDialog.bullets.requestFulfilled", { id: requestId })}
          </Typography>
          {otherActiveRequests !== null && otherActiveRequests.length > 0 && (
            <Typography component="li" variant="body2">
              {t("requestsManagement.detail.transferDialog.bullets.otherRequestsRejected", {
                count: otherActiveRequests.length,
                names: formatList(
                  otherActiveRequests.map((r) => r.requesterName),
                  i18n.resolvedLanguage ?? i18n.language,
                ),
              })}
            </Typography>
          )}
        </Box>
        <FormControl component="fieldset" fullWidth>
          <PeopleField
            onSelection={(person) => onRecipientChange(person as PersonModel | null)}
            label={t("contextMenu.transfer.dialog.recipientLabel")}
            recipient={transferRecipient}
            restrictToUser={transferRequesterPerson ?? undefined}
            // Kept separate from transferRecipient (see its declaration in RequestDetailPanel):
            // clearing the field back to an unrestricted search must not lift this restriction.
            // The requester is looked up asynchronously (see openTransferDialog there), so
            // restrictToUser above is only set on a later render. disableAutoOpen is true from
            // this dialog's very first render, avoiding a race against the field's autoFocus
            // where openOnFocus would still read as unrestricted.
            disableAutoOpen
          />
        </FormControl>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>{t("common:actions.cancel")}</Button>
        <Button
          variant="contained"
          color="callToAction"
          disableElevation
          // transferRequesterPerson === null also disables this: until the requester lookup
          // in openTransferDialog resolves, PeopleField's restrictToUser is unset and the field
          // searches every user, not just the requester - submitting before then could hand
          // the sample to whoever was picked during that window instead. isProcessingAction
          // disables it while the transfer itself is in flight, so a double-click can't submit
          // the same transfer twice.
          disabled={transferRecipient === null || transferRequesterPerson === null || isProcessingAction}
          onClick={onSubmit}
        >
          {t("common:actions.transfer")}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
