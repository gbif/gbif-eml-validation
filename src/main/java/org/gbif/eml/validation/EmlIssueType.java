package org.gbif.eml.validation;

/** What an {@link EmlIssue} is about. Callers map these to their own issue vocabulary. */
public enum EmlIssueType {
  /** The document is not well-formed XML (or is empty). Nothing else is checked. */
  NOT_WELL_FORMED,
  /** No {@code xsi:schemaLocation} for the EML namespace. */
  SCHEMA_LOCATION_MISSING,
  /** The declared schema location is not one of the bundled {@link EmlSchema}s. */
  SCHEMA_UNSUPPORTED,
  /** The document does not conform to the schema it was validated against. */
  SCHEMA_VIOLATION,
  /** The metadata could not be read into a dataset, so the quality rules did not run. */
  METADATA_UNREADABLE,
  /** GBIF quality rule: the title is missing or shorter than 10 characters. */
  TITLE_MISSING_OR_TOO_SHORT,
  /** GBIF quality rule: no licence GBIF supports. */
  LICENSE_MISSING_OR_UNKNOWN,
  /** GBIF quality rule: the description is missing or shorter than 25 characters. */
  DESCRIPTION_MISSING_OR_TOO_SHORT,
  /** GBIF quality rule: no citation author, or an author whose name cannot be generated. */
  RESOURCE_CONTACTS_MISSING_OR_INCOMPLETE
}
