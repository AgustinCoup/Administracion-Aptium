package com.example.features.equipos.common.controller.helpers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.example.common.model.EquipoKey;
import com.example.common.model.EquipoRegistrableInterface;
import com.example.common.model.EquipoRegistrableInterface.TipoEquipo;
import com.example.features.equipos.ortopedias.model.Equipo;
import com.example.features.equipos.otros.model.EquipoOtros;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SuperposicionPreviewsTest {

    private static Equipo ortopedia(int id) {
        Equipo e = new Equipo();
        e.setId(id);
        return e;
    }

    private static EquipoOtros otros(int id) {
        EquipoOtros e = new EquipoOtros();
        e.setId(id);
        return e;
    }

    private static EquipoKey clave(EquipoRegistrableInterface equipo) {
        return new EquipoKey(equipo.getTipo(), equipo.getId());
    }

    @Test
    @DisplayName("sin copias devuelve el snapshot tal cual, en su orden")
    void sinCopias_devuelveElSnapshot() {
        Equipo a = ortopedia(1);
        EquipoOtros b = otros(2);

        List<EquipoRegistrableInterface> resultado = SuperposicionPreviews.superponer(List.of(a, b), Map.of());

        assertEquals(2, resultado.size());
        assertSame(a, resultado.get(0));
        assertSame(b, resultado.get(1));
    }

    @Test
    @DisplayName("reemplaza por clave y conserva el orden del snapshot")
    void reemplazaPorClaveConservandoElOrden() {
        Equipo a = ortopedia(1);
        Equipo b = ortopedia(2);
        Equipo c = ortopedia(3);
        Equipo copiaB = b.copiarParaPreview();

        List<EquipoRegistrableInterface> resultado =
            SuperposicionPreviews.superponer(List.of(a, b, c), Map.of(clave(b), copiaB));

        assertEquals(3, resultado.size());
        assertSame(a, resultado.get(0));
        assertSame(copiaB, resultado.get(1));
        assertSame(c, resultado.get(2));
    }

    @Test
    @DisplayName("la copia de un equipo que ya no está en el snapshot no se pinta")
    void copiaDeEquipoQueYaNoEsta_seDescarta() {
        Equipo a = ortopedia(1);
        Equipo desaparecido = ortopedia(9);

        List<EquipoRegistrableInterface> resultado = SuperposicionPreviews.superponer(
            List.of(a), Map.of(clave(desaparecido), desaparecido.copiarParaPreview()));

        assertEquals(1, resultado.size());
        assertSame(a, resultado.get(0));
    }

    @Test
    @DisplayName("una ortopedia y un 'otros' con el mismo id no se confunden")
    void ortopediaYOtrosConMismoId_noSeConfunden() {
        Equipo orto = ortopedia(5);
        EquipoOtros otro = otros(5);
        EquipoOtros copiaOtro = otro.copiarParaPreview();

        List<EquipoRegistrableInterface> resultado = SuperposicionPreviews.superponer(
            List.of(orto, otro), Map.of(new EquipoKey(TipoEquipo.OTROS, 5), copiaOtro));

        assertSame(orto, resultado.get(0));
        assertSame(copiaOtro, resultado.get(1));
    }
}
