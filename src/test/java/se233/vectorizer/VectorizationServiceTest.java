package se233.vectorizer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import se233.vectorizer.exception.CliToolNotFoundException;
import se233.vectorizer.model.ConversionSettings;
import se233.vectorizer.model.ImageItem;
import se233.vectorizer.model.VectorizationService;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

public class VectorizationServiceTest {
    @TempDir
    Path tempDirectory;

    @Test
    void convertAsyncReturnsGeneratedSvgPath() throws Exception {
        File input = createPng("input.png");
        ImageItem item = Mockito.mock(ImageItem.class);
        when(item.getFile()).thenReturn(input);

        String javaExecutable = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java").toString();
        VectorizationService service = new VectorizationService(java.util.List.of(
                javaExecutable, "-cp", System.getProperty("java.class.path"),
                FakePotrace.class.getName()));
        Path svg = service.convertAsync(item, new ConversionSettings()).join();

        assertTrue(Files.exists(svg));
        assertTrue(Files.readString(svg).contains("<svg"));
    }

    @Test
    void reportsMissingPotraceExecutable() throws Exception {
        File input = createPng("input.png");
        ImageItem item = Mockito.mock(ImageItem.class);
        when(item.getFile()).thenReturn(input);

        VectorizationService service = new VectorizationService(
                tempDirectory.resolve("missing-potrace").toString());

        CompletionException exception = assertThrows(CompletionException.class,
                () -> service.convertAsync(item, new ConversionSettings()).join());
        assertTrue(exception.getCause() instanceof CliToolNotFoundException);
    }

    @Test
    void customColorSettingsUseValidPotraceArguments() throws Exception {
        File input = createPng("custom.png");
        ImageItem item = Mockito.mock(ImageItem.class);
        when(item.getFile()).thenReturn(input);

        VectorizationService service = new VectorizationService(java.util.List.of(
                Path.of(System.getProperty("java.home"), "bin",
                        System.getProperty("os.name").toLowerCase().contains("win")
                                ? "java.exe" : "java").toString(),
                "-cp", System.getProperty("java.class.path"),
                FakePotrace.class.getName()));
        ConversionSettings settings = new ConversionSettings();
        settings.setColorMode(ConversionSettings.ColorMode.CUSTOM);
        settings.setCustomColors(5);

        Path svg = service.convertAsync(item, settings).join();

        assertTrue(Files.exists(svg));
    }

    @Test
    void customColorCountCreatesMultipleSvgColorLayers() throws Exception {
        Path inputPath = tempDirectory.resolve("palette.png");
        BufferedImage image = new BufferedImage(3, 1, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, 0xFF0000);
        image.setRGB(1, 0, 0x00FF00);
        image.setRGB(2, 0, 0x0000FF);
        ImageIO.write(image, "png", inputPath.toFile());

        ImageItem item = Mockito.mock(ImageItem.class);
        when(item.getFile()).thenReturn(inputPath.toFile());
        VectorizationService service = fakeService();
        ConversionSettings settings = new ConversionSettings();
        settings.setCustomColors(3);

        String svg = Files.readString(service.convertAsync(item, settings).join());

        assertTrue(svg.contains("#FF0000"));
        assertTrue(svg.contains("#00FF00"));
        assertTrue(svg.contains("#0000FF"));
    }

    @Test
    void unlimitedKeepsSmallOriginalPalette() throws Exception {
        Path inputPath = tempDirectory.resolve("unlimited.png");
        BufferedImage image = new BufferedImage(3, 1, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, 0xFF0000);
        image.setRGB(1, 0, 0x00FF00);
        image.setRGB(2, 0, 0x0000FF);
        ImageIO.write(image, "png", inputPath.toFile());

        ImageItem item = Mockito.mock(ImageItem.class);
        when(item.getFile()).thenReturn(inputPath.toFile());
        ConversionSettings settings = new ConversionSettings();
        settings.setColorMode(ConversionSettings.ColorMode.UNLIMITED);

        String svg = Files.readString(fakeService().convertAsync(item, settings).join());

        assertTrue(svg.contains("#FF0000"));
        assertTrue(svg.contains("#00FF00"));
        assertTrue(svg.contains("#0000FF"));
    }

    @Test
    void removeBackgroundOmitsCanvasBackground() throws Exception {
        Path inputPath = tempDirectory.resolve("background.png");
        BufferedImage image = new BufferedImage(4, 2, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                image.setRGB(x, y, x == 0 ? 0xFF0000 : 0xFFFFFF);
            }
        }
        ImageIO.write(image, "png", inputPath.toFile());

        ImageItem item = Mockito.mock(ImageItem.class);
        when(item.getFile()).thenReturn(inputPath.toFile());
        ConversionSettings settings = new ConversionSettings();
        settings.setRemoveBackground(true);

        String svg = Files.readString(fakeService().convertAsync(item, settings).join());

        assertTrue(!svg.contains("<rect"));
    }

    private VectorizationService fakeService() {
        String javaExecutable = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").toLowerCase().contains("win")
                        ? "java.exe" : "java").toString();
        return new VectorizationService(java.util.List.of(
                javaExecutable, "-cp", System.getProperty("java.class.path"),
                FakePotrace.class.getName()));
    }

    private File createPng(String name) throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        Path path = tempDirectory.resolve(name);
        ImageIO.write(image, "png", path.toFile());
        return path.toFile();
    }
}
