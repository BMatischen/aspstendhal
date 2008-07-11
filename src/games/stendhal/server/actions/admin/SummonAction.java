package games.stendhal.server.actions.admin;

import static games.stendhal.server.actions.WellKnownActionConstants.X;
import static games.stendhal.server.actions.WellKnownActionConstants.Y;
import games.stendhal.common.Grammar;
import games.stendhal.server.actions.CommandCenter;
import games.stendhal.server.core.engine.SingletonRepository;
import games.stendhal.server.core.engine.StendhalRPZone;
import games.stendhal.server.core.rp.StendhalRPAction;
import games.stendhal.server.core.rule.EntityManager;
import games.stendhal.server.entity.Entity;
import games.stendhal.server.entity.creature.BabyDragon;
import games.stendhal.server.entity.creature.Cat;
import games.stendhal.server.entity.creature.Creature;
import games.stendhal.server.entity.creature.RaidCreature;
import games.stendhal.server.entity.creature.Sheep;
import games.stendhal.server.entity.mapstuff.portal.Gate;
import games.stendhal.server.entity.player.Player;
import marauroa.common.game.RPAction;

public class SummonAction extends AdministrationAction {
	private static final String USAGE = "Usage: /summon <whatToSummon> [<x> <y>]";
	private static final String _CREATURE = "creature";
	private static final String _SUMMON = "summon";

	public static void register() {
		CommandCenter.register(_SUMMON, new SummonAction(), 800);
	}

	/**
	 * Inline class to create entities of all creature types including pets.
	 */
	abstract static class EntityFactory {
		final Player player;
		final EntityManager manager = SingletonRepository.getEntityManager();

		protected boolean searching = true;

		public EntityFactory(Player player) {
			this.player = player;
		}

        boolean isSearching() {
	        return searching;
        }

		abstract void found(String type, Entity entity);
		abstract void error(String message);

		/**
		 * Create the named entity (creature, pet or sheep) of type 'type'.
		 * 
		 * @param type
		 */
		private void createEntity(String type) {
		    Entity entity = manager.getEntity(type);

		    if (entity != null) {
				found(type, entity);
			} else if ("cat".equals(type)) {
				if (player.hasPet()) {
					error("You already own a pet!");
				} else {
					Cat cat = new Cat(player);
					found(type, cat);
				}
			} else if ("baby dragon".equals(type)) {
				if (player.hasPet()) {
					error("You already own a pet!");
				} else {
					BabyDragon dragon = new BabyDragon(player);
					found(type, dragon);
				}
			} else if ("sheep".equals(type)) {
				if (player.hasSheep()) {
					error("You already own a sheep!");
				} else {
					Sheep sheep = new Sheep(player);
					found(type, sheep);
				}
			} 
	    }
	};

	@Override
	public void perform(final Player player, final RPAction action) {
		if("gate".equals(action.get(_CREATURE))){
			Gate gate = new Gate();
			gate.setPosition(action.getInt(X),action.getInt(Y));
			player.getZone().add(gate);
			return;
		}
		System.out.println(action);
		try {
			if (action.has(_CREATURE) && action.has(X) && action.has(Y)) {
				final StendhalRPZone zone = player.getZone();
				final int x = action.getInt(X);
				final int y = action.getInt(Y);
				
				if (!zone.collides(player, x, y)) {
					EntityFactory factory = new EntityFactory(player) {
						@Override
						void found(String type, Entity entity) {
							StendhalRPAction.placeat(zone, entity, x, y);

    						if (manager.isCreature(type)) {
    							entity = new RaidCreature((Creature) entity);
    						}

    						SingletonRepository.getRuleProcessor().addGameEvent(player.getName(), _SUMMON, type);

    						// We found what we are searching for.
    						searching = false;
						}

						@Override
						void error(String message) {
							player.sendPrivateText(message);

							// Stop searching because of an error.
							searching = false;
						}
					};

					String typeName = action.get(_CREATURE);
					String type = typeName;
					
					
					factory.createEntity(type);

					if (factory.isSearching()) {
    					// see it the name was in plural
						type = Grammar.singular(typeName);

						factory.createEntity(type);

						// Did we still not find any matching class?
						if (factory.isSearching()) {
    						logger.info("onSummon: Entity \"" + type + "\" not found.");
    						factory.error("onSummon: Entity \"" + type + "\" not found.");
						}
					}
				}
			}
		} catch (NumberFormatException e) {
			player.sendPrivateText(USAGE);
		}
	}

}
