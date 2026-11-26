import axios from "@/common/axios";
import type { PlanSummary } from "./PlanSummary";

/*
 * Code for completing the process of importing the DMP from Argos and into the
 * Gallery
 */

export async function importPlan(plan: PlanSummary): Promise<void> {
  const id: string = `${plan.id}`;
  const { data } = await axios.post<{ success: boolean }>(`/apps/argos/importPlan/${id}`);
  if (data?.success !== true) {
    throw new Error(`Importing DMP ${id} failed.`);
  }
}
