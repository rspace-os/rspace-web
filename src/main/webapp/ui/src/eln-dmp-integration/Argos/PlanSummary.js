// @flow

import axios from "axios";
import {
  type AdjustableTableRow,
  type AdjustableTableRowOptions,
} from "../../stores/definitions/Tables";
import RsSet from "../../util/set";

type PlanSummaryJson = {
  id: string,
  label: string,
  grant: string,
  createdAt: number,
  modifiedAt: number,
};

export type PlanSummaryAdustableColumnLabel =
  | "ID"
  | "Grant"
  | "Created At"
  | "Last Modified";

export const planSummaryAdustableColumnLabels: RsSet<PlanSummaryAdustableColumnLabel> =
  new RsSet(["ID", "Grant", "Created At", "Last Modified"]);

export class PlanSummary
  implements AdjustableTableRow<PlanSummaryAdustableColumnLabel>
{
  id: string;
  label: string;
  grant: string;
  createdAt: Date;
  modifiedAt: Date;

  constructor(json: PlanSummaryJson) {
    this.id = json.id;
    this.label = json.label;
    this.grant = json.grant;
    this.createdAt = new Date(json.createdAt);
    this.modifiedAt = new Date(json.modifiedAt);
  }

  getIdAsString(): string {
    return this.id;
  }

  getLabel(): string {
    return this.label;
  }

  isEqual(plan: PlanSummary): boolean {
    return plan.id === this.id;
  }

  adjustableTableOptions(): AdjustableTableRowOptions<PlanSummaryAdustableColumnLabel> {
    return new Map([
      ["ID", () => ({ renderOption: "node", data: this.id })],
      ["Grant", () => ({ renderOption: "node", data: this.grant })],
      [
        "Created At",
        () => ({ renderOption: "node", data: this.createdAt.toLocaleString() }),
      ],
      [
        "Last Modified",
        () => ({
          renderOption: "node",
          data: this.modifiedAt.toLocaleString(),
        }),
      ],
    ]);
  }
}

export type SearchParameters = {|
  like: ?string,
  grantsLike: ?string,
  fundersLike: ?string,
  collaboratorsLike: ?string,
  page: number,
  pageSize: number,
|};

export type Plans = {|
  totalCount: number,
  data: Array<PlanSummary>,
|};

export async function fetchPlanSummaries({
  like,
  grantsLike,
  fundersLike,
  collaboratorsLike,
  page,
  pageSize,
}: SearchParameters): Promise<Plans> {
  // prettier-ignore
  const urlArgs = new URLSearchParams({
    ...((like              ? { like              } : {}): {| like             ?: string |}),
    ...((grantsLike        ? { grantsLike        } : {}): {| grantsLike       ?: string |}),
    ...((fundersLike       ? { fundersLike       } : {}): {| fundersLike      ?: string |}),
    ...((collaboratorsLike ? { collaboratorsLike } : {}): {| collaboratorsLike?: string |}),
    page: page.toString(),
    pageSize: pageSize.toString(),
  });
  const { data } = await axios.get<
    | {|
        success: boolean,
        data: { totalCount: number, data: Array<PlanSummaryJson> },
      |}
    | {| error: mixed |}
  >(`/apps/argos/plans?${urlArgs.toString()}`);
  if (data.success) {
    const plans = data.data;
    return {
      totalCount: plans.totalCount,
      data: plans.data.map((plan) => new PlanSummary(plan)),
    };
  }
  throw data.error;
}
