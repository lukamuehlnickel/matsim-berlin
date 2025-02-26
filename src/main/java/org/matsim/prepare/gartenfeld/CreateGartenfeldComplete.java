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
public class CreateGartenfeldComplete {

	public static void main(String[] args) {

		String full = "input/gartenfeld/gartenfeld-population-full.xml.gz";
		if (!Files.exists(Path.of(full))) {
			new CreateGartenfeldPopulation().execute(
				"--output", full
			);
		}

		String population = "input/gartenfeld/gartenfeld-population-10pct.xml.gz";
		String network = "input/gartenfeld/gartenfeld-network.xml.gz";

		new CreateScenarioCutOut().execute(
			"--network", "input/v%s/berlin-v%s-network-with-pt.xml.gz".formatted(OpenBerlinScenario.VERSION, OpenBerlinScenario.VERSION),
			"--population", full,
			"--facilities", "input/v%s/berlin-v%s-facilities.xml.gz".formatted(OpenBerlinScenario.VERSION, OpenBerlinScenario.VERSION),
			"--events", "https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/de/berlin/berlin-v%s/output/berlin-v%s-10pct/berlin-v%s.output_events.xml.gz".formatted(OpenBerlinScenario.VERSION, OpenBerlinScenario.VERSION, OpenBerlinScenario.VERSION),
			"--shp", "input/gartenfeld/DNG_dilution_area.gpkg",
			"--input-crs", OpenBerlinScenario.CRS,
			"--network-modes", "car,bike",
			"--clean-modes", "truck,freight,ride",
			"--output-network", network,
			"--output-population", population,
			"--output-facilities", "input/gartenfeld/gartenfeld-facilities.xml.gz",
			"--output-network-change-events", "input/gartenfeld/gartenfeld-network-change-events.xml.gz"
		);

		// Apply the network modifications on the cut-out scenario
		new ModifyNetwork().execute(
			"--network", network,
			"--remove-links", "input/gartenfeld/DNG_LinksToDelete.txt",
			"--shp", "input/gartenfeld/DNG_network.gpkg",
			"--output", network
		);

		new PersonNetworkLinkCheck().execute(
			"--input", population,
			"--network", network,
			"--output", population
		);

		new DownSamplePopulation().execute(
			population,
			"--sample-size", "0.1",
			"--samples", "0.01"
		);

	}

}
