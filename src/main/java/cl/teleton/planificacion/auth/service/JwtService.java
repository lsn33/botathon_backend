package cl.teleton.planificacion.auth.service;

import cl.teleton.planificacion.auth.dto.UsuarioActual;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;

@Service
public class JwtService {

    private final SecretKey key;
    private final long minutos;

    public JwtService(@Value("${app.jwt.secret}") String secret,
                      @Value("${app.jwt.expiracion-minutos}") long minutos) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.minutos = minutos;
    }

    public String generar(String email, String rol, Integer institutoId) {
        long ahora = System.currentTimeMillis();
        var builder = Jwts.builder()
                .subject(email)
                .claim("rol", rol)
                .issuedAt(new Date(ahora))
                .expiration(new Date(ahora + minutos * 60_000));
        if (institutoId != null) {
            builder.claim("inst", institutoId);
        }
        return builder.signWith(key).compact();
    }

    public Optional<UsuarioActual> validar(String token) {
        try {
            Claims c = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
            return Optional.of(new UsuarioActual(c.getSubject(), c.get("rol", String.class), c.get("inst", Integer.class)));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public long getMinutos() {
        return minutos;
    }
}
