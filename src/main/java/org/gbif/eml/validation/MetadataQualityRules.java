package org.gbif.eml.validation;

import java.util.ArrayList;
import java.util.List;
import org.gbif.api.model.registry.CitationContact;
import org.gbif.api.model.registry.Dataset;
import org.gbif.api.util.CitationGenerator;
import org.gbif.eml.validation.EmlIssue.Severity;
import org.gbif.metadata.eml.parse.DatasetEmlParser;

/**
 * The GBIF metadata quality rules, formerly {@code BasicMetadataEvaluator} in pipelines'
 * validator-core. The document is read with the registry's own EML parser, so licence and author
 * interpretation match what registry would store.
 */
final class MetadataQualityRules {

  static final int MIN_TITLE_LENGTH = 10;
  static final int MIN_DESCRIPTION_LENGTH = 25;

  private MetadataQualityRules() {}

  static List<EmlIssue> evaluate(byte[] eml) {
    Dataset dataset;
    try {
      dataset = DatasetEmlParser.build(eml);
    } catch (Exception | LinkageError e) {
      return List.of(
          EmlIssue.of(
              EmlIssueType.METADATA_UNREADABLE,
              Severity.ERROR,
              "The metadata cannot be read as a dataset: " + e.getMessage()));
    }

    List<EmlIssue> issues = new ArrayList<>();
    if (isShorterThan(dataset.getTitle(), MIN_TITLE_LENGTH)) {
      issues.add(
          rule(
              EmlIssueType.TITLE_MISSING_OR_TOO_SHORT,
              "The title must have at least " + MIN_TITLE_LENGTH + " characters."));
    }
    if (dataset.getLicense() == null) {
      issues.add(
          rule(
              EmlIssueType.LICENSE_MISSING_OR_UNKNOWN,
              "No licence, or a licence GBIF does not support."));
    }
    if (isShorterThan(dataset.getDescription(), MIN_DESCRIPTION_LENGTH)) {
      issues.add(
          rule(
              EmlIssueType.DESCRIPTION_MISSING_OR_TOO_SHORT,
              "The description must have at least " + MIN_DESCRIPTION_LENGTH + " characters."));
    }
    // At least one author, and an author name can be generated for every author.
    List<CitationContact> authors = CitationGenerator.getAuthors(dataset.getContacts());
    if (authors.isEmpty()
        || authors.size() != CitationGenerator.generateAuthorsName(authors).size()) {
      issues.add(
          rule(
              EmlIssueType.RESOURCE_CONTACTS_MISSING_OR_INCOMPLETE,
              "At least one author is needed, and every author needs a name for the citation."));
    }
    return issues;
  }

  private static boolean isShorterThan(String value, int length) {
    return value == null || value.isBlank() || value.length() < length;
  }

  private static EmlIssue rule(EmlIssueType type, String message) {
    return EmlIssue.of(type, Severity.WARNING, message);
  }
}
