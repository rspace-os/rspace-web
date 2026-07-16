import { beforeEach, describe, expect, it, vi } from "vitest";
import type SampleModel from "@/stores/models/SampleModel";
import { createTemplateFromOriginSample } from "../operationsApi";

const post = vi.fn((_resource: string, _body: unknown) => Promise.resolve({ data: { id: 42 } }));
vi.mock("@/common/InvApiService", () => ({
  default: { post: (resource: string, body: unknown) => post(resource, body) },
}));

/**
 * A minimal stand-in for the origin's parent sample. `sampleCreationParams` mirrors the real
 * SampleModel contract: a field's content is carried only when its id is in the passed set
 * (otherwise it is blanked), so the test exercises which fields the operation asks to carry.
 */
function fakeSample(): SampleModel {
  const fields = [{ id: 1, name: "Template field", content: "from-template" }];
  const extraFields = [{ id: 10, name: "Notes", content: "keep me" }];
  return {
    infoLoaded: true,
    fetchAdditionalInfo: vi.fn(() => Promise.resolve()),
    fields,
    extraFields,
    sampleCreationParams: (include: Set<number>) =>
      Promise.resolve({
        fields: [
          ...fields.map((f) => ({ name: f.name, content: include.has(f.id) ? f.content : "" })),
          ...extraFields.map((e) => ({ name: e.name, content: include.has(e.id) ? e.content : "" })),
        ],
      }),
  } as unknown as SampleModel;
}

describe("createTemplateFromOriginSample", () => {
  beforeEach(() => post.mockClear());

  it("carries the parent's custom (extra) field values so they become template defaults", async () => {
    await createTemplateFromOriginSample(fakeSample(), "Derived from X");
    const body = post.mock.calls[0][1] as { fields: Array<{ name: string; content: string }> };
    const notes = body.fields.find((f) => f.name === "Notes");
    expect(notes?.content).toBe("keep me");
  });

  it("carries the parent's template-derived field values too", async () => {
    await createTemplateFromOriginSample(fakeSample(), "Derived from X");
    const body = post.mock.calls[0][1] as { fields: Array<{ name: string; content: string }> };
    const templateField = body.fields.find((f) => f.name === "Template field");
    expect(templateField?.content).toBe("from-template");
  });

  it("creates the sample template under the given (parent-derived) name and returns its new id", async () => {
    const id = await createTemplateFromOriginSample(fakeSample(), "Derived from X");
    expect(post).toHaveBeenCalledWith("sampleTemplates", expect.objectContaining({ name: "Derived from X" }));
    expect(id).toBe(42);
  });
});
