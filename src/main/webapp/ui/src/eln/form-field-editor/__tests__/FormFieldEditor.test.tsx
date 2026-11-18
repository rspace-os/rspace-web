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
import { server } from "@/__tests__/mswServer";
import ConfirmProvider from "@/components/ConfirmProvider";
import common from "@/modules/common/i18n/locales/en-US/common.json";
import workspace from "@/modules/common/i18n/locales/en-US/workspace.json";
import materialTheme from "@/theme";
import type { FormFieldEditorHandle } from "../FormFieldEditor";
import FormFieldEditorRoot from "../FormFieldEditorRoot";
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
function serveFields(items: Array<FormFieldEditorItem>, saveResponse?: () => Response) {
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

async function renderEditor({ editable = true } = {}) {
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
  // the rules panel appears once there is a field
  await screen.findByText(/^(This form has no fields yet\.|Display rules)$/);
  return { handle, ...result };
}

const card = (name: string) => screen.getByRole("region", { name });

async function addField(user: ReturnType<typeof userEvent.setup>, typeLabel: RegExp, name: string) {
  await user.click(screen.getByRole("button", { name: "Add field" }));
  await user.click(screen.getByRole("menuitem", { name: typeLabel }));
  const nameInput = screen.getAllByRole("textbox", { name: "Name" }).at(-1);
  if (!nameInput) throw new Error("no name input");
  expect(nameInput).toHaveFocus();
  await user.type(nameInput, name);
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

  it("uses a new field in a rule before the first save, and sends its key with the rule", async () => {
    const user = userEvent.setup();
    const posts = serveFields([]);
    const { handle } = await renderEditor();

    await addField(user, /^Radio/, "Hazardous?");
    await user.click(within(card("Hazardous?")).getByRole("button", { name: "Add option" }));
    await user.type(within(card("Hazardous?")).getByRole("textbox", { name: "Option 1" }), "Yes");
    await addField(user, /^String/, "Hazard details");

    await user.click(screen.getByRole("button", { name: "Add rule" }));
    await user.click(screen.getByRole("combobox", { name: "Options" }));
    await user.click(screen.getByRole("option", { name: "Yes" }));
    await user.click(screen.getByRole("combobox", { name: "Show these fields" }));
    await user.click(screen.getByRole("option", { name: "Hazard details" }));
    await handle.current?.save();

    const [source, target] = posts[0];
    expect(source).toMatchObject({ id: null, type: "Radio", source: { radioValues: "fieldRadios=Yes" } });
    expect(target.displayRule).toEqual({ sourceFieldKey: source.fieldKey, operator: "IS_ANY_OF", values: ["Yes"] });
    expect(handle.current?.isDirty()).toBe(false);
  }, 40_000);

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
    await user.click(within(card("Source")).getByRole("button", { name: "Add option" }));
    await user.type(within(card("Source")).getByRole("textbox", { name: "Option 1" }), "A");
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
  }, 40_000);

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

    expect(
      await screen.findByRole("alert", { name: "Fix the problems marked below, then save again." }),
    ).toBeInTheDocument();
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

  it("text: shows the sanitised default", async () => {
    serveFields([saved(3, "Text", { name: "Notes", defaultValue: '<p>Hello</p><img src="x" onerror="alert(1)">' })]);
    await renderEditor();
    const preview = within(card("Notes")).getByRole("group", { name: "Default text" });
    expect(preview).toHaveTextContent("Hello");
    expect(within(preview).getByRole("img")).not.toHaveAttribute("onerror");
    expect(within(card("Notes")).getByRole("button", { name: "Edit default text of Notes" })).toBeInTheDocument();
  });

  it("uri: can be renamed, made mandatory and reordered, but not created", async () => {
    const user = userEvent.setup();
    const posts = serveFields([gmo, saved(4, "Uri", { name: "Link" })]);
    const { handle } = await renderEditor();

    const link = card("Link");
    await user.type(within(link).getByRole("textbox", { name: "Name" }), "s");
    await user.click(within(card("Links")).getByRole("switch", { name: "Mandatory" }));
    await user.click(within(card("Links")).getByRole("button", { name: "Move Links up" }));
    await handle.current?.save();

    expect(posts[0][0]).toMatchObject({ id: 4, type: "Uri", source: { name: "Links", mandatory: true } });
    await user.click(screen.getByRole("button", { name: "Add field" }));
    expect(screen.queryByRole("menuitem", { name: "URI" })).not.toBeInTheDocument();
  });
});
