package games.stendhal.server.entity.mapstuff.office;

import games.stendhal.server.core.engine.StendhalRPZone;
import games.stendhal.server.entity.Entity;

import java.awt.Rectangle;
import java.awt.Shape;
import java.util.LinkedList;
import java.util.List;

import marauroa.common.game.RPObject;

/**
 * a list of storeable entities that can be accessed by a unique
 * identifier like a name.
 *
 * @author hendrik
 * @param <T> type of the storeable entities to be managed by this list
 */
abstract class StoreableEntityList<T extends Entity> {
	private StendhalRPZone zone;
	private Class<T> clazz;
	private Shape shape;

	/**
	 * creates a new StoreableEntityList
	 *
	 * @param zone  zone to store the entities in
	 * @param clazz class object of the entities to manage
	 */
	// the class object is needed, because generic type variables (T)
	// cannot be used in instanceof.
	public StoreableEntityList(StendhalRPZone zone, Class<T> clazz) {
		this.zone = zone;
		this.clazz = clazz;
	}

	public StoreableEntityList(StendhalRPZone zone, Shape shape, Class<T> clazz) {
	    this(zone, clazz);
	    this.shape = shape;
    }

	/**
     * Adds a storeable entity
     * 
     * @param entity storeable entity
     * @return true in case the entity was added successfully; 
     * 				false in case no free spot for it was found
     */
	public boolean add(T entity) {
		boolean success = calculatePosition(entity);
		if (!success) {
			return false;
		}
		zone.add(entity);
		zone.storeToDatabase();
		return true;
	}

	/**
	 * calculates a free spot to place this entity into.
	 *
	 * @param entity entity
	 * @return true, in case a spot was found or this entity should 
	 * 				not be place in the zone; false otherwise
	 */
	private boolean calculatePosition(T entity) {
		if (shape == null) {
			return true;
		}

		Rectangle rect = shape.getBounds();
		for (int x = rect.x; x < rect.x + rect.width; x++) {
			for (int y = rect.y; y < rect.y + rect.height; y++) {
				if (shape.contains(x, y)) {
					if (!zone.collides(entity, x, y)) {
						entity.setPosition(x, y);
						return true;
					}
				}
			}
		}

		return false;
    }

	/**
     * returns the storeable entity for the specified identifier
     * 
     * @param identifier name of entity
     * @return storeable entity or <code>null</code> in case there is none
     */
    public T getByName(String identifier) {
    	List<T> entities = getList();
    	for (T entity : entities) {
    		if (getName(entity).equals(identifier)) {
    			return entity;
    		}
    	}
    	return null;
    }

	/**
     * removes all storeable entities for this identifier
     * 
     * @param identifier name of entity
     */
    public void removeByName(String identifier) {
    	List<T> entities = getList();
    	for (T entity : entities) {
    		if (getName(entity).equals(identifier)) {
    			zone.remove(entity);
    		}
    	}
    	zone.storeToDatabase();
    }

	/**
     * gets a list of storeable entities from the zone storage. Note: This is only a
     * temporary snapshot, do not save it outside the scope of a method.
     * 
     * @return List of storeabe entities.
     */
    private List<T> getList() {
    	List<T> res = new LinkedList<T>();
    	for (RPObject object : zone) {
    		if (clazz.isInstance(object)) {
    			T entity = clazz.cast(object);
    			res.add(entity);
    		}
    	}
    	return res;
    }
   
    public abstract String getName(T entity);

}