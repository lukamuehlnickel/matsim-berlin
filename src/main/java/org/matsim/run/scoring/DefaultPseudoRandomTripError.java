package org.matsim.run.scoring;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;

/**
 * Computes a random seed based on person id, previous activity and routing mode.
 */
public final class DefaultPseudoRandomTripError implements PseudoRandomTripError {

	@Override
	public long getSeed(Id<Person> personId, String routingMode, String prevActivityType) {

		int personHash = personId.toString().hashCode();

		int modeHash = routingMode.hashCode();
		int modeAndActHash = 31 * modeHash + prevActivityType.hashCode();

		// Combine two integers to long
		return (long) personHash << 32 | modeAndActHash & 0xFFFFFFFFL;
	}
}
