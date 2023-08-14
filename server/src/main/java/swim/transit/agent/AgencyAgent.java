package swim.transit.agent;

import java.util.*;
import java.util.logging.Logger;

import swim.api.SwimLane;
import swim.api.agent.AbstractAgent;
import swim.api.lane.CommandLane;
import swim.api.lane.MapLane;
import swim.api.lane.ValueLane;
import swim.concurrent.AbstractTask;
import swim.concurrent.TaskRef;
import swim.concurrent.TimerRef;
import swim.recon.Recon;
import swim.structure.Attr;
import swim.structure.Item;
import swim.structure.Record;
import swim.structure.Value;
import swim.transit.NextBusHttpAPI;
import swim.transit.Assets;

public class AgencyAgent extends AbstractAgent {
    private static final Logger log = Logger.getLogger(AgencyAgent.class.getName());
    private long lastTime = 0L; // This will update via API responses

    @SwimLane("vehicles")
    public MapLane<String, Value> vehicles = this.<String, Value>mapLane();

    @SwimLane("count")
    public ValueLane<Integer> vehicleCount;

    @SwimLane("speed")
    public ValueLane<Float> avgVehicleSpeed;

    @SwimLane("addVehicles")
    public CommandLane<Value> addVehicles = this.<Value>commandLane().onCommand(this::onVehicles);

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

        for (Value v : vehicleUpdates.values()) {
            final String vehicleUri = v.get("uri").stringValue();
            if (vehicleUri != null && !vehicleUri.equals("")) {
                context.command(vehicleUri, "addVehicle", v.toValue());
                addVehicle(vehicleUri, v);
                speedSum += v.get("speed").intValue(0);
            }
        }

        vehicleCount.set(this.vehicles.size());
        if (vehicleCount.get() > 0) {
            avgVehicleSpeed.set(((float) speedSum) / vehicleCount.get());
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
        log.info("addVehicle vehicleUri: " + vehicleUri + "; v: " + Recon.toString(v));
        Value newVehicle = Record.of()
            .slot("id", getProp("id").stringValue(""))
            .slot("uri", v.get("uri").stringValue())
            .slot("agency", info.get().get("id").stringValue())
            .slot("dirId", v.get("dirTag").stringValue())
            .slot("latitude", v.get("lat").floatValue())
            .slot("longitude", v.get("lon").floatValue())
            .slot("speed", v.get("speedKmHr").intValue(0))
            .slot("secsSinceReport", v.get("secsSinceReport").intValue())
            .slot("heading", v.get("heading").stringValue());

        this.vehicles.put(vehicleUri, newVehicle);
    }

    @SwimLane("addInfo")
    public CommandLane<Value> addInfo = this.<Value>commandLane().onCommand(this::onInfo);

    @SwimLane("info")
    public ValueLane<Value> info = this.<Value>valueLane();;

    private void onInfo(Value agency) {
        info.set(agency);
    }

    private String agencyId() {
        final String nodeUri = nodeUri().toString();
        return nodeUri.substring(nodeUri.lastIndexOf("/") + 1);
      }
    
    private TimerRef timer;
    private final TaskRef agencyPollTask = asyncStage().task(new AbstractTask() {
  
      private long lastTime = 0L; // This will update via API responses
  
      @Override
      public void runTask() {
        final String aid = agencyId();
        // Make API call
        final Value payload = NextBusHttpAPI.getVehiclesForAgency(Assets.httpClient(), aid, this.lastTime);
        // Extract information for all vehicles and the payload's timestamp
        //final List<Value> vehicleInfos = new ArrayList<>(payload.length());
        final Record vehicleInfos = Record.of();
        for (Item i : payload) {
          if (i.head() instanceof Attr) {
            final String label = i.head().key().stringValue(null);
            if ("vehicle".equals(label)) {
                final Value vehicle = i.head().toValue();
                final String vehicleUri = "/vehicle/" + aid + "/" + vehicle.get("id").stringValue();
                final Value vehicleInfo = vehicle.updatedSlot("uri", vehicleUri);
                vehicleInfos.add(vehicleInfo);
            } else if ("lastTime".equals(label)) {
              this.lastTime = i.head().toValue().get("time").longValue();
            }
          }
        }
        // Relay each vehicleInfo to the appropriate VehicleAgent
        command("/agency/" + aid, "addVehicles", vehicleInfos);
      }
  
      @Override
      public boolean taskWillBlock() {
        return true;
      }
  
    });
  
    @Override
    public void didStart() {
        vehicles.clear();
        avgVehicleSpeed.set((float) 0);
        vehicleCount.set(0);
        initPoll();
        log.info(() -> String.format("Starting Agent:%s", nodeUri()));
    }

    private void initPoll() {
        this.timer = setTimer((long) (Math.random() * 1000), () -> {
          this.agencyPollTask.cue();
          // Placing reschedule() here is like ScheduledExecutorService#scheduleAtFixedRate.
          // Moving it to the end of agencyPollTask#runTask is like #scheduleWithFixedDelay.
          this.timer.reschedule(15000L);
        });
      }
}