open module swim.transit {
  requires swim.xml;
  requires transitive swim.api;
  requires swim.server;
  requires java.logging;

  exports swim.transit;
  //exports swim.transit.model;

  provides swim.api.plane.Plane with swim.transit.TransitPlane;
}
