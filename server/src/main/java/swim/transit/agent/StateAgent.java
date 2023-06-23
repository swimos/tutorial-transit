package swim.transit.agent;

import java.util.Iterator;
import java.util.logging.Logger;

import swim.api.SwimLane;
import swim.api.SwimTransient;
import swim.api.agent.AbstractAgent;
import swim.api.lane.CommandLane;
import swim.api.lane.JoinMapLane;
import swim.api.lane.JoinValueLane;
import swim.api.lane.MapLane;
import swim.api.lane.ValueLane;
import swim.structure.Record;
import swim.structure.Value;

public class StateAgent extends AbstractAgent {
    private static final Logger log = Logger.getLogger(StateAgent.class.getName());
    @SwimLane("count")
    public ValueLane<Value> count;

    @SwimTransient
    @SwimLane("agencyCount")
    public MapLane<Value, Integer> agencyCount;

    @SwimLane("joinAgencyCount")
    public JoinValueLane<Value, Integer> joinAgencyCount = this.<Value, Integer>joinValueLane()
            .didUpdate(this::updateCounts);

    @SwimTransient
    @SwimLane("vehicles")
    public MapLane<String, Value> vehicles;

    @SwimLane("joinAgencyVehicles")
    public JoinMapLane<Value, String, Value> joinAgencyVehicles = this.<Value, String, Value>joinMapLane()
            .didUpdate((String key, Value newEntry, Value oldEntry) -> vehicles.put(key, newEntry))
            .didRemove((String key, Value vehicle) -> vehicles.remove(key));

    @SwimLane("speed")
    public ValueLane<Float> speed;

    @SwimTransient
    @SwimLane("agencySpeed")
    public MapLane<Value, Float> agencySpeed;

    @SwimLane("joinStateSpeed")
    public JoinValueLane<Value, Float> joinAgencySpeed = this.<Value, Float>joinValueLane()
            .didUpdate(this::updateSpeeds);

    @SwimLane("addAgency")
    public CommandLane<Value> agencyAdd = this.<Value>commandLane().onCommand((Value agency) -> {
        log.info("uri: " + agency.get("uri").stringValue());
        joinAgencyCount.downlink(agency).nodeUri(agency.get("uri").stringValue()).laneUri("count").open();
        joinAgencyVehicles.downlink(agency).nodeUri(agency.get("uri").stringValue()).laneUri("vehicles").open();
        joinAgencySpeed.downlink(agency).nodeUri(agency.get("uri").stringValue()).laneUri("speed").open();
        // String id, String state, String country, int index
        Record newAgency = Record.of()
                .slot("id", agency.get("id").stringValue())
                .slot("state", agency.get("state").stringValue())
                .slot("country", agency.get("country").stringValue())
                .slot("index", agency.get("index").intValue())
                .slot("stateUri", nodeUri().toString());
        context.command("/country/" + getProp("country").stringValue(), "addAgency", newAgency);
    });

    public void updateCounts(Value agency, int newCount, int oldCount) {
        int vCounts = 0;
        final Iterator<Integer> it = joinAgencyCount.valueIterator();
        while (it.hasNext()) {
            final Integer next = it.next();
            vCounts += next;
        }

        final int maxCount = Integer.max(count.get().get("max").intValue(0), vCounts);
        count.set(Record.create(2).slot("current", vCounts).slot("max", maxCount));
        agencyCount.put(agency, newCount);
    }

    public void updateSpeeds(Value agency, float newSpeed, float oldSpeed) {
        float vSpeeds = 0.0f;
        final Iterator<Float> it = joinAgencySpeed.valueIterator();
        while (it.hasNext()) {
            final Float next = it.next();
            vSpeeds += next;
        }
        if (joinAgencyCount.size() > 0) {
            speed.set(vSpeeds / joinAgencyCount.size());
        }
        agencySpeed.put(agency, newSpeed);
    }

    public void didStart() {
        vehicles.clear();
        agencyCount.clear();
        agencySpeed.clear();
        log.info(()-> String.format("Started Agent: %s", nodeUri()));
    }
}