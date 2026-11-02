import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { expect, test, vi } from "vitest";
import FeatureFlagRow from "./FeatureFlagRow";
import { FEATURE_FLAGS } from "./generatedFeatureFlags";
import type { FeatureFlagEntry } from "./schema";

const entry: FeatureFlagEntry = {
  value: true,
  baselineValue: false,
  source: "DEFAULT",
  canOverride: true,
};

test.each([
  { source: "DEFAULT", action: "set", overrideValue: true },
  { source: "USER_OVERRIDE", action: "clear", overrideValue: null },
] as const)("$action sends only the override field", async ({ source, action, overrideValue }) => {
  const user = userEvent.setup();
  const onPatch = vi.fn();
  render(
    <table>
      <tbody>
        <FeatureFlagRow
          flagName={FEATURE_FLAGS.bookingEnabled}
          entry={{ ...entry, source }}
          canChangeBaselines={false}
          mutationPending={false}
          onPatch={onPatch}
        />
      </tbody>
    </table>,
  );

  await user.click(screen.getByRole("button", { name: `common:actions.${action}` }));

  expect(onPatch).toHaveBeenCalledExactlyOnceWith({ overrideValue });
});

test("toggling the override submits the new boolean", async () => {
  const user = userEvent.setup();
  const onPatch = vi.fn();
  render(
    <table>
      <tbody>
        <FeatureFlagRow
          flagName={FEATURE_FLAGS.bookingEnabled}
          entry={entry}
          canChangeBaselines={false}
          mutationPending={false}
          onPatch={onPatch}
        />
      </tbody>
    </table>,
  );

  await user.click(screen.getByRole("switch", { name: "common:featureFlags.overrideLabel" }));

  expect(onPatch).toHaveBeenCalledExactlyOnceWith({ overrideValue: false });
});

test("saving an edited baseline submits only the baseline field", async () => {
  const user = userEvent.setup();
  const onPatch = vi.fn();
  render(
    <table>
      <tbody>
        <FeatureFlagRow
          flagName={FEATURE_FLAGS.bookingEnabled}
          entry={entry}
          canChangeBaselines
          mutationPending={false}
          onPatch={onPatch}
        />
      </tbody>
    </table>,
  );
  const save = screen.getByRole("button", { name: "common:actions.save" });
  expect(save).toBeDisabled();

  await user.click(screen.getByRole("switch", { name: "common:featureFlags.baseline.controlLabel" }));
  await user.click(save);

  expect(onPatch).toHaveBeenCalledExactlyOnceWith({ baselineValue: true });
});

test("pending mutations disable override and baseline controls", async () => {
  const user = userEvent.setup();
  const onPatch = vi.fn();
  render(
    <table>
      <tbody>
        <FeatureFlagRow
          flagName={FEATURE_FLAGS.bookingEnabled}
          entry={entry}
          canChangeBaselines
          mutationPending
          onPatch={onPatch}
        />
      </tbody>
    </table>,
  );

  for (const control of screen.getAllByRole("switch")) {
    expect(control).toHaveAttribute("aria-disabled", "true");
    await user.click(control);
  }
  for (const control of screen.getAllByRole("button")) {
    expect(control).toBeDisabled();
  }
  expect(onPatch).not.toHaveBeenCalled();
});
