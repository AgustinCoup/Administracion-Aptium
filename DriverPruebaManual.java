import com.example.features.actualizaciones.service.ActualizacionInstaller;
import java.nio.file.Path;

public class DriverPruebaManual {
    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("Uso: DriverPruebaManual <ruta-jar-staged>");
            System.exit(2);
        }
        new ActualizacionInstaller().instalarYReiniciar(Path.of(args[0]));
    }
}
