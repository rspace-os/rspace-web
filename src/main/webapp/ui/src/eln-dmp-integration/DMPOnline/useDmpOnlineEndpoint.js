//@flow strict

import React from "react";
import axios from "axios";
import { isoToLocale } from "../../util/Util";
import AlertContext, { mkAlert, type Alert } from "../../stores/contexts/Alert";
import * as FetchingData from "../../util/fetchingData";
import { Optional } from "../../util/optional";

/**
 * This module provides the functionality for interacting with the
 * /apps/dmponline endpoint. It exposes a function for calling the
 * /apps/dmponline/plans to get a list of all available plans, and
 * from there operations to import a specific plan into the Gallery.
 */

type Plan = {
  title: string,
  dmp_id: {| identifier: string |},
  contact?: {
    name: string,
    affiliation: {
      name: string,
      ...
    },
    ...
  },
  created: string,
  modified: string,
  ...
};

export class DmpSummary {
  #title: string;
  #contact: Optional<{ name: string, affiliation: { name: string, ... }, ... }>;
  #created: string;
  #modified: string;
  #dmp_id: {| identifier: string |};

  #addAlert: (Alert) => void;

  constructor(dmp: Plan, addAlert: (Alert) => void) {
    this.#title = dmp.title;
    this.#contact = Optional.fromNullable(dmp.contact);
    this.#dmp_id = dmp.dmp_id;
    this.#created = dmp.created;
    this.#modified = dmp.modified;
    this.#addAlert = addAlert;
  }

  get id(): string {
    return this.#dmp_id.identifier;
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

  async importIntoGallery(): Promise<void> {
    try {
      await axios.post<void, void>(
        `apps/dmponline/importPlan?id=${encodeURIComponent(
          this.#dmp_id.identifier
        )}&filename=${this.#title}`
      );
      this.#addAlert(
        mkAlert({
          message: "Successfully imported DMP.",
          variant: "success",
        })
      );
    } catch (error) {
      this.#addAlert(
        mkAlert({
          title: "Failed to import DMP.",
          message: error.message,
          variant: "error",
        })
      );
    }
  }
}

type ListPlansResponse = {|
  data: {|
    items: Array<{| dmp: Plan |}>,
    total_items: number,
  |},
  error: null | {|
    errorMessages: Array<string>,
  |},
|};

export class DmpListing {
  dmps: Array<DmpSummary>;
  totalCount: number;
  page: number;
  pageSize: number;

  #idMapping: { [string]: DmpSummary };
  #addAlert: (Alert) => void;

  constructor(
    data: ListPlansResponse["data"],
    page: number,
    pageSize: number,
    addAlert: (Alert) => void
  ) {
    this.dmps = data.items.map(({ dmp }) => new DmpSummary(dmp, addAlert));
    this.totalCount = data.total_items;
    this.page = page;
    this.pageSize = pageSize;
    this.#idMapping = Object.fromEntries(
      this.dmps.map((plan) => [plan.id, plan])
    );
    this.#addAlert = addAlert;
  }

  setPage(page: number): Promise<DmpListing> {
    return listPlans(this.#addAlert, page, this.pageSize);
  }

  setPageSize(pageSize: number): Promise<DmpListing> {
    return listPlans(this.#addAlert, 0, pageSize);
  }

  getById(id: string): DmpSummary {
    return this.#idMapping[id];
  }
}

// do we want to expose a function that takes pagination args?
async function listPlans(
  addAlert: (Alert) => void,
  page: number = 0,
  pageSize: number = 20
): Promise<DmpListing> {
  try {
    const {
      data: { data, error },
    } = await axios.get<{|
      data: {|
        items: Array<{| dmp: Plan |}>,
        total_items: number,
      |},
      error: null | {|
        errorMessages: Array<string>,
      |},
    |}>(`/apps/dmponline/plans?page=${page + 1}&per_page=${pageSize}`);
    if (error !== null) throw new Error(error.errorMessages[0]);
    return new DmpListing(data, page, pageSize, addAlert);
  } catch (error) {
    addAlert(
      mkAlert({
        title: "Failed to get available dmps.",
        message: error.message,
        variant: "error",
      })
    );
    throw new Error(error.message);
  }
}

export function useDmpOnlineEndpoint(): {|
  firstPage: FetchingData.Fetched<DmpListing>,
|} {
  const { addAlert } = React.useContext(AlertContext);

  const [firstPage, setFirstPage] = React.useState<
    FetchingData.Fetched<DmpListing>
  >({ tag: "loading" });

  React.useEffect(() => {
    void (async () => {
      try {
        const newListing = await listPlans(addAlert);
        setFirstPage({ tag: "success", value: newListing });
      } catch (error) {
        setFirstPage({ tag: "error", error: error.message });
      }
    })();
  }, []);

  return { firstPage };
}
