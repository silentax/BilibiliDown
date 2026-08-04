package nicelee.ui.util;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.border.Border;
import javax.swing.border.EmptyBorder;

/** 低饱和、纯净动漫感的 Swing 视觉基线。 */
public final class AnimeUi {

	public static final Color BACKGROUND_TOP = new Color(248, 250, 255);
	public static final Color BACKGROUND_BOTTOM = new Color(240, 247, 252);
	public static final Color SURFACE = new Color(255, 255, 255);
	public static final Color SURFACE_ALT = new Color(247, 249, 253);
	public static final Color TEXT_PRIMARY = new Color(43, 53, 76);
	public static final Color TEXT_SECONDARY = new Color(105, 117, 142);
	public static final Color BORDER = new Color(220, 227, 239);
	public static final Color ACCENT = new Color(104, 112, 222);
	public static final Color ACCENT_SOFT = new Color(232, 235, 255);
	public static final Color SKY_SOFT = new Color(222, 242, 252);
	public static final Color MINT_SOFT = new Color(223, 245, 238);
	public static final Color PINK_SOFT = new Color(255, 231, 239);
	public static final Color WARNING_SOFT = new Color(255, 244, 216);
	public static final Color ERROR_SOFT = new Color(255, 229, 233);

	private AnimeUi() {
	}

	public static void paintBackground(Graphics2D graphics, int width, int height) {
		Graphics2D g = (Graphics2D) graphics.create();
		g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
		g.setPaint(new GradientPaint(0, 0, BACKGROUND_TOP, 0, Math.max(1, height), BACKGROUND_BOTTOM));
		g.fillRect(0, 0, width, height);

		g.setColor(new Color(219, 225, 255, 150));
		g.fill(new Ellipse2D.Double(width * 0.72, -height * 0.13, height * 0.55, height * 0.55));
		g.setColor(new Color(255, 224, 236, 135));
		g.fill(new Ellipse2D.Double(-height * 0.16, height * 0.58, height * 0.46, height * 0.46));
		g.setColor(new Color(216, 243, 239, 150));
		g.fill(new Ellipse2D.Double(width * 0.55, height * 0.72, height * 0.30, height * 0.30));

		Path2D wave = new Path2D.Double();
		wave.moveTo(0, height * 0.80);
		wave.curveTo(width * 0.25, height * 0.72, width * 0.40, height * 0.90, width * 0.68, height * 0.82);
		wave.curveTo(width * 0.82, height * 0.78, width * 0.92, height * 0.72, width, height * 0.75);
		wave.lineTo(width, height);
		wave.lineTo(0, height);
		wave.closePath();
		g.setColor(new Color(255, 255, 255, 115));
		g.fill(wave);

		g.setStroke(new BasicStroke(2.0f));
		drawSparkle(g, width * 0.12, height * 0.20, 8, new Color(255, 255, 255, 210));
		drawSparkle(g, width * 0.88, height * 0.42, 6, new Color(255, 255, 255, 190));
		drawSparkle(g, width * 0.67, height * 0.14, 5, new Color(255, 255, 255, 180));
		g.dispose();
	}

	private static void drawSparkle(Graphics2D g, double x, double y, double radius, Color color) {
		Path2D sparkle = new Path2D.Double();
		sparkle.moveTo(x, y - radius);
		sparkle.lineTo(x + radius * 0.28, y - radius * 0.28);
		sparkle.lineTo(x + radius, y);
		sparkle.lineTo(x + radius * 0.28, y + radius * 0.28);
		sparkle.lineTo(x, y + radius);
		sparkle.lineTo(x - radius * 0.28, y + radius * 0.28);
		sparkle.lineTo(x - radius, y);
		sparkle.lineTo(x - radius * 0.28, y - radius * 0.28);
		sparkle.closePath();
		g.setColor(color);
		g.fill(sparkle);
	}

	public static Border cardBorder(int verticalPadding, int horizontalPadding) {
		return BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(BORDER),
				new EmptyBorder(verticalPadding, horizontalPadding, verticalPadding, horizontalPadding));
	}

	public static void stylePrimaryButton(AbstractButton button) {
		button.setOpaque(true);
		button.setContentAreaFilled(true);
		button.setFocusPainted(false);
		button.setForeground(Color.WHITE);
		button.setBackground(ACCENT);
		button.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(ACCENT.darker()),
				new EmptyBorder(7, 14, 7, 14)));
	}

	public static void styleSecondaryButton(AbstractButton button) {
		button.setOpaque(true);
		button.setContentAreaFilled(true);
		button.setFocusPainted(false);
		button.setForeground(TEXT_PRIMARY);
		button.setBackground(SURFACE);
		button.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(BORDER),
				new EmptyBorder(6, 11, 6, 11)));
	}

	public static void styleInput(JComponent component) {
		component.setBackground(SURFACE);
		component.setForeground(TEXT_PRIMARY);
		component.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(BORDER),
				new EmptyBorder(7, 10, 7, 10)));
	}
}
