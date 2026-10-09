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
  otherActiveForSample: (sampleId: number, excludeRequestId: number) =>
    [...sampleRequestsQueryKeys.all, "otherActiveForSample", sampleId, excludeRequestId] as const,
  forSample: (sampleId: number) => [...sampleRequestsQueryKeys.all, "forSample", sampleId] as const,
  pendingCount: () => [...sampleRequestsQueryKeys.all, "pendingCount"] as const,
  sampleWithSubSamples: (sampleId: number) =>
    [...sampleRequestsQueryKeys.all, "sampleWithSubSamples", sampleId] as const,
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
 *
 * excludeRequestId is part of the query key, not just the queryFn's filtering logic: two different
 * requests against the same sample would otherwise share one cache entry keyed on sampleId alone,
 * so opening one just after another could briefly show the previous request's own result,
 * including that request incorrectly appearing in its own "will be rejected" list.
 */
export function useOtherActiveSampleRequestsQuery(sampleId: number | null, excludeRequestId: number) {
  return useQuery({
    queryKey: sampleRequestsQueryKeys.otherActiveForSample(sampleId ?? -1, excludeRequestId),
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

export type ApiContainerInfo = { id: number; globalId: string; name: string };
export type ApiSubSampleInfo = {
  id: number;
  globalId: string;
  name: string;
  parentContainers: Array<ApiContainerInfo>;
};
// The backend nulls `subSamples` (rather than omitting/emptying it) when the viewer only has
// limited/public read access to the sample, so a null here specifically means "restricted", not
// "no subsamples".
export type ApiSampleWithSubSamples = {
  subSamples: Array<ApiSubSampleInfo> | null;
  owner: { firstName: string; lastName: string };
};

/**
 * The sample's subsamples (with their current locations) and owner - shared by the "transferring
 * will move all subsamples too" warning in the Choose Sample to Prepare dialog (which only needs
 * `subSamples.length`) and RequestSampleLocations' full locations table (which needs the rest),
 * rather than each independently fetching the same GET /samples/{id} endpoint.
 *
 * Although this is Sample data, not SampleRequest data, the key lives under
 * `sampleRequestsQueryKeys` specifically so a transfer's broad invalidation (see
 * invalidateAndSeedDetailStatus in ./mutations.ts) covers it too - a transfer changes the sample's
 * owner, which can flip this between "loaded" and "restricted" for whoever's now viewing it.
 */
export function useSampleWithSubSamplesQuery(sampleId: number | null) {
  return useQuery({
    queryKey: sampleRequestsQueryKeys.sampleWithSubSamples(sampleId ?? -1),
    queryFn: () => ApiService.get<ApiSampleWithSubSamples>("samples", sampleId as number).then((r) => r.data),
    enabled: sampleId !== null,
  });
}
