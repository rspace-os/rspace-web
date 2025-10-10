# Models

This directory contains the classes used by Inventory as well as some business
logic related to these classes. The models are the core of Inventory and are
used throughout the frontend codebase. They are used to represent the data that
is stored in the backend as well as the data that is displayed in the UI.

In the new parts of the codebase (i.e. the parts of the ELN that are being
rewritten in React), we have opted for a less class-heavy approach, using
more plain objects and functions. Performing network activity in custom hooks
instead of class instances also has the advantage of being able to use
contextual functionality like displaying alerts and tracking analytics events
more easily, whereas here we rely on global functions for these things.

Some naming conventions to be aware of:
- Classes ending in `Model` are simply to distinguish them from interfaces
  defined in ../definitions that would otherwise have the same name.
- Any reference to `Record` is simply meant in the sense of "database record".
- Types ending in `Attrs` (short for attributes) refer to plain objects with no
  methods, that have been received from the API or default data that mimics the
  same structure as objects that have been received from the API. These are
  typically used when creating or updating records.
- Functions ending in `Mixin` are functions that take a class and return a new
  class that extends the original class with additional functionality. These
  typically start with `Has` (e.g. `HasLocationMixin`), enhancing the class with
  functionality related to a specific feature.
- Any reference to `Batch` or `Collection` refers to a group of records that are
  being processed together, allowing the user to perform edits across multiple
  records at once. In the UI, this is referred to as "Batch Edit" but as "bulk"
  operations in the API.

These classes make heavy use of mobx for state management, so that as they are
mutated the UI automatically updates to reflect the changes. To learn more about
mobx, start with the [mobx documentation](https://mobx.js.org/README.html).

To understand the codebase, [InventoryBaseRecord](./InventoryBaseRecord.ts) is a
good place to start as it is the base class for all Inventory records but it is
very large with lots of functionality. Other important classes include the
`Has*` mixins and the models for each of the main record types: containers,
samples, subsamples, and templates.
