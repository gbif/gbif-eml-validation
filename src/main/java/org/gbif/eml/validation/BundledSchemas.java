package org.gbif.eml.validation;

import java.io.InputStream;
import java.io.Reader;
import java.net.URL;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javax.xml.XMLConstants;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.ls.LSInput;
import org.w3c.dom.ls.LSResourceResolver;

/**
 * Loads the bundled schemas from the classpath, once per JVM. Compiled {@link Schema}s are
 * thread-safe and shared by all validators.
 *
 * <p>Relative imports resolve inside the bundled folder. The only absolute imports in the bundled
 * schemas are copies of the W3C {@code xml.xsd}; they resolve to a bundled copy. Any other
 * external access is refused, so loading never touches the network.
 */
final class BundledSchemas {

  private static final Logger LOG = LoggerFactory.getLogger(BundledSchemas.class);

  private static final String XML_XSD = EmlSchema.RESOURCE_ROOT + "w3c/xml.xsd";

  /** Absolute locations of xml.xsd used by the bundled schemas, without scheme. */
  private static final Set<String> XML_XSD_LOCATIONS =
      Set.of(
          "www.w3.org/2001/xml.xsd", "www.w3.org/2009/01/xml.xsd", "rs.gbif.org/schema/xml.xsd");

  private static final Map<EmlSchema, Schema> CACHE = new ConcurrentHashMap<>();

  private BundledSchemas() {}

  static Schema get(EmlSchema emlSchema) {
    return CACHE.computeIfAbsent(emlSchema, BundledSchemas::load);
  }

  private static Schema load(EmlSchema emlSchema) {
    URL url = resource(emlSchema.resourcePath());
    try {
      SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      // Relative imports are file: or jar: URLs; http(s) is never allowed.
      factory.setProperty(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "file,jar");
      factory.setProperty(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      factory.setResourceResolver(new XmlXsdResolver());
      Schema schema = factory.newSchema(url);
      LOG.debug("Loaded bundled schema {}", emlSchema);
      return schema;
    } catch (Exception e) {
      // A bundled schema that does not compile is a packaging bug, not a document problem.
      throw new IllegalStateException("Cannot load bundled schema " + emlSchema, e);
    }
  }

  private static URL resource(String path) {
    URL url = BundledSchemas.class.getResource(path);
    if (url == null) {
      throw new IllegalStateException("Bundled schema missing from the classpath: " + path);
    }
    return url;
  }

  private static String withoutScheme(String location) {
    int i = location.indexOf("://");
    return i >= 0 ? location.substring(i + 3) : location;
  }

  /** Maps the absolute xml.xsd imports to the bundled copy; everything else is left as is. */
  private static final class XmlXsdResolver implements LSResourceResolver {
    @Override
    public LSInput resolveResource(
        String type, String namespaceURI, String publicId, String systemId, String baseURI) {
      if (systemId == null || !XML_XSD_LOCATIONS.contains(withoutScheme(systemId))) {
        return null;
      }
      URL url = resource(XML_XSD);
      try {
        return new StreamInput(url.toExternalForm(), url.openStream());
      } catch (Exception e) {
        throw new IllegalStateException("Cannot open bundled " + XML_XSD, e);
      }
    }
  }

  private static final class StreamInput implements LSInput {
    private final String systemId;
    private InputStream byteStream;

    StreamInput(String systemId, InputStream byteStream) {
      this.systemId = systemId;
      this.byteStream = byteStream;
    }

    @Override
    public InputStream getByteStream() {
      return byteStream;
    }

    @Override
    public void setByteStream(InputStream byteStream) {
      this.byteStream = byteStream;
    }

    @Override
    public String getSystemId() {
      return systemId;
    }

    @Override
    public void setSystemId(String systemId) {}

    @Override
    public Reader getCharacterStream() {
      return null;
    }

    @Override
    public void setCharacterStream(Reader characterStream) {}

    @Override
    public String getStringData() {
      return null;
    }

    @Override
    public void setStringData(String stringData) {}

    @Override
    public String getPublicId() {
      return null;
    }

    @Override
    public void setPublicId(String publicId) {}

    @Override
    public String getBaseURI() {
      return null;
    }

    @Override
    public void setBaseURI(String baseURI) {}

    @Override
    public String getEncoding() {
      return null;
    }

    @Override
    public void setEncoding(String encoding) {}

    @Override
    public boolean getCertifiedText() {
      return false;
    }

    @Override
    public void setCertifiedText(boolean certifiedText) {}
  }
}
