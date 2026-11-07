package cl.teleton.planificacion.rrhh.repository;

import cl.teleton.planificacion.rrhh.dto.DotacionCmd;
import cl.teleton.planificacion.rrhh.entity.Dotacion;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class DotacionRepository {

    /** Dotacion activa de un mes, para detectar bajas. */
    public record Activa(long id, long personaId, int institutoId, String cargoBuk, LocalDate periodo) {
    }

    private final JdbcTemplate jdbc;
    private final NamedParameterJdbcTemplate named;

    public DotacionRepository(JdbcTemplate jdbc, NamedParameterJdbcTemplate named) {
        this.jdbc = jdbc;
        this.named = named;
    }

    public void asegurarPersona(long personaId) {
        Integer n = jdbc.queryForObject("SELECT COUNT(*) FROM rrhh.persona WHERE id = ?", Integer.class, personaId);
        if (n == null || n == 0) {
            jdbc.update("INSERT INTO rrhh.persona (id) VALUES (?)", personaId);
        }
    }

    public Optional<Dotacion> buscar(long personaId, int institutoId, String cargoBuk, LocalDate periodo) {
        return jdbc.query("SELECT id, horas_semanales, cod_esp, tipo_contrato, tipo_trabajador, activo " +
                        "FROM rrhh.dotacion_mes WHERE persona_id = ? AND instituto_id = ? AND cargo_buk = ? AND periodo = ?",
                (rs, i) -> new Dotacion(rs.getLong("id"), rs.getBigDecimal("horas_semanales"), rs.getString("cod_esp"),
                        rs.getString("tipo_contrato"), rs.getString("tipo_trabajador"), rs.getBoolean("activo")),
                personaId, institutoId, cargoBuk, periodo).stream().findFirst();
    }

    public long insertar(DotacionCmd c) {
        Map<String, Object> f = new HashMap<>();
        f.put("persona_id", c.personaId());
        f.put("instituto_id", c.institutoId());
        f.put("cargo_buk", c.cargoBuk());
        f.put("cod_esp", c.codEsp());
        f.put("periodo", java.sql.Date.valueOf(c.periodo()));
        f.put("horas_semanales", c.horasSemanales());
        f.put("tipo_contrato", c.tipoContrato());
        f.put("tipo_trabajador", c.tipoTrabajador());
        f.put("activo", true);
        Number id = new SimpleJdbcInsert(jdbc).withSchemaName("rrhh").withTableName("dotacion_mes")
                .usingGeneratedKeyColumns("id").executeAndReturnKey(f);
        return id.longValue();
    }

    public void actualizar(long id, DotacionCmd c) {
        jdbc.update("UPDATE rrhh.dotacion_mes SET cod_esp = ?, horas_semanales = ?, tipo_contrato = ?, " +
                        "tipo_trabajador = ?, activo = 1, actualizado_en = SYSUTCDATETIME() WHERE id = ?",
                c.codEsp(), c.horasSemanales(), c.tipoContrato(), c.tipoTrabajador(), id);
    }

    public void desactivar(long id) {
        jdbc.update("UPDATE rrhh.dotacion_mes SET activo = 0, actualizado_en = SYSUTCDATETIME() WHERE id = ?", id);
    }

    public List<Activa> activas(Collection<LocalDate> periodos) {
        if (periodos.isEmpty()) {
            return List.of();
        }
        return named.query("SELECT id, persona_id, instituto_id, cargo_buk, periodo FROM rrhh.dotacion_mes " +
                        "WHERE activo = 1 AND periodo IN (:periodos)",
                new MapSqlParameterSource("periodos", periodos.stream().map(java.sql.Date::valueOf).toList()),
                (rs, i) -> new Activa(rs.getLong("id"), rs.getLong("persona_id"), rs.getInt("instituto_id"),
                        rs.getString("cargo_buk"), rs.getDate("periodo").toLocalDate()));
    }

    public void historial(long dotacionId, long loteId, String tipo, String detalle) {
        jdbc.update("INSERT INTO rrhh.dotacion_historial (dotacion_id, lote_id, tipo_cambio, detalle) VALUES (?,?,?,?)",
                dotacionId, loteId, tipo, detalle);
    }
}
