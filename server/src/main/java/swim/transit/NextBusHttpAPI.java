package swim.transit;

import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Iterator;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;
import swim.api.ref.WarpRef;
import swim.codec.Utf8;
import swim.structure.Item;
import swim.structure.Record;
import swim.structure.Value;
import swim.xml.Xml;

public class NextBusHttpAPI {
    private static final Logger log = Logger.getLogger(NextBusHttpAPI.class.getName());
    
    private NextBusHttpAPI() {
    }

    private static final String ENDPOINT_FMT = "https://retro.umoiq.com/service/publicXMLFeed?command=vehicleLocations&a=%s&t=%d";

    private static String endpointForAgency(String agency, long since) {
        return String.format(ENDPOINT_FMT, agency, since);
    }

    private static HttpRequest requestForEndpoint(String endpoint) {
        return HttpRequest.newBuilder(URI.create(endpoint))
                .GET()
                .headers("Accept-Encoding", "gzip")
                .build();
    }

    public static Value getVehiclesForAgency(HttpClient executor, String agency, long since) {
        final HttpRequest request = requestForEndpoint(endpointForAgency(agency, since));
        try {
            final HttpResponse<InputStream> response = executor.send(request,
                    HttpResponse.BodyHandlers.ofInputStream());
            return Utf8.read(new GZIPInputStream(response.body()), Xml.structureParser().documentParser());
            // Alternatively: convert GZIPInputStream to String, then invoke the more
            // familiar Xml.parse()
        } catch (Exception e) {
            e.printStackTrace();
            return Value.absent();
        }
    }

}