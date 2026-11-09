import { Form, type FormStore, getInput } from "@formisch/react";
import type { ReactNode } from "react";

export function FormWithValues({ children, form }: { children: ReactNode; form: FormStore }) {
  return (
    <Form of={form} className="mx-auto max-w-5xl space-y-8 p-8" onSubmit={() => undefined}>
      {children}
      <output aria-label="Form values">{JSON.stringify(getInput(form))}</output>
    </Form>
  );
}
