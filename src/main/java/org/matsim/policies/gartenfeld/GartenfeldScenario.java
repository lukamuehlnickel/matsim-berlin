package org.matsim.policies.gartenfeld;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.application.MATSimApplication;
import org.matsim.application.prepare.population.PersonNetworkLinkCheck;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.population.algorithms.ParallelPersonAlgorithmUtils;
import org.matsim.run.OpenBerlinScenario;
import picocli.CommandLine;

import java.util.HashSet;
import java.util.Set;

/**
 * Scenario class for Gartenfeld, extending the {@link OpenBerlinScenario}.
 * <p>
 * This scenario has its own files, which extend the OpenBerlin scenario files with inhabitants and road infrastructure specific to Gartenfeld.
 * See {@link org.matsim.prepare.gartenfeld.CreateGartenfeldComplete} for the creation of these files.
 */
public class GartenfeldScenario extends OpenBerlinScenario {

	@CommandLine.Option(names = "--gartenfeld-config", description = "Path to configuration for Gartenfeld.", defaultValue = "input/gartenfeld/gartenfeld.xml")
	private String gartenFeldConfig;

	@CommandLine.Option(names = "--parking-garages", description = "Enable parking garages.", defaultValue = "NO_GARAGE")
	private GarageType garageType = GarageType.NO_GARAGE;

	public static void main(String[] args) {
		MATSimApplication.run(GartenfeldScenario.class, args);
	}

	@Override
	protected Config prepareConfig(Config config) {

		// Load the Gartenfeld specific part into the standard Berlin config
		ConfigUtils.loadConfig(config, gartenFeldConfig);

		return config;
	}

	@Override
	protected void prepareScenario(Scenario scenario) {

		Network network = scenario.getNetwork();

		if (garageType != GarageType.NO_GARAGE) {

			for (Link link : network.getLinks().values()) {

				// Make all links car free
				String linkId = link.getId().toString();

				if (linkId.startsWith("network-DNG")) {

					// First garage, a garage in any cases
					if (linkId.startsWith("network-DNG.1"))
						continue;

					// Second garage link
					if ((garageType == GarageType.TWO_LINKS || garageType == GarageType.THREE_LINKS) &&
						linkId.startsWith("network-DNG.18"))
						continue;

					// Third garage link
					if ((garageType == GarageType.THREE_LINKS) &&
						linkId.startsWith("network-DNG.25"))
						continue;

					Set<String> allowedModes = new HashSet<>(link.getAllowedModes());
					allowedModes.remove(TransportMode.car);
					allowedModes.remove(TransportMode.truck);
					allowedModes.remove(TransportMode.ride);
					allowedModes.remove("freight");

					link.setAllowedModes(allowedModes);
				}
			}

			// Clean link ids that are not valid anymore
			ParallelPersonAlgorithmUtils.run(
				scenario.getPopulation(),
				Runtime.getRuntime().availableProcessors(),
				PersonNetworkLinkCheck.createPersonAlgorithm(network)
			);

		}
	}

	public enum GarageType {
		NO_GARAGE, ONE_LINK, TWO_LINKS, THREE_LINKS
	}
}
