# gbif-eml-validation

A small library that validates EML metadata documents. Registry, the pipelines archive check and
the DwC-DP analyser share it, so a document gets the same verdict everywhere.

It replaces pipelines' `validator-eml` module (`SchemaValidatorFactory`, `XmlSchemaValidator`,
`MetadataPath`) and validator-core's `BasicMetadataEvaluator`.

## What it checks

Checks run in this order:

1. **Well-formedness.** A document that is not well-formed XML, or that has a DOCTYPE, gets one
   `NOT_WELL_FORMED` error and nothing else runs.
2. **Schema.** The schema comes from the document's `xsi:schemaLocation` for its root namespace and
   must be one of the bundled schemas (below). Locations match regardless of `http`/`https`.
   - Missing location: `SCHEMA_LOCATION_MISSING`.
   - A location that is not bundled: `SCHEMA_UNSUPPORTED`. It is never downloaded.
   - In both cases the caller's default schema is used and the issue is a **warning**. Without a
     default, the issue is an **error** and no schema validation runs.
   - Each XSD problem is a `SCHEMA_VIOLATION` with line and column.
3. **GBIF metadata quality rules** (warnings, can be turned off). The document is read with
   gbif-metadata's `DatasetEmlParser`, the parser registry uses:
   - `TITLE_MISSING_OR_TOO_SHORT`: under 10 characters
   - `LICENSE_MISSING_OR_UNKNOWN`: no licence GBIF supports
   - `DESCRIPTION_MISSING_OR_TOO_SHORT`: under 25 characters
   - `RESOURCE_CONTACTS_MISSING_OR_INCOMPLETE`: no citation author, or an author without a
     generated name
   - `METADATA_UNREADABLE` (error): the parser could not read the document

The result is neutral: `EmlValidationResult` holds the schema used and a list of `EmlIssue`
(type, `WARNING`/`ERROR`, message, line, column). `isValid()` means no errors. Each caller maps the
issues to its own vocabulary (validator `EvaluationType`, analyser `ValidationIssue`, …).

## Bundled schemas

No network access, ever: schemas load from the classpath and documents cannot pull in DTDs or
external entities.

| `EmlSchema` | Accepted locations (without scheme) |
|---|---|
| `EML_2_1_1` | `rs.gbif.org/schema/eml-2.1.1/eml.xsd`, `eml.ecoinformatics.org/eml-2.1.1/eml.xsd` |
| `EML_2_2_0` | `rs.gbif.org/schema/eml-2.2.0/eml.xsd`, `eml.ecoinformatics.org/eml-2.2.0/eml.xsd` |
| `GBIF_PROFILE_1_0` … `GBIF_PROFILE_1_2` | `rs.gbif.org/schema/eml-gbif-profile/<v>/eml.xsd` or `…/eml-gbif-profile.xsd` (EML 2.1.1 based) |
| `GBIF_PROFILE_1_3` | `rs.gbif.org/schema/eml-gbif-profile/1.3/eml.xsd` or `…/eml-gbif-profile.xsd` (EML 2.2.0 based) |

The files were copied from rs.gbif.org on 2026-11-18 and are under
`src/main/resources/org/gbif/eml/validation/schemas/`. The absolute `xml.xsd` imports
(w3.org, rs.gbif.org) resolve to the bundled W3C copy. To add a profile version, copy its folder
and add an `EmlSchema` constant; `EmlValidatorTest` checks that every constant compiles.

## Usage

```xml
<dependency>
  <groupId>org.gbif</groupId>
  <artifactId>gbif-eml-validation</artifactId>
  <version>1.0.0-SNAPSHOT</version>
</dependency>
```

```java
EmlValidator validator =
    EmlValidator.builder()
        .defaultSchema(EmlSchema.GBIF_PROFILE_1_3) // optional; the DwC-DP analyser uses EML_2_2_0
        .qualityRules(true)                         // default
        .build();

EmlValidationResult result = validator.validate(emlBytes); // or validate(Path)
result.isValid();
result.issues().forEach(i -> log.info("{} {} line {}: {}", i.severity(), i.type(), i.line(), i.message()));

// Darwin Core Archives: meta.xml's metadata attribute, else eml.xml, else metadata.xml
Optional<Path> eml = ArchiveMetadata.find(unpackedArchiveDirectory);
```

`EmlValidator` is immutable and thread-safe; compiled schemas are cached per JVM. Pass bytes, not
a decoded `String`, so the parser honours the document's declared encoding.

## Build

Java 17, Maven:

```bash
mvn clean verify
```
