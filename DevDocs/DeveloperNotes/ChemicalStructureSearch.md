# Chemical structure search

RSDEV-210. Design: `DevDocs/adr/0016-inventory-structure-search.md`.

Draw or paste a structure in Ketcher. **Substructure** (default) finds anything containing it.
**Exact** finds only that structure. The search needs the Indigo chemistry service and the Chemistry
app enabled.

- **Workspace:** Search → Chemical lists documents and Gallery files, then readable Inventory items.
- **Inventory:** the **Chemical** button in the search toolbar. It combines with the other filters,
  except a container or basket scope and Requestable.

**Searchable:** chemistry files attached to an Inventory item **from the Gallery**, including
duplicated items. Files uploaded straight into an item are not searched.

**Limits:** stops at 1,000 items (Inventory shows a warning). Follow-up: RSDEV-1713.

**API:** `GET /api/inventory/v1/search?chemQuery=<SMILES>&chemSearchType=SUBSTRUCTURE|EXACT`

**Known issues:** RSDEV-1727 (replacing a Gallery file version).
