import ApiService from "@/common/InvApiService";
import type SampleModel from "@/stores/models/SampleModel";
import type { OperationRequest } from "./types";

/** Minimal view of the created sample returned by the operations endpoint. */
export type OperationResult = { id: number; globalId: string; name: string };

/**
 * POST a configured operation to the thin backend endpoint. The /api/inventory/v1/ prefix is
 * already applied by InvApiService, so the resource is just "operations".
 */
export async function performOperation(request: OperationRequest): Promise<OperationResult> {
  const { data } = await ApiService.post<OperationResult>("operations", request);
  return data;
}

/**
 * Template option (c): create a Sample Template FROM the origin's parent sample and return its new
 * id, so the operation can then create the new sample from that template. Reuses the existing
 * sampleTemplates POST (the same body shape Search.createTemplateFromSample sends). Field content is
 * included so the template carries the sample's values as defaults, which avoids mandatory-field
 * validation when the operation later creates a sample from the template with no field values. No
 * backend change (adr/0003).
 */
export async function createTemplateFromOriginSample(sample: SampleModel, name: string): Promise<number> {
  if (!sample.infoLoaded) await sample.fetchAdditionalInfo();
  const includeContentForFields = new Set(sample.fields.map((field) => field.id));
  const args = { ...(await sample.sampleCreationParams(includeContentForFields)), name };
  const { data } = await ApiService.post<{ id: number }>("sampleTemplates", args);
  return data.id;
}
