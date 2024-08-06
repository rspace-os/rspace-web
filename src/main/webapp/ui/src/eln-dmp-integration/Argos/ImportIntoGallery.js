//@flow

import axios from "axios";
import { type PlanSummary } from "./PlanSummary";

/*
 * Code for completing the process of importing the DMP from Argos and into the
 * Gallery
 */

export async function importPlan(plan: PlanSummary): Promise<void> {
  const id: string = `${plan.id}`;
  await axios.post<void, void>(`/apps/argos/importPlan/${id}`);
  // eslint-disable-next-line no-undef
  gallery();
}
