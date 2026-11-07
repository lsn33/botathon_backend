package cl.teleton.planificacion.calculo.service;

import cl.teleton.planificacion.calculo.repository.ParametrosRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Motor de calculo asistencial por profesional y mes.
 *
 * SUPUESTOS A VALIDAR CONTRA EL EXCEL DE TELETON (marcados con [VALIDAR]):
 *  - Horas del mes = horasSemanales * diasHabilesDelMes / 5   [VALIDAR]
 *  - Tramo segun horas semanales: >= 40 ALTO, >= 22 MEDIO, resto BAJO
 *  - Atenciones planificadas = atenciones directas + atenciones indirectas   [VALIDAR]
 *  - Horas disponibles = horas de atencion directa
 *  - Pendiente: asignaciones especiales, areas grupales, vacaciones y licencias.
 */
@Service
public class CalculoService {

    private static final BigDecimal CIEN = BigDecimal.valueOf(100);
    private static final BigDecimal SESENTA = BigDecimal.valueOf(60);

    private final JdbcTemplate jdbc;
    private final ParametrosRepository params;

    public CalculoService(JdbcTemplate jdbc, ParametrosRepository params) {
        this.jdbc = jdbc;
        this.params = params;
    }

    private record Fila(long dotacionId, String codEsp, BigDecimal horasSemanales) {
    }

    /** Recalcula los periodos indicados; null o vacio = todos los periodos con dotacion. */
    @Transactional
    public int recalcular(Collection<LocalDate> periodos) {
        Set<LocalDate> meses = new TreeSet<>();
        if (periodos == null || periodos.isEmpty()) {
            meses.addAll(jdbc.query("SELECT DISTINCT periodo FROM rrhh.dotacion_mes",
                    (rs, i) -> rs.getDate(1).toLocalDate()));
        } else {
            periodos.forEach(p -> meses.add(p.withDayOfMonth(1)));
        }
        Map<String, BigDecimal> gen = params.generalComoMapa();
        var dist = params.distribuciones();
        int total = 0;
        for (LocalDate mes : meses) {
            total += recalcularMes(mes, gen, dist);
        }
        return total;
    }

    private int recalcularMes(LocalDate ini, Map<String, BigDecimal> gen,
                              List<cl.teleton.planificacion.calculo.dto.DistribucionDto> dist) {
        LocalDate fin = ini.plusMonths(1).minusDays(1);
        Set<LocalDate> feriados = new HashSet<>(jdbc.query(
                "SELECT fecha FROM calendario.feriado WHERE fecha BETWEEN ? AND ?",
                (rs, i) -> rs.getDate(1).toLocalDate(), ini, fin));
        int habiles = 0;
        for (LocalDate d = ini; !d.isAfter(fin); d = d.plusDays(1)) {
            DayOfWeek w = d.getDayOfWeek();
            if (w != DayOfWeek.SATURDAY && w != DayOfWeek.SUNDAY && !feriados.contains(d)) {
                habiles++;
            }
        }

        BigDecimal diasSemana = gen.getOrDefault("DIAS_SEMANA_HABILES", BigDecimal.valueOf(5));
        BigDecimal metaPct = gen.getOrDefault("META_GESTION_PCT", BigDecimal.valueOf(90));
        BigDecimal minInd = gen.getOrDefault("MINUTOS_INDIRECTA", BigDecimal.valueOf(30));
        BigDecimal tramoAlto = gen.getOrDefault("TRAMO_ALTO_MIN", BigDecimal.valueOf(40));
        BigDecimal tramoMedio = gen.getOrDefault("TRAMO_MEDIO_MIN", BigDecimal.valueOf(22));

        List<Fila> filas = jdbc.query(
                "SELECT id, cod_esp, horas_semanales FROM rrhh.dotacion_mes WHERE periodo = ? AND activo = 1",
                (rs, i) -> new Fila(rs.getLong("id"), rs.getString("cod_esp"), rs.getBigDecimal("horas_semanales")),
                ini);

        for (Fila f : filas) {
            BigDecimal contratadas = f.horasSemanales().multiply(BigDecimal.valueOf(habiles))
                    .divide(diasSemana, 2, RoundingMode.HALF_UP);

            String tramo = f.horasSemanales().compareTo(tramoAlto) >= 0 ? "ALTO"
                    : f.horasSemanales().compareTo(tramoMedio) >= 0 ? "MEDIO" : "BAJO";
            var d = buscarDistribucion(dist, f.codEsp(), tramo);

            BigDecimal hDir = contratadas.multiply(d.pctDirecto()).divide(CIEN, 2, RoundingMode.HALF_UP);
            BigDecimal hInd = contratadas.multiply(d.pctIndirecto()).divide(CIEN, 2, RoundingMode.HALF_UP);
            BigDecimal hNas = contratadas.subtract(hDir).subtract(hInd);

            int atDir = hDir.multiply(SESENTA)
                    .divide(BigDecimal.valueOf(d.minutosDirecta()), 0, RoundingMode.DOWN).intValue();
            int atInd = hInd.multiply(SESENTA).divide(minInd, 0, RoundingMode.DOWN).intValue();
            int planificadas = atDir + atInd;

            BigDecimal meta = BigDecimal.valueOf(planificadas).multiply(metaPct).divide(CIEN, 2, RoundingMode.HALF_UP);
            BigDecimal foja = contratadas.signum() == 0 ? BigDecimal.ZERO
                    : hDir.multiply(CIEN).divide(contratadas, 2, RoundingMode.HALF_UP);

            guardar(f.dotacionId(), contratadas, hDir, hInd, hNas, hDir, atDir, atInd, planificadas, meta, foja);
        }

        jdbc.update("DELETE r FROM planificacion.resultado r JOIN rrhh.dotacion_mes d ON d.id = r.dotacion_id " +
                "WHERE d.periodo = ? AND d.activo = 0", ini);
        return filas.size();
    }

    private cl.teleton.planificacion.calculo.dto.DistribucionDto buscarDistribucion(
            List<cl.teleton.planificacion.calculo.dto.DistribucionDto> dist, String codEsp, String tramo) {
        return dist.stream().filter(x -> x.tramo().equals(tramo) && codEsp != null && x.codEsp().equalsIgnoreCase(codEsp))
                .findFirst()
                .or(() -> dist.stream().filter(x -> x.tramo().equals(tramo) && "*".equals(x.codEsp())).findFirst())
                .orElseThrow(() -> new IllegalStateException(
                        "No hay distribucion configurada para especialidad=" + codEsp + " tramo=" + tramo));
    }

    private void guardar(long dotacionId, BigDecimal contratadas, BigDecimal hDir, BigDecimal hInd, BigDecimal hNas,
                         BigDecimal hDisp, int atDir, int atInd, int plan, BigDecimal meta, BigDecimal foja) {
        int n = jdbc.update("UPDATE planificacion.resultado SET horas_contratadas = ?, horas_directas = ?, " +
                        "horas_indirectas = ?, horas_nas = ?, horas_disponibles = ?, atenciones_directas = ?, " +
                        "atenciones_indirectas = ?, atenciones_planificadas = ?, meta_gestion = ?, pct_foja = ?, " +
                        "calculado_en = SYSUTCDATETIME() WHERE dotacion_id = ?",
                contratadas, hDir, hInd, hNas, hDisp, atDir, atInd, plan, meta, foja, dotacionId);
        if (n == 0) {
            jdbc.update("INSERT INTO planificacion.resultado (dotacion_id, horas_contratadas, horas_directas, " +
                            "horas_indirectas, horas_nas, horas_disponibles, atenciones_directas, atenciones_indirectas, " +
                            "atenciones_planificadas, meta_gestion, pct_foja) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                    dotacionId, contratadas, hDir, hInd, hNas, hDisp, atDir, atInd, plan, meta, foja);
        }
    }
}
