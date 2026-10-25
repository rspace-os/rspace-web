import * as v from "valibot";

export const researchRecordSchema = v.object({
  id: v.string(),
  title: v.string(),
  owner: v.string(),
  score: v.number(),
  enabled: v.boolean(),
  modifiedAt: v.string(),
});

export const targetFieldDocumentSchema = v.object({
  id: v.string(),
  timeZone: v.string(),
  target: v.string(),
});
