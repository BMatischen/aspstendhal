/***************************************************************************
 *                    Copyright © 2003-2022 - Stendhal                     *
 ***************************************************************************
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU Affero General Public License as        *
 *   published by the Free Software Foundation; either version 3 of the    *
 *   License, or (at your option) any later version.                       *
 *                                                                         *
 ***************************************************************************/

declare var marauroa: any;
declare var stendhal: any;


export interface Sound extends HTMLAudioElement {
	hasplayed: boolean;
	basevolume: number;
}

export class SoundManager {

	private static instance: SoundManager;

	private layers: string[] = ["music", "ambient", "creature", "sfx", "gui"];
	private cache: {[source: string]: Sound} = {};
	private active: Sound[] = [];
	private activeLoops: Sound[] = [];


	static get(): SoundManager {
		if (!SoundManager.instance) {
			SoundManager.instance = new SoundManager();
		}
		return SoundManager.instance;
	}

	private constructor() {
		// do nothing
	}

	/**
	 * Retrieves finite active sounds.
	 */
	getActive(): Sound[] {
		return this.active;
	}

	/**
	 * Retrieves looping active sounds.
	 */
	getActiveLoops(): Sound[] {
		return this.activeLoops;
	}

	/**
	 * Initializes an audio object & loads into the cache.
	 *
	 * @param id
	 *     Identifier string used to retrieve from cache.
	 * @param filename
	 *     Path to sound file.
	 * @return
	 *     New audio object.
	 */
	private load(id: string, filename: string): Sound {
		const snd = <Sound> new Audio(filename);
		snd.autoplay = false;
		// load into cache
		this.cache[id] = snd;
		return snd;
	}

	/**
	 * Sets event handlers for when sound finishes.
	 *
	 * @param sound
	 *     The playing sound.
	 */
	private onSoundAdded(sound: Sound) {
		sound.onended = (e) => {
			if (!sound.loop) {
				// remove from active sounds
				const idx = this.active.indexOf(sound);
				if (idx > -1) {
					this.active.splice(idx, 1);
				}
			}
		};

		if (sound.loop) {
			// FIXME: should be removed from this list when destroyed or
			//        preserved if sound continues on map change
			this.activeLoops.push(sound);
		} else {
			this.active.push(sound);
		}
	}

	/**
	 * Plays a sound.
	 *
	 * @param soundName
	 *     Sound file basename.
	 * @param volume
	 *     Volume level between 0.0 and 1.0.
	 * @param loop
	 *     Whether or not sound should be looped.
	 * @return
	 *     The new sound instance.
	 */
	private playEffect(soundName: string, volume=1.0, loop=false): Sound {
		// check volume sanity
		if (volume > 1) {
			volume = 1.0;
		} else if (volume < 0) {
			volume = 0.0;
		}

		// check cache first
		let sound = this.cache[soundName];
		if (sound) {
			// TODO: handle HTMLAudioElement.error
			if (!sound.paused && !sound.ended) {
				// create a temporary sound instead of interrupting the cached one
				const addSound = <Sound> new Audio(sound.src);
				addSound.autoplay = true;
				addSound.basevolume = sound.basevolume;
				addSound.volume = addSound.basevolume;
				this.onSoundAdded(addSound);
				return addSound;
			}
		} else {
			// add new sound to cache
			sound = this.load(soundName,
					stendhal.paths.sounds + "/" + soundName + ".ogg");
		}

		sound.autoplay = true;
		sound.basevolume = volume;
		sound.volume = sound.basevolume;
		sound.loop = loop;
		if (!stendhal.config.getBoolean("ui.sound")) {
			sound.muted = true;
		}

		// must be started manually if autoplay has already ocurred
		if (sound.hasplayed) {
			if (!sound.paused && !sound.ended) {
				sound.pause();
			}
			sound.currentTime = 0;
			sound.play();
		}

		sound.hasplayed = true;
		this.onSoundAdded(sound);
		return sound;
	}

	/**
	 * Plays a sound with volume relative to distance.
	 *
	 * @param x
	 *     X coordinate of sound source.
	 * @param y
	 *     Y coordinate of sound source.
	 * @param radius
	 *     Radius at which sound can be heard.
	 * @param layer
	 *     Channel on which to be played (currently not supported).
	 * @param soundName
	 *     Sound file basename.
	 * @param volume
	 *     Volume level between 0.0 and 1.0.
	 * @param loop
	 *     Whether or not sound should be looped.
	 * @return
	 *     The new sound instance.
	 */
	playLocalizedEffect(x: number, y: number, radius: number,
			layer: number, soundName: string, volume=1.0, loop=false): Sound {
		const snd = this.playEffect(soundName, volume, loop);
		// Further adjustments if the sound has a radius
		if (radius) {
			if (!marauroa.me || !x) {
				// can't calculate distance yet
				snd.volume = 0.0;
			} else {
				this.adjustForDistance(snd, radius, x, y, marauroa.me["_x"],
						marauroa.me["_y"]);
			}
		}

		return snd;
	}

	/**
	 * Plays a sound with uniform volume.
	 *
	 * @param soundName
	 *     Sound file basename.
	 * @param volume
	 *     Volume level between 0.0 and 1.0.
	 * @param loop
	 *     Whether or not sound should be looped.
	 * @return
	 *     The new sound instance.
	 */
	playGlobalizedEffect(soundName: string, volume=1.0, loop=false): Sound {
		return this.playEffect(soundName, volume, loop);
	}

	/**
	 * Loops a sound with volume relative to distance.
	 *
	 * @param x
	 *     X coordinate of sound source.
	 * @param y
	 *     Y coordinate of sound source.
	 * @param radius
	 *     Radius at which sound can be heard.
	 * @param layer
	 *     Channel on which to be played (currently not supported).
	 * @param soundName
	 *     Sound file basename.
	 * @param volume
	 *     Volume level between 0.0 and 1.0.
	 * @return
	 *     The new sound instance.
	 */
	playLocalizedLoop(x: number, y: number, radius: number, layer: number,
			soundName: string, volume=1.0): Sound {
		return this.playLocalizedEffect(x, y, radius, layer, soundName,
				volume, true);
	}

	/**
	 * Loops a sound with uniform volume.
	 *
	 * @param soundName
	 *     Sound file basename.
	 * @param volume
	 *     Volume level between 0.0 and 1.0.
	 * @return
	 *     The new sound instance.
	 */
	playGlobalizedLoop(soundName: string, volume=1.0): Sound {
		return this.playGlobalizedEffect(soundName, volume, true);
	}

	/**
	 * Loops a sound with volume relative to distance.
	 *
	 * @param x
	 *     X coordinate of sound source.
	 * @param y
	 *     Y coordinate of sound source.
	 * @param radius
	 *     Radius at which sound can be heard.
	 * @param layer
	 *     Channel on which to be played (currently not supported).
	 * @param musicName
	 *     Sound file basename.
	 * @param volume
	 *     Volume level between 0.0 and 1.0.
	 * @return
	 *     The new sound instance.
	 */
	playLocalizedMusic(x: number, y: number, radius: number,
			layer: number, musicName: string, volume=1.0): Sound {
		// load into cache so playEffect doesn't look in "data/sounds"
		if (!this.cache[musicName]) {
			this.load(musicName,
					stendhal.paths.music + "/" + musicName + ".ogg");
		}
		return this.playLocalizedLoop(x, y, radius, layer, musicName, volume);
	}

	/**
	 * Loops a sound with uniform volume.
	 *
	 * @param musicName
	 *     Sound file basename.
	 * @param volume
	 *     Volume level between 0.0 and 1.0.
	 * @return
	 *     The new sound instance.
	 */
	playGlobalizedMusic(musicName: string, volume=1.0): Sound {
		// load into cache so playEffect doesn't look in "data/sounds"
		if (!this.cache[musicName]) {
			this.load(musicName,
					stendhal.paths.music + "/" + musicName + ".ogg");
		}
		return this.playGlobalizedLoop(musicName, volume);
	}

	/**
	 * Stops a sound & removes it from active group.
	 *
	 * @param sound
	 *     The sound to be stopped.
	 * @param group
	 *     The group this sound is playing from.
	 * @return
	 *     <code>true</code> if succeeded.
	 */
	stop(sound: Sound, group: Sound[]=this.active): boolean {
		const idx = group.indexOf(sound);
		if (idx > -1) {
			sound.pause();
			sound.currentTime = 0;
			group.splice(idx, 1);
		}
		return group.indexOf(sound) < 0;
	}

	/**
	 * Stops a sound loop & removes it from active group.
	 *
	 * @param sound
	 *     The sound to be stopped.
	 * @return
	 *     <code>true</code> if succeeded.
	 */
	stopLoop(sound: Sound): boolean {
		return this.stop(sound, this.activeLoops);
	}

	/**
	 * Stops all currently playing sounds.
	 *
	 * @param clean
	 *     If <code>true</code>, removes loops instead of pausing.
	 * @return
	 *     <code>true</code> if all sounds were aborted or paused.
	 */
	stopAll(clean: boolean=false): boolean {
		let stopped = true;

		for (const snd of this.active) {
			this.stop(snd);
		}
		stopped = stopped && this.active.length == 0;

		if (clean) {
			for (const loop of this.activeLoops) {
				this.stop(loop);
			}
			stopped = stopped && this.activeLoops.length == 0;
		} else {
			for (let idx = this.activeLoops.length; idx >=0; idx--) {
				const loop = this.activeLoops[idx];
				if (loop) {
					loop.pause();
				}
				stopped = stopped && (!loop || loop.paused);
			}
		}

		return stopped;
	}

	/**
	 * Mutes all currently playing sounds.
	 *
	 * @return
	 *     <code>true</code> if all sounds were muted.
	 */
	muteAll(): boolean {
		// finite sounds are removed
		for (let idx = this.active.length; idx >= 0; idx--) {
			const snd = this.active[idx];
			// sound may have ended during this call
			if (snd) {
				this.stop(snd);
			}
		}

		let muted = true;
		for (const snd of this.activeLoops) {
			snd.muted = true;
			muted = muted && snd.muted;
		}

		return this.active.length == 0 && muted;
	}

	/**
	 * Unmutes all currently playing sounds.
	 *
	 * @return
	 *     <code>true</code> if all sounds were unmuted.
	 */
	unmuteAll(): boolean {
		let unmuted = true;
		for (const snd of this.activeLoops) {
			snd.muted = false;
			if (snd.paused) {
				snd.play();
			}
			unmuted = unmuted && !snd.paused && !snd.muted;
		}

		return unmuted;
	}

	/**
	 * Adjusts volume level relative to distance.
	 *
	 * FIXME: hearing distance is slightly further than in Java client
	 *        See: games.stendhal.client.sound.facade.Audible*Area
	 *
	 * @param snd
	 *    The sound to be adjusted.
	 * @param radius
	 *     Radius at which sound can be heard.
	 * @param sx
	 *     X coordinate of sound entity.
	 * @param sy
	 *     Y coordinate of sound entity.
	 * @param ex
	 *     X coordinate of listening entity.
	 * @param ey
	 *     Y coordinate of listening entity.
	 */
	adjustForDistance(snd: Sound, radius: number, sx: number, sy: number,
			ex: number, ey: number) {
		const xdist = ex - sx;
		const ydist = ey - sy;
		const dist2 = xdist * xdist + ydist * ydist;
		const rad2 = radius * radius;
		if (dist2 > rad2) {
			// outside the specified radius
			snd.volume = 0.0;
		} else {
			// The sound api does not guarantee anything about how the volume
			// works, so it does not matter much how we scale it.
			snd.volume = Math.min(rad2 / (dist2 * 20), snd.basevolume);
		}
	}

	/**
	 * Called at startup to pre-cache certain sounds.
	 */
	startupCache() {
		// login sound
		this.load("ui/login",
				stendhal.paths.sounds + "/ui/login.ogg");
	}
}