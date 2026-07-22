package com.example.ui.common;

import javax.swing.JTable;
import javax.swing.ToolTipManager;
import javax.swing.table.TableModel;
import java.awt.event.MouseEvent;
import java.util.function.IntFunction;

/**
 * {@link JTable} que muestra un tooltip distinto por fila, provisto por una función
 * inyectada.
 *
 * <p>Glue de Swing sin conocimiento del dominio: recibe un {@code IntFunction<String>}
 * que traduce un índice de fila <b>del modelo</b> al texto del tooltip (o {@code null}
 * para no mostrar ninguno). Las reglas de contenido viven en el proveedor, no acá.</p>
 */
public class RowTooltipTable extends JTable {

    private static final long serialVersionUID = 1L;

    private transient IntFunction<String> rowTooltipProvider;

    public RowTooltipTable(TableModel model) {
        super(model);
        // Sin texto estático el tooltip dinámico nunca se dispararía: hay que
        // registrar la tabla explícitamente en el manager.
        ToolTipManager.sharedInstance().registerComponent(this);
    }

    /** @param provider índice de fila del modelo → texto del tooltip; null desactiva. */
    public void setRowTooltipProvider(IntFunction<String> provider) {
        this.rowTooltipProvider = provider;
    }

    @Override
    public String getToolTipText(MouseEvent e) {
        if (rowTooltipProvider == null) return super.getToolTipText(e);
        int viewRow = rowAtPoint(e.getPoint());
        if (viewRow < 0) return null;
        return rowTooltipProvider.apply(convertRowIndexToModel(viewRow));
    }
}
