package org.matsim.policies.gartenfeld;

import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.application.MATSimApplication;
import org.matsim.application.options.ShpOptions;
import org.matsim.application.prepare.population.PersonNetworkLinkCheck;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.AbstractModule;
import org.matsim.core.controler.Controler;
import org.matsim.core.network.algorithms.MultimodalNetworkCleaner;
import org.matsim.core.population.algorithms.ParallelPersonAlgorithmUtils;
import org.matsim.core.router.MultimodalLinkChooser;
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

	@CommandLine.Option(names = "--gartenfeld-config", description = "Path to configuration for Gartenfeld.", defaultValue = "input/gartenfeld/gartenfeld.config.xml")
	private String gartenFeldConfig;

	@CommandLine.Option(names = "--gartenfeld-shp", description = "Path to configuration for Gartenfeld.", defaultValue = "input/gartenfeld/DNG_area.gpkg")
	private String gartenFeldArea;

	@CommandLine.Option(names = "--parking-garages", description = "Enable parking garages.", defaultValue = "NO_GARAGE")
	private GarageType garageType = GarageType.NO_GARAGE;

	public static void main(String[] args) {
		MATSimApplication.run(GartenfeldScenario.class, args);
	}

	@Override
	protected Config prepareConfig(Config config) {

		// Load the Gartenfeld specific part into the standard Berlin config
		ConfigUtils.loadConfig(config, gartenFeldConfig);

		// needs to be after load.config
		super.prepareConfig(config);

		return config;
	}

	@Override
	protected void prepareScenario(Scenario scenario) {

		super.prepareScenario(scenario);

		Network network = scenario.getNetwork();
		Set<String> removeModes = Set.of(TransportMode.car, TransportMode.truck, "freight", TransportMode.ride);

		if (garageType != GarageType.NO_GARAGE) {

			for (Link link : network.getLinks().values()) {

				// Make all links car free
				String linkId = link.getId().toString();

				if (linkId.startsWith("network-DNG")) {

					// First garage, a garage in any cases
					if (linkId.equals("network-DNG.1") || linkId.equals("network-DNG.1_r"))
						continue;

					Set<String> allowedModes = new HashSet<>(link.getAllowedModes());
					allowedModes.removeAll(removeModes);
					link.setAllowedModes(allowedModes);
				}
			}

			MultimodalNetworkCleaner cleaner = new MultimodalNetworkCleaner(network);
			removeModes.forEach(m -> cleaner.run(Set.of(m)));

			// Clean link ids that are not valid anymore
			ParallelPersonAlgorithmUtils.run(
				scenario.getPopulation(),
				Runtime.getRuntime().availableProcessors(),
				PersonNetworkLinkCheck.createPersonAlgorithm(network)
			);

		}
	}

	@Override
	protected void prepareControler(Controler controler) {

		super.prepareControler(controler);

		// Only with the car free area, the multimodal link chooser is needed
		if (garageType == GarageType.ONE_LINK)
			controler.addOverridingModule(new AbstractModule() {
				@Override
				public void install() {
					bind(MultimodalLinkChooser.class).toInstance(new GartenfeldLinkChooser(ShpOptions.ofLayer(gartenFeldArea, null)));
				}
			});
	}

	/**
	 * Enum for the different garage types and implicitly the car free areas.
	 */
	public enum GarageType {
		/**
		 * No garage, cars are allowed on all links.
		 */
		NO_GARAGE,
		/**
		 * One garage, cars are only allowed on the garage link at the entrance of the area.
		 */
		ONE_LINK
	}
}
