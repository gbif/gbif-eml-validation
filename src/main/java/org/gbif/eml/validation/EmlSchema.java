package org.gbif.eml.validation;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * The EML schemas bundled with this library. Documents are validated against one of these and the
 * library never fetches a schema over the network.
 *
 * <p>A document selects its schema with the {@code xsi:schemaLocation} pair of the EML namespace.
 * Locations are compared without scheme, so {@code http://} and {@code https://} match. A GBIF
 * profile can be referenced by its {@code eml.xsd} or its {@code eml-gbif-profile.xsd}; both load
 * the same schema set.
 */
public enum EmlSchema {
  EML_2_1_1(
      "eml-2.1.1/eml.xsd",
      "rs.gbif.org/schema/eml-2.1.1/eml.xsd",
      "eml.ecoinformatics.org/eml-2.1.1/eml.xsd"),
  EML_2_2_0(
      "eml-2.2.0/eml.xsd",
      "rs.gbif.org/schema/eml-2.2.0/eml.xsd",
      "eml.ecoinformatics.org/eml-2.2.0/eml.xsd"),
  GBIF_PROFILE_1_0(
      "eml-gbif-profile/1.0/eml.xsd",
      "rs.gbif.org/schema/eml-gbif-profile/1.0/eml.xsd",
      "rs.gbif.org/schema/eml-gbif-profile/1.0/eml-gbif-profile.xsd"),
  GBIF_PROFILE_1_0_1(
      "eml-gbif-profile/1.0.1/eml.xsd",
      "rs.gbif.org/schema/eml-gbif-profile/1.0.1/eml.xsd",
      "rs.gbif.org/schema/eml-gbif-profile/1.0.1/eml-gbif-profile.xsd"),
  GBIF_PROFILE_1_0_2(
      "eml-gbif-profile/1.0.2/eml.xsd",
      "rs.gbif.org/schema/eml-gbif-profile/1.0.2/eml.xsd",
      "rs.gbif.org/schema/eml-gbif-profile/1.0.2/eml-gbif-profile.xsd"),
  GBIF_PROFILE_1_1(
      "eml-gbif-profile/1.1/eml.xsd",
      "rs.gbif.org/schema/eml-gbif-profile/1.1/eml.xsd",
      "rs.gbif.org/schema/eml-gbif-profile/1.1/eml-gbif-profile.xsd"),
  GBIF_PROFILE_1_2(
      "eml-gbif-profile/1.2/eml.xsd",
      "rs.gbif.org/schema/eml-gbif-profile/1.2/eml.xsd",
      "rs.gbif.org/schema/eml-gbif-profile/1.2/eml-gbif-profile.xsd"),
  /** The current GBIF profile, based on EML 2.2.0. */
  GBIF_PROFILE_1_3(
      "eml-gbif-profile/1.3/eml.xsd",
      "rs.gbif.org/schema/eml-gbif-profile/1.3/eml.xsd",
      "rs.gbif.org/schema/eml-gbif-profile/1.3/eml-gbif-profile.xsd");

  /** Classpath folder holding the bundled schema files. */
  static final String RESOURCE_ROOT = "/org/gbif/eml/validation/schemas/";

  private final String resource;
  private final List<String> locations;

  EmlSchema(String resource, String... locations) {
    this.resource = resource;
    this.locations = List.of(locations);
  }

  /** Classpath path of the entry schema. */
  String resourcePath() {
    return RESOURCE_ROOT + resource;
  }

  /** The canonical location, as written in a document's {@code xsi:schemaLocation}. */
  public String location() {
    return (this == EML_2_2_0 ? "https://" : "http://") + locations.get(0);
  }

  /** Finds the bundled schema for a schema location, ignoring scheme, case and whitespace. */
  public static Optional<EmlSchema> fromLocation(String location) {
    if (location == null) {
      return Optional.empty();
    }
    String normalised = normalise(location);
    return Arrays.stream(values()).filter(s -> s.locations.contains(normalised)).findFirst();
  }

  private static String normalise(String location) {
    String l = location.trim().toLowerCase(Locale.ROOT);
    int scheme = l.indexOf("://");
    return scheme >= 0 ? l.substring(scheme + 3) : l;
  }
}
