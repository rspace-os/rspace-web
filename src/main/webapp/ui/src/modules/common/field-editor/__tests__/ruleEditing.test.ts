import { describe, expect, it } from "vitest";
import { createDisplayRule, sameDisplayRule } from "@/modules/common/field-rules/displayRule";
import { withoutOptions } from "@/modules/common/field-rules/ruleOptions";
import {
  buildRuleGraph,
  type RuleEditorField,
  ruleFromDraft,
  ruleGraphFor,
  ruleIsInvalid,
  sanitiseCondition,
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

describe("cycle prevention", () => {
  // level reads gmo, approval reads level
  const fields = [
    radio("gmo"),
    radio("level", createDisplayRule("gmo", "IS_ANY_OF", ["Yes"])),
    radio("approval", createDisplayRule("level", "IS_ANY_OF", ["Yes"])),
    field("notes"),
  ];
  const sourcesFor = (key: string) =>
    buildRuleGraph(fields)
      .sourcesFor(key)
      .map((candidate) => candidate.key);

  it("does not offer the field itself, or a field that depends on it directly or through others", () => {
    expect(sourcesFor("gmo")).toEqual([]);
    expect(sourcesFor("level")).toEqual(["gmo"]);
    expect(sourcesFor("approval")).toEqual(["gmo", "level"]);
  });

  it("lists every other field in order, with why a rule cannot read it", () => {
    const withSecret = [...fields, field("secret", { hidesValue: true })];
    expect(
      buildRuleGraph(withSecret)
        .candidatesFor("gmo")
        .map(({ field: { key }, unavailable }) => [key, unavailable]),
    ).toEqual([
      ["level", "dependsOnField"],
      ["approval", "dependsOnField"],
      ["notes", "unsupportedType"],
      ["secret", "hidesValue"],
    ]);
  });

  it("offers fields below as well as above, but only radio and choice fields", () => {
    expect(sourcesFor("notes")).toEqual(["gmo", "level", "approval"]);
  });

  it("still finds the candidates when stored rules already form a cycle", () => {
    const looped = [
      radio("a", createDisplayRule("b", "HAS_VALUE", [])),
      radio("b", createDisplayRule("a", "HAS_VALUE", [])),
      radio("c"),
    ];
    const graph = buildRuleGraph(looped);
    expect(graph.sourcesFor("a").map(({ key }) => key)).toEqual(["c"]);
    expect(graph.sourcesFor("c").map(({ key }) => key)).toEqual(["a", "b"]);
  });

  it("lists the fields that read a field directly", () => {
    expect(
      buildRuleGraph(fields)
        .dependentsOf("gmo")
        .map(({ key }) => key),
    ).toEqual(["level"]);
  });
});

describe("ruleGraphFor", () => {
  it("builds the graph once per array, so every field's editor shares it", () => {
    const fields = [radio("gmo"), field("permit")];
    expect(ruleGraphFor(fields)).toBe(ruleGraphFor(fields));
    expect(ruleGraphFor([...fields])).not.toBe(ruleGraphFor(fields));
  });

  it("never reads a field's name", () => {
    const named = (key: string) =>
      Object.defineProperty(radio(key), "name", {
        get: () => {
          throw new Error("read a name");
        },
      });
    const graph = buildRuleGraph([named("a"), named("b")]);
    expect(graph.sourcesFor("a")).toHaveLength(1);
  });

  it("stays cheap on a large template: 150 fields in a chain build in linear time per field", () => {
    const fields = Array.from({ length: 150 }, (_, i) =>
      radio(`f${i}`, i === 0 ? null : createDisplayRule(`f${i - 1}`, "HAS_VALUE", [])),
    );
    const graph = buildRuleGraph(fields);
    // every field may read only the fields above it, since every field below already depends on it
    expect(graph.sourcesFor("f0")).toHaveLength(0);
    expect(graph.sourcesFor("f149")).toHaveLength(149);
    expect(graph.sourcesFor("f75")).toHaveLength(75);
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

describe("sanitiseCondition", () => {
  it("drops a source that is gone and options the source no longer offers", () => {
    const condition = { sourceFieldKey: "gmo", operator: "IS_ANY_OF" as const, values: ["Yes", "Unsure"] };
    expect(sanitiseCondition(buildRuleGraph([radio("gmo"), field("permit")]), "permit", condition)).toEqual({
      sourceFieldKey: "gmo",
      operator: "IS_ANY_OF",
      values: ["Yes"],
    });
    expect(
      sanitiseCondition(buildRuleGraph([field("gmo"), field("permit")]), "permit", condition).sourceFieldKey,
    ).toBeNull();
  });

  it("drops a source that has since come to depend on the field", () => {
    const fields = [radio("gmo", createDisplayRule("level", "IS_ANY_OF", ["Yes"])), radio("level")];
    const condition = { sourceFieldKey: "gmo", operator: "HAS_VALUE" as const, values: [] };
    expect(sanitiseCondition(buildRuleGraph(fields), "level", condition).sourceFieldKey).toBeNull();
  });
});

describe("ruleIsInvalid", () => {
  it("rejects a rule naming an option its source does not offer, but not errors on other fields", () => {
    const fields = [radio("gmo"), field("permit"), field("old", { rule: createDisplayRule("gone", "HAS_VALUE", []) })];
    expect(ruleIsInvalid(fields, "permit", createDisplayRule("gmo", "IS_ANY_OF", ["Maybe"]))).toBe(true);
    expect(ruleIsInvalid(fields, "permit", createDisplayRule("gmo", "IS_ANY_OF", ["Yes"]))).toBe(false);
  });
});

describe("sameDisplayRule", () => {
  it("compares conditions whatever the order of their options", () => {
    expect(
      sameDisplayRule(
        createDisplayRule("gmo", "IS_ANY_OF", ["Yes", "No"]),
        createDisplayRule("gmo", "IS_ANY_OF", ["No", "Yes"]),
      ),
    ).toBe(true);
    expect(
      sameDisplayRule(createDisplayRule("gmo", "IS_ANY_OF", ["Yes"]), createDisplayRule("gmo", "IS_NONE_OF", ["Yes"])),
    ).toBe(false);
    expect(sameDisplayRule(null, null)).toBe(true);
    expect(sameDisplayRule(createDisplayRule("gmo", "HAS_VALUE", []), null)).toBe(false);
  });
});

describe("text and number sources", () => {
  const text = field("batch", { canBeSource: true, sourceKind: "TEXT" });
  const number = field("weight", { canBeSource: true, sourceKind: "NUMBER" });
  const fields = [radio("gmo"), text, number, field("notes")];

  it("can be read by a rule", () => {
    expect(
      buildRuleGraph(fields)
        .sourcesFor("notes")
        .map((candidate) => candidate.key),
    ).toEqual(["gmo", "batch", "weight"]);
  });

  it("keeps a typed value however the source's options read, and trims it into the rule", () => {
    const draft = sanitiseCondition(buildRuleGraph(fields), "notes", {
      sourceFieldKey: "batch",
      operator: "EQUALS",
      values: [" LOT "],
    });
    expect(draft.values).toEqual([" LOT "]);
    expect(ruleFromDraft(draft)).toEqual(createDisplayRule("batch", "EQUALS", ["LOT"]));
    expect(ruleFromDraft({ ...draft, values: ["\u00A0"] })).toBeNull();
  });

  it("replaces an operator the source cannot take with its first, dropping the values", () => {
    expect(
      sanitiseCondition(buildRuleGraph(fields), "notes", {
        sourceFieldKey: "weight",
        operator: "IS_ANY_OF",
        values: ["Yes"],
      }),
    ).toEqual({ sourceFieldKey: "weight", operator: "EQUALS", values: [] });
  });
});
