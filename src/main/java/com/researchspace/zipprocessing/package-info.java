/**
 * Zip archive traversal helpers. Nothing in this repository references this package, but it is NOT
 * dead code: the dataverse and figshare repository-adapter jars use these classes at runtime, and
 * this repository excludes rspace-core-util (whose copies these in-tree sources stand in for) from
 * those adapter dependencies, so deleting the package fails Spring's introspection of the adapters'
 * configuration classes. Once those adapters are themselves absorbed into this repository, their
 * usages become visible here and this package can be reassessed or deleted along with them.
 */
package com.researchspace.zipprocessing;
