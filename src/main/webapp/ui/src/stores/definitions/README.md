# Definitions

This directory contains the type definitions and interfaces used throughout the
application, but in practice mostly just Inventory. These definitions help
ensure type safety and consistency across the codebase. Anywhere where a type
or interface is used in more than one place, it should be defined here. Where
the files in this directory do contain actual code, it should be very simple
logic for manipulating the types/interfaces defined in the files. There MUST
be no dependencies on any other part of the codebase in this directory, a rule
that is enforced by dependency cruiser (see [`dependency-cruiser.js`](../../../.dependency-cruiser.js))

To learn more about Inventory, start first with [BaseRecord](./BaseRecord.ts)
and [InventoryRecord](./InventoryRecord.ts) from which all other Inventory
types derive.
