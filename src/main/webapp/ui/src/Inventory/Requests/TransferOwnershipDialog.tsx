import Alert from "@mui/material/Alert";
import Autocomplete, { autocompleteClasses } from "@mui/material/Autocomplete";
import Button from "@mui/material/Button";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import FormControl from "@mui/material/FormControl";
import InputAdornment from "@mui/material/InputAdornment";
import { darken, useTheme } from "@mui/material/styles";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import type React from "react";
import { useTranslation } from "react-i18next";
import UserDetails from "@/components/UserDetails";
import { formatList } from "@/modules/common/i18n/listFormat";
import TransRichText from "@/modules/common/i18n/TransRichText";

export default function TransferOwnershipDialog({
  open,
  requestId,
  sampleName,
  requesterId,
  requesterFullName,
  subSampleCount,
  otherActiveRequests,
  isProcessingAction,
  onClose,
  onSubmit,
}: {
  open: boolean;
  requestId: number;
  sampleName: string;
  requesterId: number;
  requesterFullName: string;
  subSampleCount: number | null;
  otherActiveRequests: Array<{ id: number; requesterName: string }> | null;
  isProcessingAction: boolean;
  onClose: () => void;
  onSubmit: () => void;
}): React.ReactNode {
  const { t, i18n } = useTranslation(["inventory", "common"]);
  const theme = useTheme();
  // The only option this read-only field will ever show, matching PeopleField's own
  // "restrictToUser" pattern of narrowing its options down to exactly one.
  const requesterOption = { label: requesterFullName };

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="sm">
      <DialogTitle>
        {t("requestsManagement.detail.transferDialog.heading", { sampleName, requester: requesterFullName })}
      </DialogTitle>
      <DialogContent>
        <Alert severity="info" sx={{ mb: 2 }}>
          <TransRichText
            i18nKey="inventory:requestsManagement.detail.transferDialog.warning"
            values={{ requester: requesterFullName }}
          />
        </Alert>
        <Typography component="p" variant="body1" sx={{ mb: 1 }}>
          {t("requestsManagement.detail.transferDialog.whatWillHappen")}
        </Typography>
        <Typography component="ul" variant="body2" sx={{ mt: 0, mb: 2, pl: 3 }}>
          {subSampleCount !== null && subSampleCount > 1 && (
            <li>
              {t("requestsManagement.detail.transferDialog.bullets.subsamplesTransferred", {
                count: subSampleCount,
                requester: requesterFullName,
              })}
            </li>
          )}
          <li>
            {t("requestsManagement.detail.transferDialog.bullets.subsamplesMoved", { requester: requesterFullName })}
          </li>
          <li>{t("requestsManagement.detail.transferDialog.bullets.requestFulfilled", { id: requestId })}</li>
          {otherActiveRequests !== null && otherActiveRequests.length > 0 && (
            <li>
              {t("requestsManagement.detail.transferDialog.bullets.otherRequestsRejected", {
                count: otherActiveRequests.length,
                names: formatList(
                  otherActiveRequests.map((r) => r.requesterName),
                  i18n.resolvedLanguage ?? i18n.language,
                ),
              })}
            </li>
          )}
        </Typography>
        {/* There is only ever one legal recipient (the requester) - the backend itself always
            transfers to them regardless of anything picked here (see
            useTransferSampleOwnershipMutation's doc comment in ./mutations.ts). Styled to match
            the editable recipient field used elsewhere (PeopleField, as seen when transferring a
            Sample directly from the Inventory UI), but read-only and with no dropdown of its own -
            there's nothing to choose. The chip itself is the same UserDetails component used
            elsewhere (e.g. RequestDetailsFields' requester field), so it still pops up the same
            details popover on click. */}
        <FormControl component="fieldset" fullWidth>
          <Autocomplete
            options={[requesterOption]}
            value={requesterOption}
            getOptionLabel={(option) => option.label}
            readOnly
            disableClearable
            forcePopupIcon={false}
            size="small"
            sx={{ maxWidth: 500, [`& .${autocompleteClasses.option}`]: { cursor: "default" } }}
            renderValue={(_value, getItemProps) => {
              // className carries the spacing MUI gives a chip-like item inside this box;
              // UserDetails renders its own Chip internally, so only that much is reusable here.
              const { className } = getItemProps();
              return (
                <span className={className}>
                  <UserDetails userId={requesterId} fullName={requesterFullName} position={["bottom", "right"]} />
                </span>
              );
            }}
            renderInput={({ slotProps: inputSlotProps, ...rest }) => (
              <TextField
                {...rest}
                variant="outlined"
                slotProps={{
                  ...inputSlotProps,
                  input: {
                    ...inputSlotProps?.input,
                    // The chip itself is inputSlotProps.input.startAdornment, computed by
                    // Autocomplete from renderValue above - prepend the label rather than
                    // replacing it.
                    startAdornment: (
                      <>
                        <InputAdornment position="start">
                          {" "}
                          {t("contextMenu.transfer.dialog.recipientLabel")}
                        </InputAdornment>
                        {inputSlotProps?.input?.startAdornment}
                      </>
                    ),
                  },
                }}
              />
            )}
          />
        </FormControl>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>{t("common:actions.cancel")}</Button>
        <Button
          variant="contained"
          // Prevents a double-click from submitting the same transfer twice.
          disabled={isProcessingAction}
          onClick={onSubmit}
          sx={{
            backgroundColor: darken(theme.palette.primary.main, 0.5),
            color: "white",
            "&:hover": {
              backgroundColor: darken(theme.palette.primary.main, 0.55),
            },
          }}
        >
          {t("common:actions.transfer")}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
