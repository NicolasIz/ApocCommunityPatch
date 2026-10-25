import java.io.File;
import java.nio.file.*;
import javax.imageio.ImageIO;

/** Rewrites a PNG the JDK cannot read (ItemsAdder-protected) as a plain PNG. */
public class Unprotect {
    public static void main(String[] a) throws Exception {
        ImageIO.write(com.arkcronist.content.core.pack.PngReader.read(Files.readAllBytes(Path.of(a[0]))), "png", new File(a[1]));
    }
}
