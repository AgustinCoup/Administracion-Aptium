package com.example.common.exception;

/**
 * Un lavarropas de la tanda dejó de estar libre entre que la pantalla lo mostró y el operador
 * apretó Lanzar.
 *
 * <p>Es subtipo de {@link ConflictoConcurrenciaException} porque es un choque como cualquier otro
 * —los controllers lo rutean igual, sin tocar un {@code catch}—, y se distingue por lo que
 * <b>no</b> hay que hacer: la ropa sigue disponible, lo único que se perdió es el destino, así que
 * el staging de la tanda no se descarta (eso es exclusivo de
 * {@link SaldoConsumidoException}). La relectura siguiente descarta sólo el del lavarropas
 * ocupado, en {@code ConstructorVistaCiclos}.</p>
 *
 * <p>El tipo tiene además un segundo consumidor: el cartel de este choque ya le explica al
 * operador qué va a pasar con lo que tenía cargado ahí, así que
 * {@code CiclosController.avisarStagingDescartado} se saltea el aviso del descarte que llegaría
 * con esa relectura. Sin eso, un solo conflicto abre dos modales seguidos diciendo lo mismo.</p>
 */
public class LavarropasOcupadoException extends ConflictoConcurrenciaException {

    public LavarropasOcupadoException(String mensaje) {
        super(mensaje);
    }
}
