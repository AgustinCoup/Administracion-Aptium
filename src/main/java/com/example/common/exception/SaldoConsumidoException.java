package com.example.common.exception;

/**
 * Otro operador ya se llevó parte de la ropa que la tanda pretendía lavar.
 *
 * <p>Es subtipo de {@link ConflictoConcurrenciaException} porque es un choque como cualquier otro
 * —los controllers lo rutean igual, sin tocar un {@code catch}—, y existe por una sola razón: es
 * el <b>único</b> choque del lanzamiento que invalida el trabajo en curso, así que es el único
 * ante el cual {@code CiclosController.lanzar} descarta el staging de la tanda entera. Lo que
 * el operador armó ya no se puede volver a mandar tal cual y él no tiene cómo saber qué parte
 * sobrevive.</p>
 *
 * <p><b>El marcador es positivo a propósito.</b> La regla es "descartar sólo ante esto", no
 * "descartar salvo ante aquello": escrita al revés, cualquier choque nuevo que aparezca en el
 * lanzamiento —hoy {@link LavarropasOcupadoException} y el rollback por deadlock de
 * {@code CicloLavaderoDAO.lanzarTanda}— hereda por omisión un descarte que no le corresponde, y
 * el operador pierde el reparto de lavarropas que nadie tocó. Los otros dos casos no invalidan
 * nada: la ropa sigue disponible y no quedó nada escrito.</p>
 */
public class SaldoConsumidoException extends ConflictoConcurrenciaException {

    public SaldoConsumidoException(String mensaje) {
        super(mensaje);
    }
}
