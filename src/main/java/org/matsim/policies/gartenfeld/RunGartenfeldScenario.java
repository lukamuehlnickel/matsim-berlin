package org.matsim.policies.gartenfeld;

import org.matsim.application.MATSimApplication;

/**
 * Run class for the Gartenfeld scenario.
 */
public final class RunGartenfeldScenario {

	private RunGartenfeldScenario() {
	}

	public static void main(String[] args) {
		MATSimApplication.runWithDefaults(GartenfeldScenario.class, args,
			"--parking-garages","ONE_LINK",
			"--config:network.inputChangeEventsFile", "../../input/gartenfeld/gartenfeld-v6.4.network-change-events.xml.gz",
			"--config:facilities.inputFacilitiesFile", "../../input/v6.4/berlin-v6.4-facilities.xml.gz",
			"--config:controller.runId", "gartenfeld-v6.4.cutout-oneLinkGarage-1pct",
			"--config:network.inputNetworkFile", "../../input/gartenfeld/gartenfeld-v6.4.network-cutout.xml.gz",
			"--config:plans.inputPlansFile", "../../input/gartenfeld/gartenfeld-v6.4.population-cutout-1pct.xml.gz",
			"--config:controller.lastIteration", "200",
			"--config:controller.outputDirectory", "output/gartenfeld-v6.4.cutout-oneLinkGarage-1pct/"
			);
	}

}
