import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { InEnglish } from "@/__tests__/realI18n";
import TemplateStep, { type TemplateSelection } from "../TemplateStep";

type FakeField = {
  name: string;
  mandatory: boolean;
  content: string | null;
  selectedOptions: Array<string> | null;
};
type FakeTemplate = {
  id: number;
  name: string;
  fetchAdditionalInfo: () => Promise<void>;
  fields: Array<FakeField>;
};

let currentTemplate: FakeTemplate;
// Records the setTemplate prop passed on each render, so a test can assert its identity is stable
// across re-renders.
const capturedSetTemplate: Array<(t: FakeTemplate) => void> = [];

// Stub the wizard's template picker so this test does not mount its real Search/fetcher; clicking
// it plays back the current fake template through the setTemplate prop.
vi.mock("../WizardTemplatePicker", () => ({
  default: ({ setTemplate }: { setTemplate: (t: FakeTemplate | null) => void }) => {
    capturedSetTemplate.push(setTemplate);
    return (
      <>
        <button type="button" data-testid="template-picker" onClick={() => setTemplate(currentTemplate)} />
        <button type="button" data-testid="template-clear" onClick={() => setTemplate(null)} />
      </>
    );
  },
}));

const base: TemplateSelection = { mode: "none", templateId: null, remember: false };
const pickMode: TemplateSelection = { mode: "pick", templateId: null, remember: false };

/**
 * Every selection the step asked for, with the updater form resolved against `from`.
 *
 * The post-lookup write sends an updater rather than a value, because it crosses an await (FE6), so
 * asserting on the raw mock argument would silently match nothing - including the negative
 * assertions, which would stop being sensitive to the thing they guard.
 */
const selectionsFrom = (
  onChange: ReturnType<typeof vi.fn>,
  from: TemplateSelection = pickMode,
): Array<TemplateSelection> =>
  onChange.mock.calls.map((call) => {
    const arg = call[0] as TemplateSelection | ((p: TemplateSelection) => TemplateSelection);
    return typeof arg === "function" ? arg(from) : arg;
  });

const makeTemplate = (fields: Array<FakeField>): FakeTemplate => ({
  id: 5,
  name: "T5",
  fetchAdditionalInfo: () => Promise.resolve(),
  fields,
});

describe("TemplateStep", () => {
  it("offers the three template choices (use parent, choose existing, none) and no remember checkbox", () => {
    render(<TemplateStep value={base} onChange={() => undefined} originSampleName="S1" />);
    expect(screen.getAllByRole("radio")).toHaveLength(3);
    expect(screen.queryByRole("checkbox")).not.toBeInTheDocument();
  });

  it("disables 'use parent template' and shows a hint when the parent has no template", () => {
    render(<TemplateStep value={base} onChange={() => undefined} originSampleName="S1" parentHasTemplate={false} />);
    // "use parent" is first; it is disabled and the hint is shown
    expect(screen.getAllByRole("radio")[0]).toBeDisabled();
    expect(screen.getByText(/template\.parentHasNoTemplate/)).toBeInTheDocument();
  });

  it("passes a referentially stable setTemplate to the picker across re-renders", () => {
    capturedSetTemplate.length = 0;
    const { rerender } = render(<TemplateStep value={pickMode} onChange={() => undefined} originSampleName="S1" />);
    const first = capturedSetTemplate.at(-1);
    rerender(<TemplateStep value={{ ...pickMode, templateId: 7 }} onChange={() => undefined} originSampleName="S1" />);
    expect(capturedSetTemplate.at(-1)).toBe(first);
  });

  it("selects the 'existing template' mode (the second radio)", async () => {
    const onChange = vi.fn();
    render(<TemplateStep value={base} onChange={onChange} originSampleName="S1" />);
    await userEvent.setup().click(screen.getAllByRole("radio")[1]);
    expect(onChange).toHaveBeenCalledWith(expect.objectContaining({ mode: "pick" }));
  });

  it("shows a remembered template as a banner with its name and no radio selected", () => {
    render(
      <TemplateStep
        value={{ mode: "remembered", templateId: 5, templateName: "My Template", remember: true }}
        onChange={() => undefined}
        originSampleName="S1"
      />,
    );
    // cimode renders the key without its parameters, so the name itself is asserted in English
    // below: the "Selected template: {name}" join moved into the catalog, because not every locale
    // separates with a colon-space (parallel review, FE14).
    expect(screen.getByRole("alert")).toHaveTextContent(/template\.selectedLabel/);
    for (const radio of screen.getAllByRole("radio")) expect(radio).not.toBeChecked();
    expect(screen.queryByTestId("template-picker")).not.toBeInTheDocument();
  });

  it("selects no radio and shows no banner when nothing is chosen yet ('unselected')", () => {
    render(
      <TemplateStep
        value={{ mode: "unselected", templateId: null, remember: false }}
        onChange={() => undefined}
        originSampleName="S1"
      />,
    );
    for (const radio of screen.getAllByRole("radio")) expect(radio).not.toBeChecked();
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("shows the template picker when 'pick' is the current mode", () => {
    render(<TemplateStep value={pickMode} onChange={() => undefined} originSampleName="S1" />);
    expect(screen.getByTestId("template-picker")).toBeInTheDocument();
  });

  it("blocks a template whose mandatory field has no default, leaving the choice unset", async () => {
    currentTemplate = makeTemplate([{ name: "Concentration", mandatory: true, content: "", selectedOptions: null }]);
    const onChange = vi.fn();
    render(<TemplateStep value={pickMode} onChange={onChange} originSampleName="S1" />);
    await userEvent.setup().click(screen.getByTestId("template-picker"));
    expect(await screen.findByText(/mandatoryFieldsError|cannot be used|no default/i)).toBeInTheDocument();
    expect(selectionsFrom(onChange)).not.toContainEqual(expect.objectContaining({ templateId: 5 }));
  });

  it("accepts a template whose mandatory fields all have defaults", async () => {
    currentTemplate = makeTemplate([{ name: "Batch", mandatory: true, content: "B1", selectedOptions: null }]);
    const onChange = vi.fn();
    render(<TemplateStep value={pickMode} onChange={onChange} originSampleName="S1" />);
    await userEvent.setup().click(screen.getByTestId("template-picker"));
    await waitFor(() => expect(selectionsFrom(onChange)).toContainEqual(expect.objectContaining({ templateId: 5 })));
  });

  it("keeps a change made while the template lookup was in flight", async () => {
    // The write after the await used to spread the `value` captured BEFORE it, so ticking
    // "remember" mid-lookup was silently undone and the user performed the operation believing the
    // bundle had been saved (parallel review, FE6). The updater form sees the newer value.
    let resolveLookup: () => void = () => undefined;
    currentTemplate = {
      id: 5,
      name: "T5",
      fetchAdditionalInfo: () => new Promise((r) => (resolveLookup = r)),
      fields: [{ name: "Batch", mandatory: true, content: "B1", selectedOptions: null }],
    };
    const onChange = vi.fn();
    render(<TemplateStep value={pickMode} onChange={onChange} originSampleName="S1" />);
    await userEvent.setup().click(screen.getByTestId("template-picker"));

    resolveLookup();
    await waitFor(() => expect(selectionsFrom(onChange)).toContainEqual(expect.objectContaining({ templateId: 5 })));

    // The wizard's state moved on while the lookup was pending: remember was ticked. Resolving the
    // step's write against THAT value must preserve it.
    const tickedMidLookup: TemplateSelection = { ...pickMode, remember: true };
    expect(selectionsFrom(onChange, tickedMidLookup)).toContainEqual(
      expect.objectContaining({ templateId: 5, remember: true }),
    );
  });

  it("discards a stale template lookup that resolves after a newer pick (latest wins)", async () => {
    // Pick A then B before A's lookup resolves, then let A resolve last: B must survive, not A.
    let resolveA: () => void = () => undefined;
    const templateA: FakeTemplate = {
      id: 5,
      name: "A",
      fetchAdditionalInfo: () => new Promise((r) => (resolveA = r)),
      fields: [{ name: "F", mandatory: false, content: "x", selectedOptions: null }],
    };
    const templateB: FakeTemplate = {
      id: 6,
      name: "B",
      fetchAdditionalInfo: () => Promise.resolve(),
      fields: [{ name: "F", mandatory: false, content: "y", selectedOptions: null }],
    };
    const onChange = vi.fn();
    render(<TemplateStep value={pickMode} onChange={onChange} originSampleName="S1" />);
    const user = userEvent.setup();

    currentTemplate = templateA;
    await user.click(screen.getByTestId("template-picker"));
    currentTemplate = templateB;
    await user.click(screen.getByTestId("template-picker"));

    // B resolved immediately and applied; now the older A resolves and must be ignored.
    await waitFor(() => expect(selectionsFrom(onChange)).toContainEqual(expect.objectContaining({ templateId: 6 })));
    resolveA();
    await Promise.resolve();
    expect(selectionsFrom(onChange)).not.toContainEqual(expect.objectContaining({ templateId: 5 }));
  });

  it("abandons a pending template lookup when the step unmounts (wizard navigated away)", async () => {
    // Pick a template, then unmount the step before its lookup resolves - as happens when the user
    // steps back and a process-name / remembered-value / operation change replaces the wizard's
    // template selection. The late result must not restore the abandoned template onto the newer
    // selection (Greptile P1: only picker/mode changes invalidated the in-flight lookup before).
    let resolveA: () => void = () => undefined;
    const templateA: FakeTemplate = {
      id: 5,
      name: "A",
      fetchAdditionalInfo: () => new Promise((r) => (resolveA = r)),
      fields: [{ name: "F", mandatory: false, content: "x", selectedOptions: null }],
    };
    const onChange = vi.fn();
    const { unmount } = render(<TemplateStep value={pickMode} onChange={onChange} originSampleName="S1" />);
    const user = userEvent.setup();

    currentTemplate = templateA;
    await user.click(screen.getByTestId("template-picker"));
    unmount(); // the wizard moved off the template step
    resolveA();
    await Promise.resolve();
    expect(selectionsFrom(onChange)).not.toContainEqual(expect.objectContaining({ templateId: 5 }));
  });

  it("abandons a pending template lookup when the user switches mode (no stale restore)", async () => {
    // Pick a template, then switch to "No template" before its lookup resolves: the late result
    // must not restore the abandoned template.
    let resolveA: () => void = () => undefined;
    const templateA: FakeTemplate = {
      id: 5,
      name: "A",
      fetchAdditionalInfo: () => new Promise((r) => (resolveA = r)),
      fields: [{ name: "F", mandatory: false, content: "x", selectedOptions: null }],
    };
    const onChange = vi.fn();
    render(<TemplateStep value={pickMode} onChange={onChange} originSampleName="S1" />);
    const user = userEvent.setup();

    currentTemplate = templateA;
    await user.click(screen.getByTestId("template-picker"));
    // Switch to "No template" (the third radio) while A's lookup is still pending.
    await user.click(screen.getAllByRole("radio")[2]);
    resolveA();
    await Promise.resolve();
    expect(selectionsFrom(onChange)).not.toContainEqual(expect.objectContaining({ templateId: 5 }));
  });
});

describe("TemplateStep in English", () => {
  const blockedBy = async (names: Array<string>) => {
    currentTemplate = makeTemplate(
      names.map((name) => ({ name, mandatory: true, content: "", selectedOptions: null })),
    );
    render(
      <InEnglish>
        <TemplateStep value={pickMode} onChange={vi.fn()} originSampleName="S1" />
      </InEnglish>,
    );
    await userEvent.setup().click(screen.getByTestId("template-picker"));
  };

  it("reads the remembered-template banner as 'Selected template: My Template'", async () => {
    render(
      <InEnglish>
        <TemplateStep
          value={{ mode: "remembered", templateId: 5, templateName: "My Template", remember: true }}
          onChange={vi.fn()}
          originSampleName="S1"
        />
      </InEnglish>,
    );
    expect(await screen.findByRole("alert")).toHaveTextContent("Selected template: My Template");
  });

  it("names the blocking fields as a list, not a bare join", async () => {
    // The message is assembled by i18n, not in code (code review, finding 10): the field names go
    // through Intl.ListFormat for the locale, so English reads "A, B, and C". cimode hides the
    // interpolated parameters entirely, which is why these tests use the real catalogs.
    await blockedBy(["A", "B", "C"]);
    expect(await screen.findByText(/the required fields A, B, and C have no default value/)).toBeInTheDocument();
  });

  it("inflects the sentence for a single blocking field rather than writing 'field(s)'", async () => {
    // "field(s) ... have" is a parenthetical plural, which only works in English and reads badly
    // even there. The count decides the wording in the catalog, so a translator can inflect it the
    // way their own language requires (PR #963 review).
    await blockedBy(["Batch"]);
    expect(await screen.findByText(/the required field Batch has no default value/)).toBeInTheDocument();
  });
});

describe("TemplateStep failure and clearing paths", () => {
  it("reports a failed template lookup instead of leaving an unhandled rejection", async () => {
    // The check ran in a detached async task with no catch: a rejection escaped unhandled and the
    // user saw only the spinner stop (Copilot review, PR #1090).
    currentTemplate = {
      ...makeTemplate([]),
      fetchAdditionalInfo: () => Promise.reject(new Error("network down")),
    };
    const onChange = vi.fn();
    render(<TemplateStep value={pickMode} onChange={onChange} originSampleName="S1" />);
    await userEvent.setup().click(screen.getByTestId("template-picker"));
    expect(await screen.findByText(/template\.lookupFailed/)).toBeInTheDocument();
    // and the selection stays unset, so Next remains blocked rather than submitting a bad template
    expect(selectionsFrom(onChange)).not.toContainEqual(expect.objectContaining({ templateId: 5 }));
  });

  it("clears the parent's selection when the picker is cleared", async () => {
    currentTemplate = makeTemplate([{ name: "Batch", mandatory: true, content: "B1", selectedOptions: null }]);
    const onChange = vi.fn();
    render(
      <TemplateStep
        value={{ mode: "pick", templateId: 5, templateName: "T5", remember: false }}
        onChange={onChange}
        originSampleName="S1"
      />,
    );
    await userEvent.setup().click(screen.getByTestId("template-clear"));
    expect(onChange).toHaveBeenCalledWith(expect.objectContaining({ templateId: null, templateName: undefined }));
  });

  // --- "use parent template" status is DISPLAYED here, checked by the wizard (F5) ---

  // The check itself moved to OperationWizard, because the gate it feeds is evaluated on every
  // step while only the active step is mounted (parallel review, C2). What remains here is that
  // this step surfaces the wizard's status, and surfaces it accessibly: both messages now appear
  // with no user action at all, on the preselected mode.
  const fromSampleMode: TemplateSelection = { mode: "fromSample", templateId: null, remember: false };

  it("shows the wizard's parent-template spinner as a live status", async () => {
    render(
      <TemplateStep value={fromSampleMode} onChange={() => undefined} originSampleName="S1" parentTemplateChecking />,
    );
    const status = await screen.findByRole("status");
    expect(status).toHaveTextContent(/template\.checking/);
  });

  it("announces the wizard's parent-template block as an alert", () => {
    render(
      <TemplateStep
        value={fromSampleMode}
        onChange={() => undefined}
        originSampleName="S1"
        parentTemplateError="Batch has no default"
      />,
    );
    expect(screen.getByRole("alert")).toHaveTextContent("Batch has no default");
  });

  it("prefers its own pick error over the wizard's parent-template error", async () => {
    // Only one of the two can be current: picking a template abandons the parent-template mode, so
    // the pick error is the one that describes what the user just did.
    //
    // This test used to render fromSampleMode with only parentTemplateError set and never pick a
    // template, so blockError stayed null and `blockError ?? parentTemplateError` produced the same
    // text either way - reversing the precedence kept it green and it was identical in force to the
    // test above (parallel review). It now produces BOTH errors, which is the only arrangement that
    // can distinguish the two orderings.
    currentTemplate = makeTemplate([{ name: "Concentration", mandatory: true, content: "", selectedOptions: null }]);
    render(
      <TemplateStep
        value={pickMode}
        onChange={() => undefined}
        originSampleName="S1"
        parentTemplateError="parent problem"
      />,
    );
    await userEvent.setup().click(screen.getByTestId("template-picker"));
    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent(/mandatoryFieldsError|cannot be used|no default/i);
    expect(alert).not.toHaveTextContent("parent problem");
  });
});
