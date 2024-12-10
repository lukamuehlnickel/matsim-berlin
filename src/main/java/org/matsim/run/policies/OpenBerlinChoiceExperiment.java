package org.matsim.run.policies;

import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.application.MATSimApplication;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.run.OpenBerlinScenario;
import picocli.CommandLine;

/**
 * This class can be used to run some synthetic choice experiments on the OpenBerlin scenario.
 */
public class OpenBerlinChoiceExperiment extends OpenBerlinScenario {

	@CommandLine.Option(names = "--bike-speed-factor", description = "Speed factor for bikes", defaultValue = "1.0")
	private double bikeSpeedFactor = 1.0;

	public static void main(String[] args) {
		MATSimApplication.execute(OpenBerlinChoiceExperiment.class, args);
	}

	@Override
	protected void prepareScenario(Scenario scenario) {

		super.prepareScenario(scenario);

		// If bike speed is adjusted, we need to remove all bike routes and travel times
		// These times will be recalculated by the router
		if (bikeSpeedFactor != 1.0) {
			for (Person person : scenario.getPopulation().getPersons().values()) {
				for (Plan plan : person.getPlans()) {
					for (Leg leg : TripStructureUtils.getLegs(plan)) {
						if (leg.getMode().equals(TransportMode.bike)) {
							leg.setRoute(null);
							leg.setTravelTimeUndefined();
						}
					}
				}
			}
		}
	}
}
