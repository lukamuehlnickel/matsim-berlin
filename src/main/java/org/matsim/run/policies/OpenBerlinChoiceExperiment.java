package org.matsim.run.policies;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.api.core.v01.population.Person;
import org.matsim.api.core.v01.population.Plan;
import org.matsim.application.MATSimApplication;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.Controler;
import org.matsim.core.replanning.strategies.DefaultPlanStrategiesModule;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.modechoice.InformedModeChoiceConfigGroup;
import org.matsim.modechoice.InformedModeChoiceModule;
import org.matsim.modechoice.ModeOptions;
import org.matsim.modechoice.constraints.RelaxedMassConservationConstraint;
import org.matsim.modechoice.estimators.DefaultActivityEstimator;
import org.matsim.modechoice.estimators.DefaultLegScoreEstimator;
import org.matsim.modechoice.estimators.FixedCostsEstimator;
import org.matsim.modechoice.pruning.PlanScoreThresholdPruner;
import org.matsim.run.OpenBerlinScenario;
import org.matsim.run.scoring.AdvancedScoringConfigGroup;
import org.matsim.run.scoring.PseudoRandomTripScoreEstimator;
import org.matsim.vehicles.VehicleType;
import picocli.CommandLine;

import java.util.List;

/**
 * This class can be used to run some synthetic mode choice experiments on the OpenBerlin scenario.
 */
public class OpenBerlinChoiceExperiment extends OpenBerlinScenario {

	@CommandLine.Option(names = "--bike-speed-offset", description = "Offset the default bike speed in km/h", defaultValue = "0")
	private double bikeSpeedOffset;

	@CommandLine.Option(names = "--imc", description = "Enable informed-mode-choice functionality")
	private boolean imc;

	@CommandLine.Option(names = "--pruning", description = "Plan pruning threshold. If 0 pruning is disabled", defaultValue = "0")
	private double pruning;

	@CommandLine.Option(names = "--top-k", description = "Top k value to use with IMC", defaultValue = "25")
	private int topK;

	@CommandLine.Option(names = "--strategy", description = "Mode choice strategy to use (imc needs to be enabled)",
		defaultValue = InformedModeChoiceModule.SELECT_SUBTOUR_MODE_STRATEGY)
	private String strategy;

	public static void main(String[] args) {
		MATSimApplication.execute(OpenBerlinChoiceExperiment.class, args);
	}

	@Override
	protected Config prepareConfig(Config config) {

		config = super.prepareConfig(config);

		if (imc) {

			InformedModeChoiceConfigGroup imcConfig = ConfigUtils.addOrGetModule(config, InformedModeChoiceConfigGroup.class);

			imcConfig.setTopK(topK);
			imcConfig.setModes(List.of(config.subtourModeChoice().getModes()));
			imcConfig.setConstraintCheck(InformedModeChoiceConfigGroup.ConstraintCheck.repair);

			InformedModeChoiceModule.replaceReplanningStrategy(config, "person",
				DefaultPlanStrategiesModule.DefaultStrategy.SubtourModeChoice,
				strategy
			);

			if (pruning > 0)
				imcConfig.setPruning("p" + pruning);

		} else if (pruning > 0) {
			throw new IllegalArgumentException("Pruning is only available with informed-mode-choice enabled");
		}

		return config;
	}

	@Override
	protected void prepareScenario(Scenario scenario) {

		super.prepareScenario(scenario);

		// If bike speed is adjusted, we need to remove all bike routes and travel times
		// These times will be recalculated by the router
		if (bikeSpeedOffset != 0) {

			VehicleType bike = scenario.getVehicles().getVehicleTypes().get(Id.create(TransportMode.bike, VehicleType.class));
			bike.setMaximumVelocity(bike.getMaximumVelocity() + bikeSpeedOffset / 3.6);

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


	@Override
	protected void prepareControler(Controler controler) {
		super.prepareControler(controler);

		if (imc) {

			InformedModeChoiceModule.Builder builder = InformedModeChoiceModule.newBuilder()
				.withActivityEstimator(DefaultActivityEstimator.class)
				.withFixedCosts(FixedCostsEstimator.DailyConstant.class, "car", "pt")
				.withLegEstimator(DefaultLegScoreEstimator.class, ModeOptions.ConsiderIfCarAvailable.class, "car")
				// Modes with fixed costs need to be considered separately
				.withLegEstimator(DefaultLegScoreEstimator.class, ModeOptions.ConsiderYesAndNo.class, "pt")
				.withLegEstimator(DefaultLegScoreEstimator.class, ModeOptions.AlwaysAvailable.class, "walk", "bike", "ride")
				.withPruner("p" + pruning, new PlanScoreThresholdPruner(pruning))
				.withConstraint(RelaxedMassConservationConstraint.class);

			if (ConfigUtils.hasModule(controler.getConfig(), AdvancedScoringConfigGroup.class)) {
				builder.withTripScoreEstimator(PseudoRandomTripScoreEstimator.class);
			}

			controler.addOverridingModule(builder.build());
		}

	}
}
