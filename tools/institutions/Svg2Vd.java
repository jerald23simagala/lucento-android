import com.android.ide.common.vectordrawable.Svg2Vector;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Converts every *.svg in a folder with Android's own converter (Vector Asset Studio's Svg2Vector, from the
 * com.android.tools:sdk-common jar AGP already uses). Writes name.xml, plus name.err when the converter reports problems.
 * Run by build_institutions.py: java -cp <sdk-common:common:guava:kotlin-stdlib> Svg2Vd.java <in> <out>
 */
public class Svg2Vd {
    public static void main(String[] args) throws Exception {
        Path in = Paths.get(args[0]);
        Path out = Paths.get(args[1]);
        Files.createDirectories(out);
        try (DirectoryStream<Path> svgs = Files.newDirectoryStream(in, "*.svg")) {
            for (Path svg : svgs) {
                String name = svg.getFileName().toString().replaceAll("\\.svg$", "");
                ByteArrayOutputStream xml = new ByteArrayOutputStream();
                String errors;
                try {
                    errors = Svg2Vector.parseSvgToXml(svg, xml);
                } catch (Exception e) {
                    errors = "ERROR: " + e;
                }
                if (xml.size() > 0) Files.write(out.resolve(name + ".xml"), xml.toByteArray());
                if (errors != null && !errors.isEmpty()) {
                    Files.write(out.resolve(name + ".err"), errors.getBytes(StandardCharsets.UTF_8));
                }
            }
        }
    }
}
