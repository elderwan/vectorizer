package se233.vectorizer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import se233.vectorizer.controller.BatchProcessor;
import se233.vectorizer.model.ConversionSettings;
import se233.vectorizer.model.ImageItem;
import se233.vectorizer.model.VectorizationService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

public class BatchProcessorTest {
    @TempDir
    Path tempDirectory;

    @Test
    void usesParallelModeForRequiredTrigger() throws Exception {
        VectorizationService service = Mockito.mock(VectorizationService.class);
        Path source = Files.writeString(tempDirectory.resolve("source.svg"), "<svg/>");
        ImageItem first = item("first.png");
        ImageItem second = item("second.png");
        when(service.convertAsync(any(), any())).thenReturn(CompletableFuture.completedFuture(source));

        ConversionSettings settings = new ConversionSettings();
        BatchProcessor.BatchResult result = new BatchProcessor(service)
                .processAsync(List.of(first, second), settings, tempDirectory.resolve("out"), null)
                .join();

        assertTrue(result.parallel());
        assertEquals(2, result.successes().size());
        Mockito.verify(service, Mockito.times(2)).convertAsync(any(), any());
    }

    @Test
    void usesSequentialModeWhenDetailIsNotMedium() {
        ConversionSettings settings = new ConversionSettings();
        settings.setDetailLevel(ConversionSettings.DetailLevel.HIGH);
        assertTrue(!BatchProcessor.shouldProcessInParallel(
                List.of(Mockito.mock(ImageItem.class), Mockito.mock(ImageItem.class)), settings));
    }

    @Test
    void createsNumberedCustomNamesWithoutDuplicatingSvgExtension() throws Exception {
        VectorizationService service = Mockito.mock(VectorizationService.class);
        Path source = Files.writeString(tempDirectory.resolve("source.svg"), "<svg/>");
        ImageItem first = item("first.png");
        ImageItem second = item("second.png");
        when(service.convertAsync(any(), any()))
                .thenReturn(CompletableFuture.completedFuture(source));

        ConversionSettings settings = new ConversionSettings();
        settings.setNamingRule(ConversionSettings.NamingRule.PREFIX_NUMBER);
        settings.setCustomName("MyVector.svg");

        BatchProcessor.BatchResult result = new BatchProcessor(service)
                .processAsync(List.of(first, second), settings, tempDirectory.resolve("named"), null)
                .join();

        assertEquals(List.of("MyVector_1.svg", "MyVector_2.svg"),
                result.successes().stream()
                        .map(path -> path.getFileName().toString())
                        .sorted()
                        .toList());
    }

    @Test
    void preprocessesMultipleImagesInParallelWithColorDetection() throws Exception {
        VectorizationService service = Mockito.mock(VectorizationService.class);
        Path source = Files.writeString(tempDirectory.resolve("source.svg"), "<svg/>");
        Path firstPath = createImage("first.png", 0xFF0000);
        Path secondPath = createImage("second.png", 0x00FF00);
        ImageItem first = item(firstPath);
        ImageItem second = item(secondPath);
        when(service.convertAsync(any(), any()))
                .thenReturn(CompletableFuture.completedFuture(source));

        BatchProcessor.PreprocessResult result = new BatchProcessor(service)
                .preprocessAsync(List.of(first, second), null)
                .join();

        assertTrue(result.parallel());
        assertEquals(2, result.successes().size());
        assertEquals(0, result.failures().size());
        Mockito.verify(service, Mockito.times(2)).convertAsync(any(), any());
    }

    private ImageItem item(String name) {
        ImageItem item = Mockito.mock(ImageItem.class);
        when(item.getFile()).thenReturn(tempDirectory.resolve(name).toFile());
        return item;
    }

    private ImageItem item(Path path) {
        ImageItem item = Mockito.mock(ImageItem.class);
        when(item.getFile()).thenReturn(path.toFile());
        return item;
    }

    private Path createImage(String name, int color) throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                image.setRGB(x, y, color);
            }
        }
        Path path = tempDirectory.resolve(name);
        ImageIO.write(image, "png", path.toFile());
        return path;
    }
}
