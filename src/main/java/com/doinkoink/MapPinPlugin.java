package com.doinkoink;

import com.doinkoink.messages.MapPin;
import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.MenuEntry;
import net.runelite.api.Point;
import net.runelite.api.RenderOverview;
import net.runelite.api.annotations.Interface;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.MenuOpened;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.party.PartyService;
import net.runelite.client.party.WSClient;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.worldmap.WorldMapOverlay;
import net.runelite.client.ui.overlay.worldmap.WorldMapPoint;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;
import net.runelite.client.util.ImageUtil;

import javax.inject.Inject;
import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.HashMap;

@Slf4j
@PluginDescriptor(
	name = "Party World Map Ping"
)
public class MapPinPlugin extends Plugin
{
	@Inject
	private Client client;

	@Inject
	private MapPinConfig config;

	@Inject
	private WSClient wsClient;

	@Inject
	private PartyService partyService;

	@Inject
	private WorldMapOverlay worldMapOverlay;

	@Inject
	private WorldMapPointManager worldMapPointManager;

	public HashMap<String, WorldMapPoint> markers = new HashMap<>();

	private static final BufferedImage MAP_PIN = ImageUtil.getResourceStreamFromClass(MapPinPlugin.class, "/map_pin.png");

	private Point mousePosOnMenuOpened;

	@Override
	protected void startUp() throws Exception
	{
		wsClient.registerMessage(MapPin.class);
	}

	@Override
	protected void shutDown() throws Exception
	{
		wsClient.unregisterMessage(MapPin.class);
	}

	@Subscribe
	public void onMenuOpened(final MenuOpened event) {
		mousePosOnMenuOpened = client.getMouseCanvasPosition();

		final Widget map = client.getWidget(ComponentID.WORLD_MAP_MAPVIEW);

		if (map == null) {
			return;
		}

		WorldPoint target = calculateMapPoint(mousePosOnMenuOpened);

		if (map.getBounds().contains(client.getMouseCanvasPosition().getX(), client.getMouseCanvasPosition().getY())) {
			final MenuEntry[] entries = event.getMenuEntries();

			client.createMenuEntry(0)
				.setOption("Send")
				.setTarget("<col=ff9040>Pin</col>")
				.onClick(e -> {
					final MapPin pin = new MapPin(target, client.getLocalPlayer().getName(), config.mapPinColor());

					if (partyService.isInParty()) {
						partyService.send(pin);
					} else {
						setTarget(pin);
					}
				}
			);

			for(MenuEntry entry : entries) {
				if (entry.getTarget().contains("Pin")) {
					String pinOwner = entry.getTarget().split("'")[0].split(">")[1];

					client.createMenuEntry(0)
						.setOption("Remove")
						.setTarget(entry.getTarget())
						.onClick(e -> {
							if (markers.containsKey(pinOwner)) {
								worldMapPointManager.removeIf(x -> x == markers.get(pinOwner));
								markers.put(pinOwner, null);
							}
						}
					);
				}
			}
		}


	}

	@Subscribe
	public void onMapPin(MapPin mapPin) {
		setTarget(mapPin);
	}

	private void setTarget(MapPin pin) {
		if (!markers.containsKey(pin.getMember())) {
			markers.put(pin.getMember(), null);
		}

		WorldMapPoint marker = markers.get(pin.getMember());

		WorldMapPoint finalMarker = marker;
		worldMapPointManager.removeIf(x -> x == finalMarker);

		marker = new WorldMapPoint(pin.getMapPoint(), changeColor(MAP_PIN, pin.getPinColor()));
		marker.setImagePoint(new Point(24, 48));
		marker.setName(pin.getMember() + "'s Pin");
		marker.setTarget(marker.getWorldPoint());
		marker.setJumpOnClick(true);
		marker.setSnapToEdge(true);

		worldMapPointManager.add(marker);
		markers.put(pin.getMember(), marker);
	}

	public static BufferedImage changeColor(BufferedImage image, Color replacement_color) {

		BufferedImage dimg = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_ARGB);
		Graphics2D g = dimg.createGraphics();
		g.setComposite(AlphaComposite.Src);
		g.drawImage(image, null, 0, 0);//w  w w  .ja  v a 2  s . co m
		g.dispose();
		for (int i = 0; i < dimg.getHeight(); i++) {
			for (int j = 0; j < dimg.getWidth(); j++) {
				int argb = dimg.getRGB(j, i);
				int alpha = (argb >> 24) & 0xff;
				if (alpha > 0) {
					Color col = new Color(replacement_color.getRed(), replacement_color.getGreen(), replacement_color.getBlue(), alpha);
					dimg.setRGB(j, i, col.getRGB());
				}
			}
		}
		return dimg;
	}

	private WorldPoint calculateMapPoint(Point point) {
		float zoom = client.getRenderOverview().getWorldMapZoom();
		RenderOverview renderOverview = client.getRenderOverview();
		final WorldPoint mapPoint = new WorldPoint(renderOverview.getWorldMapPosition().getX(), renderOverview.getWorldMapPosition().getY(), 0);
		final Point middle = worldMapOverlay.mapWorldPointToGraphicsPoint(mapPoint);

		final int dx = (int) ((point.getX() - middle.getX()) / zoom);
		final int dy = (int) ((-(point.getY() - middle.getY())) / zoom);

		return mapPoint.dx(dx).dy(dy);
	}

	@Provides
	MapPinConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(MapPinConfig.class);
	}
}
