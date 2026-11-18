import { describe, expect, it } from "vitest";
import { createDisplayRule } from "@/modules/common/field-rules/displayRule";
import { withoutOptions } from "@/modules/common/field-rules/ruleOptions";
import {
  changesAreInvalid,
  groupDisplayRules,
  type RuleEditorField,
  ruleFromDraft,
  sanitiseDraft,
  sourceCandidates,
  targetCandidates,
  usableOptions,
} from "../ruleEditing";

const field = (key: string, overrides: Partial<RuleEditorField> = {}): RuleEditorField => ({
  key,
  name: key,
  canBeSource: false,
  options: [],
  rule: null,
  ...overrides,
});

const radio = (key: string, rule: RuleEditorField["rule"] = null) =>
  field(key, { canBeSource: true, options: ["Yes", "No"], rule });

describe("usableOptions", () => {
  it("leaves out blank options and repeats", () => {
    expect(usableOptions(["Yes", "", "  ", "No", "Yes"])).toEqual(["Yes", "No"]);
  });
});

describe("groupDisplayRules", () => {
  it("shows identical rules once, with every field they show, whatever the order of their options", () => {
    const fields = [
      radio("gmo"),
      field("permit", { rule: createDisplayRule("gmo", "IS_ANY_OF", ["Yes", "No"]) }),
      field("genes", { rule: createDisplayRule("gmo", "IS_ANY_OF", ["No", "Yes"]) }),
      field("notes", { rule: createDisplayRule("gmo", "HAS_VALUE", []) }),
    ];
    expect(groupDisplayRules(fields).map((group) => group.targetKeys)).toEqual([["permit", "genes"], ["notes"]]);
  });
});

describe("cycle prevention", () => {
  // level reads gmo, approval reads level
  const fields = [
    radio("gmo"),
    radio("level", createDisplayRule("gmo", "IS_ANY_OF", ["Yes"])),
    radio("approval", createDisplayRule("level", "IS_ANY_OF", ["Yes"])),
  ];

  it("does not offer a field that depends on the target as its source", () => {
    expect(sourceCandidates(fields, ["gmo"]).map((candidate) => candidate.key)).toEqual([]);
    expect(sourceCandidates(fields, ["level"]).map((candidate) => candidate.key)).toEqual(["gmo"]);
  });

  it("does not offer the source, or a field the source depends on, as a target", () => {
    expect(targetCandidates([...fields, field("notes")], "approval", []).map((candidate) => candidate.key)).toEqual([
      "notes",
    ]);
  });

  it("does not offer a field that has a rule or that another draft claimed", () => {
    expect(
      targetCandidates([radio("gmo"), field("a"), field("b")], "gmo", [], new Set(["b"])).map((c) => c.key),
    ).toEqual(["a"]);
  });
});

describe("withoutOptions", () => {
  it("keeps the remaining options of a rule", () => {
    expect(withoutOptions(createDisplayRule("gmo", "IS_ANY_OF", ["Yes", "No"]), ["No"])?.values).toEqual(["Yes"]);
  });

  it("drops a rule left without options", () => {
    expect(withoutOptions(createDisplayRule("gmo", "IS_NONE_OF", ["No"]), ["No"])).toBeNull();
  });

  it("keeps a rule that names no option", () => {
    const rule = createDisplayRule("gmo", "HAS_VALUE", []);
    expect(withoutOptions(rule, ["No"])).toBe(rule);
  });
});

describe("ruleFromDraft", () => {
  it("is null until an operator that needs options has one", () => {
    expect(ruleFromDraft({ sourceFieldKey: "gmo", operator: "IS_ANY_OF", values: [] })).toBeNull();
    expect(ruleFromDraft({ sourceFieldKey: "gmo", operator: "IS_ANY_OF", values: ["Yes"] })).toEqual(
      createDisplayRule("gmo", "IS_ANY_OF", ["Yes"]),
    );
  });

  it("drops options an operator does not take", () => {
    expect(ruleFromDraft({ sourceFieldKey: "gmo", operator: "HAS_NO_VALUE", values: ["Yes"] })?.values).toEqual([]);
  });
});

describe("sanitiseDraft", () => {
  it("drops a source that is gone and options the source no longer offers", () => {
    const draft = { sourceFieldKey: "gmo", operator: "IS_ANY_OF" as const, values: ["Yes", "Unsure"] };
    expect(sanitiseDraft([radio("gmo"), field("permit")], draft, ["permit", "deleted"])).toEqual({
      condition: { sourceFieldKey: "gmo", operator: "IS_ANY_OF", values: ["Yes"] },
      targetKeys: ["permit"],
    });
    expect(sanitiseDraft([field("gmo"), field("permit")], draft, ["permit"]).condition.sourceFieldKey).toBeNull();
  });

  it("drops a source that another rule has since made depend on the draft's target", () => {
    // a second draft wanted level to read gmo; gmo now reads level
    const fields = [radio("gmo", createDisplayRule("level", "IS_ANY_OF", ["Yes"])), radio("level")];
    const draft = { sourceFieldKey: "gmo", operator: "HAS_VALUE" as const, values: [] };
    expect(sanitiseDraft(fields, draft, ["level"]).condition.sourceFieldKey).toBeNull();
  });
});

describe("changesAreInvalid", () => {
  it("rejects a rule naming an option its source does not offer, but not unrelated errors", () => {
    const fields = [radio("gmo"), field("permit"), field("old", { rule: createDisplayRule("gone", "HAS_VALUE", []) })];
    expect(changesAreInvalid(fields, [{ key: "permit", rule: createDisplayRule("gmo", "IS_ANY_OF", ["Maybe"]) }])).toBe(
      true,
    );
    expect(changesAreInvalid(fields, [{ key: "permit", rule: createDisplayRule("gmo", "IS_ANY_OF", ["Yes"]) }])).toBe(
      false,
    );
  });
});
