package cn.caiya.common;

import java.net.URI;

public final class SafeUrls {
    private SafeUrls() {}
    public static String check(String value) {
        if(value==null || value.isBlank()) return "";
        try {
            var uri=URI.create(value);
            if(!("https".equalsIgnoreCase(uri.getScheme())||"http".equalsIgnoreCase(uri.getScheme())) || uri.getHost()==null || uri.getUserInfo()!=null)
                throw new IllegalArgumentException();
            return value;
        } catch(IllegalArgumentException e) {throw new ApiException(400,"链接只允许完整的 HTTP 或 HTTPS 地址");}
    }
}
