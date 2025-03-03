package org.matsim.policies.gartenfeld;

import org.matsim.application.MATSimApplication;
import org.matsim.run.OpenBerlinScenario;

/**
 * Run class for the Gartenfeld scenario.
 */
public final class RunGartenfeldScenario {

	private RunGartenfeldScenario() {
	}

	public static void main(String[] args) {
		MATSimApplication.runWithDefaults(OpenBerlinScenario.class, args,
			"--config:network.inputChangeEventsFile", "../../input/gartenfeld/gartenfeld-v6.4.network-change-events.xml.gz",
			"--config:network.timeVariantNetwork", "true",
			"--config:facilities.inputFacilitiesFile", "../../input/gartenfeld/gartenfeld-v6.4.facilities.xml.gz",
			"--config:controller.runId", "gartenfeld-v6.4.cutout-base-1pct",
			"--config:network.inputNetworkFile", "../../input/gartenfeld/gartenfeld-v6.4.network-cutout.xml.gz",
			"--config:plans.inputPlansFile", "../../input/gartenfeld/gartenfeld-v6.4.population-cutout-1pct.xml.gz",
			"--config:controller.lastIteration", "500",
			"--config:controller.outputDirectory", "output/gartenfeld-v6.4.cutout-base-1pct/"
			);
	}

}
