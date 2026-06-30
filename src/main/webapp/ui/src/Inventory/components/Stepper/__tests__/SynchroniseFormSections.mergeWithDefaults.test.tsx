import { render } from "@testing-library/react";
import { type ReactNode, useContext, useEffect } from "react";
import { describe, expect, test, vi } from "vitest";
import FormSectionsContext from "../../../../stores/contexts/FormSections";
import SynchroniseFormSections from "../SynchroniseFormSections";

const mockUseUiPreference = vi.hoisted(() => vi.fn());

vi.mock("@/hooks/api/useUiPreference", async () => {
  const actual = await vi.importActual<typeof import("@/hooks/api/useUiPreference")>("@/hooks/api/useUiPreference");
  return {
    ...actual,
    UiPreferences: ({ children }: { children: ReactNode }) => children,
    default: mockUseUiPreference,
  };
});

// Simulates a preference object saved before instrument / instrumentTemplate were added.
const staleStateWithoutInstruments = {
  container: {
    information: false,
    overview: true,
    details: false,
    barcodes: false,
    identifiers: false,
    attachments: false,
    permissions: false,
    customFields: false,
    locationsAndContent: true,
  },
  sample: {
    information: false,
    overview: true,
    details: false,
    barcodes: false,
    identifiers: false,
    attachments: false,
    permissions: false,
    customFields: false,
    subsamples: true,
  },
  subSample: {
    information: false,
    overview: true,
    details: true,
    barcodes: false,
    identifiers: false,
    attachments: false,
    permissions: false,
    customFields: false,
    notes: false,
  },
  sampleTemplate: {
    overview: true,
    details: false,
    permissions: false,
    customFields: false,
    samples: true,
  },
  mixed: {
    information: false,
    overview: true,
    details: false,
    barcodes: false,
  },
  // instrument and instrumentTemplate are intentionally absent
};

function captureIsExpanded(type: string, section: string): { getValue: () => boolean | undefined } {
  const captured = { value: undefined as boolean | undefined };
  function Probe() {
    const ctx = useContext(FormSectionsContext);
    useEffect(() => {
      captured.value = ctx?.isExpanded(type as Parameters<NonNullable<typeof ctx>["isExpanded"]>[0], section);
    }, [ctx]);
    return null;
  }
  render(
    <SynchroniseFormSections>
      <Probe />
    </SynchroniseFormSections>,
  );
  return { getValue: () => captured.value };
}

describe("SynchroniseFormSections — stale persisted preferences (missing instrument keys)", () => {
  test("does not throw when both instrument and instrumentTemplate keys are absent", () => {
    mockUseUiPreference.mockReturnValue([staleStateWithoutInstruments, vi.fn()]);
    expect(() =>
      render(
        <SynchroniseFormSections>
          <span />
        </SynchroniseFormSections>,
      ),
    ).not.toThrow();
  });

  test("isExpanded('instrument', 'details') returns the default (false) when instrument key is absent", () => {
    mockUseUiPreference.mockReturnValue([staleStateWithoutInstruments, vi.fn()]);
    const { getValue } = captureIsExpanded("instrument", "details");
    expect(getValue()).toBe(false);
  });

  test("isExpanded('instrument', 'overview') returns the default (true) when instrument key is absent", () => {
    mockUseUiPreference.mockReturnValue([staleStateWithoutInstruments, vi.fn()]);
    const { getValue } = captureIsExpanded("instrument", "overview");
    expect(getValue()).toBe(true);
  });

  test("isExpanded('instrumentTemplate', 'details') returns the default (false) when instrumentTemplate key is absent", () => {
    mockUseUiPreference.mockReturnValue([staleStateWithoutInstruments, vi.fn()]);
    const { getValue } = captureIsExpanded("instrumentTemplate", "details");
    expect(getValue()).toBe(false);
  });

  test("isExpanded('instrumentTemplate', 'instruments') returns the default (true) when instrumentTemplate key is absent", () => {
    mockUseUiPreference.mockReturnValue([staleStateWithoutInstruments, vi.fn()]);
    const { getValue } = captureIsExpanded("instrumentTemplate", "instruments");
    expect(getValue()).toBe(true);
  });

  test("isExpanded('instrument', 'details') returns the default when only instrument key is absent", () => {
    mockUseUiPreference.mockReturnValue([
      {
        ...staleStateWithoutInstruments,
        instrumentTemplate: {
          overview: true,
          details: false,
          barcodes: false,
          attachments: false,
          permissions: false,
          customFields: false,
          instruments: true,
        },
        // instrument key still absent
      },
      vi.fn(),
    ]);
    const { getValue } = captureIsExpanded("instrument", "details");
    expect(getValue()).toBe(false);
  });

  test("stored values for present keys are not overwritten by the merge", () => {
    mockUseUiPreference.mockReturnValue([
      {
        ...staleStateWithoutInstruments,
        // container.overview was collapsed by a previous user interaction
        container: { ...staleStateWithoutInstruments.container, overview: false },
      },
      vi.fn(),
    ]);
    const { getValue } = captureIsExpanded("container", "overview");
    expect(getValue()).toBe(false);
  });
});
