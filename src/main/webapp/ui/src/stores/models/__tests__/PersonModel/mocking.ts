import { type PersonAttrs } from "../../../definitions/Person";

export const personAttrs = (
  attrs?: Readonly<Partial<PersonAttrs>>
): PersonAttrs => ({
  id: 1,
  username: "user1a",
  firstName: "User",
  lastName: "User",
  email: "user@example.com",
  hasPiRole: false,
  hasSysAdminRole: false,
  workbenchId: 1,
  _links: [],
  ...attrs,
});
