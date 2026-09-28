package com.example.features.equipos.otros.service;

import com.example.features.equipos.otros.model.EquipoOtros;
import com.example.features.equipos.otros.model.EquipoOtrosReporteDTO;
import com.example.features.equipos.otros.model.TipoIngresoOtros;
import net.sf.jasperreports.engine.JRPrintElement;
import net.sf.jasperreports.engine.JRPrintPage;
import net.sf.jasperreports.engine.JRPrintText;
import net.sf.jasperreports.engine.JasperPrint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EquipoOtrosReporteServiceTest {

    private static final LocalDate DESDE = LocalDate.of(2026, 9, 1);
    private static final LocalDate HASTA = LocalDate.of(2026, 9, 30);

    @Mock
    private EquipoOtrosService equipoOtrosService;

    private EquipoOtrosReporteService service;

    @BeforeEach
    void setUp() {
        service = new EquipoOtrosReporteService(equipoOtrosService);
    }

    // ── construirDatos ───────────────────────────────────────────────────────

    @Test
    void construirDatos_llevaLosLitrosDelIngresoAlDTO() {
        when(equipoOtrosService.obtenerEntreFechas(DESDE, HASTA, null))
            .thenReturn(List.of(ingreso(120), ingreso(0)));

        List<EquipoOtrosReporteDTO> datos = service.construirDatos(DESDE, HASTA, null);

        assertEquals(120, datos.get(0).getLitros());
        assertEquals(0, datos.get(1).getLitros());
    }

    // ── llenar (JRXML) ───────────────────────────────────────────────────────

    @Test
    void llenar_muestraLitrosPorFila_guionSinLitros_yTotalAlPie() throws Exception {
        List<EquipoOtrosReporteDTO> datos = List.of(dto(120), dto(0), dto(35));

        List<String> textos = textos(service.llenar(datos));

        assertTrue(textos.contains("120"));
        assertTrue(textos.contains("35"));
        assertTrue(textos.contains("—"), "un ingreso sin litros se muestra con guion");
        assertTrue(textos.contains("Total litros"));
        assertTrue(textos.contains("155"), "el total suma los litros de todas las filas");
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static EquipoOtros ingreso(int litros) {
        EquipoOtros eq = new EquipoOtros();
        eq.setFechaIngreso(LocalDateTime.of(2026, 9, 10, 9, 0));
        eq.setClienteNombre("Cliente");
        eq.setTipoIngreso(TipoIngresoOtros.DETALLES);
        eq.setVolumenEquipo(litros);
        return eq;
    }

    private static EquipoOtrosReporteDTO dto(int litros) {
        return new EquipoOtrosReporteDTO("10/09/2026", "Cliente", "DETALLES", "Gasa  x2", "", litros);
    }

    private static List<String> textos(JasperPrint print) {
        List<String> textos = new ArrayList<>();
        for (JRPrintPage pagina : print.getPages()) {
            for (JRPrintElement el : pagina.getElements()) {
                if (el instanceof JRPrintText texto) textos.add(texto.getFullText());
            }
        }
        return textos;
    }
}
