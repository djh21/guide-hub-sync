package com.guidehub.sync;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.ColorScheme;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.PluginPanel;
import net.runelite.client.config.ConfigManager;

/** Side panel: create / join / leave / pause a Guide Hub party without typing commands. */
public class PartyPanel extends PluginPanel
{
	private static final Color ACCENT = new Color(0xF9, 0x73, 0x16);
	private static final Color MUTED = new Color(0x8A, 0x94, 0xA8);
	private static final Color GOOD = new Color(0x34, 0xD3, 0x99);

	private final PartyCommands commands;
	private final GuideHubSyncConfig config;
	private final ConfigManager configManager;
	private final JCheckBox overlayToggle = new JCheckBox("Show on-screen overlay");

	private final JLabel status = new JLabel();
	private final JPanel inParty = new JPanel();
	private final JPanel notInParty = new JPanel();
	private final JLabel nameLabel = new JLabel();
	private final JLabel codeLabel = new JLabel();
	private final JLabel membersLabel = new JLabel();
	private final JLabel lootLabel = new JLabel();
	private final JButton pauseBtn = new JButton();
	private final JTextField createName = new JTextField();
	private final JTextField joinCode = new JTextField();
	private final JLabel notConfigured = new JLabel();
	private final JPanel membersBox = new JPanel();
	private final JPanel lootBox = new JPanel();

	public PartyPanel(PartyCommands commands, GuideHubSyncConfig config, ConfigManager configManager)
	{
		super(false);
		this.commands = commands;
		this.config = config;
		this.configManager = configManager;
		setLayout(new BorderLayout());
		setBackground(ColorScheme.DARK_GRAY_COLOR);
		setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

		JPanel root = new JPanel();
		root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
		root.setBackground(ColorScheme.DARK_GRAY_COLOR);

		JLabel title = new JLabel("Guide Hub Party");
		title.setFont(FontManager.getRunescapeBoldFont());
		title.setForeground(ACCENT);
		root.add(left(title));
		root.add(Box.createVerticalStrut(6));

		notConfigured.setText("<html>Set your Hub URL and account key in the plugin settings (wrench icon) first.</html>");
		notConfigured.setForeground(Color.YELLOW);
		root.add(left(notConfigured));

		// ---- in a party ----
		inParty.setLayout(new BoxLayout(inParty, BoxLayout.Y_AXIS));
		inParty.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		inParty.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
		nameLabel.setFont(FontManager.getRunescapeBoldFont());
		nameLabel.setForeground(Color.WHITE);
		codeLabel.setFont(FontManager.getRunescapeFont());
		codeLabel.setForeground(GOOD);
		membersLabel.setForeground(MUTED);
		lootLabel.setForeground(MUTED);
		JButton copyBtn = button("Copy code");
		copyBtn.addActionListener(e ->
		{
			PartyCommands.PartyState s = commands.getState();
			if (s != null)
			{
				copy(s.code);
				setStatus("Code copied. Friends can join on the website or with ::party join " + s.code);
			}
		});
		pauseBtn.addActionListener(e ->
		{
			PartyCommands.PartyState s = commands.getState();
			if (s != null)
			{
				commands.setPaused(!s.paused);
			}
		});
		JButton leaveBtn = button("Leave party");
		leaveBtn.addActionListener(e -> commands.leave());
		JButton refreshBtn = button("Refresh");
		refreshBtn.addActionListener(e -> commands.status(false));
		inParty.add(left(nameLabel));
		inParty.add(left(codeLabel));
		inParty.add(left(membersLabel));
		inParty.add(left(lootLabel));
		inParty.add(Box.createVerticalStrut(6));
		JPanel row1 = new JPanel(new GridLayout(1, 2, 6, 0));
		row1.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row1.add(copyBtn);
		row1.add(pauseBtn);
		JPanel row2 = new JPanel(new GridLayout(1, 2, 6, 0));
		row2.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		row2.add(refreshBtn);
		row2.add(leaveBtn);
		inParty.add(row1);
		inParty.add(Box.createVerticalStrut(4));
		inParty.add(row2);
		inParty.add(Box.createVerticalStrut(8));
		inParty.add(left(section("Members")));
		membersBox.setLayout(new BoxLayout(membersBox, BoxLayout.Y_AXIS));
		membersBox.setOpaque(false);
		inParty.add(membersBox);
		inParty.add(Box.createVerticalStrut(8));
		inParty.add(left(section("Recent loot")));
		lootBox.setLayout(new BoxLayout(lootBox, BoxLayout.Y_AXIS));
		lootBox.setOpaque(false);
		inParty.add(lootBox);
		inParty.add(Box.createVerticalStrut(6));
		overlayToggle.setOpaque(false);
		overlayToggle.setForeground(MUTED);
		overlayToggle.setFont(FontManager.getRunescapeSmallFont());
		overlayToggle.setSelected(config.partyOverlay());
		overlayToggle.addActionListener(e -> configManager.setConfiguration(GuideHubSyncConfig.GROUP, "partyOverlay", overlayToggle.isSelected()));
		inParty.add(left(overlayToggle));
		JLabel hint = new JLabel("<html>Drops you receive while in the party are added to its loot feed on the website.</html>");
		hint.setForeground(MUTED);
		inParty.add(left(hint));

		// ---- not in a party ----
		notInParty.setLayout(new BoxLayout(notInParty, BoxLayout.Y_AXIS));
		notInParty.setBackground(ColorScheme.DARK_GRAY_COLOR);
		notInParty.add(section("Create a party"));
		createName.setToolTipText("Party name, e.g. Raid Night");
		notInParty.add(field(createName));
		JButton createBtn = button("Create");
		createBtn.setBackground(ACCENT);
		createBtn.setForeground(Color.BLACK);
		createBtn.addActionListener(e -> commands.create(createName.getText()));
		notInParty.add(createBtn);
		notInParty.add(Box.createVerticalStrut(12));
		notInParty.add(section("Join a party"));
		joinCode.setToolTipText("Code from a friend, e.g. ABCD-EFGH");
		notInParty.add(field(joinCode));
		JButton joinBtn = button("Join");
		joinBtn.addActionListener(e -> commands.join(joinCode.getText()));
		joinCode.addActionListener(e -> commands.join(joinCode.getText()));
		notInParty.add(joinBtn);

		root.add(inParty);
		root.add(notInParty);
		root.add(Box.createVerticalStrut(8));
		status.setForeground(MUTED);
		status.setFont(FontManager.getRunescapeSmallFont());
		root.add(left(status));

		add(root, BorderLayout.NORTH);
		commands.setListener(() -> SwingUtilities.invokeLater(this::update));
		update();
	}

	/** Re-reads the command state. Always runs on the Swing thread. */
	public void update()
	{
		boolean configured = commands.isConfigured();
		notConfigured.setVisible(!configured);
		PartyCommands.PartyState s = commands.getState();
		inParty.setVisible(configured && s != null);
		notInParty.setVisible(configured && s == null);
		if (s != null)
		{
			nameLabel.setText(s.name);
			codeLabel.setText("Code: " + s.code);
			membersLabel.setText(s.members + (s.members == 1 ? " member" : " members"));
			lootLabel.setText("Loot: " + PartyCommands.fmtGp(s.totalValue) + " gp from " + s.drops + " drops");
			pauseBtn.setText(s.paused ? "Resume my loot" : "Pause my loot");
			overlayToggle.setSelected(config.partyOverlay());
			membersBox.removeAll();
			if (s.memberList.isEmpty())
			{
				membersBox.add(muted("Loading..."));
			}
			for (PartyCommands.Member m : s.memberList)
			{
				if (!m.active)
				{
					continue;
				}
				JPanel row = new JPanel(new BorderLayout());
				row.setOpaque(false);
				JLabel n = new JLabel(m.name + (m.paused ? " (paused)" : ""));
				n.setForeground(m.paused ? MUTED : Color.WHITE);
				n.setFont(FontManager.getRunescapeSmallFont());
				JLabel v = new JLabel(PartyCommands.fmtGp(m.value) + " gp" + (m.drops > 0 ? " / " + m.drops : ""));
				v.setForeground(m.value > 0 ? GOOD : MUTED);
				v.setFont(FontManager.getRunescapeSmallFont());
				row.add(n, BorderLayout.WEST);
				row.add(v, BorderLayout.EAST);
				row.setMaximumSize(new Dimension(Integer.MAX_VALUE, 18));
				membersBox.add(row);
			}
			lootBox.removeAll();
			if (s.recent.isEmpty())
			{
				lootBox.add(muted(s.memberList.isEmpty() ? "Loading..." : "No loot yet."));
			}
			for (PartyCommands.Loot l : s.recent)
			{
				JLabel line = new JLabel("<html><b>" + esc(l.player) + "</b> <font color='#8A94A8'>" + esc(l.source) + "</font><br>"
					+ esc(l.items) + " <font color='#34D399'>" + PartyCommands.fmtGp(l.value) + "</font></html>");
				line.setFont(FontManager.getRunescapeSmallFont());
				line.setForeground(Color.WHITE);
				line.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 0));
				lootBox.add(left(line));
			}
		}
		String m = commands.getLastMessage();
		if (m != null && !m.isEmpty())
		{
			setStatus(m);
		}
		revalidate();
		repaint();
	}

	private void setStatus(String text)
	{
		status.setText("<html>" + text.replace("<", "&lt;") + "</html>");
	}

	private static JLabel muted(String text)
	{
		JLabel l = new JLabel(text);
		l.setForeground(MUTED);
		l.setFont(FontManager.getRunescapeSmallFont());
		return l;
	}

	private static String esc(String s)
	{
		return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
	}

	private static JPanel left(java.awt.Component c)
	{
		JPanel p = new JPanel(new BorderLayout());
		p.setOpaque(false);
		p.add(c, BorderLayout.WEST);
		p.setMaximumSize(new Dimension(Integer.MAX_VALUE, c.getPreferredSize().height + 4));
		return p;
	}

	private static JLabel section(String text)
	{
		JLabel l = new JLabel(text.toUpperCase());
		l.setFont(FontManager.getRunescapeSmallFont());
		l.setForeground(MUTED);
		l.setBorder(BorderFactory.createEmptyBorder(0, 0, 4, 0));
		return l;
	}

	private static JTextField field(JTextField f)
	{
		f.setMaximumSize(new Dimension(Integer.MAX_VALUE, 28));
		f.setBackground(ColorScheme.DARKER_GRAY_COLOR);
		f.setForeground(Color.WHITE);
		f.setCaretColor(Color.WHITE);
		f.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
		return f;
	}

	private static JButton button(String text)
	{
		JButton b = new JButton(text);
		b.setFocusPainted(false);
		b.setFont(FontManager.getRunescapeSmallFont());
		return b;
	}

	private static void copy(String text)
	{
		try
		{
			Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(text), null);
		}
		catch (RuntimeException ignored)
		{
			// clipboard unavailable
		}
	}
}
