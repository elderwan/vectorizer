package se233.vectorizer;

import org.junit.jupiter.api.Test;
import se233.vectorizer.util.ColorAnalyzer;

import java.awt.Color;
import java.awt.image.BufferedImage;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ColorAnalyzerTest {
    @Test
    void detectsOneColor() {
        assertEquals(1, ColorAnalyzer.detectColorCount(imageWithColors(1)));
    }

    @Test
    void detectsThreeColors() {
        assertEquals(3, ColorAnalyzer.detectColorCount(imageWithColors(3)));
    }

    @Test
    void detectsSixColorsAndCapsAtFive() {
        assertEquals(5, ColorAnalyzer.detectColorCount(imageWithColors(6)));
    }

    @Test
    void detectsTenColorsAndCapsAtFive() {
        assertEquals(5, ColorAnalyzer.detectColorCount(imageWithColors(10)));
    }

    private static BufferedImage imageWithColors(int colorCount) {
        BufferedImage image = new BufferedImage(colorCount, 1, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < colorCount; x++) {
            image.setRGB(x, 0, new Color(x * 20, x * 10, x * 5).getRGB());
        }
        return image;
    }
}
