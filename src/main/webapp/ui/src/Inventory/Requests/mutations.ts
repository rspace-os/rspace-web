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
 * The request is marked fulfilled BEFORE the transfer, not after: the backend authorises the
 * fulfil transition against the sample's current owner, and that's still the caller here. Doing
 * the transfer first would change the sample's owner away from the caller, so the follow-up
 * fulfil call would then fail as the caller no longer being party to the request (reported back as
 * 404, to avoid disclosing the request's existence).
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
      const fulfilled = await markSampleRequestFulfilled(requestId);
      await ApiService.update<{ id: number }>("samples", `${sampleId}/actions/changeOwner`, {
        owner: { username: newOwnerUsername },
      });
      return fulfilled;
    },
    onSuccess: (data, { requestId }) => invalidateAndSeedDetailStatus(queryClient, requestId, data.status),
    // No trustworthy "new status" to seed on failure - it almost always means the fulfil step's
    // own request was rejected (e.g. a 409, because the requester cancelled or the request was
    // otherwise closed between this dialog opening and Transfer being pressed), i.e. the status
    // moved on server-side without this attempt succeeding. Invalidating (without seeding) lets
    // every query under `sampleRequestsQueryKeys.all` - including this same detail query - refetch
    // and pick up whatever that real current state now is on its own.
    onError: () => {
      void queryClient.invalidateQueries({ queryKey: sampleRequestsQueryKeys.all });
    },
  });
}
