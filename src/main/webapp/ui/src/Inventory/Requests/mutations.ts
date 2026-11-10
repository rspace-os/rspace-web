import { type QueryClient, useMutation, useQueryClient } from "@tanstack/react-query";
import ApiService from "@/common/InvApiService";
import { type SampleRequestDetail, sampleRequestsQueryKeys } from "./queries";

async function updateSampleRequestStatus(
  requestId: number,
  body: { status: string; reason?: string; transferredSampleGlobalId?: string },
): Promise<{ status: string }> {
  const { data } = await ApiService.update<{ status: string }>("sampleRequests", `${requestId}/status`, body);
  return data;
}

/**
 * Shared by useFulfilSampleRequestMutation and useTransferSampleOwnershipMutation below.
 * transferredSampleGlobalId records which sample the request was actually fulfilled against -
 * the originally requested one, or (via the Operations Wizard's "wizard" radio) a newly-created
 * one - so RequestDetailPanel's own transferredSampleGlobalId derivation (reading this back out of
 * statusChanges) can show the right one instead of always falling back to the original sample.
 */
function markSampleRequestFulfilled(
  requestId: number,
  transferredSampleGlobalId?: string,
): Promise<{ status: string }> {
  return updateSampleRequestStatus(requestId, { status: "FULFILLED", transferredSampleGlobalId });
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
 * Fulfils the request and transfers the sample to its requester as a single backend call
 * (PUT .../actions/fulfilByTransfer) - if either half is refused, nothing changes. The recipient
 * is never passed: the backend always transfers to the request's own requester. Defaults to the
 * originally requested sample; pass `sampleToTransferGlobalId` to transfer a different one
 * instead (e.g. one the Operations Wizard just created from it).
 */
export function useTransferSampleOwnershipMutation() {
  const queryClient = useQueryClient();
  return useMutation({
    mutationFn: ({ requestId, sampleToTransferGlobalId }: { requestId: number; sampleToTransferGlobalId?: string }) =>
      ApiService.update<{ status: string }>("sampleRequests", `${requestId}/actions/fulfilByTransfer`, {
        sampleToTransferGlobalId,
      }).then((r) => r.data),
    onSuccess: (data, { requestId }) => invalidateAndSeedDetailStatus(queryClient, requestId, data.status),
    onError: () => {
      // A single atomic call: a failure here means nothing was committed at all - most likely
      // because the request was closed by someone else in the meantime, or the requester's
      // account no longer exists. Invalidating (without seeding) lets every query under
      // `sampleRequestsQueryKeys.all` - including this same detail query - refetch and pick up
      // whatever that real current state now is on its own.
      void queryClient.invalidateQueries({ queryKey: sampleRequestsQueryKeys.all });
    },
  });
}
