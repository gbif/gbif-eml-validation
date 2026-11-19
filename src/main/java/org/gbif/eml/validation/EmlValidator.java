package org.gbif.eml.validation;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Validator;
import org.gbif.eml.validation.EmlIssue.Severity;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.ErrorHandler;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * Validates EML documents. Checks run in this order:
 *
 * <ol>
 *   <li>Well-formedness. A document that is not well-formed, or that has a DOCTYPE, gets one
 *       {@link EmlIssueType#NOT_WELL_FORMED} error and no further checks.
 *   <li>Schema. The schema is chosen from the document's {@code xsi:schemaLocation} for its root
 *       namespace among the bundled {@link EmlSchema}s. When the location is missing or not
 *       bundled, the {@linkplain Builder#defaultSchema default schema} is used and the issue is a
 *       warning; without a default it is an error and no schema validation runs.
 *   <li>The GBIF metadata quality rules (warnings), unless {@linkplain Builder#qualityRules turned
 *       off}.
 * </ol>
 *
 * <p>Instances are immutable and thread-safe. Nothing is fetched over the network, and DTDs (so
 * also external entities) are refused.
 */
public final class EmlValidator {

  private static final String XSI_NS = XMLConstants.W3C_XML_SCHEMA_INSTANCE_NS_URI;
  private static final String DISALLOW_DOCTYPE =
      "http://apache.org/xml/features/disallow-doctype-decl";

  private final EmlSchema defaultSchema;
  private final boolean qualityRules;

  private EmlValidator(Builder builder) {
    this.defaultSchema = builder.defaultSchema;
    this.qualityRules = builder.qualityRules;
  }

  public static Builder builder() {
    return new Builder();
  }

  /** Reads the file and validates it. */
  public EmlValidationResult validate(Path eml) throws IOException {
    return validate(Files.readAllBytes(eml));
  }

  /**
   * Validates a document given as raw bytes; the encoding comes from the XML declaration, as for
   * any XML parser.
   */
  public EmlValidationResult validate(byte[] eml) {
    List<EmlIssue> issues = new ArrayList<>();

    Document document = parse(eml, issues);
    if (document == null) {
      return new EmlValidationResult(null, issues);
    }

    EmlSchema schema = selectSchema(document.getDocumentElement(), issues);
    if (schema != null) {
      validateAgainst(schema, eml, issues);
    }

    if (qualityRules) {
      issues.addAll(MetadataQualityRules.evaluate(eml));
    }
    return new EmlValidationResult(schema, issues);
  }

  private static Document parse(byte[] eml, List<EmlIssue> issues) {
    if (eml == null || eml.length == 0) {
      issues.add(EmlIssue.of(EmlIssueType.NOT_WELL_FORMED, Severity.ERROR, "The document is empty."));
      return null;
    }
    try {
      DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
      dbf.setNamespaceAware(true);
      dbf.setValidating(false);
      dbf.setXIncludeAware(false);
      dbf.setExpandEntityReferences(false);
      dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      // EML does not use DTDs. Refusing them here also protects the quality rules: the
      // gbif-metadata parser they use resolves external entities.
      dbf.setFeature(DISALLOW_DOCTYPE, true);
      dbf.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      dbf.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
      DocumentBuilder db = dbf.newDocumentBuilder();
      db.setErrorHandler(SilentErrorHandler.INSTANCE);
      return db.parse(new ByteArrayInputStream(eml));
    } catch (SAXParseException e) {
      issues.add(
          EmlIssue.at(
              EmlIssueType.NOT_WELL_FORMED,
              Severity.ERROR,
              e.getMessage(),
              e.getLineNumber(),
              e.getColumnNumber()));
    } catch (Exception e) {
      issues.add(EmlIssue.of(EmlIssueType.NOT_WELL_FORMED, Severity.ERROR, e.getMessage()));
    }
    return null;
  }

  /** Picks the schema, or adds the issue and returns the default (possibly {@code null}). */
  private EmlSchema selectSchema(Element root, List<EmlIssue> issues) {
    Optional<String> location = declaredLocation(root);
    if (location.isPresent()) {
      Optional<EmlSchema> bundled = EmlSchema.fromLocation(location.get());
      if (bundled.isPresent()) {
        return bundled.get();
      }
      issues.add(
          fallbackIssue(
              EmlIssueType.SCHEMA_UNSUPPORTED,
              "The schema " + location.get() + " is not supported"));
    } else {
      issues.add(
          fallbackIssue(
              EmlIssueType.SCHEMA_LOCATION_MISSING,
              "The document does not declare a schema location for its namespace"));
    }
    return defaultSchema;
  }

  private EmlIssue fallbackIssue(EmlIssueType type, String reason) {
    if (defaultSchema == null) {
      return EmlIssue.of(type, Severity.ERROR, reason + "; it was not validated against a schema.");
    }
    return EmlIssue.of(
        type, Severity.WARNING, reason + "; it was validated against " + defaultSchema.location());
  }

  /**
   * The location paired with the root element's namespace in {@code xsi:schemaLocation}, or
   * {@code xsi:noNamespaceSchemaLocation} for a root without namespace.
   */
  static Optional<String> declaredLocation(Element root) {
    String namespace = root.getNamespaceURI();
    if (namespace == null) {
      return nonBlank(root.getAttributeNS(XSI_NS, "noNamespaceSchemaLocation"));
    }
    String[] pairs = root.getAttributeNS(XSI_NS, "schemaLocation").trim().split("\\s+");
    for (int i = 0; i + 1 < pairs.length; i += 2) {
      if (pairs[i].equals(namespace)) {
        return Optional.of(pairs[i + 1]);
      }
    }
    return Optional.empty();
  }

  private static Optional<String> nonBlank(String value) {
    return value == null || value.isBlank() ? Optional.empty() : Optional.of(value.trim());
  }

  private static void validateAgainst(EmlSchema schema, byte[] eml, List<EmlIssue> issues) {
    CollectingErrorHandler handler = new CollectingErrorHandler();
    try {
      Validator validator = BundledSchemas.get(schema).newValidator();
      validator.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      validator.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
      validator.setErrorHandler(handler);
      validator.validate(new StreamSource(new ByteArrayInputStream(eml)));
    } catch (SAXParseException e) {
      // Already collected by the handler (fatal errors are thrown after being reported).
    } catch (SAXException | IOException e) {
      handler.issues.add(EmlIssue.of(EmlIssueType.SCHEMA_VIOLATION, Severity.ERROR, e.getMessage()));
    }
    issues.addAll(handler.issues);
  }

  private static final class CollectingErrorHandler implements ErrorHandler {
    private final List<EmlIssue> issues = new ArrayList<>();

    @Override
    public void warning(SAXParseException e) {
      issues.add(issue(Severity.WARNING, e));
    }

    @Override
    public void error(SAXParseException e) {
      issues.add(issue(Severity.ERROR, e));
    }

    @Override
    public void fatalError(SAXParseException e) throws SAXException {
      issues.add(issue(Severity.ERROR, e));
      throw e;
    }

    private static EmlIssue issue(Severity severity, SAXParseException e) {
      return EmlIssue.at(
          EmlIssueType.SCHEMA_VIOLATION,
          severity,
          e.getMessage(),
          e.getLineNumber(),
          e.getColumnNumber());
    }
  }

  /** Keeps the parser from printing to stderr; the exception carries the message. */
  private enum SilentErrorHandler implements ErrorHandler {
    INSTANCE;

    @Override
    public void warning(SAXParseException e) {}

    @Override
    public void error(SAXParseException e) throws SAXException {
      throw e;
    }

    @Override
    public void fatalError(SAXParseException e) throws SAXException {
      throw e;
    }
  }

  public static final class Builder {
    private EmlSchema defaultSchema;
    private boolean qualityRules = true;

    private Builder() {}

    /**
     * Schema used when a document declares no location, or one that is not bundled. Without a
     * default such documents get an error and no schema validation.
     */
    public Builder defaultSchema(EmlSchema defaultSchema) {
      this.defaultSchema = defaultSchema;
      return this;
    }

    /** Whether to run the GBIF metadata quality rules (default {@code true}). */
    public Builder qualityRules(boolean qualityRules) {
      this.qualityRules = qualityRules;
      return this;
    }

    public EmlValidator build() {
      return new EmlValidator(this);
    }
  }
}
