import 'package:flutter/material.dart';
import 'package:nsd_example_app/nsd_service.dart';

class NsdClient extends StatefulWidget {
  String serviceType;
  String serviceName;

  NsdClient({this.serviceName, this.serviceType});

  @override
  _NsdClientState createState() => _NsdClientState();
}

class _NsdClientState extends State<NsdClient> {
  bool _isRefresh;
  NetworkDiscovery nsdClient;
  List<HostInfo> infoList;

  @override
  void initState() {
    _isRefresh = true;
    nsdClient = NetworkDiscovery();
    nsdClient.startDiscovery(
        serviceNameNSD: widget.serviceName,
        serviceTypeNSD:
            widget.serviceType); //Passing NULL values takes default value
    super.initState();
    infoList = nsdClient.hostsList;
  }

  Future<bool> showConfirmation() {
    return showDialog(
      context: context,
      builder: (context) {
        return AlertDialog(
          title: Text("Are you sure?"),
          content: Text("The discover service will be stopped"),
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

  void refreshList() async {
    await Future.delayed(Duration(seconds: 3));
    setState(() {
      _isRefresh = false;
      infoList = nsdClient.hostsList;
    });
  }

  @override
  Widget build(BuildContext context) {
    if(_isRefresh) refreshList();
    return WillPopScope(
      onWillPop: showConfirmation,
      child: Scaffold(
        appBar: AppBar(
          title: Text("List of Hosts"),
          centerTitle: true,
        ),
        floatingActionButton: FloatingActionButton(
            child: Icon(Icons.refresh),
            onPressed: () {
              setState(() {
                _isRefresh = true;
              });
            }),
        body: _isRefresh
            ? Center(child: CircularProgressIndicator())
            : Container(
                padding: EdgeInsets.all(5.0),
                child: infoList.isEmpty
                    ? Center(
                        child: Text("No Hosts Found!"),
                      )
                    : ListView.builder(
                        itemCount: infoList.length,
                        itemBuilder: (context, index) {
                          return Card(
                            child: ListTile(
                              leading: Icon(Icons.phone_android),
                              title: Text(infoList[index].name),
                              subtitle: Text(infoList[index].host +
                                  " : " +
                                  infoList[index].port.toString()),
                            ),
                          );
                        }),
              ),
      ),
    );
  }

  @override
  void dispose() {
    nsdClient.stopDiscovery();
    super.dispose();
  }
}
