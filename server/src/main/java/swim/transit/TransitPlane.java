package swim.transit;

import java.io.IOException;
import java.io.InputStream;
import java.util.Scanner;
import java.util.logging.Logger;

import swim.api.agent.AgentRoute;
import swim.api.plane.AbstractPlane;
import swim.api.ref.WarpRef;
import swim.api.space.Space;
import swim.kernel.Kernel;
import swim.recon.Recon;
import swim.server.ServerLoader;
import swim.structure.Item;
import swim.structure.Record;
import swim.transit.agent.AgencyAgent;
import swim.transit.agent.VehicleAgent;
import swim.structure.Value;

public class TransitPlane extends AbstractPlane {
  private static final Logger log = Logger.getLogger(TransitPlane.class.getName());

  public TransitPlane() {}

  AgentRoute<AgencyAgent> agencyAgent;
  AgentRoute<VehicleAgent> vehicleAgent;

  public static void main(String[] args) {
    final Kernel kernel = ServerLoader.loadServer();
    final Space space = kernel.getSpace("transit");
    kernel.start();
    log.info("Running TransitPlane...");
    startAgencies(space);
    kernel.run(); // blocks until termination
  }

  private static void startAgencies(WarpRef warp) {
    final Value agencies = loadAgencies();
    for (Item agency : agencies) {
      log.info(Recon.toString(agency));
      String agencyUri = "/agency/" +
              "/" + agency.get("id").stringValue();
      warp.command(agencyUri, "addInfo", agency.toValue());
    }
    try {
      Thread.sleep(3000);
    } catch (InterruptedException e) {

    }
  }

  private static Value loadAgencies() {
    Record agencies = Record.of();
    InputStream is = null;
    Scanner scanner = null;
    try {
      is = TransitPlane.class.getResourceAsStream("/agencies.csv");
      scanner = new Scanner(is, "UTF-8");
      int index = 0;
      while (scanner.hasNextLine()) {
        final String[] line = scanner.nextLine().split(",");
        if (line.length >= 3) {
          Value newAgency = Record.of().slot("id", line[0]).slot("state", line[1]).slot("country", line[2]).slot("index", index++);
          agencies.item(newAgency);
        }
      }
    } catch (Throwable t) {
      log.severe(()->String.format("Exception thrown\n%s", t));
    } finally {
      try {
        if (is != null) {
          is.close();
        }
      } catch (IOException ignore) {
      }
      if (scanner != null) {
        scanner.close();
      }
    }
    return agencies;
  }
}
