package org.matsim.run.scoring;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.population.Person;

/**
 * Interface to provide pseudo-random scoring for a trip.*
 */
public interface PseudoRandomTripScore {

	/**
	 * Return a seed for a trip. The seed must be designed such that it is constant for the same choice situations.
	 */
	long getSeed(Id<Person> personId, String routingMode, String prevActivityType);


}
