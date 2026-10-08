import { useQuery } from "@tanstack/react-query";
import ApiService from "@/common/InvApiService";
import type { ApiSampleRequestStatusChangeItem } from "./RequestHistoryTable";
import type { ApiSampleRequestListItem, RequestsFilter, StatusFilter } from "./RequestsList";

const ACTIVE_STATUSES = "PENDING,APPROVED";
const PAST_STATUSES = "REJECTED,FULFILLED,CANCELLED";

// ApiPaginationCriteria.MAX_PAGE_SIZE on the backend - the most this endpoint will ever return
// from a single call; requesting more is rejected outright, so "fetch everything" means walking
// through it a page at a time instead.
const BACKEND_MAX_PAGE_SIZE = 100;
// Safety bound on how many pages that walk will take, in case a filter combination genuinely
// matches thousands of requests; ordinary use comes nowhere near this.
const MAX_FETCH_PAGES = 50;

/**
 * Every query below lives under this one prefix, so that every mutation in ./mutations.ts can
 * invalidate the lot with a single `invalidateQueries({ queryKey: sampleRequestsQueryKeys.all })`
 * - matching (and, for the Sample form's send/cancel actions, actually fixing a gap in) what the
 * old `notifySampleRequestStatusChanged()` window event broadcast to every listener.
 */
export const sampleRequestsQueryKeys = {
  all: ["sampleRequests"] as const,
  list: (requestsFilter: RequestsFilter, statusFilter: StatusFilter) =>
    [...sampleRequestsQueryKeys.all, "list", requestsFilter, statusFilter] as const,
  detail: (requestId: number) => [...sampleRequestsQueryKeys.all, "detail", requestId] as const,
  otherActiveForSample: (sampleId: number) =>
    [...sampleRequestsQueryKeys.all, "otherActiveForSample", sampleId] as const,
  forSample: (sampleId: number) => [...sampleRequestsQueryKeys.all, "forSample", sampleId] as const,
  pendingCount: () => [...sampleRequestsQueryKeys.all, "pendingCount"] as const,
};

async function fetchAllSampleRequests(filterParams: Record<string, string>): Promise<Array<ApiSampleRequestListItem>> {
  const allRequests: Array<ApiSampleRequestListItem> = [];
  for (let pageNum = 0; pageNum < MAX_FETCH_PAGES; pageNum++) {
    const params = new URLSearchParams({
      ...filterParams,
      pageSize: String(BACKEND_MAX_PAGE_SIZE),
      pageNumber: String(pageNum),
    });
    const { data } = await ApiService.query<{
      requests: Array<ApiSampleRequestListItem>;
      totalHits: number | null;
    }>("sampleRequests", params);
    allRequests.push(...data.requests);
    const gotFullPage = data.requests.length === BACKEND_MAX_PAGE_SIZE;
    const moreToFetch = typeof data.totalHits === "number" ? allRequests.length < data.totalHits : gotFullPage;
    if (!gotFullPage || !moreToFetch) break;
  }
  return allRequests;
}

/** The Requests list pane's full, role/status-filtered set of requests (sorted/paginated client-side). */
export function useSampleRequestsListQuery(requestsFilter: RequestsFilter, statusFilter: StatusFilter) {
  return useQuery({
    queryKey: sampleRequestsQueryKeys.list(requestsFilter, statusFilter),
    queryFn: () => {
      const filterParams: Record<string, string> = {};
      if (requestsFilter === "sent") filterParams.role = "REQUESTER";
      if (requestsFilter === "received") filterParams.role = "OWNER";
      // "all" omits the role filter entirely, so the API returns both sent and received requests.
      if (statusFilter === "active") filterParams.status = ACTIVE_STATUSES;
      if (statusFilter === "past") filterParams.status = PAST_STATUSES;
      return fetchAllSampleRequests(filterParams);
    },
  });
}

export type SampleRequestDetail = {
  status: string;
  statusChanges: Array<ApiSampleRequestStatusChangeItem>;
  sample: { owner: { firstName: string; lastName: string } };
};

/** A single request's current status, status history, and sample owner - the detail pane's core data. */
export function useSampleRequestDetailQuery(requestId: number | null) {
  return useQuery({
    queryKey: sampleRequestsQueryKeys.detail(requestId ?? -1),
    queryFn: () => ApiService.get<SampleRequestDetail>("sampleRequests", requestId as number).then((r) => r.data),
    enabled: requestId !== null,
  });
}

export type OtherActiveSampleRequest = { id: number; requesterName: string };

/**
 * Other PENDING/APPROVED requests against the same sample, excluding `excludeRequestId` - backs
 * the Choose Sample to Prepare dialog's "other requests will be closed automatically" warning and
 * the Transfer Ownership dialog's "will be automatically rejected" bullet.
 */
export function useOtherActiveSampleRequestsQuery(sampleId: number | null, excludeRequestId: number) {
  return useQuery({
    queryKey: sampleRequestsQueryKeys.otherActiveForSample(sampleId ?? -1),
    queryFn: () => {
      const params = new URLSearchParams({
        sampleId: String(sampleId),
        status: "PENDING,APPROVED",
        pageSize: "100",
      });
      return ApiService.query<{ requests: Array<{ id: number; requester: { firstName: string; lastName: string } }> }>(
        "sampleRequests",
        params,
      ).then(
        ({ data }): Array<OtherActiveSampleRequest> =>
          data.requests
            .filter((r) => r.id !== excludeRequestId)
            .map((r) => ({ id: r.id, requesterName: `${r.requester.firstName} ${r.requester.lastName}` })),
      );
    },
    enabled: sampleId !== null,
  });
}

export type ExistingSampleRequest = { id: number; status: string; created: string };

/** The current user's most recent request (if any) against a given sample. */
export function useExistingSampleRequestQuery(sampleId: number | null, enabled: boolean) {
  return useQuery({
    queryKey: sampleRequestsQueryKeys.forSample(sampleId ?? -1),
    queryFn: () =>
      ApiService.query<{ requests: Array<ExistingSampleRequest> }>(
        "sampleRequests",
        new URLSearchParams({ sampleId: String(sampleId) }),
      ).then(({ data }) =>
        data.requests.reduce<ExistingSampleRequest | null>(
          (latest, req) =>
            !latest || new Date(req.created).getTime() > new Date(latest.created).getTime() ? req : latest,
          null,
        ),
      ),
    enabled: enabled && sampleId !== null,
  });
}

/** The count behind the Inventory sidebar's "Requests" pending-count badge. */
export function usePendingSampleRequestCountQuery() {
  return useQuery({
    queryKey: sampleRequestsQueryKeys.pendingCount(),
    queryFn: () =>
      ApiService.query<{ totalHits: number }>(
        "sampleRequests",
        new URLSearchParams({ role: "OWNER", status: "PENDING", pageSize: "1" }),
      ).then((r) => r.data.totalHits),
  });
}

/**
 * Only needed to size the "transferring will move all subsamples too" warning in the Choose
 * Sample to Prepare dialog, so a plain count suffices; `subSamples` comes back null for a
 * restricted (non-owner) viewer, same as in RequestSampleLocations. This is a Sample query, not a
 * SampleRequest one - no mutation here ever changes it, so it's kept out of
 * `sampleRequestsQueryKeys` rather than being invalidated for no reason alongside them.
 */
export function useSampleSubSampleCountQuery(sampleId: number | null) {
  return useQuery({
    queryKey: ["samples", "subSampleCount", sampleId ?? -1] as const,
    queryFn: () =>
      ApiService.get<{ subSamples: Array<{ id: number }> | null }>("samples", sampleId as number).then(
        (r) => r.data.subSamples?.length ?? null,
      ),
    enabled: sampleId !== null,
  });
}
