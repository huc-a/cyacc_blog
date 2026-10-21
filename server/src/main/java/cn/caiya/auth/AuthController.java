package cn.caiya.auth;

import java.security.Principal;
import java.util.Map;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/auth")
public class AuthController {
    private final AuthMapper mapper;
    public AuthController(AuthMapper mapper) { this.mapper=mapper; }
    @GetMapping("/csrf") public Map<String,String> csrf(CsrfToken token) { return Map.of("token",token.getToken(),"headerName",token.getHeaderName()); }
    @GetMapping("/me") public Map<String,Object> me(Principal p) {
        var user=mapper.find(p.getName());
        return Map.of("username",p.getName(),"displayName",user.get("display_name"));
    }
}
