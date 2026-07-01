import React from "react";
import axios from "@/common/axios";
import i18n from "@/modules/common/i18n";
import AlertContext, { type Alert, mkAlert } from "../../stores/contexts/Alert";
import type * as FetchingData from "../../util/fetchingData";
import { Optional } from "../../util/optional";
import { isoToLocale } from "../../util/Util";

/**
 * Hook + types for the /apps/dmpassistant/* HTTP surface. Modelled on
 * DMPOnline's useDmpOnlineEndpoint — DMP Assistant runs on the same RDA DMP
 * Common Standard shape that DMPRoadmap (and therefore DMPOnline) emits.
 */

type Plan = {
  title: string;
  dmp_id: { identifier: string };
  contact?: {
    name: string;
    affiliation: {
      name: string;
    };
  };
  created: string;
  modified: string;
};

export class DmpSummary {
  #title: string;
  #contact: Optional<{ name: string; affiliation: { name: string } }>;
  #created: string;
  #modified: string;
  #dmp_id: { identifier: string };

  constructor(dmp: Plan) {
    this.#title = dmp.title;
    this.#contact = Optional.fromNullable(dmp.contact);
    this.#dmp_id = dmp.dmp_id;
    this.#created = dmp.created;
    this.#modified = dmp.modified;
  }

  get id(): string {
    // dmp_id.identifier is usually a URL like "http://dmp-pgd.ca/api/v2/plans/19142";
    // the plan id is the last non-empty path segment (tolerating a trailing slash).
    // Anything that is not an http(s) URL (plain ids, DOIs, URNs) is returned
    // unchanged rather than silently corrupted.
    const identifier = this.#dmp_id.identifier;
    try {
      const url = new URL(identifier);
      if (url.protocol !== "http:" && url.protocol !== "https:") {
        return identifier;
      }
      const segments = url.pathname.split("/").filter((s) => s.length > 0);
      return segments.length > 0 ? segments[segments.length - 1] : identifier;
    } catch {
      return identifier;
    }
  }

  get title(): string {
    return this.#title;
  }

  get contactName(): Optional<string> {
    return this.#contact.map((c) => c.name);
  }

  get contactAffiliationName(): Optional<string> {
    return this.#contact.map((c) => c.affiliation.name);
  }

  get created(): string {
    return isoToLocale(this.#created);
  }

  get modified(): string {
    return isoToLocale(this.#modified);
  }
}

/**
 * Imports one or more DMPs from DMP Assistant into the Gallery as a single
 * batch request. The backend persists each DMP in turn and returns the list of
 * imported plans; on success a single success alert is raised; on failure a
 * single error alert is raised with the server's message.
 */
export async function importDmpsIntoGallery(
  dmps: ReadonlyArray<DmpSummary>,
  addAlert: (alert: Alert) => void,
): Promise<void> {
  if (dmps.length === 0) return;
  try {
    const {
      data: { error },
    } = await axios.post<{
      data: unknown;
      error: null | { errorMessages: Array<string> };
    }>(
      "/apps/dmpassistant/importPlans",
      dmps.map((d) => ({ id: d.id, filename: d.title })),
    );
    if (error !== null) throw new Error(error.errorMessages[0]);
    addAlert(
      mkAlert({
        message: i18n.t("dmpIntegrations.endpoint.importSuccess", { ns: "apps", count: dmps.length }),
        variant: "success",
      }),
    );
  } catch (error) {
    if (error instanceof Error) {
      addAlert(
        mkAlert({
          title: i18n.t("dmpIntegrations.endpoint.importFailed", { ns: "apps", count: dmps.length }),
          message: error.message,
          variant: "error",
        }),
      );
    }
  }
}

type ListPlansResponse = {
  data: {
    items: Array<{ dmp: Plan }>;
    total_items: number;
  };
  error: null | {
    errorMessages: Array<string>;
  };
};

export class DmpListing {
  dmps: Array<DmpSummary>;
  totalCount: number;
  page: number;
  pageSize: number;

  // index access can always come back empty at runtime (unknown or stale ids),
  // so the value type is honest about that
  #idMapping: { [id: string]: DmpSummary | undefined };
  #addAlert: (alert: Alert) => void;

  constructor(data: ListPlansResponse["data"], page: number, pageSize: number, addAlert: (alert: Alert) => void) {
    this.dmps = data.items.map(({ dmp }) => new DmpSummary(dmp));
    this.totalCount = data.total_items;
    this.page = page;
    this.pageSize = pageSize;
    this.#idMapping = Object.fromEntries(this.dmps.map((plan) => [plan.id, plan]));
    this.#addAlert = addAlert;
  }

  setPage(page: number): Promise<DmpListing> {
    return listPlans(this.#addAlert, page, this.pageSize);
  }

  setPageSize(pageSize: number): Promise<DmpListing> {
    return listPlans(this.#addAlert, 0, pageSize);
  }

  getById(id: string): DmpSummary | undefined {
    return this.#idMapping[id];
  }
}

async function listPlans(
  addAlert: (alert: Alert) => void,
  page: number = 0,
  pageSize: number = 20,
): Promise<DmpListing> {
  try {
    const {
      data: { data, error },
    } = await axios.get<ListPlansResponse>(`/apps/dmpassistant/plans?page=${page + 1}&per_page=${pageSize}`);
    if (error !== null) throw new Error(error.errorMessages[0]);
    return new DmpListing(data, page, pageSize, addAlert);
  } catch (error) {
    if (error instanceof Error) {
      addAlert(
        mkAlert({
          title: i18n.t("dmpIntegrations.endpoint.listFailed", { ns: "apps" }),
          message: error.message,
          variant: "error",
        }),
      );
      throw new Error(error.message);
    }
    throw new Error(i18n.t("common:apiErrors.unknown"));
  }
}

export function useDmpAssistantEndpoint(): {
  firstPage: FetchingData.Fetched<DmpListing>;
} {
  const { addAlert } = React.useContext(AlertContext);

  const [firstPage, setFirstPage] = React.useState<FetchingData.Fetched<DmpListing>>({ tag: "loading" });

  React.useEffect(() => {
    void (async () => {
      try {
        const newListing = await listPlans(addAlert);
        setFirstPage({ tag: "success", value: newListing });
      } catch (error) {
        if (error instanceof Error) setFirstPage({ tag: "error", error: error.message });
      }
    })();
  }, []);

  return { firstPage };
}
