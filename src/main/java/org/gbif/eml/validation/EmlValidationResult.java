package org.gbif.eml.validation;

import java.util.List;
import java.util.Optional;

/**
 * The outcome of validating one EML document.
 *
 * @param schema the bundled schema the document was validated against; {@code null} when no
 *     schema validation ran (not well-formed, or no usable location and no default)
 * @param issues every finding, in the order the checks ran
 */
public record EmlValidationResult(EmlSchema schema, List<EmlIssue> issues) {

  public EmlValidationResult {
    issues = List.copyOf(issues);
  }

  public Optional<EmlSchema> schemaUsed() {
    return Optional.ofNullable(schema);
  }

  /** {@code true} when no issue has {@link EmlIssue.Severity#ERROR}. */
  public boolean isValid() {
    return issues.stream().noneMatch(i -> i.severity() == EmlIssue.Severity.ERROR);
  }

  public List<EmlIssue> errors() {
    return issues.stream().filter(i -> i.severity() == EmlIssue.Severity.ERROR).toList();
  }

  public List<EmlIssue> warnings() {
    return issues.stream().filter(i -> i.severity() == EmlIssue.Severity.WARNING).toList();
  }
}
