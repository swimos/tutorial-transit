package swim.transit.agent;

import java.util.*;
import java.util.logging.Logger;

import swim.api.SwimLane;
import swim.api.SwimTransient;
import swim.api.agent.AbstractAgent;
import swim.api.lane.CommandLane;
import swim.api.lane.MapLane;
import swim.api.lane.ValueLane;
import swim.concurrent.AbstractTask;
import swim.concurrent.TaskRef;
import swim.concurrent.TimerRef;
import swim.recon.Recon;
import swim.structure.Item;
import swim.structure.Record;
import swim.structure.Value;
import swim.transit.NextBusHttpAPI;

public class AgencyAgent extends AbstractAgent {
    private static final Logger log = Logger.getLogger(AgencyAgent.class.getName());
    @SwimTransient
    @SwimLane("vehicles")
    public MapLane<String, Value> vehicles;

    @SwimLane("count")
    public ValueLane<Integer> vehiclesCount;

    @SwimLane("speed")
    public ValueLane<Float> vehiclesSpeed;

    @SwimLane("addVehicles")
    public CommandLane<Value> addVehicles = this.<Value>commandLane().onCommand(this::onVehicles);

    @SwimLane("boundingBox")
    public ValueLane<Value> boundingBox;

    private void onVehicles(Value newVehicles) {
        if (newVehicles == null || newVehicles.length() == 0) {
            return;
        }
        Map<String, Value> vehicleUpdates = new HashMap<>();

        for (Item v : newVehicles) {
            vehicleUpdates.put(v.get("uri").stringValue(), v.toValue());
        }

        updateVehicles(vehicleUpdates);
        int speedSum = 0;
        float minLat = Integer.MAX_VALUE, minLng = Integer.MAX_VALUE, maxLat = Integer.MIN_VALUE, maxLng = Integer.MIN_VALUE;

        for (Value v : vehicleUpdates.values()) {
            final String vehicleUri = v.get("uri").stringValue();
            if (vehicleUri != null && !vehicleUri.equals("")) {
                context.command(vehicleUri, "addVehicle", v.toValue());
                addVehicle(vehicleUri, v);
                speedSum += v.get("speed").intValue();
                if (v.get("latitude").floatValue() < minLat) {
                    minLat = v.get("latitude").floatValue();
                }
                if (v.get("latitude").floatValue() > maxLat) {
                    maxLat = v.get("latitude").floatValue();
                }
                if (v.get("longitude").floatValue() < minLng) {
                    minLng = v.get("longitude").floatValue() ;
                }
                if (v.get("longitude").floatValue()  > maxLng) {
                    maxLng = v.get("longitude").floatValue() ;
                }
            }
        }

        Value bb = Record.of()
                .slot("minLat", minLat)
                .slot("maxLat", maxLat)
                .slot("minLng", minLng)
                .slot("maxLng", maxLng);

        boundingBox.set(bb);
        vehiclesCount.set(this.vehicles.size());
        if (vehiclesCount.get() > 0) {
            vehiclesSpeed.set(((float) speedSum) / vehiclesCount.get());
        }
    }

    private void updateVehicles(Map<String, Value> newVehicles) {
        final Collection<Value> currentVehicles = this.vehicles.values();
        for (Value vehicle : currentVehicles) {
            if (!newVehicles.containsKey(vehicle.get("uri").stringValue())) {
                vehicles.remove(vehicle.get("uri").stringValue());
            }
        }
    }

    private void addVehicle(String vehicleUri, Value v) {
        // log.info("addVehicle vehicleUri: " + vehicleUri + "; v: " + Recon.toString(v));
        final Value r = routes.get(v.get("routeTag").stringValue());

        if (r != null) {
             Value newVehicle = Record.of()
                     .slot("id", getProp("id").stringValue(""))
                     .slot("uri", v.get("uri").stringValue())
                     .slot("agency", info.get().get("id").stringValue())
                     .slot("routeTag", r.get("title").stringValue())
                     .slot("dirId", v.get("dirId").stringValue())
                     .slot("latitude", v.get("latitude").floatValue())
                     .slot("longitude", v.get("longitude").floatValue())
                     .slot("speed", v.get("speed").intValue())
                     .slot("secsSinceReport", v.get("secsSinceReport").intValue())
                     .slot("heading", v.get("heading").stringValue())
                     .slot("routeTitle", r.get("title"));
            this.vehicles.put(vehicleUri, newVehicle);
        }
    }

    @SwimLane("addInfo")
    public CommandLane<Value> addInfo = this.<Value>commandLane().onCommand(this::onInfo);

    @SwimLane("info")
    public ValueLane<Value> info = this.<Value>valueLane()
            .didSet((n, o) -> {
                abortPoll();
                startPoll(n);
            });

    private void onInfo(Value agency) {
        Record agencyValue = Record.of()
                .slot("id", agency.get("id").stringValue())
                .slot("state", agency.get("state").stringValue())
                .slot("country", agency.get("country").stringValue())
                .slot("index", agency.get("index").intValue())
                .slot("uri", this.nodeUri().toString());
        //final Value agencyValue = agency.unflattened().slot("agencyUri", this.nodeUri().toString());
        context.command("/state/" + agency.get("country").stringValue() + "/" + agency.get("state").stringValue(), "addAgency", agencyValue);
        info.set(agency);
    }

    @SwimLane("addRoutes")
    public CommandLane<Value> addRoutes = this.<Value>commandLane().onCommand(this::onRoutes);

    @SwimLane("routes")
    public MapLane<String, Value> routes;

    private void onRoutes(Value r) {
        for (Item route : r) {
            routes.put(route.get("tag").stringValue(), route.toValue());
        }
    }

    private TaskRef pollVehicleInfo;

    private TimerRef timer;

    private void startPoll(final Value ag) {
        abortPoll();

        // Define task
        this.pollVehicleInfo = asyncStage().task(new AbstractTask() {

            final Value agency = ag;
            final String url = String.format("https://retro.umoiq.com/service/publicXMLFeed?command=vehicleLocations&a=%s&t=0",
                    ag.get("id").stringValue());

            @Override
            public void runTask() {
                NextBusHttpAPI.sendVehicleInfo(this.url, this.agency, AgencyAgent.this.context);
            }

            @Override
            public boolean taskWillBlock() {
                return true;
            }
        });

        // Define timer to periodically reschedule task
        if (this.pollVehicleInfo != null) {
            this.timer = setTimer(1000, () -> {
                this.pollVehicleInfo.cue();
                this.timer.reschedule(POLL_INTERVAL);
            });
        }
    }

    private void abortPoll() {
        if (this.pollVehicleInfo != null) {
            this.pollVehicleInfo.cancel();
            this.pollVehicleInfo = null;
        }
        if (this.timer != null) {
            this.timer.cancel();
            this.timer = null;
        }
    }

    @Override
    public void didStart() {
        vehicles.clear();
        vehiclesSpeed.set((float) 0);
        vehiclesCount.set(0);
        log.info(() -> String.format("Starting Agent:%s", nodeUri()));
    }

    @Override
    public void willStop() {
        abortPoll();
        super.willStop();
    }

    private static final long POLL_INTERVAL = 10000L;
}