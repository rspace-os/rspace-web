import { type QueryClient, useMutation, useQueryClient } from "@tanstack/react-query";
import ApiService from "@/common/InvApiService";
import { type SampleRequestDetail, sampleRequestsQueryKeys } from "./queries";

async function updateSampleRequestStatus(
  requestId: number,
  body: { status: string; reason?: string },
): Promise<{ status: string }> {
  const { data } = await ApiService.update<{ status: string }>("sampleRequests", `${requestId}/status`, body);
  return data;
}

/** Shared by useFulfilSampleRequestMutation and useTransferSampleOwnershipMutation below. */
function markSampleRequestFulfilled(requestId: number): Promise<{ status: string }> {
  return updateSampleRequestStatus(requestId, { status: "FULFILLED" });
}

/**
 * Says which half of useTransferSampleOwnershipMutation's two-step call actually failed, so the
 * caller can show an accurate message - "fulfil" means nothing happened (safe to treat like any
 * other rejected status change); "changeOwner" means the request is now irreversibly FULFILLED
 * (see the mutation's own doc comment) even though the sample was never actually transferred.
 */
export class SampleOwnershipTransferError extends Error {
  constructor(
    readonly step: "fulfil" | "changeOwner",
    cause: unknown,
  ) {
    super("Sample ownership transfer failed", { cause });
    this.name = "SampleOwnershipTransferError";
  }
}

/**
 * Invalidates every query under `sampleRequestsQueryKeys.all` (so the Requests list, the sidebar's
 * pending-count badge, and this same detail query all refetch), then overwrites the detail query's
 * `status` with the value this mutation's own response just returned - authoritative, and not
 * dependent on that refetch's GET actually resolving to the same fresh value (in a test, a mocked
 * GET has no way to know what a separately-mocked PATCH just "changed"; in production, the two can
 * still race). Called from every successful status-transition mutation below.
 */
async function invalidateAndSeedDetailStatus(
  queryClient: QueryClient,
  requestId: number,
  status: string,
): Promise<void> {
  await queryClient.invalidateQueries({ queryKey: sampleRequestsQueryKeys.all });
  queryClient.setQueryData(sampleRequestsQueryKeys.detail(requestId), (old: SampleRequestDetail | undefined) =>
    old ? { ...old, status } : old,
  );
}

export function useApproveSampleRequestMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (requestId: number) => updateSampleRequestStatus(requestId, { status: "APPROVED" }),
    onSuccess: (data, requestId) => invalidateAndSeedDetailStatus(queryClient, requestId, data.status),
  });
}

export function useRejectSampleRequestMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ requestId, reason }: { requestId: number; reason: string }) =>
      updateSampleRequestStatus(requestId, { status: "REJECTED", reason }),
    onSuccess: (data, { requestId }) => invalidateAndSeedDetailStatus(queryClient, requestId, data.status),
  });
}

export function useFulfilSampleRequestMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (requestId: number) => markSampleRequestFulfilled(requestId),
    onSuccess: (data, requestId) => invalidateAndSeedDetailStatus(queryClient, requestId, data.status),
  });
}

/**
 * Cancelling is only legal for the requester, and only while the request is PENDING - enforced
 * server-side; shared by the non-owner "Cancel" button in RequestDetailPanel and the Sample form's
 * "Request this sample" box, since both are the same requester-cancels-their-own-request action.
 */
export function useCancelSampleRequestMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: (requestId: number) => updateSampleRequestStatus(requestId, { status: "CANCELLED" }),
    onSuccess: (data, requestId) => invalidateAndSeedDetailStatus(queryClient, requestId, data.status),
  });
}

export function useSendSampleRequestMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ sampleGlobalId, note }: { sampleGlobalId: string; note: string }) =>
      ApiService.post<{ id: number; status: string; created: string }>("sampleRequests", {
        sampleGlobalId,
        note,
      }).then((r) => r.data),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: sampleRequestsQueryKeys.all }),
  });
}

/**
 * A SubSample has no owner of its own (it always derives from its parent Sample), so "preparing" a
 * subsample for transfer means transferring ownership of the whole Sample.
 *
 * The request is marked fulfilled BEFORE the transfer, not after - and this order is required, not
 * just a preference:
 * - The fulfil transition is authorised against the sample's CURRENT owner (a live lookup), so
 *   transferring first would mean the follow-up fulfil call gets rejected as the caller no longer
 *   being that owner.
 *   Worse: SampleApiManagerImpl's changeOwner action auto-rejects every other PENDING/APPROVED
 *   request against the same sample as a side effect - which, before this request has itself been
 *   fulfilled, includes THIS request. Transferring first would have that side effect auto-reject
 *   the very request this flow is trying to fulfil, before the fulfil call ever got a chance to
 *   run.
 * - FULFILLED is a terminal status server-side: there is no transition back to APPROVED or
 *   anything else. So if the fulfil call above succeeds but this changeOwner call then fails (edit
 *   lock held, permissions, network), the request is left genuinely, irreversibly FULFILLED with
 *   the sample never having actually moved - the backend gives no way to undo that half once it's
 *   committed. What IS fixable (see SampleOwnershipTransferError and the two onError branches
 *   below) is telling the user the truth about which of those two situations they're in, instead
 *   of showing the same "the request was already cancelled" message for both.
 */
export function useTransferSampleOwnershipMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: async ({
      requestId,
      sampleId,
      newOwnerUsername,
    }: {
      requestId: number;
      sampleId: number;
      newOwnerUsername: string;
    }) => {
      let fulfilled: { status: string };
      try {
        fulfilled = await markSampleRequestFulfilled(requestId);
      } catch (error) {
        throw new SampleOwnershipTransferError("fulfil", error);
      }
      try {
        await ApiService.update<{ id: number }>("samples", `${sampleId}/actions/changeOwner`, {
          owner: { username: newOwnerUsername },
        });
      } catch (error) {
        throw new SampleOwnershipTransferError("changeOwner", error);
      }
      return fulfilled;
    },
    onSuccess: (data, { requestId }) => invalidateAndSeedDetailStatus(queryClient, requestId, data.status),
    onError: (error, { requestId }) => {
      if (error instanceof SampleOwnershipTransferError && error.step === "changeOwner") {
        // The fulfil half of this call DID succeed, and (see the doc comment above) can't be
        // rolled back - reflect that irreversible fact rather than leaving the UI showing the
        // stale pre-fulfil status.
        void invalidateAndSeedDetailStatus(queryClient, requestId, "FULFILLED");
        return;
      }
      // The fulfil step itself failed - nothing happened. Most likely cause: a 409, because the
      // requester cancelled or the request was otherwise closed between this dialog opening and
      // Transfer being pressed, i.e. the status moved on server-side without this attempt
      // succeeding. Invalidating (without seeding) lets every query under
      // `sampleRequestsQueryKeys.all` - including this same detail query - refetch and pick up
      // whatever that real current state now is on its own.
      void queryClient.invalidateQueries({ queryKey: sampleRequestsQueryKeys.all });
    },
  });
}
