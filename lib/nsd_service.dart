import 'package:flutter/cupertino.dart';
import 'package:flutter/services.dart' show MethodChannel, MethodCall;

class NetworkDiscovery {
  MethodChannel _platform;
  MethodChannel _native;
  List<HostInfo> hostsList;
  String serviceName;
  String serviceType;

  NetworkDiscovery(){
    this.hostsList=[];
    this.serviceName = "io.irfan.NSD.service";
    this.serviceType = "_http._tcp.";

    this._platform = MethodChannel("io.irfan.NSD");
    this._native = MethodChannel("io.irfan.NSD.discovery");
    this._native.setMethodCallHandler((call) => this.discoveryHandler(call));
    this._platform.invokeMethod("initNSD");
  }

  void startDiscovery({String serviceTypeNSD, String serviceNameNSD}) {
    if(serviceTypeNSD==null) serviceTypeNSD = serviceType;
    if(serviceTypeNSD==null) serviceNameNSD = serviceName;
    this._platform.invokeMethod("discoverService",{"serviceType": serviceTypeNSD, "serviceName": serviceNameNSD});
  }

  void startAdvertise({@required String deviceName, @required int port, String serviceTypeNSD, String serviceNameNSD}) {
    if(serviceTypeNSD==null) serviceTypeNSD = serviceType;
    if(serviceTypeNSD==null) serviceNameNSD = serviceName;
    this._platform.invokeMethod("changeDeviceName", {"deviceName": deviceName});
    this._platform.invokeMethod("broadcastService", {"serviceName": serviceNameNSD, "serviceType": serviceTypeNSD, "port": port});
  }

  Future<void> discoveryHandler(MethodCall call) async {
    // type inference will work here avoiding an explicit cast
    switch (call.method) {
      case "foundHost":
        var host = HostInfo(call.arguments["name"], call.arguments["host"], call.arguments["port"]);
        if (this._checkHostExists(host) == -1) this.hostsList.add(host);
        return;
      case "lostHost":
        var host = HostInfo(call.arguments["name"], call.arguments["host"], call.arguments["port"]);
        var p = this._checkHostExists(host);
        if (p != -1) this.hostsList.removeAt(p);
        return;
    }
  }

  int _checkHostExists(HostInfo host) {
    for (var i = 0; i < hostsList.length; ++i) {
      if ((hostsList[i].name == host.name) &&
          (hostsList[i].host == host.host) &&
          (hostsList[i].port == host.port)) return i;
    }
    return -1;
  }

  void stopDiscovery(){
    this._platform.invokeMethod("stopDiscovery");
  }

  void stopAdvertise(){
    this._platform.invokeMethod("stopBroadcast");
  }

}

class HostInfo {
  int port;
  String name;
  String host;

  HostInfo(this.name, this.host, this.port);
}
