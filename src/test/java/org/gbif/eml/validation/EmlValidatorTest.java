package org.gbif.eml.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.gbif.eml.validation.EmlIssue.Severity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class EmlValidatorTest {

  private static final EmlValidator SCHEMA_ONLY =
      EmlValidator.builder().qualityRules(false).build();

  private static final String EML_2_2_0_NS = "https://eml.ecoinformatics.org/eml-2.2.0";

  @ParameterizedTest
  @EnumSource(EmlSchema.class)
  void everyBundledSchemaLoadsWithoutNetwork(EmlSchema schema) {
    assertNotNull(BundledSchemas.get(schema));
  }

  @Test
  void currentProfileDocumentIsValidWithoutIssues() throws IOException {
    EmlValidationResult result = EmlValidator.builder().build().validate(resource("gbif-profile-1.3.xml"));

    assertEquals(EmlSchema.GBIF_PROFILE_1_3, result.schema());
    assertEquals(List.of(), result.issues());
    assertTrue(result.isValid());
  }

  @Test
  void profile11DocumentConformsToItsSchema() throws IOException {
    EmlValidationResult result = SCHEMA_ONLY.validate(resource("gbif-profile-1.1.xml"));

    assertEquals(EmlSchema.GBIF_PROFILE_1_1, result.schema());
    assertEquals(List.of(), result.errors());
  }

  @Test
  void profileReferencedByItsProfileXsdIsRecognised() throws IOException {
    // .../eml-gbif-profile/1.0.1/eml-gbif-profile.xsd, as in registry's sample documents
    EmlValidationResult result = SCHEMA_ONLY.validate(resource("gbif-profile-1.0.1.xml"));

    assertEquals(EmlSchema.GBIF_PROFILE_1_0_1, result.schema());
    // The sample's only schema problem is xml:lang="en_US" (xs:language needs a hyphen).
    assertTrue(result.errors().stream().allMatch(e -> e.message().contains("en_US")));
    assertFalse(result.errors().isEmpty());
  }

  @Test
  void schemaViolationsAreErrorsWithPosition() throws IOException {
    assertTrue(SCHEMA_ONLY.validate(resource("ebird.xml")).isValid());

    EmlValidationResult result = SCHEMA_ONLY.validate(resource("ebird-invalid.xml"));

    assertFalse(result.isValid());
    EmlIssue first = result.errors().get(0);
    assertEquals(EmlIssueType.SCHEMA_VIOLATION, first.type());
    assertNotNull(first.line());
    assertNotNull(first.message());
  }

  @Test
  void qualityRulesReportEveryContentIssueAsWarning() throws IOException {
    EmlValidationResult result = EmlValidator.builder().build().validate(resource("content-issues.xml"));

    Set<EmlIssueType> rules =
        result.warnings().stream().map(EmlIssue::type).collect(Collectors.toSet());
    assertEquals(
        Set.of(
            EmlIssueType.TITLE_MISSING_OR_TOO_SHORT,
            EmlIssueType.LICENSE_MISSING_OR_UNKNOWN,
            EmlIssueType.DESCRIPTION_MISSING_OR_TOO_SHORT,
            EmlIssueType.RESOURCE_CONTACTS_MISSING_OR_INCOMPLETE),
        rules);
  }

  @Test
  void qualityRulesCanBeTurnedOff() throws IOException {
    assertTrue(SCHEMA_ONLY.validate(resource("content-issues.xml")).warnings().isEmpty());
  }

  @Test
  void notWellFormedStopsAllOtherChecks() {
    EmlValidationResult result = EmlValidator.builder().build().validate(bytes("null"));

    assertNull(result.schema());
    assertEquals(List.of(EmlIssueType.NOT_WELL_FORMED), types(result));
    assertEquals(1, result.issues().get(0).line());
  }

  @Test
  void emptyDocumentIsNotWellFormed() {
    EmlValidationResult result = SCHEMA_ONLY.validate(new byte[0]);

    assertEquals(List.of(EmlIssueType.NOT_WELL_FORMED), types(result));
    assertFalse(result.isValid());
  }

  @Test
  void missingLocationUsesTheDefaultWithAWarning() {
    EmlValidator validator =
        EmlValidator.builder().defaultSchema(EmlSchema.EML_2_2_0).qualityRules(false).build();

    EmlValidationResult result = validator.validate(bytes(minimal220(null)));

    assertEquals(EmlSchema.EML_2_2_0, result.schema());
    assertEquals(List.of(EmlIssueType.SCHEMA_LOCATION_MISSING), types(result));
    assertEquals(Severity.WARNING, result.issues().get(0).severity());
    assertTrue(result.isValid());
  }

  @Test
  void missingLocationWithoutDefaultIsAnErrorAndSkipsSchemaValidation() {
    EmlValidationResult result = SCHEMA_ONLY.validate(bytes(minimal220(null)));

    assertNull(result.schema());
    assertEquals(List.of(EmlIssueType.SCHEMA_LOCATION_MISSING), types(result));
    assertFalse(result.isValid());
  }

  @Test
  void unsupportedLocationIsReportedAndNeverFetched() {
    EmlValidator validator =
        EmlValidator.builder().defaultSchema(EmlSchema.EML_2_2_0).qualityRules(false).build();

    EmlValidationResult result =
        validator.validate(bytes(minimal220("https://example.invalid/eml.xsd")));

    assertEquals(EmlSchema.EML_2_2_0, result.schema());
    assertEquals(List.of(EmlIssueType.SCHEMA_UNSUPPORTED), types(result));
    assertTrue(result.issues().get(0).message().contains("https://example.invalid/eml.xsd"));
  }

  @Test
  void locationOfAnotherNamespaceDoesNotCount() {
    String doc =
        """
        <eml:eml xmlns:eml="%s" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
            xsi:schemaLocation="http://purl.org/dc/terms/ https://rs.gbif.org/schema/eml-2.2.0/eml.xsd"
            packageId="p" system="s"><dataset><title>t</title><creator><organizationName>o</organizationName></creator><contact><organizationName>o</organizationName></contact></dataset></eml:eml>
        """
            .formatted(EML_2_2_0_NS);

    assertEquals(
        List.of(EmlIssueType.SCHEMA_LOCATION_MISSING), types(SCHEMA_ONLY.validate(bytes(doc))));
  }

  @Test
  void locationsMatchRegardlessOfScheme() {
    assertEquals(
        EmlSchema.GBIF_PROFILE_1_1,
        EmlSchema.fromLocation("https://rs.gbif.org/schema/eml-gbif-profile/1.1/eml.xsd").get());
    assertEquals(
        EmlSchema.EML_2_2_0,
        EmlSchema.fromLocation(" http://eml.ecoinformatics.org/eml-2.2.0/eml.xsd ").get());
    assertTrue(EmlSchema.fromLocation("http://rs.gbif.org/schema/eml-gbif-profile/dev/eml.xsd").isEmpty());
  }

  @Test
  void externalEntitiesAreNotRead(@TempDir Path dir) throws IOException {
    Path secret = dir.resolve("secret.txt");
    Files.writeString(secret, "TOP-SECRET-CONTENT");
    String doc =
        """
        <?xml version="1.0"?>
        <!DOCTYPE eml [<!ENTITY xxe SYSTEM "%s">]>
        <eml:eml xmlns:eml="%s"><dataset><title>&xxe;</title></dataset></eml:eml>
        """
            .formatted(secret.toUri(), EML_2_2_0_NS);

    EmlValidationResult result = EmlValidator.builder().build().validate(bytes(doc));

    // The DOCTYPE is refused before the quality rules run; their gbif-metadata parser would
    // otherwise read the file into the title.
    assertEquals(List.of(EmlIssueType.NOT_WELL_FORMED), types(result));
    assertTrue(
        result.issues().stream()
            .map(EmlIssue::message)
            .noneMatch(m -> m != null && m.contains("TOP-SECRET-CONTENT")),
        () -> result.issues().toString());
  }

  /** A minimal EML 2.2.0 document, valid against EML 2.2.0, with an optional schema location. */
  private static String minimal220(String location) {
    String schemaLocation =
        location == null
            ? ""
            : " xsi:schemaLocation=\"%s %s\"".formatted(EML_2_2_0_NS, location);
    return """
        <?xml version="1.0" encoding="UTF-8"?>
        <eml:eml xmlns:eml="%s" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"%s
            packageId="p" system="s">
          <dataset>
            <title>A minimal dataset title</title>
            <creator><organizationName>Example</organizationName></creator>
            <contact><organizationName>Example</organizationName></contact>
          </dataset>
        </eml:eml>
        """
        .formatted(EML_2_2_0_NS, schemaLocation);
  }

  private static List<EmlIssueType> types(EmlValidationResult result) {
    return result.issues().stream().map(EmlIssue::type).toList();
  }

  private static byte[] bytes(String s) {
    return s.getBytes(StandardCharsets.UTF_8);
  }

  private static byte[] resource(String name) throws IOException {
    try (InputStream in = EmlValidatorTest.class.getResourceAsStream("/eml/" + name)) {
      return in.readAllBytes();
    }
  }
}
