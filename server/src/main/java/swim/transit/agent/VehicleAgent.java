package swim.transit.agent;

import swim.api.SwimLane;
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

  @SwimLane("speeds")
  public MapLane<Long, Integer> speeds;

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
    this.speeds.put(time, newSpeed);
    if (this.speeds.size() > 10) {
      this.speeds.drop(speeds.size() - 10);
    }
    if (lastReportedTime > 0) {
      final float acceleration = (float) ((newSpeed - oldSpeed)) / (time - this.lastReportedTime) * 3600;
      this.accelerations.put(time, Math.round(acceleration));
      if (this.accelerations.size() > 10) {
        this.accelerations.drop(accelerations.size() - 10);
      }
    }
    this.lastReportedTime = time;
  }

  @Override
  public void didStart() {
    log.info(()-> String.format("Started Agent: %s", nodeUri()));
  }
}
