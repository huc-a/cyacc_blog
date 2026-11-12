package cn.caiya.content;

import java.time.LocalDate;

/** Single parameter object for content list queries (replaces the former telescoping overloads). */
public record ContentQuery(
    boolean publicOnly,
    String type,
    String status,
    String q,
    Long tagId,
    Long categoryId,
    LocalDate date,
    boolean home,
    int page,
    int size) {
  public ContentQuery {
    type = type == null ? "" : type;
    status = status == null ? "all" : status;
    q = q == null ? "" : q;
  }
}
