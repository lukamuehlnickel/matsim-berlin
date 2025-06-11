package org.matsim.prepare.schumacher;

import org.matsim.application.MATSimAppCommand;
import org.matsim.application.prepare.population.*;
import org.matsim.prepare.population.CreateFixedPopulation;
import org.matsim.prepare.population.InitLocationChoice;
import org.matsim.prepare.population.RunActivitySampling;
import org.matsim.run.OpenBerlinScenario;
import picocli.CommandLine;

@CommandLine.Command(name = "create-schumacher-population", description = "Create the population for the Schumacher scenario.")
public class CreateSchumacherPopulation implements MATSimAppCommand {

    private static final String SVN = "../shared-svn/projects/matsim-germany";
    private static final String SRV = "../shared-svn/projects/matsim-berlin/data/SrV/converted";

    @CommandLine.Option(names = "--output", description = "Path to output population", defaultValue = "input/schumacher/schumacher-population-10pct.xml.gz")
    private String output;

    public static void main(String[] args) {
        new CreateSchumacherPopulation().execute(args);
    }

    @Override
    @SuppressWarnings("MultipleStringLiteralsExtended")
    public Integer call() throws Exception {

        new CreateFixedPopulation().execute(
                "--n", "10050",
                "--sample", "0.1",
                "--unemployed", "0.013",
                "--age-dist", "0.241", "0.065", /*Denke, dass die erste Zahl für u18 und die zweite ü65 steht*/
                "--facilities", "input/schumacherQuartier/schumacher_residential.shp",
                "--prefix", "dng",
                "--output", output
        );

        new RunActivitySampling().execute(
                "--seed", "1",
                "--persons", SRV + "/table-persons.csv",
                "--activities", SRV + "/table-activities.csv",
                "--input", output,
                "--output", output
        );

        new InitLocationChoice().execute(
                "--input", output,
                "--output", output,
                "--k", "1",
                "--facilities", "input/v%s/berlin-v%s-facilities.xml.gz".formatted(OpenBerlinScenario.VERSION, OpenBerlinScenario.VERSION),
                "--network", "input/v%s/berlin-v%s-network.xml.gz".formatted(OpenBerlinScenario.VERSION, OpenBerlinScenario.VERSION),
                "--shp", SVN + "/vg5000/vg5000_ebenen_0101/VG5000_GEM.shp",
                "--commuter", SVN + "/regionalstatistik/commuter.csv",
                "--berlin-commuter", SRV +"/berlin-work-commuter.csv",
                "--commute-prob", "0.1",
                "--sample", "0.1"
        );

        new SplitActivityTypesDuration().execute(
                "--input", output,
                "--output", output,
                "--exclude", "commercial_start,commercial_end,freight_start,freight_end"
        );

        new SetCarAvailabilityByAge().execute(
                "--input", output,
                "--output", output
        );

        new CheckCarAvailability().execute(
                "--input", output,
                "--output", output
        );

        new FixSubtourModes().execute(
                "--input", output,
                "--output", output,
                "--coord-dist", "100"
        );

        // Merge with calibrated plans into one
        new MergePopulations().execute(
                output, "input/v%s/berlin-v%s-10pct.plans.xml.gz".formatted(OpenBerlinScenario.VERSION, OpenBerlinScenario.VERSION),
                "--output", output
        );


        return 0;
    }
}
