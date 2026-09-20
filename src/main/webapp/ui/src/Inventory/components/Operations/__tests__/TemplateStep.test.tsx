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
const capturedSetTemplate: Array<(t: FakeTemplate) => void> = [];

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
 * The post-lookup write sends an updater rather than a value, because it crosses an await, so
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

/** A template whose lookup stays in flight until the returned resolver is called. */
const pendingTemplate = (id: number, name: string): { template: FakeTemplate; resolve: () => void } => {
  let settle: () => void = () => undefined;
  return {
    template: {
      id,
      name,
      fetchAdditionalInfo: () => new Promise((r) => (settle = r)),
      fields: [{ name: "F", mandatory: false, content: "x", selectedOptions: null }],
    },
    resolve: () => settle(),
  };
};

describe("TemplateStep", () => {
  it("offers the three template choices (use parent, choose existing, none) and no remember checkbox", () => {
    render(<TemplateStep value={base} onChange={() => undefined} originSampleName="S1" />);
    expect(screen.getAllByRole("radio")).toHaveLength(3);
    expect(screen.queryByRole("checkbox")).not.toBeInTheDocument();
  });

  it("disables 'use parent template' and shows a hint when the parent has no template", () => {
    render(<TemplateStep value={base} onChange={() => undefined} originSampleName="S1" parentHasTemplate={false} />);
    // "use parent" is first
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
    // below.
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

    const tickedMidLookup: TemplateSelection = { ...pickMode, remember: true };
    expect(selectionsFrom(onChange, tickedMidLookup)).toContainEqual(
      expect.objectContaining({ templateId: 5, remember: true }),
    );
  });

  it("discards a stale template lookup that resolves after a newer pick (latest wins)", async () => {
    const { template: templateA, resolve: resolveA } = pendingTemplate(5, "A");
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

    await waitFor(() => expect(selectionsFrom(onChange)).toContainEqual(expect.objectContaining({ templateId: 6 })));
    resolveA();
    await Promise.resolve();
    expect(selectionsFrom(onChange)).not.toContainEqual(expect.objectContaining({ templateId: 5 }));
  });

  it("abandons a pending template lookup when the step unmounts (wizard navigated away)", async () => {
    const { template: templateA, resolve: resolveA } = pendingTemplate(5, "A");
    const onChange = vi.fn();
    const { unmount } = render(<TemplateStep value={pickMode} onChange={onChange} originSampleName="S1" />);
    const user = userEvent.setup();

    currentTemplate = templateA;
    await user.click(screen.getByTestId("template-picker"));
    unmount();
    resolveA();
    await Promise.resolve();
    expect(selectionsFrom(onChange)).not.toContainEqual(expect.objectContaining({ templateId: 5 }));
  });

  it("abandons a pending template lookup when the user switches mode (no stale restore)", async () => {
    const { template: templateA, resolve: resolveA } = pendingTemplate(5, "A");
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
    await blockedBy(["A", "B", "C"]);
    expect(await screen.findByText(/the required fields A, B, and C have no default value/)).toBeInTheDocument();
  });

  it("inflects the sentence for a single blocking field rather than writing 'field(s)'", async () => {
    await blockedBy(["Batch"]);
    expect(await screen.findByText(/the required field Batch has no default value/)).toBeInTheDocument();
  });
});

describe("TemplateStep failure and clearing paths", () => {
  it("reports a failed template lookup instead of leaving an unhandled rejection", async () => {
    currentTemplate = {
      ...makeTemplate([]),
      fetchAdditionalInfo: () => Promise.reject(new Error("network down")),
    };
    const onChange = vi.fn();
    render(<TemplateStep value={pickMode} onChange={onChange} originSampleName="S1" />);
    await userEvent.setup().click(screen.getByTestId("template-picker"));
    expect(await screen.findByText(/template\.lookupFailed/)).toBeInTheDocument();
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
    // Both errors must be set here, or the assertion cannot distinguish the two orderings.
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
