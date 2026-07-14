import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { render } from "@/__tests__/customQueries";
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
// (an unstable one drives Picker's selection effect into an infinite loop).
const capturedSetTemplate: Array<(t: FakeTemplate) => void> = [];

// Stub the picker so this test does not mount its real Search/fetcher; clicking it plays back the
// current fake template through the setTemplate prop.
vi.mock("@/Inventory/components/Picker/TemplatePicker", () => ({
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
  it("offers the three template choices and a remember checkbox", () => {
    render(<TemplateStep value={base} onChange={() => undefined} originSampleName="S1" />);
    expect(screen.getAllByRole("radio")).toHaveLength(3);
    expect(screen.getByRole("checkbox")).toBeInTheDocument();
  });

  it("passes a referentially stable setTemplate to the picker across re-renders", () => {
    capturedSetTemplate.length = 0;
    const { rerender } = render(<TemplateStep value={pickMode} onChange={() => undefined} originSampleName="S1" />);
    const first = capturedSetTemplate.at(-1);
    // Re-render with a changed value (as selecting a template does), which would otherwise create a
    // fresh handler and retrigger the picker's selection effect endlessly.
    rerender(<TemplateStep value={{ ...pickMode, templateId: 7 }} onChange={() => undefined} originSampleName="S1" />);
    expect(capturedSetTemplate.at(-1)).toBe(first);
  });

  it("selects the 'existing template' mode", async () => {
    const onChange = vi.fn();
    render(<TemplateStep value={base} onChange={onChange} originSampleName="S1" />);
    await userEvent.setup().click(screen.getAllByRole("radio")[1]);
    expect(onChange).toHaveBeenCalledWith(expect.objectContaining({ mode: "pick" }));
  });

  it("toggles remember", async () => {
    const onChange = vi.fn();
    render(<TemplateStep value={base} onChange={onChange} originSampleName="S1" />);
    await userEvent.setup().click(screen.getByRole("checkbox"));
    expect(onChange).toHaveBeenCalledWith(expect.objectContaining({ remember: true }));
  });

  it("shows a remembered template as a banner with its name and no radio selected", () => {
    render(
      <TemplateStep
        value={{ mode: "remembered", templateId: 5, templateName: "My Template", remember: true }}
        onChange={() => undefined}
        originSampleName="S1"
      />,
    );
    // emphasised via the standard Inventory info Alert, not a plain line of text
    expect(screen.getByRole("alert")).toHaveTextContent("My Template");
    for (const radio of screen.getAllByRole("radio")) expect(radio).not.toBeChecked();
    // and the picker is not open
    expect(screen.queryByTestId("template-picker")).not.toBeInTheDocument();
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
    // an error is displayed (English or, if i18n is not initialised, the raw key) ...
    expect(await screen.findByText(/mandatoryFieldsError|cannot be used|no default/i)).toBeInTheDocument();
    // ... and the template id is never accepted, so Next stays blocked
    expect(onChange).not.toHaveBeenCalledWith(expect.objectContaining({ templateId: 5 }));
  });

  it("accepts a template whose mandatory fields all have defaults", async () => {
    currentTemplate = makeTemplate([{ name: "Batch", mandatory: true, content: "B1", selectedOptions: null }]);
    const onChange = vi.fn();
    render(<TemplateStep value={pickMode} onChange={onChange} originSampleName="S1" />);
    await userEvent.setup().click(screen.getByTestId("template-picker"));
    await waitFor(() => expect(onChange).toHaveBeenCalledWith(expect.objectContaining({ templateId: 5 })));
  });
});
