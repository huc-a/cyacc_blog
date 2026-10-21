package cn.caiya.site;

import java.util.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class SiteController {
    private final SiteService service;
    public SiteController(SiteService service) {this.service=service;}
    public record Input(@NotNull @Valid SiteSettings settings,@NotNull @PositiveOrZero Long lockVersion) {}
    @GetMapping("/api/public/site") public Object publicSite() {return service.read().get("settings");}
    @GetMapping("/api/admin/site") public Map<String,Object> adminSite() {return service.read();}
    @PutMapping("/api/admin/site") public Map<String,Object> save(@Valid @RequestBody Input input) {return service.save(input.settings(),input.lockVersion());}
}
