import "@/stores/stores/RootStore";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import type React from "react";
import { beforeAll, describe, expect, it, vi } from "vitest";
import { createRealI18nWrapper } from "@/__tests__/helpers/realI18n";
import common from "@/modules/common/i18n/locales/en-US/common.json";
import inventory from "@/modules/common/i18n/locales/en-US/inventory.json";
import { makeMockContainer } from "@/stores/models/__tests__/ContainerModel/mocking";
import type ContainerModel from "@/stores/models/ContainerModel";
import type Search from "@/stores/models/Search";
import PlacementStep from "../PlacementStep";
import type { PlacementSelection } from "../placement";

let InEnglish: React.ComponentType<{ children: React.ReactNode }>;
beforeAll(async () => {
  InEnglish = await createRealI18nWrapper({ resources: { common, inventory }, defaultNS: "common" });
});

const picked = vi.hoisted(() => ({ container: null as unknown }));
vi.mock("../../Picker/Picker", () => ({
  default: ({ search }: { search: Search }) => (
    <button
      type="button"
      data-testid="picker-pick"
      onClick={() => search.callbacks?.setActiveResult?.(picked.container as ContainerModel)}
    />
  ),
}));

function renderStep(value: PlacementSelection, count = 1) {
  const onChange = vi.fn();
  render(
    <InEnglish>
      <PlacementStep value={value} onChange={onChange} count={count} />
    </InEnglish>,
  );
  return onChange;
}

describe("PlacementStep", () => {
  it("offers the workbench as the selected choice and hides the picker", () => {
    renderStep({ mode: "workbench" });
    expect(screen.getByRole("radio", { name: "Leave on my workbench" })).toBeChecked();
    expect(screen.queryByTestId("picker-pick")).not.toBeInTheDocument();
  });

  it("switches to container mode with nothing picked yet", async () => {
    const user = userEvent.setup();
    const onChange = renderStep({ mode: "workbench" });
    await user.click(screen.getByRole("radio", { name: "Place in a container" }));
    expect(onChange).toHaveBeenCalledWith({ mode: "container", container: null });
  });

  it("reports the container picked in the step's own picker", async () => {
    const user = userEvent.setup();
    const container = makeMockContainer({ name: "Shelf" });
    picked.container = container;
    const onChange = renderStep({ mode: "container", container: null });
    await user.click(screen.getByTestId("picker-pick"));
    const [selection] = onChange.mock.lastCall as [PlacementSelection];
    expect(selection.mode === "container" && selection.container).toBe(container);
  });
});
