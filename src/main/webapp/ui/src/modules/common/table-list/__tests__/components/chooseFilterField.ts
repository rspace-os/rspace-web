import { screen, waitFor } from "@testing-library/react";
import type { UserEvent } from "@testing-library/user-event";
import { expect } from "vitest";

export async function chooseFilterField(user: UserEvent, label: string | RegExp): Promise<void> {
  await chooseFrom(user, "common:tableList.filters.field", label);
}

export async function chooseFilterOperator(user: UserEvent, label: string | RegExp): Promise<void> {
  await chooseFrom(user, "common:tableList.filters.operator", label);
}

export async function chooseFilterValue(user: UserEvent, label: string | RegExp): Promise<void> {
  await chooseFrom(user, "common:tableList.filters.value", label);
}

async function chooseFrom(user: UserEvent, control: string, label: string | RegExp): Promise<void> {
  await user.click(screen.getByRole("combobox", { name: control }));
  const option = await screen.findByRole("option", { name: label });
  await user.click(option);
  await waitFor(() => expect(screen.queryByRole("option", { name: label })).not.toBeInTheDocument());
}
