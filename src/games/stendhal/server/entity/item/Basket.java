package games.stendhal.server.entity.item;

import games.stendhal.common.Grammar;
import games.stendhal.common.Rand;
import games.stendhal.server.StendhalRPWorld;
import games.stendhal.server.entity.Entity;
import games.stendhal.server.entity.item.Box;
import games.stendhal.server.entity.RPEntity;
import games.stendhal.server.entity.player.Player;
import games.stendhal.server.events.UseListener;

import java.util.Map;


import org.apache.log4j.Logger;
import marauroa.common.game.RPObject;

/**
 * a basket which can be unwrapped.
 *
 * @author kymara
 */
public class Basket extends Box implements UseListener {

	private Logger logger = Logger.getLogger(Basket.class);

	// TODO: Make these configurable
        // for easter presents
        private static final String[] ITEMS = { "greater_potion", "pie", "sandwich", "cherry", "blue_elf_cloak",
						"home_scroll" };


	/**
	 * Creates a new Basket
	 *
	 * @param name
	 * @param clazz
	 * @param subclass
	 * @param attributes
	 */
	public Basket(String name, String clazz, String subclass, Map<String, String> attributes) {
		super(name, clazz, subclass, attributes);
	}

	/**
	 * copy constructor
	 *
	 * @param item item to copy
	 */
	public Basket(Basket item) {
		super(item);
	}

       @Override
	protected boolean useMe(Player player) {
	    this.removeOne();
	    String itemName;
	    if (Rand.roll1D20() == 1) {
		itemName = "easter_egg";
	    } else {
		itemName = ITEMS[Rand.rand(ITEMS.length)];
	    }
	    Item item = StendhalRPWorld.get().getRuleManager().getEntityManager().getItem(itemName);
	    if (itemName == "easter_egg") {
		item.setBoundTo(player.getName());
	    }
	    player.sendPrivateText("Congratulations, you've got " + Grammar.a_noun(itemName));
	    player.equip(item, true);
	    player.notifyWorldAboutChanges();
	    return true;
       }

}
