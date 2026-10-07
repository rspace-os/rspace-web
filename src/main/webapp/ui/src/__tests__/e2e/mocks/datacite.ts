import { HttpResponse, http } from "msw";

let nextDoi = 1;

type DoiState = "draft" | "findable" | "registered";
type DoiData = {
  id?: string;
  attributes?: {
    prefix?: string;
    event?: string;
    doi?: string;
    state?: DoiState;
    titles?: Array<{ title?: string }>;
    metadataVersion?: number;
  };
};

/**
 * A suffix starting with this marker resolves as an already-findable Instrument DOI - Findable state
 * plus an Instrument resourceType, both required by `PidinstLookupManagerImpl.fetchByPid`. A marker,
 * not one fixed DOI, so parallel runs each mint their own and never collide.
 */
export const IMPORTABLE_DOI_PREFIX = "10.5072";
export const IMPORTABLE_DOI_SUFFIX_PREFIX = "e2e-pidinst-import";

function importableDoiRecord(doi: string, suffix: string) {
  return {
    id: doi,
    type: "dois",
    attributes: {
      doi,
      state: "findable" as DoiState,
      titles: [{ title: `E2E Import Target ${suffix}` }],
      types: { resourceType: "Instrument", resourceTypeGeneral: "Instrument" },
      contributors: [{ contributorType: "HostingInstitution", name: "E2E Test Institution" }],
      creators: [{ name: "E2E Instrument Co" }],
      url: "https://example.com/e2e-pidinst-fixture",
    },
  };
}

/**
 * Every DOI this mock has minted, as its last POST/PUT left it, so the retrieve-by-id GET can return it
 * with its `metadataVersion`.
 */
const storedDois = new Map<string, ReturnType<typeof responseData>>();

/** The metadata a DOI's XML carries: everything but the lifecycle event and server-managed fields. */
function metadataOf(attributes: DoiData["attributes"]): string {
  const { event, state, doi, metadataVersion, ...metadata } = attributes ?? {};
  return JSON.stringify(metadata);
}

// Like real DataCite, a PUT advances the revision only when the metadata changes, not on a publish
// or hide that resends the same metadata.
function nextMetadataVersion(id: string, attributes: DoiData["attributes"]): number {
  const stored = storedDois.get(id);
  if (!stored) return 1;
  const current = stored.attributes.metadataVersion;
  return metadataOf(stored.attributes) === metadataOf(attributes) ? current : current + 1;
}

/** A magic word in the outbound title that makes the PUT below fail - a real outage on demand. */
export const FORCE_EXTERNAL_UPDATE_FAILURE_SENTINEL = "e2e-pidinst-forcefail";

function responseData(requestData: DoiData, state: DoiState, metadataVersion: number) {
  const id = requestData.id ?? `10.99999/e2e-igsn-${nextDoi++}`;
  return {
    ...requestData,
    id,
    type: "dois",
    attributes: {
      ...requestData.attributes,
      doi: id,
      state,
      metadataVersion,
    },
  };
}

export const dataciteHandlers = [
  http.get("/heartbeat", () => new HttpResponse("OK")),
  http.get("/client-prefixes", () => HttpResponse.json({ meta: { total: 1 } })),
  http.post("/dois", async ({ request }) => {
    const body = (await request.json()) as { data?: DoiData };
    const requestData = body.data;
    if (!requestData?.attributes?.prefix) {
      return HttpResponse.json({ errors: [{ status: "403" }] }, { status: 403 });
    }
    const data = responseData(requestData, "draft", 0);
    storedDois.set(data.id, data);
    return HttpResponse.json({ data }, { status: 201 });
  }),

  http.get("/dois/:prefix/:suffix", ({ params }) => {
    const { prefix, suffix } = params as { prefix: string; suffix: string };
    if (prefix === IMPORTABLE_DOI_PREFIX && suffix.startsWith(IMPORTABLE_DOI_SUFFIX_PREFIX)) {
      return HttpResponse.json({ data: importableDoiRecord(`${prefix}/${suffix}`, suffix) });
    }
    const stored = storedDois.get(`${prefix}/${suffix}`);
    return stored ? HttpResponse.json({ data: stored }) : new HttpResponse(null, { status: 404 });
  }),
  // DOI paths contain separate prefix and suffix segments.
  http.put("/dois/:prefix/:suffix", async ({ request, params }) => {
    const body = (await request.json()) as { data: DoiData };
    if (body.data?.attributes?.titles?.[0]?.title?.includes(FORCE_EXTERNAL_UPDATE_FAILURE_SENTINEL)) {
      return HttpResponse.json({ errors: [{ status: "500", title: "e2e forced failure" }] }, { status: 500 });
    }
    const id = `${params.prefix}/${params.suffix}`;
    const event = body.data?.attributes?.event;
    const state = event === "publish" ? "findable" : event === "hide" ? "registered" : "draft";
    const metadataVersion = nextMetadataVersion(id, body.data?.attributes);
    const data = responseData({ ...body.data, id }, state, metadataVersion);
    storedDois.set(id, data);
    return HttpResponse.json({ data });
  }),
  http.delete("/dois/:prefix/:suffix", ({ params }) => {
    storedDois.delete(`${params.prefix}/${params.suffix}`);
    return new HttpResponse(null, { status: 204 });
  }),
];
