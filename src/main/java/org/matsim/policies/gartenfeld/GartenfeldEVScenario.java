package org.matsim.policies.gartenfeld;

import com.google.common.util.concurrent.AtomicDouble;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.population.Leg;
import org.matsim.application.MATSimApplication;
import org.matsim.contrib.ev.EvConfigGroup;
import org.matsim.contrib.ev.EvModule;
import org.matsim.contrib.ev.fleet.ElectricFleetUtils;
import org.matsim.contrib.ev.strategic.StrategicChargingConfigGroup;
import org.matsim.contrib.ev.strategic.StrategicChargingUtils;
import org.matsim.contrib.ev.strategic.scoring.ChargingPlanScoringParameters;
import org.matsim.contrib.ev.withinday.WithinDayEvEngine;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.config.groups.QSimConfigGroup;
import org.matsim.core.config.groups.VspExperimentalConfigGroup;
import org.matsim.core.controler.Controler;
import org.matsim.core.router.TripStructureUtils;
import org.matsim.vehicles.Vehicle;
import org.matsim.vehicles.VehicleType;
import org.matsim.vehicles.VehicleUtils;
import org.matsim.vehicles.Vehicles;

/**
 * Scenario class for Gartenfeld with electric vehicles, extending the {@link GartenfeldScenario}.
 */
public class GartenfeldEVScenario extends GartenfeldScenario {

	private static final Logger log = LogManager.getLogger(GartenfeldEVScenario.class);

	//normally i would like to put the https URL here, but it isn't possible to have MATSim application load a config from a URL, atm.
//    private static final String SVN_OUPTUT_DIRECTORY = "https://svn.vsp.tu-berlin.de/repos/public-svn/matsim/scenarios/countries/de/gartenfeld/output/gartenfeld-v6.4-cutout-1pct/";
	private static final String SVN_OUPTUT_DIRECTORY = "D:/public-svn/matsim/scenarios/countries/de/gartenfeld/output/gartenfeld-v6.4-cutout-1pct/";

	public static void main(String[] args) {
		MATSimApplication.runWithDefaults(GartenfeldEVScenario.class, args,
			"--parking-garages", "NO_GARAGE",
			"--config", SVN_OUPTUT_DIRECTORY + "gartenfeld-v6.4-cutout-1pct.output_config.xml",
			"--config:network.inputChangeEventsFile", "gartenfeld-v6.4-cutout-1pct.output_change_events.xml.gz",
			"--config:facilities.inputFacilitiesFile", "gartenfeld-v6.4-cutout-1pct.output_facilities.xml.gz",
			"--config:controller.runId", "gartenfeld-v6.4.cutout-1pct-EV",
			"--config:network.inputNetworkFile", "gartenfeld-v6.4-cutout-1pct.output_network.xml.gz",
			"--config:plans.inputPlansFile", "gartenfeld-v6.4-cutout-1pct.output_plans.xml.gz",
			"--config:vehicles.vehiclesFile", "gartenfeld-v6.4-cutout-1pct.output_vehicles.xml.gz",
			"--config:controller.outputDirectory", "output/gartenfeld-v6.4-cutout-1pct-ev-postsim/",
			"--config:controller.lastIteration", "1",
			"--config:controller.overwriteFiles", "deleteDirectoryIfExists"
		);
	}

	@Override
	protected Config prepareConfig(Config config) {
		config = super.prepareConfig(config);

		// Add the EV config group
		EvConfigGroup evConfigGroup = ConfigUtils.addOrGetModule(config, EvConfigGroup.class);
		// Set the charging infrastructure file
		evConfigGroup.setChargersFile("../../input/chargers/gartenfeld-v6.4.chargers.xml");

		// Deletes all 'normal' MATSim replanning strategies and configures replanning for charging, only.
		// Also sets maxPlanMemorySize (for transport plans) to 1
		StrategicChargingUtils.configureStandalone(config);
		// the VSPConfigConsistencyChecker complains about us having no strategy that uses ChangeExpBeta.
		// However, here we do not want _any_ transport replanning, but only replanning of EV charging (which is why we call StrategicChargingUtils.configureStanadlone(config); above).
		// So we stop the checker from aborting
		config.vspExperimental().setVspDefaultsCheckingLevel(VspExperimentalConfigGroup.VspDefaultsCheckingLevel.warn);


		StrategicChargingConfigGroup strategicChargingConfigGroup = ConfigUtils.addOrGetModule(config, StrategicChargingConfigGroup.class);
		//TODO configure the StrategicChargingConfigGroup

		//configure the scoring parameters for the strategic charging
		ChargingPlanScoringParameters chargingScoringParameters = new ChargingPlanScoringParameters();
		//TODO configure the scoring parameters

		// TODO: this has been broken by not including the setter?
//		strategicChargingConfigGroup.scoring = chargingScoringParameters;

		// Because we take simulation output as input, all vehicles are actually created/contained in the vehicles file.
		// Later (in prepareScenario), we will just override the vehicle type for a few vehicles
		// This means we do not want to (re-) create the vehicles from the vehicle types, and we do want different vehicle types for vehicles of the same mode.
		// Thus we change the vehicles source away from QSimConfigGroup.VehiclesSource.modeVehicleTypesFromVehiclesData
		config.qsim().setVehiclesSource(QSimConfigGroup.VehiclesSource.fromVehiclesData);

		return config;
	}

	@Override
	protected void prepareScenario(Scenario scenario) {
		super.prepareScenario(scenario);

		Vehicles vehicles = scenario.getVehicles();
		//create the vehicle type for EVs
		VehicleType evType = VehicleUtils.createVehicleType(Id.create("electric_car", VehicleType.class), TransportMode.car);
		//copy all attributes from the car type
		VehicleUtils.copyFromTo(vehicles.getVehicleTypes().get(Id.create("car", VehicleType.class)), evType);
		//set ev attributes
		VehicleUtils.setHbefaTechnology(evType.getEngineInformation(), ElectricFleetUtils.EV_ENGINE_HBEFA_TECHNOLOGY);
		VehicleUtils.setEnergyCapacity(evType.getEngineInformation(), 100);

//        ElectricFleetUtils.setChargerTypes(carVehicleType, Arrays.asList("a", "b", "default" ) );

		//add the ev type
		scenario.getVehicles().addVehicleType(evType);

		//activate strategic ev charging for all Gartenfeld inhabitants and change their cars to be evs
		scenario.getPopulation().getPersons().values().stream()
			.filter(person -> person.getId().toString().contains("dng"))
			.forEach(person -> {
				WithinDayEvEngine.activate(person);
				Id<Vehicle> vehicleId = VehicleUtils.getVehicleId(person, TransportMode.car);

				//we can not override the vehicle type, so we have to recreate the vehicle.
				vehicles.removeVehicle(vehicleId);
				Vehicle newVehicle = VehicleUtils.createVehicle(vehicleId, evType);
				//TODO think about a better way to set the initial SoC
				ElectricFleetUtils.setInitialSoc(newVehicle, 0.7);
				vehicles.addVehicle(newVehicle);


				//debugging
				AtomicDouble travelTime = new AtomicDouble(0.0);
				AtomicDouble travelDistance = new AtomicDouble(0.0);

				for (Leg leg : TripStructureUtils.getLegs(person.getSelectedPlan())) {
					if (leg.getTravelTime().isUndefined()) {
						log.warn("Undefined travel time for agent {} at depTime{} with mode {}", person.getId(), leg.getDepartureTime(), leg.getMode());
					}
					travelTime.addAndGet(-leg.getTravelTime().seconds());
					travelDistance.addAndGet(-leg.getRoute().getDistance());
				}
			});
	}

	@Override
	protected void prepareControler(Controler controler) {
		super.prepareControler(controler);

		//add the basic functionality to charge and discharge vehicles and to model chargers etc.
		//actually, I think the EVBaseModule is enough, when we use the StrategicCharging module. TODO: test that
		controler.addOverridingModule(new EvModule());

		// add the strategic charging replanning
		StrategicChargingUtils.configureController(controler);
	}
}
