package cl.teleton.planificacion.calculo.service;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Consultas que alimentan el dashboard y las tablas del frontend. */
@Service
public class PlanificacionService {

    private static final String SELECT_PROFESIONAL =
            "SELECT d.id AS dotacionId, d.persona_id AS personaId, p.nombre AS nombre, " +
            "d.instituto_id AS institutoId, i.nombre AS instituto, d.cargo_buk AS cargoBuk, d.cod_esp AS codEsp, " +
            "d.periodo AS periodo, d.horas_semanales AS horasSemanales, d.tipo_contrato AS tipoContrato, " +
            "d.tipo_trabajador AS tipoTrabajador, r.horas_contratadas AS horasContratadas, " +
            "r.horas_directas AS horasDirectas, r.horas_indirectas AS horasIndirectas, r.horas_nas AS horasNas, " +
            "r.horas_disponibles AS horasDisponibles, r.atenciones_planificadas AS atencionesPlanificadas, " +
            "r.meta_gestion AS metaGestion, r.pct_foja AS pctFoja ";
    private static final String FROM_PROFESIONAL =
            "FROM rrhh.dotacion_mes d " +
            "JOIN catalogo.instituto i ON i.id = d.instituto_id " +
            "LEFT JOIN rrhh.persona p ON p.id = d.persona_id " +
            "LEFT JOIN planificacion.resultado r ON r.dotacion_id = d.id " +
            "WHERE d.activo = 1 ";

    private final NamedParameterJdbcTemplate jdbc;

    public PlanificacionService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static LocalDate mes(LocalDate f) {
        return f == null ? null : f.withDayOfMonth(1);
    }

    private static void filtros(StringBuilder sql, MapSqlParameterSource p, LocalDate periodo, Integer instituto) {
        if (periodo != null) {
            sql.append("AND d.periodo = :periodo ");
            p.addValue("periodo", java.sql.Date.valueOf(mes(periodo)));
        }
        if (instituto != null) {
            sql.append("AND d.instituto_id = :instituto ");
            p.addValue("instituto", instituto);
        }
    }

    public Map<String, Object> listarProfesionales(LocalDate periodo, Integer instituto, Long personaId,
                                                   int page, int size) {
        int pg = Math.max(page, 0);
        int sz = Math.max(1, Math.min(size, 200));
        StringBuilder where = new StringBuilder();
        MapSqlParameterSource p = new MapSqlParameterSource();
        filtros(where, p, periodo, instituto);
        if (personaId != null) {
            where.append("AND d.persona_id = :personaId ");
            p.addValue("personaId", personaId);
        }
        Long total = jdbc.queryForObject("SELECT COUNT(*) " + FROM_PROFESIONAL + where, p, Long.class);
        p.addValue("off", pg * sz).addValue("sz", sz);
        List<Map<String, Object>> items = jdbc.queryForList(
                SELECT_PROFESIONAL + FROM_PROFESIONAL + where +
                        "ORDER BY d.periodo DESC, d.persona_id, d.id OFFSET :off ROWS FETCH NEXT :sz ROWS ONLY", p);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("items", items);
        r.put("total", total == null ? 0 : total);
        r.put("page", pg);
        r.put("size", sz);
        return r;
    }

    public List<Map<String, Object>> porProfesional(long personaId, LocalDate periodo, Integer instituto) {
        StringBuilder where = new StringBuilder("AND d.persona_id = :personaId ");
        MapSqlParameterSource p = new MapSqlParameterSource("personaId", personaId);
        filtros(where, p, periodo, instituto);
        return jdbc.queryForList(SELECT_PROFESIONAL + FROM_PROFESIONAL + where +
                "ORDER BY d.periodo, d.instituto_id", p);
    }

    public Map<String, Object> resumen(LocalDate periodo, Integer instituto) {
        StringBuilder where = new StringBuilder();
        MapSqlParameterSource p = new MapSqlParameterSource();
        filtros(where, p, periodo, instituto);
        Map<String, Object> r = new LinkedHashMap<>(jdbc.queryForMap(
                "SELECT COUNT(DISTINCT d.persona_id) AS profesionales, COUNT(*) AS dotaciones, " +
                        "COALESCE(SUM(r.horas_contratadas),0) AS horasContratadas, " +
                        "COALESCE(SUM(r.horas_directas),0) AS horasDirectas, " +
                        "COALESCE(SUM(r.horas_indirectas),0) AS horasIndirectas, " +
                        "COALESCE(SUM(r.horas_nas),0) AS horasNas, " +
                        "COALESCE(SUM(r.horas_disponibles),0) AS horasDisponibles, " +
                        "COALESCE(SUM(r.atenciones_planificadas),0) AS atencionesPlanificadas, " +
                        "COALESCE(SUM(r.meta_gestion),0) AS metaGestion, " +
                        "COALESCE(AVG(r.pct_foja),0) AS pctFoja " + FROM_PROFESIONAL + where, p));

        StringBuilder wSic = new StringBuilder("WHERE 1=1 ");
        MapSqlParameterSource ps = new MapSqlParameterSource();
        if (periodo != null) {
            wSic.append("AND periodo = :periodo ");
            ps.addValue("periodo", java.sql.Date.valueOf(mes(periodo)));
        }
        if (instituto != null) {
            wSic.append("AND instituto_id = :instituto ");
            ps.addValue("instituto", instituto);
        }
        Long realizadas = jdbc.queryForObject(
                "SELECT COALESCE(SUM(atenciones_realizadas),0) FROM sic.atencion " + wSic, ps, Long.class);
        r.put("atencionesRealizadas", realizadas == null ? 0 : realizadas);
        return r;
    }

    public List<Map<String, Object>> planVsReal(LocalDate periodo, Integer instituto) {
        MapSqlParameterSource p = new MapSqlParameterSource();
        String filtroPlan = "";
        String filtroSic = "";
        if (periodo != null) {
            filtroPlan = "AND d.periodo = :periodo ";
            filtroSic = "WHERE periodo = :periodo ";
            p.addValue("periodo", java.sql.Date.valueOf(mes(periodo)));
        }
        String filtroInst = "";
        if (instituto != null) {
            filtroInst = "WHERE i.id = :instituto ";
            p.addValue("instituto", instituto);
        }
        return jdbc.queryForList(
                "SELECT i.id AS institutoId, i.nombre AS instituto, pl.atenciones AS atencionesPlanificadas, " +
                        "pl.meta AS metaGestion, COALESCE(s.realizadas,0) AS atencionesRealizadas, " +
                        "CASE WHEN pl.meta > 0 THEN ROUND(COALESCE(s.realizadas,0) * 100.0 / pl.meta, 1) ELSE NULL END " +
                        "AS cumplimientoMetaPct " +
                        "FROM catalogo.instituto i " +
                        "JOIN (SELECT d.instituto_id, SUM(r.atenciones_planificadas) AS atenciones, SUM(r.meta_gestion) AS meta " +
                        "      FROM rrhh.dotacion_mes d JOIN planificacion.resultado r ON r.dotacion_id = d.id " +
                        "      WHERE d.activo = 1 " + filtroPlan + "GROUP BY d.instituto_id) pl ON pl.instituto_id = i.id " +
                        "LEFT JOIN (SELECT instituto_id, SUM(atenciones_realizadas) AS realizadas FROM sic.atencion " +
                        filtroSic + "GROUP BY instituto_id) s ON s.instituto_id = i.id " +
                        filtroInst + "ORDER BY i.id", p);
    }

    public List<LocalDate> periodos() {
        return jdbc.getJdbcTemplate().query("SELECT DISTINCT periodo FROM rrhh.dotacion_mes ORDER BY periodo DESC",
                (rs, i) -> rs.getDate(1).toLocalDate());
    }
}
