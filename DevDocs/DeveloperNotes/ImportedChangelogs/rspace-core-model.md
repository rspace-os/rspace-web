## Changelog

Summary of important or breaking changes.

## 3.3.0 2026-08-20
 - rspace-parent 3.0.0 -> 3.1.0, bringing Shiro 2.1.0 -> 3.0.0

## 3.2.0 2026-08-07
 - Keep temporary autosave document copies out of the Lucene index

## 3.1.0 2026-07-29
 - Adapting B2inst object for the community workflow

## 3.0.0 2026-07-28
- Spring 6 / Hibernate 6 / Jakarta namespace migration
- Switch to rspace-parent 3.0.0
- Incorporates all changes up to 2.31.0 (merged from main)

## 2.31.0 2026-07-24
- Added `isEditable` flag to the Instrument Template 

## 2.30.0 2026-06-25
- Added `com.researchspace.b2inst.model` PIDINST/B2INST domain wrappers

## 2.29.0 2026-06-24
- New `SampleTemplate` entity to support sample templates in the inventory module

## 2.28.0 2026-06-18
- Amended the existing `IdentifierType.DATACITE_IGSN` to be `IdentifierType.IGSN_DATACITE`
- Added new `IdentifierType.PIDINST_DATACITE` and `IdentifierType.PIDINST_B2INST`

## 2.27.0 2026-06-11
- Restructuring core model to allow inventory linking

## 2.26.0 2026-06-11
- Removed models related to calendar creation.
- Removed the description and external link fields from UserProfile.

## 2.24.0 2026-05-15
- Add new `Instrument Templates` end points

## 2.22.0 2026-05-15
- Add new `InstrumentEntity` model and renaming `SampleField` to `InventoryEntityField

## 2.21.0 2026-03-31
- Change `connectedUser` and `connectedGroups` to be a `Set` instead of a `List`

- ## 2.14.0 2025-09-05
- Restructuring core model objects for DMP

## 2.9.0 2025-04-02
- Extended DigitalObjectIdentifier to be not associated to any inventory item

## 2.1.1 2024-08-09
- Added integration with Digital Commons Data

## 2.1.0 2024-07-23
- version buildable with jitpack & downloadable from https://jitpack.io/#rspace-os/rspace-core-model

## 2.0.1
- published as open-source at https://github.com/rspace-os/rspace-core-model
