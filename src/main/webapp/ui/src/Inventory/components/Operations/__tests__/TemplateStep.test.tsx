import { render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
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
  default: ({ setTemplate }: { setTemplate: (t: FakeTemplate) => void }) => {
    capturedSetTemplate.push(setTemplate);
    return <button type="button" data-testid="template-picker" onClick={() => setTemplate(currentTemplate)} />;
  },
}));

const base: TemplateSelection = { mode: "none", templateId: null, remember: false };
const pickMode: TemplateSelection = { mode: "pick", templateId: null, remember: false };

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
    expect(screen.getByRole("alert")).toHaveTextContent("My Template");
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
    expect(onChange).not.toHaveBeenCalledWith(expect.objectContaining({ templateId: 5 }));
  });

  it("accepts a template whose mandatory fields all have defaults", async () => {
    currentTemplate = makeTemplate([{ name: "Batch", mandatory: true, content: "B1", selectedOptions: null }]);
    const onChange = vi.fn();
    render(<TemplateStep value={pickMode} onChange={onChange} originSampleName="S1" />);
    await userEvent.setup().click(screen.getByTestId("template-picker"));
    await waitFor(() => expect(onChange).toHaveBeenCalledWith(expect.objectContaining({ templateId: 5 })));
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
    await waitFor(() => expect(onChange).toHaveBeenCalledWith(expect.objectContaining({ templateId: 6 })));
    resolveA();
    await Promise.resolve();
    expect(onChange).not.toHaveBeenCalledWith(expect.objectContaining({ templateId: 5 }));
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
    expect(onChange).not.toHaveBeenCalledWith(expect.objectContaining({ templateId: 5 }));
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
    expect(onChange).not.toHaveBeenCalledWith(expect.objectContaining({ templateId: 5 }));
  });
});
