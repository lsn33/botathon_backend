package cl.teleton.planificacion.catalogo.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class CatalogoService {

    private final JdbcTemplate jdbc;

    public CatalogoService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean existeInstituto(int id) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM catalogo.instituto WHERE id = ?", Integer.class, id);
        return n != null && n > 0;
    }

    public boolean existeCargo(String cargoBuk) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM catalogo.cargo WHERE cargo_buk = ?", Integer.class, cargoBuk);
        return n != null && n > 0;
    }

    /** Crea el cargo (y su especialidad si no existe). */
    public void crearCargo(String cargoBuk, String codEsp) {
        if (codEsp != null) {
            Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM catalogo.especialidad WHERE cod_esp = ?", Integer.class, codEsp);
            if (n == null || n == 0) {
                jdbc.update("INSERT INTO catalogo.especialidad (cod_esp, nombre) VALUES (?, ?)", codEsp, codEsp);
            }
        }
        jdbc.update("INSERT INTO catalogo.cargo (cargo_buk, cod_esp) VALUES (?, ?)", cargoBuk, codEsp);
    }

    public List<Map<String, Object>> institutos() {
        return jdbc.queryForList("SELECT id, nombre FROM catalogo.instituto ORDER BY id");
    }

    public List<Map<String, Object>> cargos() {
        return jdbc.queryForList("SELECT cargo_buk AS cargoBuk, cod_esp AS codEsp FROM catalogo.cargo ORDER BY cargo_buk");
    }
}
