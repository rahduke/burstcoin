package brs.services.impl;

import brs.Burst;
import brs.services.PropertyService;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PropertyServiceImpl implements PropertyService {

  private final Logger logger = LoggerFactory.getLogger(Burst.class);
  private static final String LOG_UNDEF_NAME_DEFAULT = "{} undefined. Default: {}";

  private final Properties properties;

  public PropertyServiceImpl(Properties properties) {
    this.properties = properties;
  }

  @Override
  public Boolean getBooleanProperty(String name, boolean assume) {
    String value = properties.getProperty(name);

    if (value != null) {
      if (value.matches("(?i)^1|active|true|yes|on$")) {
        logger.debug("{} = 'true'", name);
        return true;
      }

      if (value.matches("(?i)^0|false|no|off$")) {
        logger.debug("{} = 'false'", name);
        return false;
      }
    }

    logger.info(LOG_UNDEF_NAME_DEFAULT, name, assume);
    return assume;
  }

  @Override
  public Boolean getBooleanProperty(String name) {
    return getBooleanProperty(name, false);
  }

  @Override
  public int getIntProperty(String name, int defaultValue) {
    try {
      String value = properties.getProperty(name);
      int radix = 10;

      if (value != null && value.matches("(?i)^0x.+$")) {
        value = value.replaceFirst("^0x", "");
        radix = 16;
      } else if (value != null && value.matches("(?i)^0b[01]+$")) {
        value = value.replaceFirst("^0b", "");
        radix = 2;
      }

      int result = Integer.parseInt(value, radix);
      logger.debug("{} = '{}'", name, result);
      return result;
    } catch (NumberFormatException e) {
      logger.info(LOG_UNDEF_NAME_DEFAULT, name, defaultValue);
      return defaultValue;
    }
  }

  @Override
  public int getIntProperty(String name) {
    return getIntProperty(name, 0);
  }

  @Override
  public String getStringProperty(String name, String defaultValue) {
    String value = properties.getProperty(name);
    if (value != null && !"".equals(value)) {
      logger.debug(name + " = \"" + value + "\"");
      return value;
    }

    logger.info(LOG_UNDEF_NAME_DEFAULT, name, defaultValue);

    return defaultValue;
  }

  @Override
  public String getStringProperty(String name) {
    return getStringProperty(name, null);
  }

  @Override
  public List<String> getStringListProperty(String name) {
    String value = getStringProperty(name);
    if (value == null || value.length() == 0) {
      return Collections.emptyList();
    }
    List<String> result = new ArrayList<>();
    for (String s : value.split(";")) {
      s = s.trim();
      if (s.length() > 0) {
        result.add(s);
      }
    }
    return result;
  }

}
