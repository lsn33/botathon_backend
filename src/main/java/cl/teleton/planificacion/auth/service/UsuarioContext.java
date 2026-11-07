package cl.teleton.planificacion.auth.service;

import cl.teleton.planificacion.auth.dto.UsuarioActual;
import cl.teleton.planificacion.common.NegocioException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/** Acceso al usuario autenticado y regla de alcance por instituto. */
public final class UsuarioContext {

    private UsuarioContext() {
    }

    public static UsuarioActual actual() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a != null && a.getPrincipal() instanceof UsuarioActual u) {
            return u;
        }
        throw new NegocioException(HttpStatus.UNAUTHORIZED, "No autenticado");
    }

    public static String email() {
        return actual().email();
    }

    /** JEFATURA_IT solo ve su instituto; el resto usa el filtro que pida. */
    public static Integer institutoPermitido(Integer solicitado) {
        UsuarioActual u = actual();
        if ("JEFATURA_IT".equals(u.rol())) {
            if (u.institutoId() == null) {
                throw new NegocioException(HttpStatus.FORBIDDEN, "El usuario no tiene instituto asignado");
            }
            return u.institutoId();
        }
        return solicitado;
    }
}
