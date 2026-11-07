package cl.teleton.planificacion.auth.service;

import cl.teleton.planificacion.auth.dto.LoginRequest;
import cl.teleton.planificacion.auth.dto.LoginResponse;
import cl.teleton.planificacion.auth.entity.Usuario;
import cl.teleton.planificacion.auth.repository.UsuarioRepository;
import cl.teleton.planificacion.auditoria.service.AuditoriaService;
import cl.teleton.planificacion.common.NegocioException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final JwtService jwt;
    private final AuditoriaService auditoria;

    public AuthService(UsuarioRepository usuarios, PasswordEncoder encoder, JwtService jwt, AuditoriaService auditoria) {
        this.usuarios = usuarios;
        this.encoder = encoder;
        this.jwt = jwt;
        this.auditoria = auditoria;
    }

    public LoginResponse login(LoginRequest req) {
        Usuario u = usuarios.porEmail(req.email().trim().toLowerCase())
                .filter(Usuario::activo)
                .filter(x -> encoder.matches(req.password(), x.passwordHash()))
                .orElseThrow(() -> new NegocioException(HttpStatus.UNAUTHORIZED, "Credenciales inválidas"));
        auditoria.registrar(u.email(), "LOGIN", "usuario", null);
        String token = jwt.generar(u.email(), u.rol(), u.institutoId());
        return new LoginResponse(token, "Bearer", jwt.getMinutos(), u.email(), u.nombre(), u.rol(), u.institutoId());
    }

    public Usuario me(String email) {
        return usuarios.porEmail(email)
                .orElseThrow(() -> new NegocioException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
    }
}
