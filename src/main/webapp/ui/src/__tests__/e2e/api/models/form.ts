export type ApiFormFieldCreate = { name: string } & (
  | { type: "Text" | "String"; defaultValue?: string }
  | { type: "Number"; defaultValue?: number; min?: number; max?: number }
  | { type: "Date" | "Time"; defaultValue?: number }
  | { type: "Choice"; options: string[]; defaultOptions?: string[]; multipleChoice: boolean }
  | { type: "Radio"; options: string[]; defaultOption?: string }
);

export interface ApiFormCreate {
  name: string;
  fields: ApiFormFieldCreate[];
}

export interface ApiForm {
  id: number;
  name: string;
}
