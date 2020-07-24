import 'package:flutter/material.dart';
import 'package:nsd_example_app/nsd_service.dart';

class NsdHost extends StatefulWidget {
  String deviceName;
  int port;
  String serviceType;
  String serviceName;

  NsdHost({@required this.deviceName, @required this.port, this.serviceType, this.serviceName});

  @override
  _NsdHostState createState() => _NsdHostState();
}

class _NsdHostState extends State<NsdHost> {
  NetworkDiscovery nsdHost;

  @override
  void initState() {
    nsdHost = NetworkDiscovery();
    //Passing NULL values for non-required fields takes default value
    nsdHost.startAdvertise(deviceName: widget.deviceName, port: widget.port, serviceNameNSD: widget.serviceName, serviceTypeNSD: widget.serviceType);
    super.initState();
  }

  Future<bool> showConfirmation() {
    return showDialog(
      context: context,
      builder: (context) {
        return AlertDialog(
          title: Text("Are you sure?"),
          content: Text("The Host service will be stopped"),
          actions: <Widget>[
            OutlineButton(
              child: Text("Yes"),
              onPressed: () => Navigator.of(context).pop(true),
            ),
            OutlineButton(
              child: Text("No"),
              onPressed: () => Navigator.of(context).pop(false),
            )
          ],
        );
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    return WillPopScope(
      onWillPop: showConfirmation,
      child: Scaffold(
        appBar: AppBar(
          title: Text("Service Host"),
          centerTitle: true,
        ),
        body: Container(
          padding: EdgeInsets.all(5.0),
          child: Center(
            child: Text("Service is being advertised"),
          ),
        ),
      ),
    );
  }

  @override
  void dispose() {
    nsdHost.stopAdvertise();
    super.dispose();
  }
}
