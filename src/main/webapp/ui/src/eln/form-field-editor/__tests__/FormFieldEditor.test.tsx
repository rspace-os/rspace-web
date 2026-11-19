import { afterEach, describe, expect, it } from "vitest";
import "@/__tests__/__mocks__/matchMedia";
import { ThemeProvider } from "@mui/material/styles";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { createRef } from "react";
import { expectAccessible } from "@/__tests__/accessibility";
import { renderWithRealI18n } from "@/__tests__/helpers/realI18n";
import { silenceConsole } from "@/__tests__/helpers/silenceConsole";
import { server } from "@/__tests__/mswServer";
import ConfirmProvider from "@/components/ConfirmProvider";
import common from "@/modules/common/i18n/locales/en-US/common.json";
import workspace from "@/modules/common/i18n/locales/en-US/workspace.json";
import materialTheme from "@/theme";
import FormFieldEditorRoot, { type FormFieldEditorHandle } from "../FormFieldEditorRoot";
import type { FormFieldEditorItem, FormFieldSaveItem } from "../formFieldsApi";

const FIELDS_URL = "/workspace/editor/form/ajax/fields";
const GMO = "6f1c2e0a-0000-4000-8000-000000000001";
const PERMIT = "6f1c2e0a-0000-4000-8000-000000000002";

const saved = (
  id: number,
  type: FormFieldEditorItem["type"],
  source: Record<string, unknown>,
  extra: Partial<FormFieldEditorItem> = {},
): FormFieldEditorItem => ({
  id,
  type,
  fieldKey: `6f1c2e0a-0000-4000-8000-00000000000${id}`,
  displayRule: null,
  canDelete: true,
  source: { mandatory: false, ...source },
  ...extra,
});

const gmo = saved(
  1,
  "Radio",
  { name: "Is this a GMO?", radioValues: "fieldRadios=Yes&fieldRadios=No" },
  { fieldKey: GMO },
);
const permit = saved(
  2,
  "String",
  { name: "GMO permit", ifPassword: "no", defaultStringValue: "" },
  { fieldKey: PERMIT, displayRule: { sourceFieldKey: GMO, operator: "IS_ANY_OF", values: ["Yes"] } },
);

/** Serves the fields, echoes each save back with ids for new fields, and records what was sent. */
function serveFields(items: Array<FormFieldEditorItem>, saveResponse?: () => Response | Promise<Response>) {
  const posts: Array<Array<FormFieldSaveItem>> = [];
  server.use(
    http.get(FIELDS_URL, () => HttpResponse.json({ data: items, errorMsg: null })),
    http.post(FIELDS_URL, async ({ request }) => {
      const body = (await request.json()) as Array<FormFieldSaveItem>;
      posts.push(body);
      if (saveResponse) return saveResponse();
      return HttpResponse.json({
        data: body.map((item, i) => ({ ...item, id: item.id ?? 100 + i, canDelete: true })),
        errorMsg: null,
      });
    }),
  );
  return posts;
}

async function renderRoot({ editable = true } = {}) {
  const handle = createRef<FormFieldEditorHandle>();
  const result = await renderWithRealI18n(
    <ThemeProvider theme={materialTheme}>
      <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>
        <ConfirmProvider>
          <FormFieldEditorRoot formId={5} editable={editable} ref={handle} />
        </ConfirmProvider>
      </QueryClientProvider>
    </ThemeProvider>,
    { resources: { common, workspace }, defaultNS: "workspace" },
  );
  return { handle, ...result };
}

async function renderEditor(options: { editable?: boolean } = {}) {
  const rendered = await renderRoot(options);
  // the rules panel appears once there is a field
  await screen.findByText(/^(This form has no fields yet\.|Display rules)$/);
  return rendered;
}

const card = (name: string) => screen.getByRole("region", { name });

async function addField(user: ReturnType<typeof userEvent.setup>, typeLabel: RegExp, name: string) {
  await user.click(screen.getByRole("button", { name: "Add field" }));
  await user.click(screen.getByRole("menuitem", { name: typeLabel }));
  const nameInput = screen.getAllByRole("textbox", { name: "Name" }).at(-1);
  if (!nameInput) throw new Error("no name input");
  expect(nameInput).toHaveFocus();
  await user.paste(name);
}

async function addOption(user: ReturnType<typeof userEvent.setup>, fieldName: string, option: string) {
  await user.click(within(card(fieldName)).getByRole("button", { name: "Add option" }));
  const inputs = within(card(fieldName)).getAllByRole("textbox", { name: /^Option \d+$/ });
  await user.click(inputs[inputs.length - 1]);
  await user.paste(option);
}

/** A response the test releases, to act while a save is in flight. */
function heldResponse() {
  let release: () => void = () => {};
  const released = new Promise<void>((resolve) => {
    release = resolve;
  });
  return { release, released };
}

afterEach(() => {
  server.resetHandlers();
});

describe("FormFieldEditor", () => {
  it("shows each field's rule summary and is accessible", async () => {
    serveFields([gmo, permit]);
    const { container } = await renderEditor();

    expect(within(card("GMO permit")).getByText("Shown when Is this a GMO? is Yes")).toBeInTheDocument();
    await expectAccessible(container);
  });

  it("gives a new field its key when it is added, and sends it as new", async () => {
    const user = userEvent.setup();
    const posts = serveFields([]);
    const { handle } = await renderEditor();

    await addField(user, /^String/, "Hazard details");
    await handle.current?.save();

    expect(posts[0][0]).toMatchObject({ id: null, type: "String", source: { name: "Hazard details" } });
    expect(posts[0][0].fieldKey).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);
    expect(handle.current?.isDirty()).toBe(false);
  });

  it("uses new fields in a rule before the first save", async () => {
    const user = userEvent.setup();
    const posts = serveFields([]);
    const { handle } = await renderEditor();
    await addField(user, /^Radio/, "Hazardous?");
    await addOption(user, "Hazardous?", "Yes");
    await addField(user, /^String/, "Hazard details");

    await user.click(screen.getByRole("button", { name: "Add rule" }));
    await user.click(screen.getByRole("combobox", { name: "Options" }));
    await user.click(screen.getByRole("option", { name: "Yes" }));
    await user.click(screen.getByRole("combobox", { name: "Show these fields" }));
    await user.click(screen.getByRole("option", { name: "Hazard details" }));
    await handle.current?.save();

    const [source, target] = posts[0];
    expect(target.displayRule).toEqual({ sourceFieldKey: source.fieldKey, operator: "IS_ANY_OF", values: ["Yes"] });
  });

  it("keeps edits made while a save is in flight, and gives the saved new fields their ids", async () => {
    const user = userEvent.setup();
    const held = heldResponse();
    let release = true;
    const posts = serveFields([gmo], async () => {
      if (release) {
        release = false;
        await held.released;
      }
      const body = posts.at(-1) ?? [];
      return HttpResponse.json({
        data: body.map((item, i) => ({ ...item, id: item.id ?? 100 + i, canDelete: true })),
        errorMsg: null,
      });
    });
    const { handle } = await renderEditor();
    await addField(user, /^String/, "Permit");

    const saving = handle.current?.save();
    const gmoName = within(card("Is this a GMO?")).getByRole("textbox", { name: "Name" });
    await user.clear(gmoName);
    await user.paste("GMO status");
    held.release();
    await saving;

    expect(within(card("GMO status")).getByRole("textbox", { name: "Name" })).toHaveValue("GMO status");
    expect(handle.current?.isDirty()).toBe(true);
    await handle.current?.save();
    expect(posts[1]).toEqual([
      expect.objectContaining({ id: 1, source: expect.objectContaining({ name: "GMO status" }) }),
      expect.objectContaining({ id: 101, source: expect.objectContaining({ name: "Permit" }) }),
    ]);
  });

  it("sends one save when the toolbar asks again while a save is in flight", async () => {
    const user = userEvent.setup();
    const held = heldResponse();
    const posts = serveFields([gmo], async () => {
      await held.released;
      return HttpResponse.json({ data: [gmo], errorMsg: null });
    });
    const { handle } = await renderEditor();
    await user.click(within(card("Is this a GMO?")).getByRole("switch", { name: "Mandatory" }));

    const first = handle.current?.save();
    const second = handle.current?.save();
    held.release();
    await Promise.all([first, second]);

    expect(posts).toHaveLength(1);
  });

  it("does not let stored fields that break the editor's checks block saving other changes", async () => {
    const user = userEvent.setup();
    // written through the forms API, which does not check options the way the editor does
    const apiField = saved(3, "Radio", { name: "x".repeat(60), radioValues: "fieldRadios=a=b&fieldRadios=a=b" });
    const posts = serveFields([apiField, permit]);
    const { handle } = await renderEditor();

    await user.click(within(card("GMO permit")).getByRole("switch", { name: "Mandatory" }));
    await handle.current?.save();

    expect(posts).toHaveLength(1);
    expect(screen.queryByText("Use at most 50 characters.")).not.toBeInTheDocument();
  });

  it("checks a stored field again once it is changed", async () => {
    const user = userEvent.setup();
    const apiField = saved(3, "Radio", { name: "x".repeat(60), radioValues: "fieldRadios=a&fieldRadios=b" });
    const posts = serveFields([apiField]);
    const { handle } = await renderEditor();

    await user.click(within(card("x".repeat(60))).getByRole("switch", { name: "Mandatory" }));
    await expect(handle.current?.save()).rejects.toThrow();

    expect(screen.getByText("Use at most 50 characters.")).toBeInTheDocument();
    expect(posts).toHaveLength(0);
  });

  it("asks before deleting a field that a rule reads, then clears that rule explicitly", async () => {
    const user = userEvent.setup();
    const posts = serveFields([gmo, permit]);
    const { handle } = await renderEditor();

    await user.click(screen.getByRole("button", { name: "Delete field Is this a GMO?" }));
    const dialog = await screen.findByRole("dialog", { name: "Delete Is this a GMO??" });
    expect(within(dialog).getByText("The display rule of GMO permit reads it and will be removed.")).toBeVisible();
    await user.click(within(dialog).getByRole("button", { name: "Delete" }));
    await handle.current?.save();

    expect(posts[0]).toEqual([expect.objectContaining({ id: 2, fieldKey: PERMIT, displayRule: null })]);
  });

  it("keeps the field when deleting is cancelled", async () => {
    const user = userEvent.setup();
    serveFields([gmo, permit]);
    await renderEditor();

    await user.click(screen.getByRole("button", { name: "Delete field Is this a GMO?" }));
    await user.click(within(await screen.findByRole("dialog")).getByRole("button", { name: "Cancel" }));

    expect(await screen.findByRole("region", { name: "Is this a GMO?" })).toBeInTheDocument();
    expect(within(card("GMO permit")).getByText("Shown when Is this a GMO? is Yes")).toBeInTheDocument();
  });

  it("moves focus to the next field after a delete", async () => {
    const user = userEvent.setup();
    serveFields([permit, gmo]);
    await renderEditor();

    await user.click(screen.getByRole("button", { name: "Delete field GMO permit" }));

    expect(within(card("Is this a GMO?")).getByRole("textbox", { name: "Name" })).toHaveFocus();
  });

  it("moves focus to Add field after deleting the last field", async () => {
    const user = userEvent.setup();
    serveFields([gmo, permit]);
    await renderEditor();

    await user.click(screen.getByRole("button", { name: "Delete field GMO permit" }));

    expect(screen.getByRole("button", { name: "Add field" })).toHaveFocus();
  });

  it("asks before removing an option a rule uses, and removes it from the rule", async () => {
    const user = userEvent.setup();
    const posts = serveFields([gmo, permit]);
    const { handle } = await renderEditor();

    expect(within(card("Is this a GMO?")).getByRole("textbox", { name: "Option 1" })).toHaveAttribute("readonly");
    await user.click(within(card("Is this a GMO?")).getByRole("button", { name: "Remove option Yes" }));
    await user.click(within(await screen.findByRole("dialog")).getByRole("button", { name: "Remove" }));
    await handle.current?.save();

    expect(posts[0][1].displayRule).toBeNull();
    expect(posts[0][0].source.radioValues).toBe("fieldRadios=No");
  });

  it("does not let a saved field change type, or a copied field be deleted", async () => {
    serveFields([{ ...gmo, canDelete: false }]);
    await renderEditor();

    expect(within(card("Is this a GMO?")).queryByRole("menuitem", { name: /Change type/ })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Delete field Is this a GMO?" })).not.toBeInTheDocument();
  });

  it("asks before an unsaved source becomes a type that rules cannot read", async () => {
    const user = userEvent.setup();
    serveFields([]);
    await renderEditor();
    await addField(user, /^Radio/, "Source");
    await addOption(user, "Source", "A");
    await addField(user, /^Number/, "Target");
    await user.click(screen.getByRole("button", { name: "Add rule" }));
    await user.click(screen.getByRole("combobox", { name: "Options" }));
    await user.click(screen.getByRole("option", { name: "A" }));
    await user.click(screen.getByRole("combobox", { name: "Show these fields" }));
    await user.click(screen.getByRole("option", { name: "Target" }));

    await user.click(within(card("Source")).getByRole("menuitem", { name: /Change type/ }));
    await user.click(screen.getByRole("menuitem", { name: "Date" }));
    await user.click(within(await screen.findByRole("dialog")).getByRole("button", { name: "Change type" }));

    const target = await screen.findByRole("region", { name: "Target" });
    expect(within(target).queryByText(/Shown when/)).not.toBeInTheDocument();
  });

  it("keeps the editor dirty and shows the server's reasons when a save is rejected", async () => {
    const user = userEvent.setup();
    serveFields([gmo], () => HttpResponse.json({ data: null, errorMsg: { errorMessages: ["Name is too long"] } }));
    const { handle } = await renderEditor();

    await user.type(within(card("Is this a GMO?")).getByRole("textbox", { name: "Name" }), "!");
    await expect(handle.current?.save()).rejects.toThrow();

    const alert = await screen.findByRole("alert", { name: "The fields were not saved." });
    expect(within(alert).getByText("Name is too long")).toBeVisible();
    expect(handle.current?.isDirty()).toBe(true);
  });

  it("does not send fields that fail the client checks", async () => {
    const user = userEvent.setup();
    const posts = serveFields([]);
    const { handle } = await renderEditor();

    await user.click(screen.getByRole("button", { name: "Add field" }));
    await user.click(screen.getByRole("menuitem", { name: /^Number/ }));
    await expect(handle.current?.save()).rejects.toThrow();

    const alert = await screen.findByRole("alert", { name: "Fix the problems marked below, then save again." });
    expect(within(alert).getByText("The first is in Unnamed field.")).toBeVisible();
    expect(screen.getByRole("textbox", { name: "Name" })).toHaveFocus();
    expect(screen.getByText("Enter a name.")).toBeInTheDocument();
    expect(posts).toHaveLength(0);
  });

  it("stops editing once the toolbar has published the form, and says how to change it", async () => {
    serveFields([gmo]);
    const { handle } = await renderEditor();

    act(() => handle.current?.markPublished());

    expect(screen.getByText(/This form is published\. To change its fields, edit it again/)).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Add field" })).not.toBeInTheDocument();
    expect(screen.queryByRole("textbox", { name: "Name" })).not.toBeInTheDocument();
  });

  it("still asks before Revert discards the draft when the fields could not be loaded", async () => {
    const user = userEvent.setup();
    const restoreConsole = silenceConsole(["error"], [/fields/i, /Request failed/, /above error/]);
    server.use(http.get(FIELDS_URL, () => HttpResponse.json({ message: "down" }, { status: 500 })));
    const { handle } = await renderRoot();
    expect(await screen.findByText("The fields of this form could not be loaded.")).toBeInTheDocument();

    const answer = handle.current?.confirmAbandon();
    const dialog = await screen.findByRole("dialog", { name: "Abandon the changes to this form?" });
    await user.click(within(dialog).getByRole("button", { name: "Cancel" }));

    await expect(answer).resolves.toBe(false);
    restoreConsole();
  });

  it("holds the fields read-only while the toolbar publishes, and gives them back if it does not", async () => {
    serveFields([gmo]);
    const { handle } = await renderEditor();

    act(() => handle.current?.setReadOnly(true));
    expect(screen.queryByRole("textbox", { name: "Name" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Add field" })).not.toBeInTheDocument();

    act(() => handle.current?.setReadOnly(false));
    expect(screen.getByRole("textbox", { name: "Name" })).toHaveValue("Is this a GMO?");
  });

  it("focuses the first problem once a Publish refused by the client checks gives the fields back", async () => {
    const user = userEvent.setup();
    serveFields([]);
    const { handle } = await renderEditor();
    await user.click(screen.getByRole("button", { name: "Add field" }));
    await user.click(screen.getByRole("menuitem", { name: /^Number/ }));

    act(() => handle.current?.setReadOnly(true));
    await act(() => expect(handle.current?.save()).rejects.toThrow());
    act(() => handle.current?.setReadOnly(false));

    expect(screen.getByRole("textbox", { name: "Name" })).toHaveFocus();
  });

  it("is read-only when the form cannot be edited", async () => {
    serveFields([gmo, permit]);
    await renderEditor({ editable: false });

    expect(screen.queryByRole("button", { name: "Add field" })).not.toBeInTheDocument();
    expect(screen.queryByRole("textbox", { name: "Name" })).not.toBeInTheDocument();
  });
});

describe("ELN field settings", () => {
  async function editAndSave(
    item: FormFieldEditorItem,
    edit: (user: ReturnType<typeof userEvent.setup>) => Promise<void>,
  ) {
    const user = userEvent.setup();
    const posts = serveFields([item]);
    const { handle } = await renderEditor();
    await edit(user);
    await handle.current?.save();
    return posts[0][0].source;
  }

  it("number: decimal places, minimum, maximum and default", async () => {
    const number = saved(3, "Number", {
      name: "Weight",
      minNumberValue: "1",
      maxNumberValue: "",
      decimalPlaces: "",
      defaultNumberValue: "",
    });
    const source = await editAndSave(number, async (user) => {
      const weight = card("Weight");
      await user.type(within(weight).getByRole("textbox", { name: "Decimal places" }), "2");
      await user.clear(within(weight).getByRole("textbox", { name: "Minimum" }));
      await user.type(within(weight).getByRole("textbox", { name: "Maximum" }), "10");
      await user.type(within(weight).getByRole("textbox", { name: "Default value" }), "5");
    });
    expect(source).toMatchObject({
      decimalPlaces: "2",
      minNumberValue: "",
      maxNumberValue: "10",
      defaultNumberValue: "5",
    });
  });

  it("number: flags a default outside the bounds", async () => {
    const user = userEvent.setup();
    serveFields([saved(3, "Number", { name: "Weight", maxNumberValue: "10" })]);
    await renderEditor();
    await user.type(within(card("Weight")).getByRole("textbox", { name: "Default value" }), "11");
    expect(within(card("Weight")).getByRole("textbox", { name: "Default value" })).toHaveAccessibleDescription(
      "The default cannot be more than the maximum.",
    );
  });

  it("string: password and default", async () => {
    const source = await editAndSave(saved(3, "String", { name: "Code", ifPassword: "no" }), async (user) => {
      await user.click(within(card("Code")).getByRole("switch", { name: "Hide the value, like a password" }));
      await user.type(within(card("Code")).getByRole("textbox", { name: "Default value" }), "abc");
    });
    expect(source).toMatchObject({ ifPassword: "yes", defaultStringValue: "abc" });
  });

  it("radio: picklist display, alphabetical sort and default", async () => {
    const source = await editAndSave(gmo, async (user) => {
      const radio = card("Is this a GMO?");
      await user.click(within(radio).getByRole("switch", { name: "Show as a drop-down list" }));
      await user.click(within(radio).getByRole("switch", { name: "Sort options alphabetically" }));
      await user.click(within(radio).getByRole("combobox", { name: "Default option" }));
      await user.click(screen.getByRole("option", { name: "No" }));
    });
    expect(source).toMatchObject({ showAsPickList: true, sortAlphabetic: true, radioSelected: "No" });
  });

  it("choice: multiple selection and several defaults", async () => {
    const choice = saved(3, "Choice", {
      name: "Hazards",
      choiceValues: "fieldChoices=Heat&fieldChoices=Cold&fieldChoices=Light",
      multipleChoice: "no",
    });
    const source = await editAndSave(choice, async (user) => {
      const hazards = card("Hazards");
      await user.click(within(hazards).getByRole("switch", { name: "Multiple selection" }));
      await user.click(within(hazards).getByRole("checkbox", { name: "Light" }));
      await user.click(within(hazards).getByRole("checkbox", { name: "Heat" }));
    });
    expect(source).toMatchObject({
      multipleChoice: "yes",
      selectedValues: "fieldSelectedChoices=Heat&fieldSelectedChoices=Light",
    });
  });

  it("date: format, earliest, latest and default", async () => {
    const date = saved(3, "Date", {
      name: "Collected",
      dateFormat: "yyyy-MM-dd",
      minValue: "",
      maxValue: "",
      defaultValue: "",
    });
    const source = await editAndSave(date, async (user) => {
      const collected = card("Collected");
      await user.click(within(collected).getByRole("combobox", { name: "Date format" }));
      await user.click(screen.getByRole("option", { name: "dd/MM/yyyy" }));
      await user.type(within(collected).getByLabelText("Earliest"), "2026-01-01");
      await user.type(within(collected).getByLabelText("Default value"), "2026-11-18");
    });
    expect(source).toMatchObject({
      dateFormat: "dd/MM/yyyy",
      minValue: "01/01/2026",
      maxValue: "",
      defaultValue: "18/11/2026",
    });
  });

  it("time: format, earliest, latest and default", async () => {
    const time = saved(3, "Time", {
      name: "Start",
      timeFormat: "HH:mm",
      minValue: "08:00",
      maxValue: "",
      defaultValue: "",
    });
    const source = await editAndSave(time, async (user) => {
      const start = card("Start");
      await user.click(within(start).getByRole("combobox", { name: "Time format" }));
      await user.click(screen.getByRole("option", { name: "12-hour (02:30 PM)" }));
      await user.type(within(start).getByLabelText("Latest"), "17:30");
    });
    expect(source).toMatchObject({
      timeFormat: "hh:mm a",
      minValue: "08:00 AM",
      maxValue: "05:30 PM",
      defaultValue: "",
    });
  });

  // "F" is a Java pattern letter the editor cannot write, from a form imported from an archive
  const unlistedTime = saved(3, "Time", {
    name: "Start",
    timeFormat: "HH:mm F",
    minValue: "09:00 2",
    maxValue: "",
    defaultValue: "",
  });

  it("time: keeps a value stored in a format the editor cannot write until another format is chosen", async () => {
    const user = userEvent.setup();
    const posts = serveFields([unlistedTime]);
    const { handle } = await renderEditor();

    const earliest = within(card("Start")).getByLabelText("Earliest");
    expect(earliest).toBeDisabled();
    expect(earliest).toHaveAccessibleDescription(
      "Stored as 09:00 2, which cannot be shown here. It is kept unless you enter a new value or choose another format. Choose a listed format to enter a new value; that clears this one.",
    );
    await user.click(within(card("Start")).getByRole("switch", { name: "Mandatory" }));
    await handle.current?.save();
    expect(posts[0][0].source).toMatchObject({ timeFormat: "HH:mm F", minValue: "09:00 2" });

    await user.click(within(card("Start")).getByRole("combobox", { name: "Time format" }));
    await user.click(screen.getByRole("option", { name: "24-hour (14:30)" }));
    expect(within(card("Start")).getByLabelText("Earliest")).toBeEnabled();
    expect(within(card("Start")).getByLabelText("Earliest")).not.toHaveAccessibleDescription();
    await handle.current?.save();
    // the stored value cannot be written in the new format, so it is not sent with it
    expect(posts[1][0].source).toMatchObject({ timeFormat: "HH:mm", minValue: "" });
  });

  it("time: clears a stored value it cannot read on request", async () => {
    const user = userEvent.setup();
    const posts = serveFields([unlistedTime]);
    const { handle } = await renderEditor();

    await user.click(within(card("Start")).getByRole("button", { name: "Clear stored Earliest" }));
    await handle.current?.save();

    expect(posts[0][0].source).toMatchObject({ timeFormat: "HH:mm F", minValue: "" });
  });

  it("text: shows the sanitised default", async () => {
    serveFields([saved(3, "Text", { name: "Notes", defaultValue: '<p>Hello</p><img src="x" onerror="alert(1)">' })]);
    await renderEditor();
    const preview = within(card("Notes")).getByRole("group", { name: "Default text" });
    expect(preview).toHaveTextContent("Hello");
    expect(within(preview).getByRole("img")).not.toHaveAttribute("onerror");
    // it scrolls, so it must be reachable by keyboard
    expect(preview).toHaveAttribute("tabindex", "0");
    expect(within(card("Notes")).getByRole("button", { name: "Edit default text of Notes" })).toBeInTheDocument();
  });

  it.each([
    ["Uri", "URI"],
    ["Reference", "Reference"],
    ["Attachment", "Attachment"],
  ] as const)("%s: can be renamed, made mandatory and reordered, but not created", async (type, menuLabel) => {
    const user = userEvent.setup();
    const posts = serveFields([gmo, saved(4, type, { name: "Link" })]);
    const { handle } = await renderEditor();

    await user.click(within(card("Link")).getByRole("textbox", { name: "Name" }));
    await user.paste("s");
    await user.click(within(card("Links")).getByRole("switch", { name: "Mandatory" }));
    await user.click(within(card("Links")).getByRole("button", { name: "Move Links up" }));
    await handle.current?.save();

    expect(posts[0][0]).toMatchObject({ id: 4, type, source: { name: "Links", mandatory: true } });
    await user.click(screen.getByRole("button", { name: "Add field" }));
    expect(screen.queryByRole("menuitem", { name: menuLabel })).not.toBeInTheDocument();
  });
});
