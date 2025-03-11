package org.matsim.policies.gartenfeld;

import org.matsim.api.core.v01.Scenario;
import org.matsim.application.MATSimApplication;
import org.matsim.contrib.ev.EvConfigGroup;
import org.matsim.contrib.ev.EvModule;
import org.matsim.contrib.ev.infrastructure.ChargingInfrastructureSpecification;
import org.matsim.contrib.ev.infrastructure.ChargingInfrastructureUtils;
import org.matsim.contrib.ev.strategic.StrategicChargingUtils;
import org.matsim.contrib.ev.strategic.infrastructure.PersonChargerProvider;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.controler.Controler;

public class GartenfeldEVScenario extends GartenfeldScenario {

    public static void main(String[] args) {
        MATSimApplication.runWithDefaults(GartenfeldEVScenario.class, args,
                "--parking-garages", "NO_GARAGE",
                "--config:network.inputChangeEventsFile", "../../input/gartenfeld/gartenfeld-v6.4.network-change-events.xml.gz",
                "--config:facilities.inputFacilitiesFile", "../../input/gartenfeld/gartenfeld-v6.4.facilities.xml.gz",
                "--config:controller.runId", "gartenfeld-v6.4.cutout-base-1pct",
                "--config:network.inputNetworkFile", "../../input/gartenfeld/gartenfeld-v6.4.network-cutout.xml.gz",
                "--config:plans.inputPlansFile", "../../input/gartenfeld/gartenfeld-v6.4.population-cutout-1pct.xml.gz",
                "--config:controller.lastIteration", "500",
                "--config:controller.outputDirectory", "output/gartenfeld-v6.4.cutout-base-1pct/"
        );
    }

    @Override
    protected void prepareScenario(Scenario scenario) {
        super.prepareScenario(scenario);

        //TODO: activate strategic ev charging for (specific) persons
    }

    @Override
    protected Config prepareConfig(Config config) {
        config = super.prepareConfig(config);

        // Add the EV config group
        EvConfigGroup evConfigGroup = ConfigUtils.addOrGetModule(config, EvConfigGroup.class);
        // Set the charging infrastructure file
        evConfigGroup.chargersFile = "../../input/gartenfeld/gartenfeld-v6.4.chargers.xml.gz";

        //deletes all 'normal' MATSim replanning strategies and configures only replanning for charging
        StrategicChargingUtils.configureStanadlone(config);

        return config;
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
