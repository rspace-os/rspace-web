import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import OperationConfirmation from "../OperationConfirmation";
import type { InventoryOperation } from "../operationsConfig";
import type { TemplateSelection } from "../TemplateStep";
import type { OperationInputs } from "../types";

// i18n runs in cimode in tests, so t(key, params) renders the namespaced key with no interpolation;
// assertions match on the key, which still tells us which branch rendered.
vi.mock("@/stores/use-stores", () => ({
  default: () => ({ unitStore: { getUnit: () => ({ label: "ml" }) } }),
}));

const operation = {
  key: "derive",
  effect: {
    nameFrom: "sampleName",
    countFrom: "count",
    eachAmountFrom: "eachAmount",
    amountTakenFrom: "amountTaken",
    links: [{ relationType: "IsDerivedFrom", fieldNameKey: "operations.derive.linkFieldName" }],
  },
} as unknown as InventoryOperation;

const values: OperationInputs = {
  sampleName: "New material",
  count: 2,
  eachAmount: { numericValue: 5, unitId: 3 },
  amountTaken: { numericValue: 1, unitId: 3 },
};

const renderConf = (overrides: {
  templateSelection: TemplateSelection;
  documentation?: { globalId: string; name: string } | null;
  op?: InventoryOperation;
}) =>
  render(
    <OperationConfirmation
      operation={overrides.op ?? operation}
      values={values}
      documentation={overrides.documentation ?? null}
      templateSelection={overrides.templateSelection}
      originSampleName="S1"
    />,
  );

describe("OperationConfirmation", () => {
  it("summarises a picked template, the amount taken, and a linked document", () => {
    renderConf({
      templateSelection: { mode: "pick", templateId: 5, templateName: "T5", remember: false },
      documentation: { globalId: "SD1", name: "My SOP" },
    });
    expect(screen.getByText(/template\.summaryPick/)).toBeInTheDocument();
    expect(screen.getByText(/confirm\.amountTaken/)).toBeInTheDocument();
    expect(screen.getByText(/confirm\.documentation/)).toBeInTheDocument();
  });

  it("uses the 'no template' summary for mode 'none'", () => {
    renderConf({ templateSelection: { mode: "none", templateId: null, remember: false } });
    expect(screen.getByText(/template\.summaryNone/)).toBeInTheDocument();
  });

  it("uses the 'from parent sample' summary for mode 'fromSample'", () => {
    renderConf({ templateSelection: { mode: "fromSample", templateId: null, remember: false } });
    expect(screen.getByText(/template\.summaryFromSample/)).toBeInTheDocument();
  });

  it("omits the amount-taken and documentation rows when there is neither", () => {
    const op = { ...operation, effect: { ...operation.effect, amountTakenFrom: undefined } } as InventoryOperation;
    renderConf({ op, templateSelection: { mode: "none", templateId: null, remember: false } });
    expect(screen.queryByText(/confirm\.amountTaken/)).not.toBeInTheDocument();
    expect(screen.queryByText(/confirm\.documentation/)).not.toBeInTheDocument();
  });

  it("shows the process name line and renders the summary in an emphasised info panel", () => {
    const op = { ...operation, effect: { ...operation.effect, processNameFrom: "processName" } } as InventoryOperation;
    render(
      <OperationConfirmation
        operation={op}
        values={{ ...values, processName: "dna extraction" }}
        documentation={null}
        templateSelection={{ mode: "none", templateId: null, remember: false }}
        originSampleName="S1"
      />,
    );
    expect(screen.getByText(/confirm\.process/)).toBeInTheDocument();
    // the whole summary stands out in the standard Inventory info panel
    expect(screen.getByRole("alert")).toBeInTheDocument();
  });

  it("omits the process line when the operation has no process name", () => {
    renderConf({ templateSelection: { mode: "none", templateId: null, remember: false } });
    expect(screen.queryByText(/confirm\.process/)).not.toBeInTheDocument();
  });
});
