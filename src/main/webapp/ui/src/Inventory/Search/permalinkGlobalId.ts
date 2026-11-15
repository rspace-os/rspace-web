import { type GlobalId, globalId } from "@/stores/definitions/BaseRecord";
import type { Permalink, PermalinkType } from "@/stores/definitions/Search";

// PermalinkType uses URL-lowercase names; globalId expects camelCase keys
const permalinkTypeToGlobalIdType = {
  sample: "sample",
  container: "container",
  subsample: "subsample",
  sampletemplate: "sampleTemplate",
  instrument: "instrument",
  instrumenttemplate: "instrumentTemplate",
} as const satisfies Record<PermalinkType, Parameters<typeof globalId>[0]["type"]>;

export function permalinkGlobalId(permalink: Permalink): GlobalId {
  return globalId({ type: permalinkTypeToGlobalIdType[permalink.type], id: permalink.id });
}
