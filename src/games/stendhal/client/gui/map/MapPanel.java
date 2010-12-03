/* $Id$ */
/***************************************************************************
 *                   (C) Copyright 2003-2010 - Stendhal                    *
 ***************************************************************************
 ***************************************************************************
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *                                                                         *
 ***************************************************************************/
package games.stendhal.client.gui.map;

import games.stendhal.client.StendhalClient;
import games.stendhal.client.entity.DomesticAnimal;
import games.stendhal.client.entity.EntityChangeListener;
import games.stendhal.client.entity.HousePortal;
import games.stendhal.client.entity.IEntity;
import games.stendhal.client.entity.Player;
import games.stendhal.client.entity.Portal;
import games.stendhal.client.entity.RPEntity;
import games.stendhal.client.entity.User;
import games.stendhal.client.entity.WalkBlocker;
import games.stendhal.client.listener.PositionChangeListener;
import games.stendhal.common.CollisionDetection;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.GraphicsConfiguration;
import java.awt.Image;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.Rectangle2D;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.swing.JComponent;
import javax.swing.SwingUtilities;

import marauroa.common.game.RPAction;

public class MapPanel extends JComponent implements PositionChangeListener {
	/**
	 * serial version uid
	 */
	private static final long serialVersionUID = -6471592733173102868L;

	/**
	 * The color of the background (palest grey).
	 */
	private static final Color COLOR_BACKGROUND = new Color(0.8f, 0.8f, 0.8f);
	/**
	 * The color of blocked areas (red).
	 */
	private static final Color COLOR_BLOCKED = new Color(1.0f, 0.0f, 0.0f);
	/**
	 * The color of protected areas (palest green).
	 */
	private static final Color COLOR_PROTECTION = new Color(202, 230, 202);
	/**
	 * The color of other players (white).
	 */
	
	private final Map<IEntity, MapObject> mapObjects = new ConcurrentHashMap<IEntity, MapObject>();
	
	
	/** width of the minimap. */
	@SuppressWarnings("hiding") // from ImageObserver
	private static final int WIDTH = 128;
	/** height of the minimap. */
	@SuppressWarnings("hiding")
	private static final int HEIGHT = 128;
	/** height of the title */
	private static final int TITLE_HEIGHT = 15;
	/** Minimum scale of the map; the minimum size of one tile in pixels */
	private static final int MINIMUM_SCALE = 2;
	
	private StendhalClient client;
	/**
	 * The player X coordinate.
	 */
	private double playerX;
	/**
	 * The player Y coordinate.
	 */
	private double playerY;
	private int xOffset;
	private int yOffset;

	private static final boolean supermanMode = (System.getProperty("stendhal.superman") != null);
	
	private int width;
	private int height;
	private int scale;
	
	/** true iff the map should be repainted */ 
	private boolean needsRefresh;
	private Image mapImage; 
	
	/** Name of the map */
	private String title = "";
	
	/**
	 * Create a new MapPanel.
	 * 
	 * @param client
	 */
	public MapPanel(final StendhalClient client) {
		this.client = client;
		// black area outside the map
		setBackground(Color.black);
		updateSize(new Dimension(WIDTH, HEIGHT + TITLE_HEIGHT));
		setOpaque(true);
		
		// handle clicks for moving.
		this.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseClicked(final MouseEvent e) {
				movePlayer(e.getPoint(), e.getClickCount() > 1);
			}
		});
	}
	
	/**
	 * Add an entity to the map, if it should be displayed to the user. This
	 * method is thread safe.
	 * 
	 * @param entity the added entity
	 */
	protected void addEntity(final IEntity entity) {
		MapObject object = null;
		
		if (entity instanceof User) {
			entity.addChangeListener(new EntityChangeListener() {
				public void entityChanged(final IEntity entity, final Object property) {
					if (property == IEntity.PROP_POSITION) {
						positionChanged(entity.getX(), entity.getY());
					}
				}
			});
			positionChanged(entity.getX(), entity.getY());
			
			object = new PlayerMapObject(entity);
		} else if (entity instanceof Player) {
			object = new PlayerMapObject(entity);
		} else if (entity instanceof Portal) {
			final Portal portal = (Portal) entity;

			if (!portal.isHidden()) {
				mapObjects.put(entity, new PortalMapObject(entity));
			}
		} else if (entity instanceof HousePortal) {
			object = new PortalMapObject(entity);
		} else if (entity instanceof WalkBlocker) {
			object = new WalkBlockerMapObject(entity);
		} else if (entity instanceof DomesticAnimal) {
			// Only own pets and sheep are drawn but this is checked in the map object so the user status is always up to date
			object = new DomesticAnimalMapObject((DomesticAnimal)entity);
		} else if (supermanMode && User.isAdmin()) {
			if (entity instanceof RPEntity) {
				object = new RPEntityMapObject(entity);
			} else {
				object = new MovingMapObject(entity);
			}
		}
		
		if (object != null) {
			mapObjects.put(entity, object);
			needsRefresh = true;
			
			// changes to objects that should trigger a refresh
			if (object instanceof MovingMapObject) {
				entity.addChangeListener(new EntityChangeListener() {
					public void entityChanged(final IEntity entity, final Object property) {
						if ((property == IEntity.PROP_POSITION) 
								|| (property == RPEntity.PROP_GHOSTMODE)) {
							needsRefresh = true;
						}
					}
				});
			}
		}
	}
	
	/**
	 * Remove an entity from the map. This method is thread safe.
	 * 
	 * @param entity the entity to be removed
	 */
	protected void removeEntity(final IEntity entity) {
		if (mapObjects.containsKey(entity)) {
			mapObjects.remove(entity);
			needsRefresh = true;
		}
	}
	
	@Override
	public void paintComponent(final Graphics g) {
		needsRefresh = false;
		
		g.setColor(getBackground());
		g.fillRect(0, 0, getWidth(), getHeight());
		drawTitle(g);
		// The rest of the things should be drawn inside the actual map area
		g.clipRect(0, 0, width, height);
		// also choose the origin so that we can simply draw to the 
		// normal coordinates
		g.translate(-xOffset, -yOffset);
		
		drawMap(g);
		drawEntities(g);
		
		g.dispose();
	}
	
	/**
	 * Draw the entities on the map.
	 *  
	 * @param g The graphics context
	 */
	private void drawEntities(final Graphics g) {
		for (final MapObject object : mapObjects.values()) {
			object.draw(g, scale);
		}
	}
	
	/**
	 * Set the dimensions of the component.
	 *  
	 * @param dim the new dimensions
	 */
	private void updateSize(final Dimension dim) {
		setMaximumSize(dim);
		setMinimumSize(new Dimension(0, dim.height));
		setPreferredSize(dim);
		// the user may have hidden the component partly or entirely
		setSize(getWidth(), dim.height);
				                                
		revalidate();
	}
	
	/**
	 * Draw the map background.
	 * 
	 * @param g The graphics context
	 */
	private void drawMap(final Graphics g) {
		g.drawImage(mapImage, 0, 0, null);
	}
	
	/**
	 * Draw the map title.
	 * 
	 * @param g The graphics context
	 */
	private void drawTitle(final Graphics g) {
		final Rectangle bounds = g.getClipBounds();
		
		// draw only if drawing the title area is requested
		if (bounds.y + bounds.height > height) {
			final Rectangle2D rect = g.getFontMetrics().getStringBounds(title, g);
			g.setColor(Color.white);
			g.drawString(title, Math.max(0, (width - (int) rect.getWidth()) / 2), height + TITLE_HEIGHT - 3);
		}
	}
	
	/**
	 * The player's position changed.
	 * 
	 * @param x
	 *            The X coordinate (in world units).
	 * @param y
	 *            The Y coordinate (in world units).
	 */
	public void positionChanged(final double x, final double y) {
		/*
		 * The client gets occasionally spurious events.
		 * Suppress repainting unless the position actually changed
		 */
		if ((playerX != x) || (playerY != y)) {
			playerX = x;
			playerY = y;

			updateView();
		}
	}
	
	/**
	 * Redraw the map area. To be called from the game loop.
	 */
	public void refresh() {
		if (needsRefresh) {
			repaint(0, 0, width, height);
		}
	}
	
	@Override
	public void paintImmediately(int x, int y, int w, int h) {
		/*
		 * Try to keep the view screen while the user is switching maps.
		 * 
		 * NOTE: Relies on the repaint() requests to eventually come to
		 * this, so if swing internals change some time in the future,
		 * a new solution may be needed.
		 */
		if (StendhalClient.get().tryAcquireDrawingSemaphore()) {
			try {
				super.paintImmediately(x, y, w, h);
			} finally {
				StendhalClient.get().releaseDrawingSemaphore();
			}
		}
	}
	
	/**
	 * Update the view pan. This should be done when the map size or player
	 * position changes.
	 */
	private void updateView() {
		xOffset = 0;
		yOffset = 0;

		if (mapImage == null) {
			return;
		}

		final int imageWidth = mapImage.getWidth(null);
		final int imageHeight = mapImage.getHeight(null);

		final int xpos = (int) ((playerX * scale) + 0.5) - width / 2;
		final int ypos = (int) ((playerY * scale) + 0.5) - width / 2;

		if (imageWidth > width) {
			// need to pan width
			if ((xpos + width) > imageWidth) {
				// x is at the screen border
				xOffset = imageWidth - width;
			} else if (xpos > 0) {
				xOffset = xpos;
			}
		}

		if (imageHeight > height) {
			// need to pan height
			if ((ypos + height) > imageHeight) {
				// y is at the screen border
				yOffset = imageHeight - height;
			} else if (ypos > 0) {
				yOffset = ypos;
			}
		}
	}
	
	/**
	 * Update the map with new data.
	 * 
	 * @param cd
	 *            The collision map.
	 * @param pd  
	 *      	  The protection map.
	 * @param gc
	 *            A graphics configuration.
	 * @param zone
	 *            The zone name.
	 */
	public void update(final CollisionDetection cd, final CollisionDetection pd, final GraphicsConfiguration gc,
			final String zone) {
		title = zone;

		// calculate the size and scale of the map
		final int mapWidth = cd.getWidth();
		final int mapHeight = cd.getHeight();
		scale = Math.max(MINIMUM_SCALE, Math.min(HEIGHT / mapHeight, WIDTH / mapWidth));
		width = Math.min(WIDTH, mapWidth * scale);
		height = Math.min(HEIGHT, mapHeight * scale);
		
		// create the map image, and fill it with the wanted details
		mapImage = this.getGraphicsConfiguration().createCompatibleImage(mapWidth * scale, mapHeight * scale);
		final Graphics g = mapImage.getGraphics();
		g.setColor(COLOR_BACKGROUND);
		g.fillRect(0, 0, mapWidth * scale, mapHeight * scale);
		
		for (int x = 0; x < mapWidth; x++) {
			for (int y = 0; y < mapHeight; y++) {
				if (!cd.walkable(x, y)) {
					g.setColor(COLOR_BLOCKED);
					g.fillRect(x * scale, y * scale, scale, scale);
				} else if (pd != null && !pd.walkable(x, y)) {
					// draw protection only if there is no collision to draw
					g.setColor(COLOR_PROTECTION);
					g.fillRect(x * scale, y * scale, scale, scale);
				}
			}
		}
		g.dispose();
		
		SwingUtilities.invokeLater(new Runnable() {
			public void run() {
				updateSize(new Dimension(WIDTH, height + TITLE_HEIGHT));
			}
		});
		
		updateView();
		
		repaint();
	}
	
	/**
	 * Tell the player to move to point p
	 * 
	 * @param p the point
	 * @param doubleClick <code>true</code> if the movement was requested with
	 * 	a double click, <code>false</code> otherwise
	 */
	private void movePlayer(final Point p, boolean doubleClick) {
		// Ignore clicks to the title area 
		if (p.y <= height) {
			final RPAction action = new RPAction();
			action.put("type", "moveto");
			action.put("x", (p.x + xOffset) / scale);
			action.put("y", (p.y + yOffset) / scale);
			if (doubleClick) {
				action.put("double_click", "");
			}
			client.send(action);
		}
	}
}
