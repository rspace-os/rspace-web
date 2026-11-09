import "@/stores/stores/RootStore";

import { ThemeProvider } from "@mui/material/styles";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, test, vi } from "vitest";
import { expectAccessible } from "@/__tests__/accessibility";
import { DeploymentPropertyContext } from "@/hooks/api/useDeploymentProperty";
import { makeMockSample } from "@/stores/models/__tests__/SampleModel/mocking";
import { SampleCollection } from "@/stores/models/SampleModel";
import materialTheme from "@/theme";
import RsSet from "@/util/set";
import Requestable from "../Requestable";

vi.mock("@/common/InvApiService", () => ({ default: {} }));

const CAN_SET_REQUESTABLE = ["READ", "UPDATE", "CHANGE_OWNER", "CHANGE_REQUESTABLE"] as const;

function deploymentProperties(available: boolean) {
  return new Map<string, unknown>([["inventory.sampleRequests.available", available ? "ALLOWED" : "DENIED"]]);
}

function renderFor(collection: SampleCollection, { available = true }: { available?: boolean } = {}) {
  return render(
    <ThemeProvider theme={materialTheme}>
      <DeploymentPropertyContext.Provider value={deploymentProperties(available)}>
        <Requestable collection={collection} />
      </DeploymentPropertyContext.Provider>
    </ThemeProvider>,
  );
}

describe("Requestable (batch)", () => {
  test("renders nothing when sample requests are not available.", () => {
    const collection = new SampleCollection(
      new RsSet([
        makeMockSample({ id: 1, requestable: false, permittedActions: [...CAN_SET_REQUESTABLE] }),
        makeMockSample({ id: 2, globalId: "SA2", requestable: false, permittedActions: [...CAN_SET_REQUESTABLE] }),
      ]),
    );
    const { container } = renderFor(collection, { available: false });
    expect(container).toBeEmptyDOMElement();
  });

  test("renders nothing when one of the selected samples cannot have its requestable status changed.", () => {
    const collection = new SampleCollection(
      new RsSet([
        makeMockSample({ id: 1, requestable: false, permittedActions: [...CAN_SET_REQUESTABLE] }),
        makeMockSample({ id: 2, globalId: "SA2", requestable: false, permittedActions: ["READ", "UPDATE"] }),
      ]),
    );
    const { container } = renderFor(collection);
    expect(container).toBeEmptyDOMElement();
  });

  test("shows the switch disabled, alongside the Batch edit this field checkbox, until opted in.", async () => {
    const collection = new SampleCollection(
      new RsSet([
        makeMockSample({ id: 1, requestable: false, permittedActions: [...CAN_SET_REQUESTABLE] }),
        makeMockSample({ id: 2, globalId: "SA2", requestable: false, permittedActions: [...CAN_SET_REQUESTABLE] }),
      ]),
    );
    const { container } = renderFor(collection);
    await expectAccessible(container);

    const toggle = screen.getByRole("switch", { name: "inventory:sample.requestsSection.switchLabel" });
    expect(toggle).toBeDisabled();
    expect(toggle).not.toBeChecked();
    expect(screen.getByRole("checkbox", { name: "common:inputs.chooseToEdit.label" })).not.toBeChecked();
  });

  test("shows 'Varies' instead of the switch when the selection disagrees and batch editing is not yet enabled.", () => {
    const collection = new SampleCollection(
      new RsSet([
        makeMockSample({ id: 1, requestable: true, permittedActions: [...CAN_SET_REQUESTABLE] }),
        makeMockSample({ id: 2, globalId: "SA2", requestable: false, permittedActions: [...CAN_SET_REQUESTABLE] }),
      ]),
    );
    renderFor(collection);
    expect(
      screen.queryByRole("switch", { name: "inventory:sample.requestsSection.switchLabel" }),
    ).not.toBeInTheDocument();
    expect(screen.getByText("Varies")).toBeInTheDocument();
  });

  test("opting in on a disagreeing selection normalises every sample to the value shown, without touching the switch.", async () => {
    const user = userEvent.setup();
    const samples = [
      makeMockSample({ id: 1, requestable: false, permittedActions: [...CAN_SET_REQUESTABLE] }),
      makeMockSample({ id: 2, globalId: "SA2", requestable: true, permittedActions: [...CAN_SET_REQUESTABLE] }),
    ];
    const collection = new SampleCollection(new RsSet(samples));
    renderFor(collection);

    await user.click(screen.getByRole("checkbox", { name: "common:inputs.chooseToEdit.label" }));

    const toggle = screen.getByRole("switch", {
      name: "inventory:sample.requestsSection.switchLabel",
    }) as HTMLInputElement;
    expect(toggle).toBeEnabled();

    // Every sample must already match the shown position (see setFieldEditable).
    expect(samples.every((s) => s.requestable === toggle.checked)).toBe(true);
  });

  test("toggling the switch after opting in applies the new value to every selected sample.", async () => {
    const user = userEvent.setup();
    const samples = [
      makeMockSample({ id: 1, requestable: false, permittedActions: [...CAN_SET_REQUESTABLE] }),
      makeMockSample({ id: 2, globalId: "SA2", requestable: true, permittedActions: [...CAN_SET_REQUESTABLE] }),
    ];
    const collection = new SampleCollection(new RsSet(samples));
    renderFor(collection);

    await user.click(screen.getByRole("checkbox", { name: "common:inputs.chooseToEdit.label" }));

    const toggle = screen.getByRole("switch", {
      name: "inventory:sample.requestsSection.switchLabel",
    }) as HTMLInputElement;
    expect(toggle).toBeEnabled();

    const initiallyChecked = toggle.checked;
    await user.click(toggle);
    expect(samples.every((s) => s.requestable === !initiallyChecked)).toBe(true);

    await user.click(toggle);
    expect(samples.every((s) => s.requestable === initiallyChecked)).toBe(true);
  });
});
