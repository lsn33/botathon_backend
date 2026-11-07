package cl.teleton.planificacion.config;

import cl.teleton.planificacion.auth.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/** Crea usuarios iniciales (uno por rol) si no existen. Cambiar contraseñas antes de produccion. */
@Component
public class DataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final UsuarioRepository usuarios;
    private final PasswordEncoder encoder;
    private final String password;

    public DataInitializer(UsuarioRepository usuarios, PasswordEncoder encoder,
                           @Value("${app.usuarios-iniciales.password}") String password) {
        this.usuarios = usuarios;
        this.encoder = encoder;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        crear("admin@teleton.cl", "Administrador", "ADMIN", null);
        crear("planificador@teleton.cl", "Planificador Central", "PLANIFICADOR_CENTRAL", null);
        crear("jefatura@teleton.cl", "Jefatura Instituto 131", "JEFATURA_IT", 131);
        crear("aprobador@teleton.cl", "Aprobador DMN", "APROBADOR_DMN", null);
    }

    private void crear(String email, String nombre, String rol, Integer instituto) {
        if (!usuarios.existe(email)) {
            usuarios.crear(email, encoder.encode(password), nombre, rol, instituto);
            log.info("Usuario inicial creado: {} ({})", email, rol);
        }
    }
}
