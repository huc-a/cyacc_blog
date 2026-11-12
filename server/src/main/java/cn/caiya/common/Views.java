package cn.caiya.common;

import java.sql.Timestamp;
import java.util.*;

public final class Views {
  private Views() {}

  public static Map<String, Object> camel(Map<String, Object> source) {
    var result = new LinkedHashMap<String, Object>();
    source.forEach(
        (key, value) -> {
          var parts = key.split("_");
          var name = new StringBuilder(parts[0]);
          for (int i = 1; i < parts.length; i++)
            name.append(Character.toUpperCase(parts[i].charAt(0))).append(parts[i].substring(1));
          if (value instanceof java.sql.Array array) {
            try {
              value = Arrays.asList((Object[]) array.getArray());
            } catch (java.sql.SQLException e) {
              throw new IllegalStateException(e);
            }
          }
          if (value instanceof Object[] array) value = Arrays.asList(array);
          result.put(
              name.toString(), value instanceof Timestamp t ? t.toInstant().toString() : value);
        });
    return result;
  }

  public static long number(Map<String, Object> row, String key) {
    return ((Number) row.get(key)).longValue();
  }
}
