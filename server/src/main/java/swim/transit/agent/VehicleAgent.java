package swim.transit.agent;

import swim.api.SwimLane;
import swim.api.SwimTransient;
import swim.api.agent.AbstractAgent;
import swim.api.lane.CommandLane;
import swim.api.lane.MapLane;
import swim.api.lane.ValueLane;
import swim.recon.Recon;
import swim.structure.Value;

import java.util.logging.Logger;

public class VehicleAgent extends AbstractAgent {
  private static final Logger log = Logger.getLogger(VehicleAgent.class.getName());
  private long lastReportedTime = 0L;

  @SwimLane("vehicle")
  public ValueLane<Value> vehicle = this.<Value>valueLane()
          .didSet((nv, ov) -> {
            log.info("vehicle changed from " + Recon.toString(nv) + " from " + Recon.toString(ov));
          });

  @SwimTransient
  @SwimLane("speeds")
  public MapLane<Long, Integer> speeds;

  @SwimTransient
  @SwimLane("accelerations")
  public MapLane<Long, Integer> accelerations;

  @SwimLane("updateVehicle")
  public CommandLane<Value> updateVehicle = this.<Value>commandLane().onCommand(this::onUpdateVehicle);

  private void onUpdateVehicle(Value v) {
    final Value oldState = vehicle.get();
    final long time = System.currentTimeMillis() - (v.get("secsSinceReport").longValue(0) * 1000L);
    final int oldSpeed = oldState.isDefined() ? oldState.get("speed").intValue(0) : 0;
    final int newSpeed = v.get("speed").intValue(0);

    this.vehicle.set(v);
    speeds.put(time, newSpeed);
    if (speeds.size() > 10) {
      speeds.drop(speeds.size() - 10);
    }
    if (lastReportedTime > 0) {
      final float acceleration = (float) ((newSpeed - oldSpeed)) / (time - lastReportedTime) * 3600;
      accelerations.put(time, Math.round(acceleration));
      if (accelerations.size() > 10) {
        accelerations.drop(accelerations.size() - 10);
      }
    }
    lastReportedTime = time;
  }

  @Override
  public void didStart() {
    log.info(()-> String.format("Started Agent: %s", nodeUri()));
  }
}
