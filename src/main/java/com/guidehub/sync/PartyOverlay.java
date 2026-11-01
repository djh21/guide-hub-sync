package com.guidehub.sync;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/** Small corner panel: party name, members and the running total. Hidden when not in a party. */
public class PartyOverlay extends OverlayPanel
{
	private final PartyCommands commands;
	private final GuideHubSyncConfig config;

	public PartyOverlay(PartyCommands commands, GuideHubSyncConfig config)
	{
		this.commands = commands;
		this.config = config;
		setPosition(OverlayPosition.TOP_LEFT);
		setPreferredSize(new Dimension(170, 0));
	}

	@Override
	public Dimension render(Graphics2D g)
	{
		PartyCommands.PartyState s = commands.getState();
		if (s == null || !config.partyOverlay())
		{
			return null;
		}
		panelComponent.getChildren().clear();
		panelComponent.getChildren().add(TitleComponent.builder()
			.text(s.name)
			.color(new Color(0xFB, 0x92, 0x3C))
			.build());
		panelComponent.getChildren().add(LineComponent.builder()
			.left("Members").right(Integer.toString(s.members)).build());
		panelComponent.getChildren().add(LineComponent.builder()
			.left("Loot").right(PartyCommands.fmtGp(s.totalValue) + " gp").build());
		int shown = 0;
		for (PartyCommands.Member m : s.memberList)
		{
			if (!m.active || shown++ >= 5)
			{
				continue;
			}
			panelComponent.getChildren().add(LineComponent.builder()
				.left(" " + m.name).leftColor(m.paused ? Color.GRAY : Color.LIGHT_GRAY)
				.right(PartyCommands.fmtGp(m.value)).rightColor(m.value > 0 ? new Color(0x34, 0xD3, 0x99) : Color.GRAY).build());
		}
		if (s.paused)
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Paused").leftColor(Color.YELLOW).right("::party resume").build());
		}
		return super.render(g);
	}
}
