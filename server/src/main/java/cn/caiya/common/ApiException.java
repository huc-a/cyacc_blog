package cn.caiya.common;

public class ApiException extends RuntimeException {
    public final int status;
    public ApiException(int status, String message) { super(message); this.status = status; }
    public static ApiException conflict() { return new ApiException(409, "内容已被修改，请刷新后重试"); }
}
