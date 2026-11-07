package cl.teleton.planificacion.auth.controller;

import cl.teleton.planificacion.auth.dto.LoginRequest;
import cl.teleton.planificacion.auth.dto.LoginResponse;
import cl.teleton.planificacion.auth.entity.Usuario;
import cl.teleton.planificacion.auth.service.AuthService;
import cl.teleton.planificacion.auth.service.UsuarioContext;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthService service;

    public AuthController(AuthService service) {
        this.service = service;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest req) {
        return service.login(req);
    }

    @GetMapping("/me")
    public Map<String, Object> me() {
        Usuario u = service.me(UsuarioContext.email());
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("email", u.email());
        r.put("nombre", u.nombre());
        r.put("rol", u.rol());
        r.put("institutoId", u.institutoId());
        return r;
    }
}
