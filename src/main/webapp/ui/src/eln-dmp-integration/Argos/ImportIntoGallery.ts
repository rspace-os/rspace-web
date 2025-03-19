//@flow

import axios from "@/common/axios";
import { type PlanSummary } from "./PlanSummary";

/*
 * Code for completing the process of importing the DMP from Argos and into the
 * Gallery
 */

export async function importPlan(plan: PlanSummary): Promise<void> {
  const id: string = `${plan.id}`;
  await axios.post<void, void>(`/apps/argos/importPlan/${id}`);
  // @ts-expect-error gallery is a global on the old gallery
  // eslint-disable-next-line no-undef, @typescript-eslint/no-unsafe-call
  gallery();
}
