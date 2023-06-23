package swim.transit;

import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Iterator;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;
import swim.api.ref.WarpRef;
import swim.codec.Utf8;
import swim.recon.Recon;
import swim.structure.Item;
import swim.structure.Record;
import swim.structure.Value;
import swim.xml.Xml;

public class NextBusHttpAPI {
    private static final Logger log = Logger.getLogger(NextBusHttpAPI.class.getName());
    private NextBusHttpAPI() { }

    public static void sendRoutes(Value agencies, WarpRef warp) {
        for (Item agency: agencies) {
            sendRoutesInternal(agency.toValue(), warp);
        }
    }

    public static void sendVehicleInfo(String pollUrl, Value agency, WarpRef warp) {
        final Value vehicles = getVehicleLocations(pollUrl, agency);
        final String agencyUri = "/agency/" +
                agency.get("country").stringValue() +
                "/" + agency.get("state").stringValue() +
                "/" + agency.get("id").stringValue();
        if (vehicles != null && vehicles.length() > 0) {
            warp.command(agencyUri, "addVehicles", vehicles);
        }
    }

    private static void sendRoutesInternal(Value agency, WarpRef warp) {
        final Value routes = getRoutes(agency);
        if (routes != null && routes.length() > 0) {
            final String agencyUri = "/agency/" +
                    agency.get("country").stringValue() +
                    "/" + agency.get("state").stringValue() +
                    "/" + agency.get("id").stringValue();
            warp.command(agencyUri, "addRoutes", routes);
        }
    }

    private static Value getRoutes(Value ag) {
        try {
            final URL url = new URL(String.format(
                    "https://retro.umoiq.com/service/publicXMLFeed?command=routeList&a=%s", ag.get("id").stringValue()));
            final Value allRoutes = parse(url);
            if (!allRoutes.isDefined()) {
                return null;
            }
            final Iterator<Item> it = allRoutes.iterator();
            final Record routes = Record.of();
            while (it.hasNext()) {
                final Item item = it.next();
                final Value header = item.getAttr("route");
                if (header.isDefined()) {
                    final Value route = Record.of()
                            .slot("tag", header.get("tag").stringValue())
                            .slot("title", header.get("title").stringValue());
                    routes.item(route);
                }
            }
            return routes;
        } catch (Exception e) {
            log.severe(() -> String.format("Exception thrown:\n%s", e));
        }
        return null;
    }

        public static Value getVehicleLocations(String pollUrl, Value ag) {
        try {
            final URL url = new URL(pollUrl);
            final Value vehicleLocs = parse(url);
            if (!vehicleLocs.isDefined()) {
                return null;
            }

            final Iterator<Item> it = vehicleLocs.iterator();
            final Record vehicles = Record.of();
            while (it.hasNext()) {
                final Item item = it.next();
                final Value header = item.getAttr("vehicle");
                if (header.isDefined()) {
                    final String id = header.get("id").stringValue().trim();
                    final String routeTag = header.get("routeTag").stringValue();
                    final float latitude = header.get("lat").floatValue(0.0f);
                    final float longitude = header.get("lon").floatValue(0.0f);
                    final int speed = header.get("speedKmHr").intValue(0);
                    final int secsSinceReport = header.get("secsSinceReport").intValue(0);
                    final String dir = header.get("dirTag").stringValue("");
                    final String dirId;
                    if (!dir.equals("")) {
                        dirId = dir.contains("_0") ? "outbound" : "inbound";
                    } else {
                        dirId = "outbound";
                    }

                    final int headingInt = header.get("heading").intValue(0);
                    String heading = "";
                    if (headingInt < 23 || headingInt >= 338) {
                        heading = "E";
                    } else if (23 <= headingInt && headingInt < 68) {
                        heading = "NE";
                    } else if (68 <= headingInt && headingInt < 113) {
                        heading = "N";
                    } else if (113 <= headingInt && headingInt < 158) {
                        heading = "NW";
                    } else if (158 <= headingInt && headingInt < 203) {
                        heading = "W";
                    } else if (203 <= headingInt && headingInt < 248) {
                        heading = "SW";
                    } else if (248 <= headingInt && headingInt < 293) {
                        heading = "S";
                    } else if (293 <= headingInt && headingInt < 338) {
                        heading = "SE";
                    }
                    final String uri = "/vehicle/" +
                            ag.get("country").stringValue() +
                            "/" + ag.get("state").stringValue() +
                            "/" + ag.get("id").stringValue() +
                            "/" + parseUri(id);
                    final Record vehicle = Record.of()
                            .slot("id", id)
                            .slot("uri", uri)
                            .slot("dirId", dirId)
                            .slot("index", ag.get("index").intValue())
                            .slot("latitude", latitude)
                            .slot("longitude", longitude)
                            .slot("routeTag", routeTag)
                            .slot("secsSinceReport", secsSinceReport)
                            .slot("speed", speed)
                            .slot("heading", heading);
                    vehicles.add(vehicle);
                }
            }
            return vehicles;
        } catch (Exception e) {
            log.severe(() -> String.format("Exception thrown:\n%s", e));
        }
        return null;
    }

    private static String parseUri(String uri) {
        try {
            return java.net.URLEncoder.encode(uri, "UTF-8").toString();
        } catch (UnsupportedEncodingException e) {
            return null;
        }
    }

    private static Value parse(URL url) {
        final HttpURLConnection urlConnection;
        try {
            urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setRequestProperty("Accept-Encoding", "gzip, deflate");
            final InputStream stream = new GZIPInputStream(urlConnection.getInputStream());
            final Value configValue = Utf8.read(stream, Xml.structureParser().documentParser());
            return configValue;
        } catch (Throwable e) {
            log.severe(() -> String.format("Exception thrown:\n%s", e));
        }
        return Value.absent();
    }

}