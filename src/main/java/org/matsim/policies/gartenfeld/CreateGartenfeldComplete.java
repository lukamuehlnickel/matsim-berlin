package org.matsim.policies.gartenfeld;


import org.matsim.application.prepare.scenario.CreateScenarioCutOut;
import org.matsim.run.OpenBerlinScenario;

/**
 * Utility class to create all necessary input files for the Gartenfeld scenario.
 *
 */
public class CreateGartenfeldComplete {

	public static void main(String[] args) {

		new CreateGartenfeldNetwork().execute();

		new CreateGartenfeldPopulation().execute();

		new CreateScenarioCutOut().execute(
			"--network", "input/gartenfeld/gartenfeld-network.xml.gz",
			"--population", "input/gartenfeld/gartenfeld-population-10pct.xml.gz",
			"--facilities", "input/v%s/berlin-v%s-facilities.xml.gz".formatted(OpenBerlinScenario.VERSION, OpenBerlinScenario.VERSION),
			"--events", "https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/de/berlin/berlin-v%s/output/berlin-v%s-10pct/berlin-v%s.output_events.xml.gz".formatted(OpenBerlinScenario.VERSION, OpenBerlinScenario.VERSION, OpenBerlinScenario.VERSION),
			"--shp", "input/gartenfeld/area/gartenfeld-shape.shp",
			"--input-crs", OpenBerlinScenario.CRS,
			"--output-network", "input/gartenfeld/gartenfeld-network.xml.gz",
			"--output-population", "input/gartenfeld/gartenfeld-population-10pct.xml.gz"
		);


	}

}
