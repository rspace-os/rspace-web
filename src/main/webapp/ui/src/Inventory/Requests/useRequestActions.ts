import { useTranslation } from "react-i18next";
import { mkAlert } from "@/stores/contexts/Alert";
import useStores from "@/stores/use-stores";
import { getErrorMessage } from "@/util/error";
import {
  useApproveSampleRequestMutation,
  useCancelSampleRequestMutation,
  useFulfilSampleRequestMutation,
  useRejectSampleRequestMutation,
  useTransferSampleOwnershipMutation,
} from "./mutations";

/**
 * Bundles the Requests detail panel's five status-transition mutations together with the one
 * "processing" flag and error-reporting helper they share, so RequestDetailPanel doesn't need to
 * call all five mutation hooks itself and separately derive isProcessingAction from them.
 *
 * The mutations themselves are returned as-is (not wrapped) since each call site's onSuccess/
 * onError also needs to drive that call site's own local UI state (closing a dialog, resetting a
 * typed reason, re-checking the request's status after a failed transfer, ...), which belongs next
 * to that state, not buried in here.
 */
export function useRequestActions() {
  const { t } = useTranslation(["inventory", "common"]);
  const { uiStore } = useStores();

  const approve = useApproveSampleRequestMutation();
  const reject = useRejectSampleRequestMutation();
  const fulfil = useFulfilSampleRequestMutation();
  const cancel = useCancelSampleRequestMutation();
  const transfer = useTransferSampleOwnershipMutation();

  const isProcessingAction =
    approve.isPending || reject.isPending || fulfil.isPending || cancel.isPending || transfer.isPending;

  /** Shared by every top-level action: each hits the same "silent failure" shape otherwise. */
  const showActionError = (action: string, error: unknown) => {
    console.error(action, error);
    uiStore.addAlert(
      mkAlert({
        variant: "error",
        message: t("errors.genericActionError", { error: getErrorMessage(error, t("errors.unknownReason")) }),
      }),
    );
  };

  return { approve, reject, fulfil, cancel, transfer, isProcessingAction, showActionError };
}
