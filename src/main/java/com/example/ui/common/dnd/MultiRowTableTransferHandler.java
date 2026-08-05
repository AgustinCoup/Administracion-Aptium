package com.example.ui.common.dnd;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.JComponent;
import javax.swing.TransferHandler;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * {@link TransferHandler} genérico para arrastrar y soltar varias filas de una
 * tabla. Las reglas de negocio (qué filas se arrastran, si se acepta el drop, qué
 * hacer con las filas soltadas) se inyectan por callbacks, de modo que el handler
 * no conoce ningún dominio concreto y es reutilizable.
 *
 * <p>Construir vía {@link Builder}:
 * <pre>{@code
 * new MultiRowTableTransferHandler.Builder<Foo>(MI_FLAVOR)
 *     .sourceActions(TransferHandler.COPY)
 *     .selectionSupplier(panel::getSeleccionados)
 *     .onImport(items -> controller.recibir(items))
 *     .build();
 * }</pre>
 *
 * @param <T> tipo de los ítems transferidos
 */
public class MultiRowTableTransferHandler<T> extends TransferHandler {

    private static final Logger log = LoggerFactory.getLogger(MultiRowTableTransferHandler.class);

    private final DataFlavor flavor;
    private final int sourceActions;
    private final Supplier<List<T>> selectionSupplier;
    private final Predicate<TransferSupport> canImportExtra;
    private final Consumer<List<T>> onImport;
    private final IntConsumer onExportDone;

    private MultiRowTableTransferHandler(Builder<T> builder) {
        this.flavor = builder.flavor;
        this.sourceActions = builder.sourceActions;
        this.selectionSupplier = builder.selectionSupplier;
        this.canImportExtra = builder.canImportExtra;
        this.onImport = builder.onImport;
        this.onExportDone = builder.onExportDone;
    }

    @Override
    public int getSourceActions(JComponent c) {
        return sourceActions;
    }

    @Override
    protected Transferable createTransferable(JComponent c) {
        List<T> seleccion = selectionSupplier.get();
        if (seleccion == null || seleccion.isEmpty()) {
            return null;
        }
        return new MultiRowTransferable<>(seleccion, flavor);
    }

    @Override
    public boolean canImport(TransferSupport support) {
        if (!support.isDrop() || !support.isDataFlavorSupported(flavor)) {
            return false;
        }
        boolean ok = canImportExtra.test(support);
        support.setShowDropLocation(ok);
        return ok;
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean importData(TransferSupport support) {
        if (!canImport(support)) {
            return false;
        }
        try {
            List<T> items = (List<T>) support.getTransferable().getTransferData(flavor);
            onImport.accept(items);
            return true;
        } catch (Exception e) {
            log.error("Error al procesar drop multi-fila", e);
            return false;
        }
    }

    /**
     * Se invoca <strong>siempre</strong> al terminar el arrastre, incluso si se
     * abortó ({@code action == NONE}). El consumidor decide según el {@code action}
     * qué hacer (p.ej. resetear flags de estado siempre; refrescar solo en MOVE).
     */
    @Override
    protected void exportDone(JComponent source, Transferable data, int action) {
        if (onExportDone != null) {
            onExportDone.accept(action);
        }
    }

    /** Builder del handler. Solo {@code flavor} es obligatorio. */
    public static class Builder<T> {
        private final DataFlavor flavor;
        private int sourceActions = COPY;
        private Supplier<List<T>> selectionSupplier = List::of;
        private Predicate<TransferSupport> canImportExtra = support -> true;
        private Consumer<List<T>> onImport = items -> { };
        private IntConsumer onExportDone = null;

        public Builder(DataFlavor flavor) {
            this.flavor = Objects.requireNonNull(flavor, "flavor requerido");
        }

        public Builder<T> sourceActions(int actions) {
            this.sourceActions = actions;
            return this;
        }

        public Builder<T> selectionSupplier(Supplier<List<T>> supplier) {
            this.selectionSupplier = Objects.requireNonNull(supplier, "selectionSupplier requerido");
            return this;
        }

        public Builder<T> canImportExtra(Predicate<TransferSupport> predicate) {
            this.canImportExtra = Objects.requireNonNull(predicate, "canImportExtra requerido");
            return this;
        }

        public Builder<T> onImport(Consumer<List<T>> consumer) {
            this.onImport = Objects.requireNonNull(consumer, "onImport requerido");
            return this;
        }

        public Builder<T> onExportDone(IntConsumer consumer) {
            this.onExportDone = consumer;
            return this;
        }

        public MultiRowTableTransferHandler<T> build() {
            return new MultiRowTableTransferHandler<>(this);
        }
    }
}
