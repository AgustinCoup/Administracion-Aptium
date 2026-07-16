package com.example.features.lotes.view.helpers;

/**
 * Sincroniza el volumen final del lote con el total calculado (litros por
 * ingreso + volumen de ortopedias): lo sigue hasta que el usuario lo edita a
 * mano, momento en el que deja de seguirlo. Lógica pura, sin Swing: testeable
 * en aislamiento.
 */
final class SincronizadorVolumenFinal {

    private final int volumenOrtopedias;
    private final int capacidadTotal;

    private boolean editadoAMano;
    private int totalIngresos;
    private int volumenFinal;

    SincronizadorVolumenFinal(int volumenOrtopedias, int capacidadTotal) {
        this.volumenOrtopedias = volumenOrtopedias;
        this.capacidadTotal = capacidadTotal;
        this.volumenFinal = clamp(volumenOrtopedias);
    }

    /** Litros de ingreso recalculados: algún spinner de ingreso cambió. */
    int onLitrosIngresoChange(int totalLitrosIngreso) {
        totalIngresos = totalLitrosIngreso;
        if (!editadoAMano) {
            volumenFinal = clamp(totalCalculado());
        }
        return volumenFinal;
    }

    /** El usuario tocó el spinner de volumen final directamente (no la sincronización). */
    void onVolumenFinalEditadoPorUsuario(int nuevoValor) {
        editadoAMano = true;
        volumenFinal = nuevoValor;
    }

    int totalCalculado() {
        return volumenOrtopedias + totalIngresos;
    }

    int getVolumenFinal() {
        return volumenFinal;
    }

    String textoCalculado() {
        return String.format("Volumen calculado: %d / %d", totalCalculado(), capacidadTotal);
    }

    String textoAdvertencia() {
        StringBuilder sb = new StringBuilder();
        if (volumenFinal != totalCalculado()) sb.append("⚠ Volumen ajustado manualmente. ");
        double porcentaje = capacidadTotal == 0 ? 0 : (double) volumenFinal / capacidadTotal;
        if (porcentaje < 0.8) sb.append("⚠ Menos del 80% de capacidad.");
        return sb.length() == 0 ? " " : sb.toString();
    }

    private int clamp(int valor) {
        return Math.max(1, Math.min(valor, capacidadTotal));
    }
}
