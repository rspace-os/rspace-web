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
import FormFieldEditorRoot, {
  type FormFieldEditorHandle,
  FormFieldEditorUnavailableError,
} from "../FormFieldEditorRoot";
import { type FormFieldEditorItem, type FormFieldSaveItem, FormFieldsConflictError } from "../formFieldsApi";

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

/** The server's answer with these fields, as the version after `saves` saves. */
const fieldList = (fields: ReadonlyArray<unknown>, saves = 0) =>
  HttpResponse.json({ data: { fields, version: saves }, errorMsg: null });

/** A save's fields as the server returns them, with ids for new fields. */
const echoed = (body: ReadonlyArray<FormFieldSaveItem>) =>
  body.map((item, i) => ({ ...item, id: item.id ?? 100 + i, canDelete: true }));

/**
 * Serves the fields, echoes each save back with ids for new fields and a new
 * version, and records what was sent and the version each save was made from.
 */
function serveFields(items: Array<FormFieldEditorItem>, saveResponse?: () => Response | Promise<Response>) {
  const posts: Array<Array<FormFieldSaveItem>> = [];
  const versions: Array<string | null> = [];
  server.use(
    http.get(FIELDS_URL, () => fieldList(items)),
    http.post(FIELDS_URL, async ({ request }) => {
      const body = (await request.json()) as Array<FormFieldSaveItem>;
      posts.push(body);
      versions.push(new URL(request.url).searchParams.get("version"));
      if (saveResponse) return saveResponse();
      return fieldList(echoed(body), posts.length);
    }),
  );
  return Object.assign(posts, { versions });
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
  await screen.findByRole("heading", { name: "Fields", level: 2 });
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

/** Gives a field a condition on the first field it may read: is any of `option`. */
async function showOnlyWhen(user: ReturnType<typeof userEvent.setup>, fieldName: string, option: string) {
  const target = card(fieldName);
  await user.click(within(target).getByRole("radio", { name: "Only when another field has certain values" }));
  await user.click(within(target).getByRole("combobox", { name: "Options" }));
  await user.click(screen.getByRole("option", { name: option }));
}

/**
 * What happens to a save: applied and answered, applied with its response lost, or lost before
 * the server sees it.
 */
type SaveFate = "answered" | "responseLost" | "requestLost";

/**
 * A server that holds the fields at a version, as the real one does: it refuses a save made from
 * another version with 409, and a new field with a key it already holds with 400. Each save meets
 * the next of `fates`; the field list can be made unreadable.
 */
function serveVersionedFields(items: Array<FormFieldEditorItem>, fates: Array<SaveFate>) {
  let held: ReadonlyArray<{ fieldKey: string }> = items;
  let version = 0;
  const state = { readFails: false };
  const posts: Array<Array<FormFieldSaveItem>> = [];
  const versions: Array<string | null> = [];
  server.use(
    http.get(FIELDS_URL, () => (state.readFails ? HttpResponse.error() : fieldList(held, version))),
    http.post(FIELDS_URL, async ({ request }) => {
      const body = (await request.json()) as Array<FormFieldSaveItem>;
      posts.push(body);
      versions.push(new URL(request.url).searchParams.get("version"));
      const fate = fates.shift() ?? "answered";
      if (fate === "requestLost") return HttpResponse.error();
      if (Number(versions.at(-1)) !== version) {
        return HttpResponse.json({ data: null, errorMsg: { errorMessages: ["changed elsewhere"] } }, { status: 409 });
      }
      const heldKeys = new Set(held.map(({ fieldKey }) => fieldKey));
      if (body.some((item) => item.id === null && heldKeys.has(item.fieldKey))) {
        return HttpResponse.json({ data: null, errorMsg: { errorMessages: ["key in use"] } }, { status: 400 });
      }
      held = echoed(body);
      version += 1;
      return fate === "responseLost" ? HttpResponse.error() : fieldList(held, version);
    }),
  );
  return Object.assign(posts, { versions, state });
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
  it("edits each field's display condition in its own card, and is accessible", async () => {
    serveFields([gmo, permit]);
    const { container } = await renderEditor();

    const condition = within(card("GMO permit")).getByRole("group", { name: "Display this field when…" });
    expect(within(condition).getByRole("radio", { name: "Only when another field has certain values" })).toBeChecked();
    expect(within(condition).getByRole("combobox", { name: "Field" })).toHaveTextContent("Is this a GMO?");
    expect(within(condition).getByRole("button", { name: "Yes" })).toBeInTheDocument();
    expect(within(card("Is this a GMO?")).getByText("Other fields depend on this field: GMO permit")).toBeVisible();
    expect(screen.getAllByText(/A mandatory field is mandatory only while it is shown/)).toHaveLength(1);
    await expectAccessible(container);
  });

  it("clears a field's rule when it is set to Always", async () => {
    const user = userEvent.setup();
    const posts = serveFields([gmo, permit]);
    const { handle } = await renderEditor();

    await user.click(within(card("GMO permit")).getByRole("radio", { name: "Always" }));
    await handle.current?.save();

    expect(posts[0][1]).toMatchObject({ fieldKey: PERMIT, displayRule: null });
  });

  it("shows read-only fields' rules as summaries, with no editor", async () => {
    serveFields([gmo, permit]);
    await renderEditor({ editable: false });

    expect(within(card("GMO permit")).getByText("Shown when Is this a GMO? is Yes")).toBeVisible();
    expect(screen.queryByRole("radio")).not.toBeInTheDocument();
  });

  it("adds a field where crypto.randomUUID is missing, as on a plain-HTTP site", async () => {
    const user = userEvent.setup();
    const posts = serveFields([]);
    const randomUUID = Object.getOwnPropertyDescriptor(crypto, "randomUUID");
    Object.defineProperty(crypto, "randomUUID", { value: undefined, configurable: true });
    try {
      const { handle } = await renderEditor();
      await addField(user, /^String/, "Notes");
      await handle.current?.save();
    } finally {
      if (randomUUID) Object.defineProperty(crypto, "randomUUID", randomUUID);
      else Reflect.deleteProperty(crypto, "randomUUID");
    }

    expect(posts[0][0].fieldKey).toEqual(expect.any(String));
  });

  it("sends each save with the version of the fields it was made from", async () => {
    const user = userEvent.setup();
    const posts = serveFields([gmo]);
    const { handle } = await renderEditor();
    const mandatory = within(card("Is this a GMO?")).getByRole("switch", { name: "Mandatory" });

    await user.click(mandatory);
    await expect(handle.current?.save()).resolves.toBe(1);
    await user.click(mandatory);
    await expect(handle.current?.save()).resolves.toBe(2);

    expect(posts.versions).toEqual(["0", "1"]);
  });

  it("says the form was changed elsewhere, and keeps refusing to save until the page is reloaded", async () => {
    const user = userEvent.setup();
    const posts = serveFields([gmo], () =>
      HttpResponse.json(
        { data: null, errorMsg: { errorMessages: ["This form's fields were changed elsewhere"] } },
        { status: 409 },
      ),
    );
    const { handle } = await renderEditor();

    await user.click(within(card("Is this a GMO?")).getByRole("switch", { name: "Mandatory" }));
    await expect(handle.current?.save()).rejects.toThrow(FormFieldsConflictError);

    const alert = await screen.findByRole("alert", { name: "This form was changed elsewhere." });
    expect(within(alert).getByText(/saved in another tab or window after this page loaded them/)).toBeVisible();
    expect(within(alert).getByRole("button", { name: "Reload" })).toHaveFocus();
    expect(within(alert).queryByRole("button", { name: "Close" })).not.toBeInTheDocument();
    // read-only, so the change cannot be taken back, or made again, and saved over the other one
    expect(screen.queryByRole("switch", { name: "Mandatory" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Add field" })).not.toBeInTheDocument();
    await expect(handle.current?.save()).rejects.toThrow(FormFieldsConflictError);
    expect(posts.versions).toEqual(["0"]);
    act(() => handle.current?.setReadOnly(false));
    expect(screen.queryByRole("switch", { name: "Mandatory" })).not.toBeInTheDocument();
  });

  it("refuses the toolbar's action on an untouched page whose fields were changed elsewhere", async () => {
    const posts = serveFields([gmo]);
    const { handle } = await renderEditor();
    const renamed = { ...gmo, source: { ...gmo.source, name: "GMO status" } };
    let gets = 0;
    server.use(
      http.get(FIELDS_URL, () => {
        gets += 1;
        return fieldList([renamed], 1);
      }),
    );

    await expect(handle.current?.save()).rejects.toThrow(FormFieldsConflictError);
    expect(await screen.findByRole("alert", { name: "This form was changed elsewhere." })).toBeVisible();
    // latched: later calls are refused without asking the server again
    await expect(handle.current?.save()).rejects.toThrow(FormFieldsConflictError);
    expect(gets).toBe(1);
    expect(posts).toHaveLength(0);
  });

  it("lets the toolbar act on an untouched page whose fields are still the server's", async () => {
    const posts = serveFields([gmo]);
    const { handle } = await renderEditor();

    // the toolbar sends the version checked with its action
    await expect(handle.current?.save()).resolves.toBe(0);

    expect(posts).toHaveLength(0);
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("takes the server's ids when a check finds the same fields at a newer version", async () => {
    const user = userEvent.setup();
    const posts = serveFields([gmo]);
    const { handle } = await renderEditor();
    // saved elsewhere as the same field under a new id
    server.use(http.get(FIELDS_URL, () => fieldList([{ ...gmo, id: 9 }], 3)));

    await expect(handle.current?.save()).resolves.toBe(3);
    await user.click(within(card("Is this a GMO?")).getByRole("switch", { name: "Mandatory" }));
    await handle.current?.save();

    expect(posts.versions).toEqual(["3"]);
    expect(posts[0][0]).toMatchObject({ id: 9, fieldKey: GMO });
  });

  it("latches a conflict the toolbar reports, and says unsaved changes will be lost on reload", async () => {
    const user = userEvent.setup();
    const posts = serveFields([gmo]);
    const { handle } = await renderEditor();
    await user.click(within(card("Is this a GMO?")).getByRole("switch", { name: "Mandatory" }));

    act(() => handle.current?.reportConflict());

    const alert = await screen.findByRole("alert", { name: "This form was changed elsewhere." });
    expect(within(alert).getByText(/not saved will be lost when you reload/)).toBeVisible();
    await expect(handle.current?.save()).rejects.toThrow(FormFieldsConflictError);
    expect(posts).toHaveLength(0);
  });

  it("does not say changes will be lost on a conflict when there are none", async () => {
    serveFields([gmo]);
    const { handle } = await renderEditor();

    act(() => handle.current?.reportConflict());

    const alert = await screen.findByRole("alert", { name: "This form was changed elsewhere." });
    expect(within(alert).queryByText(/will be lost/)).not.toBeInTheDocument();
  });

  it("treats a save whose response was lost as done when the server has the fields it sent", async () => {
    const user = userEvent.setup();
    let lost = true;
    const posts = serveFields([gmo], () => {
      // the first attempt was saved, but its response never arrived; the retry finds version 1
      if (lost) {
        lost = false;
        return HttpResponse.json({ data: null, errorMsg: { errorMessages: ["changed elsewhere"] } }, { status: 409 });
      }
      return fieldList(echoed(posts.at(-1) ?? []), 2);
    });
    const { handle } = await renderEditor();
    server.use(http.get(FIELDS_URL, () => fieldList(echoed(posts[0]), 1)));
    const mandatory = within(card("Is this a GMO?")).getByRole("switch", { name: "Mandatory" });

    await user.click(mandatory);
    await handle.current?.save();

    expect(handle.current?.isDirty()).toBe(false);
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
    await user.click(mandatory);
    await handle.current?.save();
    expect(posts.versions).toEqual(["0", "1"]);
  });

  it("follows an earlier save whose response was lost when the author has edited since", async () => {
    const user = userEvent.setup();
    let held: ReadonlyArray<FormFieldSaveItem> = [];
    let version = 0;
    const posts = serveFields([gmo], () => {
      const sent = posts.at(-1) ?? [];
      const from = Number(posts.versions.at(-1));
      if (from !== version) {
        return HttpResponse.json({ data: null, errorMsg: { errorMessages: ["changed elsewhere"] } }, { status: 409 });
      }
      held = echoed(sent);
      version += 1;
      // the first save is applied, but its response never arrives
      if (posts.length === 1) return HttpResponse.error();
      return fieldList(held, version);
    });
    server.use(http.get(FIELDS_URL, () => (version === 0 ? fieldList([gmo]) : fieldList(held, version))));
    const restoreConsole = silenceConsole(["error"], [/Network Error/]);
    const { handle } = await renderEditor();
    await addField(user, /^String/, "Permit");
    await expect(handle.current?.save()).rejects.toThrow();
    restoreConsole();

    await user.click(within(card("Is this a GMO?")).getByRole("switch", { name: "Mandatory" }));
    await expect(handle.current?.save()).resolves.toBe(2);

    expect(posts.versions).toEqual(["0", "0", "1"]);
    // the retry updates the field the lost save created, rather than creating it again
    expect(posts[2]).toEqual([
      expect.objectContaining({ id: 1, source: expect.objectContaining({ mandatory: true }) }),
      expect.objectContaining({ id: 101, source: expect.objectContaining({ name: "Permit" }) }),
    ]);
    expect(handle.current?.isDirty()).toBe(false);
    expect(screen.queryByRole("alert", { name: "This form was changed elsewhere." })).not.toBeInTheDocument();
  });

  it("follows a save whose response was lost again when the save that followed it failed", async () => {
    const user = userEvent.setup();
    const posts = serveVersionedFields([gmo], ["responseLost", "answered", "requestLost"]);
    const restoreConsole = silenceConsole(["error"], [/Network Error/]);
    const { handle } = await renderEditor();
    await addField(user, /^String/, "Permit");
    await expect(handle.current?.save()).rejects.toThrow();
    await user.click(within(card("Is this a GMO?")).getByRole("switch", { name: "Mandatory" }));
    // refused, then followed from the lost save's version, but that save is lost too
    await expect(handle.current?.save()).rejects.toThrow();
    restoreConsole();

    await expect(handle.current?.save()).resolves.toBe(2);

    expect(posts.versions).toEqual(["0", "0", "1", "0", "1"]);
    // the field the lost save created is updated, not sent as new again
    expect(posts[4][1]).toMatchObject({ id: 101, source: expect.objectContaining({ name: "Permit" }) });
    expect(handle.current?.isDirty()).toBe(false);
  });

  it("lets the author try again when the fields cannot be read after a refused save", async () => {
    const user = userEvent.setup();
    const posts = serveVersionedFields([gmo], ["responseLost"]);
    const restoreConsole = silenceConsole(["error"], [/Network Error/]);
    const { handle } = await renderEditor();
    await addField(user, /^String/, "Permit");
    await expect(handle.current?.save()).rejects.toThrow();
    await user.click(within(card("Is this a GMO?")).getByRole("switch", { name: "Mandatory" }));
    posts.state.readFails = true;

    const refused = handle.current?.save();
    await expect(refused).rejects.toThrow();
    await expect(refused).rejects.not.toBeInstanceOf(FormFieldsConflictError);
    restoreConsole();
    expect(await screen.findByRole("alert", { name: "The fields were not saved." })).toBeVisible();
    expect(screen.queryByRole("alert", { name: "This form was changed elsewhere." })).not.toBeInTheDocument();

    posts.state.readFails = false;
    await expect(handle.current?.save()).resolves.toBe(2);
    expect(posts.at(-1)?.[1]).toMatchObject({ id: 101 });
  });

  it("follows the earlier of two saves whose responses were lost when the server has it", async () => {
    const user = userEvent.setup();
    const posts = serveVersionedFields([gmo], ["responseLost", "requestLost"]);
    const restoreConsole = silenceConsole(["error"], [/Network Error/]);
    const { handle } = await renderEditor();
    await addField(user, /^String/, "Permit");
    await expect(handle.current?.save()).rejects.toThrow();
    await user.click(within(card("Is this a GMO?")).getByRole("switch", { name: "Mandatory" }));
    await expect(handle.current?.save()).rejects.toThrow();
    restoreConsole();

    await user.click(within(card("Permit")).getByRole("switch", { name: "Mandatory" }));
    await expect(handle.current?.save()).resolves.toBe(2);

    expect(posts.versions).toEqual(["0", "0", "0", "1"]);
    expect(posts[3]).toEqual([
      expect.objectContaining({ id: 1, source: expect.objectContaining({ mandatory: true }) }),
      expect.objectContaining({ id: 101, source: expect.objectContaining({ mandatory: true }) }),
    ]);
  });

  it("lets the author try again when the database was busy with another save", async () => {
    const user = userEvent.setup();
    let busy = true;
    const posts = serveFields([gmo], () => {
      if (!busy) return fieldList(echoed(posts.at(-1) ?? []), 1);
      busy = false;
      return HttpResponse.json(
        { data: null, errorMsg: { errorMessages: ["Saved nothing. Try again."] } },
        { status: 503 },
      );
    });
    const { handle } = await renderEditor();
    await user.click(within(card("Is this a GMO?")).getByRole("switch", { name: "Mandatory" }));

    await expect(handle.current?.save()).rejects.not.toBeInstanceOf(FormFieldsConflictError);
    const alert = await screen.findByRole("alert", { name: "The fields were not saved." });
    expect(within(alert).getByText("Saved nothing. Try again.")).toBeVisible();

    await expect(handle.current?.save()).resolves.toBe(1);
    expect(screen.queryByRole("alert", { name: "This form was changed elsewhere." })).not.toBeInTheDocument();
  });

  it("shows the server's reason when the user may not change the form", async () => {
    const user = userEvent.setup();
    serveFields([gmo], () =>
      HttpResponse.json(
        { data: null, errorMsg: { errorMessages: ["Unauthorised attempt to update a form field"] } },
        { status: 403 },
      ),
    );
    const { handle } = await renderEditor();

    await user.click(within(card("Is this a GMO?")).getByRole("switch", { name: "Mandatory" }));
    await expect(handle.current?.save()).rejects.toThrow();

    const alert = await screen.findByRole("alert", { name: "The fields were not saved." });
    expect(within(alert).getByText("Unauthorised attempt to update a form field")).toBeVisible();
    expect(within(alert).queryByText(/Try again/)).not.toBeInTheDocument();
  });

  it("sends options and defaults stored in an old format back as they were, until the options are edited", async () => {
    const user = userEvent.setup();
    const choice = saved(3, "Choice", {
      name: "C",
      choiceValues: "fieldChoices=pH=7&fieldChoices=&fieldChoices=B",
      multipleChoice: "yes",
      // read as B and pH, but not written that way
      selectedValues: "fieldSelectedChoices=B&fieldSelectedChoices=pH=7&junk",
    });
    const radio = saved(4, "Radio", {
      name: "R",
      radioValues: "fieldChoices=X&&fieldChoices=Y=1",
      // not one of the options read, X and Y, as stored
      radioSelected: "Y=1",
    });
    const text = saved(5, "String", { name: "S", ifPassword: "no", defaultStringValue: "" });
    const posts = serveFields([choice, radio, text]);
    const { handle } = await renderEditor();

    await user.type(within(card("S")).getByRole("textbox", { name: "Name" }), "2");
    await handle.current?.save();
    await addOption(user, "C", "D");
    await addOption(user, "R", "Z");
    await handle.current?.save();

    expect(posts[0][0].source).toMatchObject({
      choiceValues: "fieldChoices=pH=7&fieldChoices=&fieldChoices=B",
      selectedValues: "fieldSelectedChoices=B&fieldSelectedChoices=pH=7&junk",
    });
    expect(posts[0][1].source).toMatchObject({ radioValues: "fieldChoices=X&&fieldChoices=Y=1", radioSelected: "Y=1" });
    expect(posts[1][0].source).toMatchObject({
      choiceValues: "fieldChoices=pH&fieldChoices=B&fieldChoices=D",
      selectedValues: "fieldSelectedChoices=B&fieldSelectedChoices=pH",
    });
    // the default is read as its options were, so Y=1 is Y, which the options still offer
    expect(posts[1][1].source).toMatchObject({
      radioValues: "fieldRadios=X&fieldRadios=Y&fieldRadios=Z",
      radioSelected: "Y",
    });
  });

  it("takes the default a save cleared when the author did not change it while the save was in flight", async () => {
    const user = userEvent.setup();
    const held = heldResponse();
    // Q is not an option, so writing the options anew clears it
    const radio = saved(4, "Radio", { name: "R", radioValues: "fieldRadios=X&fieldRadios=Y", radioSelected: "Q" });
    const text = saved(5, "String", { name: "S", ifPassword: "no", defaultStringValue: "" });
    let first = true;
    const posts = serveFields([radio, text], async () => {
      if (first) {
        first = false;
        await held.released;
      }
      return fieldList(echoed(posts.at(-1) ?? []), posts.length);
    });
    const { handle } = await renderEditor();
    await addOption(user, "R", "Z");

    const saving = handle.current?.save();
    await user.type(within(card("S")).getByRole("textbox", { name: "Name" }), "2");
    held.release();
    await saving;
    await handle.current?.save();

    expect(posts[0][0].source).toMatchObject({ radioSelected: "" });
    expect(posts[1][0].source).toMatchObject({ radioSelected: "" });
  });

  it("checks only the settings that changed on a stored field, so stored duplicate options do not block a change", async () => {
    const user = userEvent.setup();
    const duplicated = saved(3, "Choice", {
      name: "Legacy",
      choiceValues: "fieldChoices=a&fieldChoices=a&fieldChoices=b",
      multipleChoice: "no",
      selectedValues: "",
    });
    const posts = serveFields([duplicated]);
    const { handle } = await renderEditor();

    await user.click(within(card("Legacy")).getByRole("switch", { name: "Mandatory" }));
    await handle.current?.save();
    expect(posts).toHaveLength(1);

    await addOption(user, "Legacy", "c");
    await expect(handle.current?.save()).rejects.toThrow();
    expect(posts).toHaveLength(1);
  });

  it("shows a source's new name in a condition still being drafted", async () => {
    const user = userEvent.setup();
    serveFields([gmo, saved(2, "String", { name: "Target", ifPassword: "no", defaultStringValue: "" })]);
    await renderEditor();

    await user.click(within(card("Target")).getByRole("radio", { name: "Only when another field has certain values" }));
    const sourceMenu = within(card("Target")).getByRole("combobox", { name: "Field" });
    expect(sourceMenu).toHaveTextContent("Is this a GMO?");
    const sourceName = within(card("Is this a GMO?")).getByRole("textbox", { name: "Name" });
    await user.clear(sourceName);
    await user.paste("GMO status");

    expect(within(card("Target")).getByRole("combobox", { name: "Field" })).toHaveTextContent("GMO status");
  });

  it("gives a new field its key when it is added, and sends it as new", async () => {
    const user = userEvent.setup();
    const posts = serveFields([]);
    const { handle } = await renderEditor();

    await addField(user, /^String/, "Hazard details");
    await handle.current?.save();

    expect(posts[0][0]).toMatchObject({ id: null, type: "String", source: { name: "Hazard details" } });
    expect(posts[0][0].fieldKey).toEqual(expect.any(String));
    expect(handle.current?.isDirty()).toBe(false);
  });

  it("uses new fields in a rule before the first save", async () => {
    const user = userEvent.setup();
    const posts = serveFields([]);
    const { handle } = await renderEditor();
    await addField(user, /^Radio/, "Hazardous?");
    await addOption(user, "Hazardous?", "Yes");
    await addField(user, /^String/, "Hazard details");

    await showOnlyWhen(user, "Hazard details", "Yes");
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
      return fieldList(echoed(posts.at(-1) ?? []), posts.length);
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
      return fieldList([gmo], 1);
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
    expect(within(card("GMO permit")).getByRole("combobox", { name: "Field" })).toHaveTextContent("Is this a GMO?");
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
    await showOnlyWhen(user, "Target", "A");

    await user.click(within(card("Source")).getByRole("menuitem", { name: /Change type/ }));
    await user.click(screen.getByRole("menuitem", { name: "Date" }));
    await user.click(within(await screen.findByRole("dialog")).getByRole("button", { name: "Change type" }));

    const target = await screen.findByRole("region", { name: "Target" });
    expect(within(target).getByRole("radio", { name: "Always" })).toBeChecked();
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

  it("refuses the toolbar's actions when the fields could not be loaded, as they cannot be checked", async () => {
    const restoreConsole = silenceConsole(["error"], [/fields/i, /Request failed/, /above error/]);
    server.use(http.get(FIELDS_URL, () => HttpResponse.json({ message: "down" }, { status: 500 })));
    const { handle } = await renderRoot();
    expect(await screen.findByText("The fields of this form could not be loaded.")).toBeInTheDocument();

    await expect(handle.current?.save()).rejects.toThrow(FormFieldEditorUnavailableError);
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
