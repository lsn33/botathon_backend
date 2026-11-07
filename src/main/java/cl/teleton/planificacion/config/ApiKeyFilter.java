package cl.teleton.planificacion.config;

import cl.teleton.planificacion.auth.dto.UsuarioActual;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

/** Autentica a Blue Prism mediante el header X-API-Key (rol BOT). */
public class ApiKeyFilter extends OncePerRequestFilter {

    private final byte[] apiKey;

    public ApiKeyFilter(String apiKey) {
        this.apiKey = apiKey.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String enviada = req.getHeader("X-API-Key");
        if (enviada != null
                && MessageDigest.isEqual(apiKey, enviada.getBytes(StandardCharsets.UTF_8))) {
            var bot = new UsuarioActual("bot", "BOT", null);
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(bot, null, List.of(new SimpleGrantedAuthority("ROLE_BOT"))));
        }
        chain.doFilter(req, res);
    }
}
