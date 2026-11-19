import { describe, expect, it } from "vitest";
import { createDisplayRule } from "@/modules/common/field-rules/displayRule";
import {
  adoptSavedIds,
  changeType,
  deleteField,
  type EditorField,
  fieldProblems,
  fromServer,
  newField,
  removeOption,
  renameOption,
  toSaveItem,
} from "../editorModel";
import type { FormFieldEditorItem } from "../formFieldsApi";

const GMO = "6f1c2e0a-0000-4000-8000-000000000001";
const LEVEL = "6f1c2e0a-0000-4000-8000-000000000002";

const item = (type: FormFieldEditorItem["type"], source: Record<string, unknown>, extra = {}): FormFieldEditorItem => ({
  id: 7,
  type,
  fieldKey: GMO,
  displayRule: null,
  canDelete: true,
  source: { name: "Field", mandatory: true, ...source },
  ...extra,
});

/** A server item, read into the editor and written back unchanged. */
const roundTrip = (serverItem: FormFieldEditorItem) => toSaveItem(fromServer(serverItem)).source;

describe("fromServer and toSaveItem", () => {
  it("keep every number setting", () => {
    const source = { minNumberValue: "1.5", maxNumberValue: "10", decimalPlaces: "2", defaultNumberValue: "3" };
    expect(roundTrip(item("Number", source))).toEqual({ name: "Field", mandatory: true, ...source });
  });

  it("keep the string password flag and default", () => {
    const source = { ifPassword: "yes", defaultStringValue: "secret" };
    expect(roundTrip(item("String", source))).toEqual({ name: "Field", mandatory: true, ...source });
  });

  it("keep radio options in the stored format, with the picklist and sort flags and the default", () => {
    const source = {
      radioValues: "fieldRadios=Yes&fieldRadios=No",
      radioSelected: "No",
      showAsPickList: true,
      sortAlphabetic: true,
    };
    expect(roundTrip(item("Radio", source))).toEqual({ name: "Field", fieldName: "Field", mandatory: true, ...source });
  });

  it("keep choice options, the multiple flag and several defaults", () => {
    const source = {
      choiceValues: "fieldChoices=a&fieldChoices=b&fieldChoices=c",
      multipleChoice: "yes",
      selectedValues: "fieldSelectedChoices=a&fieldSelectedChoices=c",
    };
    expect(roundTrip(item("Choice", source))).toEqual({
      name: "Field",
      fieldName: "Field",
      mandatory: true,
      ...source,
    });
  });

  it("read dates in the field's format and write them back in it", () => {
    const source = {
      dateFormat: "dd MMM yyyy",
      minValue: "01 Jan 2026",
      maxValue: "31 Dec 2026",
      defaultValue: "18 Nov 2026",
    };
    const field = fromServer(item("Date", source));

    expect(field).toMatchObject({ min: "2026-01-01", max: "2026-12-31", defaultValue: "2026-11-18" });
    expect(toSaveItem(field).source).toEqual({ name: "Field", mandatory: true, ...source });
  });

  it("write dates in a newly chosen format", () => {
    const field = fromServer(item("Date", { dateFormat: "yyyy-MM-dd", defaultValue: "2026-11-18" }));
    const reformatted = { ...field, format: "dd/MM/yyyy" } as EditorField;
    expect(toSaveItem(reformatted).source).toMatchObject({ dateFormat: "dd/MM/yyyy", defaultValue: "18/11/2026" });
  });

  it("read 12-hour times and write them back", () => {
    const source = { timeFormat: "hh:mm a", minValue: "09:00 AM", maxValue: "05:30 PM", defaultValue: "" };
    const field = fromServer(item("Time", source));

    expect(field).toMatchObject({ min: "09:00", max: "17:30", defaultValue: "" });
    expect(toSaveItem(field).source).toEqual({ name: "Field", mandatory: true, ...source });
  });

  it("keep a stored date the editor cannot read, and send it back unchanged", () => {
    // another locale's month abbreviation
    const source = { dateFormat: "dd MMM yyyy", minValue: "18 nov. 2026", maxValue: "31 Dec 2026", defaultValue: "" };
    const field = fromServer(item("Date", source));

    expect(field).toMatchObject({ min: "", max: "2026-12-31", unread: { min: "18 nov. 2026" } });
    expect(toSaveItem(field).source).toMatchObject({ minValue: "18 nov. 2026", maxValue: "31 Dec 2026" });
  });

  it("load a field whose format the editor does not know, keeping its values", () => {
    // "F" is a Java pattern letter that date-fns rejects with an error
    const source = { timeFormat: "HH:mm F", minValue: "09:00 2", maxValue: "", defaultValue: "" };
    const field = fromServer(item("Time", source));

    expect(field).toMatchObject({ format: "HH:mm F", unread: { min: "09:00 2" } });
    expect(toSaveItem(field).source).toMatchObject(source);
  });

  it("replace an unread value once the author enters a new one", () => {
    const field = fromServer(item("Date", { dateFormat: "dd MMM yyyy", defaultValue: "18 nov. 2026" }));
    const edited = { ...field, defaultValue: "2026-11-20", unread: {} } as EditorField;
    expect(toSaveItem(edited).source).toMatchObject({ defaultValue: "20 Nov 2026" });
  });

  it("keep the text default", () => {
    expect(roundTrip(item("Text", { defaultValue: "<p>Hello</p>" }))).toEqual({
      name: "Field",
      mandatory: true,
      defaultValue: "<p>Hello</p>",
    });
  });

  it.each(["Uri", "Reference", "Attachment"] as const)("keep %s fields with their name and mandatory flag", (type) => {
    const saved = toSaveItem(fromServer(item(type, {})));
    expect(saved).toMatchObject({ id: 7, type, source: { name: "Field", mandatory: true } });
  });

  it("send the options and default as saved, not as first stored, once the edits are taken back", () => {
    const legacy = fromServer(
      item("Choice", {
        choiceValues: "fieldChoices=a&&fieldChoices=b",
        multipleChoice: "no",
        selectedValues: "fieldSelectedChoices=a&junk",
      }),
    );
    if (legacy.type !== "Choice") throw new Error("not a choice");
    const sent = { ...legacy, options: [...legacy.options, "c"] };
    const saved = fromServer({ ...item("Choice", toSaveItem(sent).source), id: 7 });
    // while the save was in flight the author added "d"; later both new options are removed
    const [current] = adoptSavedIds([{ ...sent, options: [...sent.options, "d"] }], [saved], [sent]);
    const takenBack = { ...current, options: legacy.options } as EditorField;

    expect(toSaveItem(takenBack).source).toMatchObject({
      choiceValues: "fieldChoices=a&fieldChoices=b",
      selectedValues: "fieldSelectedChoices=a",
    });
  });

  it("read a stored default as its options were read, once the options are written anew", () => {
    const radio = fromServer(item("Radio", { radioValues: "fieldRadios=X&fieldRadios=Y=1", radioSelected: "Y=1" }));
    const choice = fromServer(
      item("Choice", { choiceValues: "fieldChoices=X&fieldChoices=Y=1", selectedValues: "fieldSelectedChoices=Y=1" }),
    );
    if (radio.type !== "Radio" || choice.type !== "Choice") throw new Error("not a radio and a choice");

    expect(toSaveItem({ ...radio, options: [...radio.options, "Z"] }).source).toMatchObject({
      radioValues: "fieldRadios=X&fieldRadios=Y&fieldRadios=Z",
      radioSelected: "Y",
    });
    expect(toSaveItem({ ...choice, options: [...choice.options, "Z"] }).source).toMatchObject({
      selectedValues: "fieldSelectedChoices=Y",
    });
    // a default that, read that way, names no option is cleared
    expect(toSaveItem({ ...radio, options: ["X", "Z"] }).source).toMatchObject({ radioSelected: "" });
  });

  it("take the default the server saved unless the author changed it while the save was in flight", () => {
    const legacy = fromServer(item("Radio", { radioValues: "fieldRadios=X&fieldRadios=Y", radioSelected: "Q" }));
    if (legacy.type !== "Radio") throw new Error("not a radio");
    // Q is not an option, so writing the options anew clears it
    const sent = { ...legacy, options: [...legacy.options, "Z"] };
    const saved = fromServer(item("Radio", toSaveItem(sent).source));
    const editedName = { ...sent, name: "Renamed" };

    const [kept] = adoptSavedIds([editedName], [saved], [sent]);
    expect(kept).toMatchObject({ defaultValue: "", name: "Renamed" });
    // the options are now as saved, so they and the default go back as stored: never Q again
    expect(toSaveItem(kept).source).toMatchObject({ radioSelected: "" });

    const [chosen] = adoptSavedIds([{ ...sent, defaultValue: "Y" }], [saved], [sent]);
    expect(chosen).toMatchObject({ defaultValue: "Y" });
  });

  it("send a rule, and an explicit null for a field without one", () => {
    const rule = { sourceFieldKey: LEVEL, operator: "HAS_VALUE", values: null };
    expect(toSaveItem(fromServer(item("Text", {}, { displayRule: rule }))).displayRule).toEqual({
      sourceFieldKey: LEVEL,
      operator: "HAS_VALUE",
    });
    expect(toSaveItem(fromServer(item("Text", {}))).displayRule).toBeNull();
  });
});

const radio = (key: string, options: Array<string>, defaultValue = ""): EditorField => ({
  ...newField("Radio"),
  key,
  name: key,
  type: "Radio",
  options,
  defaultValue,
  showAsPickList: false,
  sortAlphabetic: false,
});
const dependent = (key: string, values: Array<string>): EditorField => ({
  ...newField("String"),
  key,
  name: key,
  rule: createDisplayRule(GMO, "IS_ANY_OF", values),
});

describe("edits that keep rules consistent", () => {
  it("give a new field a lowercase UUID key at once", () => {
    expect(newField("Radio").key).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);
  });

  it("clear the rules that read a deleted field", () => {
    const fields = deleteField([radio(GMO, ["Yes"]), dependent("permit", ["Yes"])], GMO);
    expect(fields).toEqual([expect.objectContaining({ key: "permit", rule: null })]);
  });

  it("remove a removed option from rules and defaults, dropping a rule left with none", () => {
    const fields = removeOption(
      [radio(GMO, ["Yes", "No", "Maybe"], "Yes"), dependent("a", ["Yes"]), dependent("b", ["Yes", "Maybe"])],
      GMO,
      0,
    );
    expect(fields[0]).toMatchObject({ options: ["No", "Maybe"], defaultValue: "" });
    expect(fields[1].rule).toBeNull();
    expect(fields[2].rule?.values).toEqual(["Maybe"]);
  });

  it("leave rules alone when another option still has the removed value", () => {
    const fields = removeOption([radio(GMO, ["Yes", "Yes"]), dependent("a", ["Yes"])], GMO, 1);
    expect(fields[1].rule?.values).toEqual(["Yes"]);
  });

  it("carry a default over a rename", () => {
    expect(renameOption([radio(GMO, ["Yes"], "Yes")], GMO, 0, "Yes!")[0]).toMatchObject({
      options: ["Yes!"],
      defaultValue: "Yes!",
    });
  });

  it("carry a default stored in an old format over a rename of the option it was read as", () => {
    const stored = fromServer(item("Radio", { radioValues: "fieldRadios=X&fieldRadios=Y=1", radioSelected: "Y=1" }));

    const [renamed] = renameOption([stored], GMO, 1, "Yes");

    expect(toSaveItem(renamed).source).toMatchObject({
      radioValues: "fieldRadios=X&fieldRadios=Yes",
      radioSelected: "Yes",
    });
  });

  it("leave an unset default alone while a new option is typed", () => {
    expect(renameOption([radio(GMO, ["Yes", ""])], GMO, 1, "No")[0]).toMatchObject({
      options: ["Yes", "No"],
      defaultValue: "",
    });
  });

  it("clear rules when an unsaved source becomes a type that cannot be read", () => {
    const fields = changeType([radio(GMO, ["Yes"]), dependent("a", ["Yes"])], GMO, "Number");
    expect(fields[0]).toMatchObject({ key: GMO, type: "Number" });
    expect(fields[1].rule).toBeNull();
  });

  it("keep options and rules when a source changes between radio and choice", () => {
    const fields = changeType([radio(GMO, ["Yes"]), dependent("a", ["Yes"])], GMO, "Choice");
    expect(fields[0]).toMatchObject({ type: "Choice", options: ["Yes"] });
    expect(fields[1].rule).not.toBeNull();
  });
});

describe("fieldProblems", () => {
  it("needs a name and at least one usable option", () => {
    expect(fieldProblems(radio("", []))).toEqual({ name: "nameRequired", options: "optionsRequired" });
    expect(fieldProblems(radio("r", ["a", "a"]))).toEqual({ options: "optionsInvalid" });
    expect(fieldProblems(radio("r", ["a=b"]))).toEqual({ options: "optionCharacters" });
  });

  it("checks number bounds the way the server does", () => {
    const number = { ...newField("Number"), name: "n" } as EditorField & { type: "Number" };
    expect(fieldProblems({ ...number, min: "abc" })).toEqual({ min: "notANumber" });
    expect(fieldProblems({ ...number, min: "5", max: "1" })).toEqual({ min: "minAboveMax" });
    expect(fieldProblems({ ...number, min: "5", defaultValue: "1" })).toEqual({ defaultValue: "belowMin" });
    expect(fieldProblems({ ...number, decimalPlaces: "200" })).toEqual({ decimalPlaces: "decimalPlaces" });
  });

  it("checks date bounds", () => {
    const date = { ...newField("Date"), name: "d" } as EditorField & { type: "Date" };
    expect(fieldProblems({ ...date, max: "2026-01-01", defaultValue: "2026-02-01" })).toEqual({
      defaultValue: "aboveMax",
    });
  });
});
