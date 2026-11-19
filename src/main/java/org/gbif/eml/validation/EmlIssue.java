package org.gbif.eml.validation;

import java.util.Objects;

/**
 * One finding about an EML document.
 *
 * @param type what the issue is about
 * @param severity {@link Severity#ERROR} makes the document invalid
 * @param message human-readable detail, for example the XML parser's message
 * @param line 1-based line in the document, or {@code null} when not known
 * @param column 1-based column in the document, or {@code null} when not known
 */
public record EmlIssue(
    EmlIssueType type, Severity severity, String message, Integer line, Integer column) {

  public enum Severity {
    WARNING,
    ERROR
  }

  public EmlIssue {
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(severity, "severity");
  }

  static EmlIssue of(EmlIssueType type, Severity severity, String message) {
    return new EmlIssue(type, severity, message, null, null);
  }

  static EmlIssue at(
      EmlIssueType type, Severity severity, String message, int line, int column) {
    return new EmlIssue(
        type, severity, message, line > 0 ? line : null, column > 0 ? column : null);
  }
}
