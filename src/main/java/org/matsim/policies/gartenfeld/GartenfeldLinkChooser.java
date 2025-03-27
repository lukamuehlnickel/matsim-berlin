package org.matsim.policies.gartenfeld;

import org.locationtech.jts.geom.Geometry;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.TransportMode;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.application.options.ShpOptions;
import org.matsim.core.network.NetworkUtils;
import org.matsim.core.router.MultimodalLinkChooser;
import org.matsim.core.router.RoutingRequest;
import org.matsim.core.utils.geometry.geotools.MGC;
import org.matsim.facilities.Facility;
import org.matsim.run.OpenBerlinScenario;

import java.util.Objects;

/**
 * Link chooser for the Gartenfeld scenario which forces agents to use a designated parking garage link when using a car.
 */
public class GartenfeldLinkChooser implements MultimodalLinkChooser {

	private static final Id<Link> accessLink = Id.createLinkId("network-DNG.1_r");

	private static final Id<Link> egressLink = Id.createLinkId("network-DNG.1");

	/**
	 * Geometry of the Gartenfeld area.
	 */
	private final Geometry area;

	public GartenfeldLinkChooser(ShpOptions area) {
		this.area = area.getGeometry(OpenBerlinScenario.CRS);
	}

	@Override
	public Link decideAccessLink(RoutingRequest routingRequest, String mode, Network network) {
		return decideOnLink(routingRequest.getFromFacility(), mode, network, true);
	}

	@Override
	public Link decideEgressLink(RoutingRequest routingRequest, String mode, Network network) {
		return decideOnLink(routingRequest.getToFacility(), mode, network, false);
	}

	/**
	 * Decide on the Gartenfeld specific link.
	 */
	private Link decideOnLink(Facility facility, String mode, Network network, boolean access) {

		Id<Link> targetLink = facility.getLinkId();

		Link link = network.getLinks().get(targetLink);

		// Reset the link and always search for a new link to be selected if the coordinate is within the area.
		// The provided link could be wrong already (i.e. across the water)
		if (Objects.equals(mode, TransportMode.car) && area.contains(MGC.coord2Point(facility.getCoord()))) {
			link = null;
		}

		// Select a new link
		if (link == null) {

			// The coordinate is within the gartenfeld area
			if (Objects.equals(mode, TransportMode.car) && area.contains(MGC.coord2Point(facility.getCoord()))) {
				targetLink = access ? accessLink : egressLink;
				return network.getLinks().get(targetLink);

			} else {

				// Choose the nearest link on the filtered network
				link = NetworkUtils.getNearestLink(network, facility.getCoord()) ;
			}
		}

		return Objects.requireNonNull(link, () -> "Link not found for facility " + facility);
	}

}
