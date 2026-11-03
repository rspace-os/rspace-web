import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import { useTheme } from "@mui/material/styles";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import { observer } from "mobx-react-lite";
import type React from "react";
import { useContext, useId, useState } from "react";
import { useTranslation } from "react-i18next";
import PadlockIcon from "../../assets/graphics/PadlockIcon";
import { Heading } from "../../components/DynamicHeadingLevel";
import HelpLinkIcon from "../../components/HelpLinkIcon";
import FieldLabel from "../../components/Inputs/FieldLabel";
import { useDeploymentProperty } from "../../hooks/api/useDeploymentProperty";
import { helpDocsArticleUrl } from "../../modules/common/i18n/TransRichText";
import { mkAlert } from "../../stores/contexts/Alert";
import NavigateContext from "../../stores/contexts/Navigate";
import type SampleModel from "../../stores/models/SampleModel";
import useStores from "../../stores/use-stores";
import { getErrorMessage } from "../../util/error";
import * as FetchingData from "../../util/fetchingData";
import * as Parser from "../../util/parsers";
import { isoToLocale } from "../../util/Util";
import { useCancelSampleRequestMutation, useSendSampleRequestMutation } from "../Requests/mutations";
import { useExistingSampleRequestQuery } from "../Requests/queries";
import RequestsStatusChip from "../Requests/RequestsStatusChip";

function RequestSampleButton({ onClick }: { onClick: () => void }): React.ReactNode {
  const { t } = useTranslation("inventory");
  const theme = useTheme();
  return (
    <Button
      variant="outlined"
      onClick={onClick}
      sx={{
        color: theme.palette.record.sample.lighter,
        backgroundColor: theme.palette.primary.main,
        borderColor: theme.palette.primary.main,
        "&:hover": {
          backgroundColor: theme.palette.primary.dark,
          borderColor: theme.palette.primary.dark,
        },
      }}
    >
      {t("sample.requestMaterialSection.requestSampleButton")}
    </Button>
  );
}

const RequestMaterialSection = observer(({ activeResult }: { activeResult: SampleModel }) => {
  const { t } = useTranslation(["inventory", "common"]);
  const theme = useTheme();
  const requestTextFieldId = useId();
  const [requestText, setRequestText] = useState("");
  const [dialogOpen, setDialogOpen] = useState(false);
  const { useNavigate } = useContext(NavigateContext);
  const navigate = useNavigate();
  const { uiStore } = useStores();
  const sampleRequestsAvailable = FetchingData.getSuccessValue(
    useDeploymentProperty("inventory.sampleRequests.available"),
  )
    .flatMap(Parser.isString)
    .map((value) => value === "ALLOWED")
    .orElse(false);

  // Check whether the current user already has a request against this sample, so that the
  // request button can be replaced with the existing request's status instead. Further UI
  // enhancements around this (richer status display, etc.) will come later.
  //
  // Gated on sampleRequestsAvailable too, not just below in the render: this whole component
  // renders nothing once the property is DENIED (see the early return below), so without this
  // guard the fetch would still fire on every mount for no UI benefit at all.
  const existingRequestQuery = useExistingSampleRequestQuery(
    activeResult.id,
    sampleRequestsAvailable && activeResult.requestable && activeResult.id != null,
  );
  const existingRequest = existingRequestQuery.data ?? null;
  const checkingForExistingRequest = existingRequestQuery.isLoading;
  // Guards sendRequest/cancelRequest below against double-clicks; the two are never both
  // available at once (the Cancel button only shows once a request already exists), so one
  // shared flag is enough.
  const sendRequestMutation = useSendSampleRequestMutation();
  const cancelRequestMutation = useCancelSampleRequestMutation();
  const isProcessingAction = sendRequestMutation.isPending || cancelRequestMutation.isPending;
  const viewRequest = () => {
    if (!existingRequest) return;
    navigate(`/inventory/requests?requestId=${existingRequest.id}`);
  };

  // Mirrors RequestableSwitch's own guard above, so the two stay mutually exclusive: anyone who
  // can toggle requestable (the owner, or a PI/Lab Admin of the owner's group) sees that switch
  // instead of a request form for a sample they can already manage directly. Deliberately not
  // canEdit: a plain group member who can edit but not transfer the sample is still a candidate
  // to request it from, not someone who can already manage it directly.
  if (!sampleRequestsAvailable || activeResult.canSetRequestable) return null;

  const showActionError = (action: string, error: unknown) => {
    console.error(action, error);
    uiStore.addAlert(
      mkAlert({
        variant: "error",
        message: t("errors.genericActionError", { error: getErrorMessage(error, t("errors.unknownReason")) }),
      }),
    );
  };

  // On success the newly-created request's status simply replaces the request button, and the
  // dialog closes.
  const sendRequest = () => {
    if (!activeResult.globalId || isProcessingAction) return;
    sendRequestMutation.mutate(
      { sampleGlobalId: activeResult.globalId, note: requestText },
      {
        onSuccess: () => {
          setDialogOpen(false);
          setRequestText("");
        },
        onError: (error) => showActionError(t("sample.requestMaterialSection.actionErrors.send"), error),
      },
    );
  };

  // Cancelling is only legal for the requester, and only while the request is PENDING.
  const cancelRequest = () => {
    if (!existingRequest || isProcessingAction) return;
    cancelRequestMutation.mutate(existingRequest.id, {
      onError: (error) => showActionError(t("sample.requestMaterialSection.actionErrors.cancel"), error),
    });
  };

  return (
    <Box
      sx={{
        display: "flex",
        alignItems: "center",
        justifyContent: "space-between",
        gap: 2,
        p: 1.5,
        borderRadius: 1,
        border: `1px solid ${theme.palette.divider}`,
        backgroundColor: theme.palette.record.sample.lighter,
      }}
    >
      {activeResult.requestable ? (
        <>
          <Box>
            <Heading sx={{ mt: 0, display: "flex", alignItems: "center", gap: 0.5 }}>
              {t("sample.requestMaterialSection.compactTitle")}
              <HelpLinkIcon
                link={helpDocsArticleUrl("sampleRequests")}
                title={t("requestsManagement.helpTitle")}
                fontSize="inherit"
              />
            </Heading>
            <Typography variant="body2">
              {existingRequest?.status === "PENDING"
                ? t("sample.requestMaterialSection.pendingDescription", {
                    owner: activeResult.owner?.fullName ?? "",
                  })
                : t("sample.requestMaterialSection.compactDescription", {
                    owner: activeResult.owner?.fullName ?? "",
                  })}
            </Typography>
          </Box>
          {checkingForExistingRequest ? null : existingRequest && existingRequest.status !== "CANCELLED" ? (
            existingRequest.status === "PENDING" ? (
              <Box sx={{ display: "flex", flexDirection: "column", alignItems: "flex-end", gap: 0.5 }}>
                <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
                  <RequestsStatusChip status={existingRequest.status} onClick={viewRequest} bordered />
                  <Button
                    size="small"
                    variant="outlined"
                    disabled={isProcessingAction}
                    onClick={cancelRequest}
                    sx={{
                      // The Inventory accented theme's MuiButton override out-specifies a plain
                      // sx class; "&&" repeats this rule's own selector to match and win.
                      "&&": {
                        backgroundColor: "white",
                        "&:hover": { backgroundColor: "white" },
                      },
                    }}
                  >
                    {t("common:actions.cancel")}
                  </Button>
                </Box>
                <Typography variant="body2">
                  {t("sample.requestMaterialSection.pendingSentText", {
                    date: isoToLocale(existingRequest.created, { dateOnly: true }),
                    owner: activeResult.owner?.fullName ?? "",
                  })}
                </Typography>
              </Box>
            ) : existingRequest.status !== "APPROVED" ? (
              // The button is available whenever the most recent request is in any state
              // other than APPROVED (PENDING is handled separately, above).
              <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
                <RequestsStatusChip status={existingRequest.status} onClick={viewRequest} bordered />
                <RequestSampleButton onClick={() => setDialogOpen(true)} />
              </Box>
            ) : (
              <RequestsStatusChip status={existingRequest.status} onClick={viewRequest} bordered />
            )
          ) : (
            <RequestSampleButton onClick={() => setDialogOpen(true)} />
          )}
        </>
      ) : (
        <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
          <PadlockIcon color={theme.palette.action.disabled} />
          <Box>
            <Typography variant="subtitle2" sx={{ fontSize: "1rem", display: "flex", alignItems: "center", gap: 0.5 }}>
              {t("sample.requestMaterialSection.notAvailableHeader")}
              <HelpLinkIcon
                link={helpDocsArticleUrl("sampleRequests")}
                title={t("requestsManagement.helpTitle")}
                fontSize="inherit"
              />
            </Typography>
            <Typography variant="body2" color="text.secondary">
              {t("sample.requestMaterialSection.notAvailableBody")}
            </Typography>
          </Box>
        </Box>
      )}
      <Dialog open={dialogOpen} onClose={() => setDialogOpen(false)} fullWidth maxWidth="sm">
        <DialogTitle>{t("sample.requestMaterialSection.dialogTitle", { sampleName: activeResult.name })}</DialogTitle>
        <DialogContent>
          <Box sx={{ display: "flex", flexDirection: "column", gap: 2, pt: 1 }}>
            <Typography variant="body2">
              {t("sample.requestMaterialSection.reviewText", { owner: activeResult.owner?.fullName ?? "" })}
            </Typography>
            <Box>
              <FieldLabel htmlFor={requestTextFieldId} sx={{ textTransform: "uppercase" }}>
                {t("sample.requestMaterialSection.whatYouNeedLabel")}
              </FieldLabel>
              <TextField
                id={requestTextFieldId}
                placeholder={t("sample.requestMaterialSection.whatYouNeedHelperText")}
                multiline
                minRows={3}
                fullWidth
                value={requestText}
                onChange={({ target: { value } }) => setRequestText(value)}
              />
            </Box>
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setDialogOpen(false)}>{t("common:actions.cancel")}</Button>
          <Button color="primary" variant="contained" disabled={isProcessingAction} onClick={sendRequest}>
            {t("sample.requestMaterialSection.sendRequestButton")}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
});

export default RequestMaterialSection;
