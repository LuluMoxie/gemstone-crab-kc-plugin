package com.gemstonecrabkc;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;

/**
 * Side panel. When the plugin isn't connected yet it shows a big, simple
 * "paste your sync key here and click Connect" box at the top. Once connected
 * it shows the player's tracked counts, leaderboard rank and sync status.
 */
class GemstoneCrabKcPanel extends PluginPanel
{
	private static final NumberFormat NUMBER = NumberFormat.getIntegerInstance(Locale.US);
	private static final Color GOLD = new Color(255, 176, 0);
	private static final Color GREEN = new Color(90, 173, 74);

	private final JLabel killsValue = valueLabel();
	private final JLabel top3Value = valueLabel();
	private final JLabel rankValue = valueLabel();
	private final JLabel statusLabel = new JLabel();

	private final JPanel connectBox;
	private final JPanel connectedBox;
	private final JTextField keyField = new JTextField();
	private final JLabel keyError = new JLabel();

	GemstoneCrabKcPanel(
		Consumer<String> onConnect,
		Runnable onDisconnect,
		Runnable onSyncNow,
		Runnable onOpenLeaderboard,
		Runnable onOpenSyncPage)
	{
		super(false);
		setLayout(new BorderLayout());
		setBorder(new EmptyBorder(10, 10, 10, 10));
		setBackground(ColorScheme.DARK_GRAY_COLOR);

		JPanel content = new JPanel();
		content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
		content.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JLabel title = new JLabel("Gemstone Crab KC");
		title.setFont(FontManager.getRunescapeBoldFont().deriveFont(20f));
		title.setForeground(GOLD);
		left(title);
		content.add(title);
		content.add(Box.createVerticalStrut(8));

		// ---- Not connected: big, obvious setup box --------------------------
		connectBox = new JPanel();
		connectBox.setLayout(new BoxLayout(connectBox, BoxLayout.Y_AXIS));
		connectBox.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		connectBox.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(GOLD, 2),
			new EmptyBorder(10, 10, 10, 10)));
		left(connectBox);

		connectBox.add(big("CONNECT TO THE", Color.WHITE));
		connectBox.add(big("LEADERBOARD", GOLD));
		connectBox.add(Box.createVerticalStrut(10));

		connectBox.add(stepLabel("STEP 1"));
		connectBox.add(wrap("Get a sync key for this character from the website."));
		connectBox.add(Box.createVerticalStrut(4));
		connectBox.add(fullWidth(button("Get my sync key", onOpenSyncPage, false)));
		connectBox.add(Box.createVerticalStrut(12));

		connectBox.add(stepLabel("STEP 2"));
		connectBox.add(boldWrap("Once you have your sync key, paste it in the box below and click Connect."));
		connectBox.add(wrap("Each character needs its own key. Log in to the character first."));
		connectBox.add(Box.createVerticalStrut(6));

		JLabel pasteLabel = new JLabel("Paste your sync key here:");
		pasteLabel.setFont(FontManager.getRunescapeSmallFont());
		pasteLabel.setForeground(GOLD);
		left(pasteLabel);
		connectBox.add(pasteLabel);
		connectBox.add(Box.createVerticalStrut(3));

		// A bright outline and lighter fill so it is obviously a text box.
		keyField.setFont(FontManager.getRunescapeFont());
		keyField.setToolTipText("Paste your sync key here (Ctrl+V)");
		keyField.setBackground(new Color(60, 60, 60));
		keyField.setForeground(Color.WHITE);
		keyField.setCaretColor(Color.WHITE);
		keyField.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(GOLD, 2),
			new EmptyBorder(6, 6, 6, 6)));
		keyField.addActionListener(e -> onConnect.accept(keyField.getText()));
		connectBox.add(fullWidth(keyField));
		connectBox.add(Box.createVerticalStrut(6));

		JButton connect = button("CONNECT", () -> onConnect.accept(keyField.getText()), true);
		connect.setFont(FontManager.getRunescapeBoldFont().deriveFont(18f));
		connectBox.add(fullWidth(connect));

		keyError.setFont(FontManager.getRunescapeSmallFont());
		keyError.setForeground(ColorScheme.PROGRESS_ERROR_COLOR);
		left(keyError);
		connectBox.add(keyError);

		content.add(connectBox);

		// ---- Connected: small banner with a disconnect option ----------------
		connectedBox = new JPanel(new BorderLayout(6, 0));
		connectedBox.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		connectedBox.setBorder(BorderFactory.createCompoundBorder(
			BorderFactory.createLineBorder(GREEN, 2),
			new EmptyBorder(8, 10, 8, 10)));
		JLabel connectedLabel = new JLabel("CONNECTED");
		connectedLabel.setFont(FontManager.getRunescapeBoldFont().deriveFont(16f));
		connectedLabel.setForeground(GREEN);
		connectedBox.add(connectedLabel, BorderLayout.CENTER);
		JButton disconnect = button("Disconnect", onDisconnect, false);
		disconnect.setFont(FontManager.getRunescapeSmallFont());
		connectedBox.add(disconnect, BorderLayout.EAST);
		left(connectedBox);
		connectedBox.setVisible(false);
		content.add(connectedBox);

		content.add(Box.createVerticalStrut(10));

		// ---- Stats ------------------------------------------------------------
		JPanel stats = new JPanel(new GridLayout(3, 2, 0, 6));
		stats.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		stats.setBorder(new EmptyBorder(10, 10, 10, 10));
		stats.add(nameLabel("Kill count"));
		stats.add(killsValue);
		stats.add(nameLabel("Top 3 finishes"));
		stats.add(top3Value);
		stats.add(nameLabel("Rank"));
		stats.add(rankValue);
		left(stats);
		content.add(stats);
		content.add(Box.createVerticalStrut(8));

		statusLabel.setFont(FontManager.getRunescapeSmallFont());
		statusLabel.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		left(statusLabel);
		content.add(statusLabel);
		content.add(Box.createVerticalStrut(10));

		JPanel buttons = new JPanel(new GridLayout(2, 1, 0, 6));
		buttons.setBackground(ColorScheme.DARK_GRAY_COLOR);
		buttons.add(button("Sync now", onSyncNow, false));
		buttons.add(button("View leaderboard", onOpenLeaderboard, false));
		left(buttons);
		content.add(buttons);

		add(content, BorderLayout.NORTH);

		setCounts(0, 0);
		setRank(null);
		setStatus("Log in to start tracking.", ColorScheme.LIGHT_GRAY_COLOR);
	}

	/** Shows the big setup box, or the small "Connected" banner. Safe from any thread. */
	void setConnected(boolean connected)
	{
		SwingUtilities.invokeLater(() ->
		{
			connectBox.setVisible(!connected);
			connectedBox.setVisible(connected);
			if (connected)
			{
				keyField.setText("");
				keyError.setText("");
			}
			revalidate();
			repaint();
		});
	}

	/** Error under the key box, e.g. when the pasted text isn't a key. Safe from any thread. */
	void setKeyError(String text)
	{
		SwingUtilities.invokeLater(() -> keyError.setText(text == null ? "" : html(text, 145)));
	}

	/** Safe to call from any thread. */
	void setCounts(int kills, int top3)
	{
		SwingUtilities.invokeLater(() ->
		{
			killsValue.setText(NUMBER.format(kills));
			top3Value.setText(NUMBER.format(top3));
		});
	}

	/** Safe to call from any thread. */
	void setRank(Integer rank)
	{
		SwingUtilities.invokeLater(() -> rankValue.setText(rank == null ? "-" : "#" + NUMBER.format(rank)));
	}

	/** Safe to call from any thread. */
	void setStatus(String text, Color color)
	{
		SwingUtilities.invokeLater(() ->
		{
			statusLabel.setText(html(text));
			statusLabel.setForeground(color);
		});
	}

	// ---- helpers -------------------------------------------------------------

	/** HTML lets long text wrap inside the narrow side panel. */
	private static String html(String text)
	{
		return html(text, 170);
	}

	private static String html(String text, int width)
	{
		String safe = text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
		return "<html><body style='width: " + width + "px'>" + safe + "</body></html>";
	}

	private static void left(JComponent c)
	{
		c.setAlignmentX(LEFT_ALIGNMENT);
	}

	private static JComponent fullWidth(JComponent c)
	{
		c.setAlignmentX(LEFT_ALIGNMENT);
		c.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height + 6));
		return c;
	}

	private static JLabel big(String text, Color color)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeBoldFont().deriveFont(20f));
		label.setForeground(color);
		left(label);
		return label;
	}

	private static JLabel stepLabel(String text)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeBoldFont().deriveFont(16f));
		label.setForeground(GOLD);
		left(label);
		return label;
	}

	private static JLabel wrap(String text)
	{
		JLabel label = new JLabel(html(text, 145));
		label.setFont(FontManager.getRunescapeFont());
		label.setForeground(ColorScheme.TEXT_COLOR);
		left(label);
		return label;
	}

	private static JLabel boldWrap(String text)
	{
		JLabel label = new JLabel(html(text, 145));
		label.setFont(FontManager.getRunescapeBoldFont().deriveFont(Font.BOLD, 16f));
		label.setForeground(Color.WHITE);
		left(label);
		return label;
	}

	private static JLabel nameLabel(String text)
	{
		JLabel label = new JLabel(text);
		label.setFont(FontManager.getRunescapeSmallFont());
		label.setForeground(ColorScheme.LIGHT_GRAY_COLOR);
		return label;
	}

	private static JLabel valueLabel()
	{
		JLabel label = new JLabel();
		label.setFont(FontManager.getRunescapeBoldFont());
		label.setForeground(Color.WHITE);
		label.setHorizontalAlignment(SwingConstants.RIGHT);
		return label;
	}

	private static JButton button(String text, Runnable action, boolean primary)
	{
		JButton button = new JButton(text);
		button.setFocusPainted(false);
		button.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		if (primary)
		{
			button.setBackground(GREEN);
			button.setForeground(Color.WHITE);
		}
		button.addActionListener(e -> action.run());
		return button;
	}
}
