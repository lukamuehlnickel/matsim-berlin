package org.matsim.prepare.gartenfeld;


import org.matsim.application.prepare.population.DownSamplePopulation;
import org.matsim.application.prepare.population.PersonNetworkLinkCheck;
import org.matsim.application.prepare.scenario.CreateScenarioCutOut;
import org.matsim.prepare.network.ModifyNetwork;
import org.matsim.run.OpenBerlinScenario;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Utility class to create all necessary input files for the Gartenfeld scenario.
 */
public final class CreateGartenfeldComplete {

	private CreateGartenfeldComplete() {
	}

	public static void main(String[] args) {

		String fullPopulation = "input/gartenfeld/gartenfeld-v6.4.population-full-10pct.xml.gz";
		if (!Files.exists(Path.of(fullPopulation))) {
			new CreateGartenfeldPopulation().execute(
					"--output", fullPopulation
			);
		}

		String population = "input/gartenfeld/gartenfeld-v6.4.population-cutout-10pct.xml.gz";
		String network = "input/gartenfeld/gartenfeld-v6.4.network-cutout.xml.gz";
		String fullNetwork = "input/gartenfeld/gartenfeld-v6.4.network.xml.gz";
		String berlinNetwork = "input/v%s/berlin-v%s-network-with-pt.xml.gz".formatted(OpenBerlinScenario.VERSION, OpenBerlinScenario.VERSION);

		new CreateScenarioCutOut().execute(
				"--network", berlinNetwork,
				"--population", fullPopulation,
				"--facilities", "input/v%s/berlin-v%s-facilities.xml.gz".formatted(OpenBerlinScenario.VERSION, OpenBerlinScenario.VERSION),
				"--events", "https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/de/berlin/berlin-v%s/output/berlin-v%s-10pct/berlin-v%s.output_events.xml.gz".formatted(OpenBerlinScenario.VERSION, OpenBerlinScenario.VERSION, OpenBerlinScenario.VERSION),
				"--shp", "input/gartenfeld/DNG_dilution_area.gpkg",
				"--input-crs", OpenBerlinScenario.CRS,
				"--network-modes", "car,bike",
				"--clean-modes", "truck,freight,ride",
				"--buffer", "1000",
				"--keep-capacities",
				"--output-network", network,
				"--output-population", population,
				"--output-facilities", "input/gartenfeld/gartenfeld-v6.4.facilities-cutout.xml.gz",
				"--output-network-change-events", "input/gartenfeld/gartenfeld-v6.4.network-change-events.xml.gz"
		);

		createNetwork(network, network, "input/gartenfeld/DNG_network.gpkg");
		createNetwork(berlinNetwork, fullNetwork, "input/gartenfeld/DNG_network.gpkg");

		new PersonNetworkLinkCheck().execute(
				"--input", population,
				"--network", network,
				"--output", population
		);

		new PersonNetworkLinkCheck().execute(
				"--input", fullPopulation,
				"--network", fullNetwork,
				"--output", fullPopulation
		);

		new DownSamplePopulation().execute(
				population,
				"--sample-size", "0.1",
				"--samples", "0.01"
		);

		new DownSamplePopulation().execute(
				fullPopulation,
				"--sample-size", "0.1",
				"--samples", "0.01"
		);

	}

	private static void createNetwork(String network, String outputNetwork, String shp) {

		new ModifyNetwork().execute(
				"--network", network,
				"--remove-links", "input/gartenfeld/DNG_LinksToDelete.txt",
				"--shp", shp,
				"--output", outputNetwork
		);

	}
}
